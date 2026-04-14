import os
import time
import uuid
import pytest
from httpx import ASGITransport, AsyncClient
from app.main import app


pytestmark = pytest.mark.asyncio

_TEST_TRANSPORT = ASGITransport(app=app)


async def test_healthz():
    async with AsyncClient(transport=_TEST_TRANSPORT, base_url="http://test") as ac:
        resp = await ac.get("/healthz")
        assert resp.status_code == 200
        assert resp.json()["status"] == "ok"


async def test_pair_and_sync_flow(monkeypatch, tmp_path):
    async with AsyncClient(transport=_TEST_TRANSPORT, base_url="http://test") as ac:
        device_id = f"dev-{uuid.uuid4()}"
        r = await ac.post(
            "/pair",
            json={"pairing_code": "abc", "device_id": device_id, "name": "Phone"},
        )
        assert r.status_code == 200
        token = r.json()["token"]

        now = int(time.time())
        ev_id = str(uuid.uuid4())
        event = {
            "event_id": ev_id,
            "type": "sleep",
            "payload": {"note": "nap"},
            "start_ts": now - 3600,
            "end_ts": now - 3000,
            "ts": None,
            "created_ts": now - 3600,
            "updated_ts": now - 3000,
            "version": 1,
            "deleted": False,
            "device_id": device_id,
        }
        headers = {"Authorization": f"Bearer {token}"}

        r2 = await ac.post("/sync/push", json=[event], headers=headers)
        assert r2.status_code == 200
        push_payload = r2.json()
        assert push_payload["server_clock"] >= 1
        assert len(push_payload["results"]) == 1

        r3 = await ac.get("/sync/pull?since=0", headers=headers)
        assert r3.status_code == 200
        pulled = r3.json()
        assert any(e["event_id"] == ev_id for e in pulled["events"])

        event_low = dict(event)
        event_low["version"] = 0
        r4 = await ac.post("/sync/push", json=[event_low], headers=headers)
        assert r4.status_code == 200

        event_high = dict(event)
        event_high["version"] = 2
        event_high["updated_ts"] = now
        r5 = await ac.post("/sync/push", json=[event_high], headers=headers)
        assert r5.status_code == 200

        r6 = await ac.get("/sync/pull?since=0", headers=headers)
        latest = [e for e in r6.json()["events"] if e["event_id"] == ev_id][0]
        assert latest["version"] == 2


async def test_sync_push_merges_adjacent_closed_sleep_events():
    async with AsyncClient(transport=_TEST_TRANSPORT, base_url="http://test") as ac:
        device_id = f"merge-dev-{uuid.uuid4()}"
        pair = await ac.post(
            "/pair",
            json={"pairing_code": "abc", "device_id": device_id, "name": "Phone"},
        )
        assert pair.status_code == 200
        token = pair.json()["token"]
        headers = {"Authorization": f"Bearer {token}"}

        now = int(time.time())
        first_id = str(uuid.uuid4())
        second_id = str(uuid.uuid4())

        first = {
            "event_id": first_id,
            "type": "sleep",
            "payload": {"note": "segment-1"},
            "start_ts": now - 600,
            "end_ts": now - 300,
            "ts": None,
            "created_ts": now - 600,
            "updated_ts": now - 300,
            "version": 1,
            "deleted": False,
            "device_id": device_id,
        }
        second = {
            "event_id": second_id,
            "type": "sleep",
            "payload": {"note": "segment-2"},
            "start_ts": now - 270,
            "end_ts": now - 120,
            "ts": None,
            "created_ts": now - 270,
            "updated_ts": now - 120,
            "version": 1,
            "deleted": False,
            "device_id": device_id,
        }

        r1 = await ac.post("/sync/push", json=[first], headers=headers)
        assert r1.status_code == 200
        r2 = await ac.post("/sync/push", json=[second], headers=headers)
        assert r2.status_code == 200

        pull = await ac.get("/sync/pull?since=0", headers=headers)
        assert pull.status_code == 200
        events = pull.json()["events"]

        relevant = {e["event_id"]: e for e in events if e["event_id"] in {first_id, second_id}}
        assert set(relevant.keys()) == {first_id, second_id}
        assert relevant[first_id]["deleted"] is False
        assert relevant[first_id]["start_ts"] == first["start_ts"]
        assert relevant[first_id]["end_ts"] == second["end_ts"]
        assert relevant[second_id]["deleted"] is True


async def test_growth_conflict_resolution_and_pull_filters():
    async with AsyncClient(transport=_TEST_TRANSPORT, base_url="http://test") as ac:
        now = int(time.time())
        growth_id = str(uuid.uuid4())
        device_a = f"growth-dev-a-{uuid.uuid4()}"
        device_b = f"growth-dev-b-{uuid.uuid4()}"

        first = {
            "id": growth_id,
            "device_id": device_a,
            "category": "weight",
            "value": 12.5,
            "unit": "lb",
            "ts": now - 120,
            "created_ts": now - 120,
            "updated_ts": now - 120,
            "version": 1,
            "deleted": False,
        }
        r1 = await ac.post("/growth", json=first)
        assert r1.status_code == 200
        first_payload = r1.json()
        assert first_payload["applied"] is True
        initial_clock = first_payload["server_clock"]

        stale = dict(first)
        stale["value"] = 9.9
        stale["updated_ts"] = now - 119
        stale["version"] = 0
        stale["device_id"] = device_b
        r2 = await ac.post("/growth", json=stale)
        assert r2.status_code == 200
        stale_payload = r2.json()
        assert stale_payload["data"]["value"] == first["value"]
        assert stale_payload["data"]["version"] == first["version"]
        assert stale_payload["server_clock"] == initial_clock

        newer = dict(first)
        newer["value"] = 13.2
        newer["updated_ts"] = now
        newer["version"] = 2
        newer["device_id"] = device_b
        r3 = await ac.post("/growth", json=newer)
        assert r3.status_code == 200
        latest_payload = r3.json()
        assert latest_payload["data"]["value"] == newer["value"]
        assert latest_payload["data"]["version"] == newer["version"]
        assert latest_payload["server_clock"] > initial_clock

        r4 = await ac.get("/growth?since=0&category=weight")
        assert r4.status_code == 200
        rows = [d for d in r4.json()["data"] if d["id"] == growth_id]
        assert len(rows) == 1
        assert rows[0]["value"] == newer["value"]

        r4b = await ac.get("/growth?since=0")
        assert r4b.status_code == 200
        all_rows = [d for d in r4b.json()["data"] if d["id"] == growth_id]
        assert len(all_rows) == 1
        assert all_rows[0]["value"] == newer["value"]

        r5 = await ac.get(f"/growth?since={initial_clock}")
        assert r5.status_code == 200
        changed = [d for d in r5.json()["data"] if d["id"] == growth_id]
        assert len(changed) == 1
        assert changed[0]["version"] == newer["version"]


async def test_words_conflict_resolution_and_pull_filters():
    async with AsyncClient(transport=_TEST_TRANSPORT, base_url="http://test") as ac:
        now = int(time.time())
        word_id = str(uuid.uuid4())
        device_a = f"words-dev-a-{uuid.uuid4()}"
        device_b = f"words-dev-b-{uuid.uuid4()}"

        first = {
            "id": word_id,
            "device_id": device_a,
            "word": "mama",
            "ts": now - 120,
            "created_ts": now - 120,
            "updated_ts": now - 120,
            "version": 1,
            "deleted": False,
        }
        r1 = await ac.post("/words", json=first)
        assert r1.status_code == 200
        first_payload = r1.json()
        assert first_payload["applied"] is True
        initial_clock = first_payload["server_clock"]

        stale = dict(first)
        stale["word"] = "dada"
        stale["updated_ts"] = now - 119
        stale["version"] = 0
        stale["device_id"] = device_b
        r2 = await ac.post("/words", json=stale)
        assert r2.status_code == 200
        stale_payload = r2.json()
        assert stale_payload["data"]["word"] == first["word"]
        assert stale_payload["data"]["version"] == first["version"]
        assert stale_payload["server_clock"] == initial_clock

        newer = dict(first)
        newer["word"] = "hello"
        newer["updated_ts"] = now
        newer["version"] = 2
        newer["device_id"] = device_b
        r3 = await ac.post("/words", json=newer)
        assert r3.status_code == 200
        latest_payload = r3.json()
        assert latest_payload["data"]["word"] == newer["word"]
        assert latest_payload["data"]["version"] == newer["version"]
        assert latest_payload["server_clock"] > initial_clock

        r4 = await ac.get("/words?since=0")
        assert r4.status_code == 200
        rows = [d for d in r4.json()["data"] if d["id"] == word_id]
        assert len(rows) == 1
        assert rows[0]["word"] == newer["word"]

        r5 = await ac.get(f"/words?since={initial_clock}")
        assert r5.status_code == 200
        changed = [d for d in r5.json()["data"] if d["id"] == word_id]
        assert len(changed) == 1
        assert changed[0]["version"] == newer["version"]


async def test_webhook_occupied_creates_then_idempotent():
    device_id = f"webhook-test-{uuid.uuid4()}"
    async with AsyncClient(transport=_TEST_TRANSPORT, base_url="http://test") as ac:
        r1 = await ac.post("/webhook/crib", json={"state": "occupied", "device_id": device_id})
        assert r1.status_code == 200
        data1 = r1.json()
        assert data1["action"] == "created"
        assert data1["event_id"] is not None
        event_id = data1["event_id"]

        r2 = await ac.post("/webhook/crib", json={"state": "occupied", "device_id": device_id})
        assert r2.status_code == 200
        data2 = r2.json()
        assert data2["action"] == "already_recording"
        assert data2["event_id"] == event_id


async def test_webhook_empty_closes_then_idempotent(monkeypatch):
    """Advance wall clock by 400s between occupied and empty so duration exceeds 5 min discard."""
    t = [1_700_000_000]

    def fake_time():
        r = t[0]
        t[0] += 400
        return r

    monkeypatch.setattr(time, "time", fake_time)
    device_id = f"webhook-test-{uuid.uuid4()}"
    async with AsyncClient(transport=_TEST_TRANSPORT, base_url="http://test") as ac:
        await ac.post("/webhook/crib", json={"state": "occupied", "device_id": device_id})
        r1 = await ac.post("/webhook/crib", json={"state": "empty", "device_id": device_id})
        assert r1.status_code == 200
        data1 = r1.json()
        assert data1["action"] == "closed"
        assert data1["event_id"] is not None

        r2 = await ac.post("/webhook/crib", json={"state": "empty", "device_id": device_id})
        assert r2.status_code == 200
        data2 = r2.json()
        assert data2["action"] == "no_open_sleep"


async def test_webhook_short_sleep_discarded(monkeypatch):
    t = [1_800_000_000]

    def fake_time():
        r = t[0]
        t[0] += 60
        return r

    monkeypatch.setattr(time, "time", fake_time)
    device_id = f"webhook-test-{uuid.uuid4()}"
    async with AsyncClient(transport=_TEST_TRANSPORT, base_url="http://test") as ac:
        await ac.post("/webhook/crib", json={"state": "occupied", "device_id": device_id})
        r1 = await ac.post("/webhook/crib", json={"state": "empty", "device_id": device_id})
        assert r1.status_code == 200
        assert r1.json()["action"] == "discarded"
        assert r1.json()["event_id"] is not None


async def test_webhook_empty_no_open_sleep():
    async with AsyncClient(transport=_TEST_TRANSPORT, base_url="http://test") as ac:
        r = await ac.post(
            "/webhook/crib",
            json={"state": "empty", "device_id": "nonexistent-device-no-open-sleep"},
        )
        assert r.status_code == 200
        assert r.json()["action"] == "no_open_sleep"


async def test_webhook_uses_default_device_id(monkeypatch):
    monkeypatch.delenv("CRIB_WEBHOOK_DEVICE_ID", raising=False)
    async with AsyncClient(transport=_TEST_TRANSPORT, base_url="http://test") as ac:
        await ac.post("/webhook/crib", json={"state": "empty"})
        r1 = await ac.post("/webhook/crib", json={"state": "occupied"})
        assert r1.status_code == 200
        data1 = r1.json()
        assert data1["action"] == "created"
        assert data1["event_id"] is not None
        
        r2 = await ac.post("/webhook/crib", json={"state": "occupied"})
        assert r2.status_code == 200
        data2 = r2.json()
        assert data2["action"] == "already_recording"
        assert data2["event_id"] == data1["event_id"]


async def test_webhook_validation_invalid_state():
    device_id = f"webhook-test-{uuid.uuid4()}"
    async with AsyncClient(transport=_TEST_TRANSPORT, base_url="http://test") as ac:
        r = await ac.post(
            "/webhook/crib",
            json={"state": "invalid", "device_id": device_id},
        )
        assert r.status_code == 422


async def test_webhook_auth_required_when_secret_set(monkeypatch):
    device_id = f"webhook-test-{uuid.uuid4()}"
    monkeypatch.setitem(os.environ, "CRIB_WEBHOOK_SECRET", "test-secret")
    try:
        async with AsyncClient(transport=_TEST_TRANSPORT, base_url="http://test") as ac:
            r = await ac.post(
                "/webhook/crib",
                json={"state": "occupied", "device_id": device_id},
            )
            assert r.status_code == 401
            r2 = await ac.post(
                "/webhook/crib",
                json={"state": "occupied", "device_id": device_id},
                headers={"X-Webhook-Secret": "wrong"},
            )
            assert r2.status_code == 401
            r3 = await ac.post(
                "/webhook/crib",
                json={"state": "occupied", "device_id": device_id},
                headers={"X-Webhook-Secret": "test-secret"},
            )
            assert r3.status_code == 200
            assert r3.json()["action"] == "created"
    finally:
        monkeypatch.delenv("CRIB_WEBHOOK_SECRET", raising=False)


