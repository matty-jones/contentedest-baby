from __future__ import annotations

import logging
import os
import subprocess
from pathlib import Path

from fastapi import APIRouter, status
from fastapi.responses import FileResponse

from ..schemas import UpdateInfoResponse

router = APIRouter()
logger = logging.getLogger(__name__)


@router.get("/app/update", response_model=UpdateInfoResponse)
def get_update_info():
    base_url = os.getenv("BASE_URL", "http://192.168.86.3:8005")
    commit_message = None
    try:
        project_root = Path(__file__).parent.parent.parent.parent
        result = subprocess.run(
            ["git", "log", "-1", "--pretty=format:%s"],
            cwd=project_root,
            capture_output=True,
            text=True,
            timeout=5,
        )
        if result.returncode == 0 and result.stdout:
            commit_message = result.stdout.strip()
    except (subprocess.TimeoutExpired, subprocess.SubprocessError, Exception) as exc:
        logger.warning("Failed to get commit message: %s", exc)
        commit_message = None

    return UpdateInfoResponse(
        version_code=40,
        version_name="1.7.0",
        download_url=f"{base_url}/app/download/latest.apk",
        release_notes="Initial release",
        commit_message=commit_message,
        mandatory=False,
    )


@router.get("/app/download/{filename}")
def download_apk(filename: str):
    apk_dir = Path(__file__).parent.parent.parent / "apks"
    apk_path = apk_dir / filename

    if not apk_path.exists():
        logger.error("APK not found: %s", apk_path)
        return {"error": "APK not found"}, status.HTTP_404_NOT_FOUND

    if not apk_path.is_file():
        logger.error("Path is not a file: %s", apk_path)
        return {"error": "Invalid path"}, status.HTTP_400_BAD_REQUEST

    logger.info("Serving APK: %s", filename)
    return FileResponse(
        path=str(apk_path),
        media_type="application/vnd.android.package-archive",
        filename=filename,
    )
