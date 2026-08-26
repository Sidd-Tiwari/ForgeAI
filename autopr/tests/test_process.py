import os
from unittest.mock import AsyncMock, Mock, patch

import pytest

os.environ.setdefault("GITHUB_WEBHOOK_SECRET", "test-webhook-secret")
os.environ.setdefault("FORGEAI_INTERNAL_TOKEN", "test-internal-token")

from forge_autopr.main import process
from forge_autopr.models import Finding, Review


@pytest.mark.asyncio
async def test_process_posts_gateway_backed_review_with_risk_score():
    github = Mock()
    github.diff = AsyncMock(return_value="+password = 'unsafe'")
    github.post_review = AsyncMock()
    reviewer = Mock()
    reviewer.review = AsyncMock(
        return_value=(
            Review(
                summary="Remove the embedded password.",
                risk_score=7.5,
                risk_level="HIGH",
                categories={"security": 8, "correctness": 3, "performance": 0, "testing": 4},
                findings=[
                    Finding(
                        path="app.py",
                        line=3,
                        severity="HIGH",
                        title="Hard-coded password",
                        explanation="Credentials in source can leak.",
                        recommendation="Use a secret manager.",
                    )
                ],
            ),
            {
                "requestId": "gateway-request-2",
                "provider": "ANTHROPIC",
                "usage": {"estimatedCost": 0.02},
            },
        )
    )
    payload = {
        "repository": {"full_name": "forgeai/test"},
        "pull_request": {"number": 9},
        "installation": {"id": 42},
    }

    with (
        patch("forge_autopr.main.GitHubClient", return_value=github),
        patch("forge_autopr.main.Reviewer", return_value=reviewer),
    ):
        await process(payload)

    github.diff.assert_awaited_once_with("forgeai/test", 9)
    reviewer.review.assert_awaited_once_with(42, "+password = 'unsafe'")
    posted = github.post_review.await_args.args[2]
    assert "Risk: HIGH (7.5/10)" in posted
    assert "Hard-coded password" in posted
    assert "gateway-request-2" in posted
    assert "ANTHROPIC" in posted


@pytest.mark.asyncio
async def test_process_records_failure_and_propagates_error():
    github = Mock()
    github.diff = AsyncMock(side_effect=RuntimeError("GitHub unavailable"))

    with (
        patch("forge_autopr.main.GitHubClient", return_value=github),
        patch("forge_autopr.main.reviews") as review_counter,
        pytest.raises(RuntimeError, match="GitHub unavailable"),
    ):
        await process(
            {
                "repository": {"full_name": "forgeai/test"},
                "pull_request": {"number": 9},
                "installation": {"id": 42},
            }
        )

    review_counter.labels.assert_called_once_with("failure")
    review_counter.labels.return_value.inc.assert_called_once()
