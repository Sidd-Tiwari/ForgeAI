from unittest.mock import AsyncMock, Mock, patch

import pytest

from forge_autopr.github import GitHubClient


@pytest.mark.asyncio
async def test_github_client_fetches_diff_and_posts_comment_review():
    transport = Mock()
    diff_response = Mock(text="diff --git a/app.py b/app.py")
    review_response = Mock()
    transport.get = AsyncMock(return_value=diff_response)
    transport.post = AsyncMock(return_value=review_response)

    with patch("forge_autopr.github.httpx.AsyncClient", return_value=transport) as client_type:
        client = GitHubClient("github-token")
        diff = await client.diff("forgeai/test", 7)
        await client.post_review("forgeai/test", 7, "review body")

    assert diff == "diff --git a/app.py b/app.py"
    client_type.assert_called_once()
    assert client_type.call_args.kwargs["headers"]["Authorization"] == "Bearer github-token"
    transport.get.assert_awaited_once_with(
        "/repos/forgeai/test/pulls/7",
        headers={"Accept": "application/vnd.github.v3.diff"},
    )
    transport.post.assert_awaited_once_with(
        "/repos/forgeai/test/pulls/7/reviews",
        json={"body": "review body", "event": "COMMENT"},
    )
    diff_response.raise_for_status.assert_called_once()
    review_response.raise_for_status.assert_called_once()
