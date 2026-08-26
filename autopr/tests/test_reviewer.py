import json
from unittest.mock import patch

import pytest

from forge_autopr.reviewer import Reviewer


class GatewayResponse:
    def __init__(self, envelope: dict):
        self.envelope = envelope

    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict:
        return self.envelope


class GatewayClient:
    def __init__(self, envelope: dict):
        self.envelope = envelope
        self.calls: list[tuple[str, dict]] = []

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_args):
        return None

    async def post(self, url: str, **kwargs):
        self.calls.append((url, kwargs))
        return GatewayResponse(self.envelope)


@pytest.mark.asyncio
async def test_reviewer_routes_through_gateway_and_applies_risk_floor():
    review = {
        "summary": "Credential handling needs attention",
        "risk_score": 0.5,
        "risk_level": "HIGH",
        "categories": {"security": 4, "correctness": 1, "performance": 0, "testing": 2},
        "findings": [
            {
                "path": "app.py",
                "line": 4,
                "severity": "HIGH",
                "title": "Hard-coded secret",
                "explanation": "A secret is embedded in source.",
                "recommendation": "Load it from a secret manager.",
            }
        ],
    }
    envelope = {
        "requestId": "gateway-request-1",
        "provider": "ANTHROPIC",
        "content": f"```json\n{json.dumps(review)}\n```",
        "usage": {"estimatedCost": 0.01},
    }
    client = GatewayClient(envelope)
    diff = "+password = 'unsafe'\n+authorization = 'unsafe'"

    with patch("forge_autopr.reviewer.httpx.AsyncClient", return_value=client):
        result, returned_envelope = await Reviewer(
            "http://gateway:8080", "internal-token", "claude-test"
        ).review(42, diff)

    assert result.risk_score == 3.0
    assert returned_envelope == envelope
    assert len(client.calls) == 1
    url, request = client.calls[0]
    assert url == "http://gateway:8080/v1/generate"
    assert request["headers"] == {
        "X-Tenant-Id": "github-installation-42",
        "X-Application": "autopr",
        "Authorization": "Bearer internal-token",
    }
    assert "X-Provider-Key" not in request["headers"]
    assert request["json"]["routingMode"] == "AUTO"


def test_signals_detect_large_sensitive_diff_and_test_changes():
    diff = "\n".join(["+value = 1"] * 501 + ["+API_KEY = 'unsafe'", "+test_value = 2"])

    signals = Reviewer._signals(diff)

    assert signals == {
        "additions": 503,
        "sensitive_terms": 1,
        "tests_changed": True,
        "risk_floor": 3.5,
    }
