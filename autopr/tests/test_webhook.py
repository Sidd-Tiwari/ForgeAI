import hashlib
import hmac
import json
import os
from unittest.mock import AsyncMock, patch

os.environ.setdefault("GITHUB_WEBHOOK_SECRET", "test-webhook-secret")
os.environ.setdefault("FORGEAI_INTERNAL_TOKEN", "test-internal-token")

from fastapi.testclient import TestClient

from forge_autopr.main import app


def signed(body: bytes, secret: str = "test-webhook-secret") -> str:
    return "sha256=" + hmac.new(secret.encode(), body, hashlib.sha256).hexdigest()


def pull_request_body() -> bytes:
    return json.dumps({
        "action": "opened",
        "repository": {"full_name": "forgeai/test"},
        "pull_request": {"number": 7},
        "installation": {"id": 42},
    }).encode()


def test_webhook_accepts_signed_pull_request_and_schedules_review():
    body = pull_request_body()
    process = AsyncMock()
    with patch("forge_autopr.main.process", process):
        response = TestClient(app).post("/webhooks/github", content=body, headers={
            "X-Hub-Signature-256": signed(body), "X-GitHub-Event": "pull_request",
            "Content-Type": "application/json",
        })
    assert response.status_code == 202
    assert response.json() == {"accepted": True}
    process.assert_awaited_once()


def test_webhook_rejects_bad_signature():
    body = pull_request_body()
    response = TestClient(app).post("/webhooks/github", content=body, headers={
        "X-Hub-Signature-256": "sha256=" + "0" * 64,
        "X-GitHub-Event": "pull_request",
    })
    assert response.status_code == 401
    assert response.json() == {"detail": "Invalid webhook signature"}


def test_webhook_rejects_malformed_signed_json():
    body = b"{not-json"
    response = TestClient(app).post("/webhooks/github", content=body, headers={
        "X-Hub-Signature-256": signed(body), "X-GitHub-Event": "pull_request",
    })
    assert response.status_code == 400
    assert response.json() == {"detail": "Malformed webhook JSON"}


def test_webhook_returns_server_error_when_scheduler_fails():
    body = pull_request_body()
    with patch("fastapi.BackgroundTasks.add_task", side_effect=RuntimeError("scheduler failed")):
        response = TestClient(app, raise_server_exceptions=False).post(
            "/webhooks/github", content=body, headers={
                "X-Hub-Signature-256": signed(body), "X-GitHub-Event": "pull_request",
            })
    assert response.status_code == 500
