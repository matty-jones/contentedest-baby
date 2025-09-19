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


