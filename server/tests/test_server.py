import os
import time
import uuid
import pytest
from httpx import AsyncClient
from app.main import app


pytestmark = pytest.mark.asyncio


async def test_healthz():
    async with AsyncClient(app=app, base_url="http://test") as ac:
        resp = await ac.get("/healthz")
        assert resp.status_code == 200
        assert resp.json()["status"] == "ok"


async def test_pair_and_sync_flow(monkeypatch, tmp_path):
    async with AsyncClient(app=app, base_url="http://test") as ac:
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


async def test_webhook_occupied_creates_then_idempotent():
    device_id = f"webhook-test-{uuid.uuid4()}"
    async with AsyncClient(app=app, base_url="http://test") as ac:
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


async def test_webhook_empty_closes_then_idempotent():
    device_id = f"webhook-test-{uuid.uuid4()}"
    async with AsyncClient(app=app, base_url="http://test") as ac:
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


async def test_webhook_empty_no_open_sleep():
    async with AsyncClient(app=app, base_url="http://test") as ac:
        r = await ac.post(
            "/webhook/crib",
            json={"state": "empty", "device_id": "nonexistent-device-no-open-sleep"},
        )
        assert r.status_code == 200
        assert r.json()["action"] == "no_open_sleep"


async def test_webhook_uses_default_device_id(monkeypatch):
    monkeypatch.delenv("CRIB_WEBHOOK_DEVICE_ID", raising=False)
    async with AsyncClient(app=app, base_url="http://test") as ac:
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
    async with AsyncClient(app=app, base_url="http://test") as ac:
        r = await ac.post(
            "/webhook/crib",
            json={"state": "invalid", "device_id": device_id},
        )
        assert r.status_code == 422


async def test_webhook_auth_required_when_secret_set(monkeypatch):
    device_id = f"webhook-test-{uuid.uuid4()}"
    monkeypatch.setitem(os.environ, "CRIB_WEBHOOK_SECRET", "test-secret")
    try:
        async with AsyncClient(app=app, base_url="http://test") as ac:
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


