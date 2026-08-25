import httpx


class GitHubClient:
    def __init__(self, token: str):
        self.client = httpx.AsyncClient(base_url="https://api.github.com", headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}, timeout=30)

    async def diff(self, repo: str, number: int) -> str:
        response = await self.client.get(f"/repos/{repo}/pulls/{number}", headers={"Accept": "application/vnd.github.v3.diff"})
        response.raise_for_status()
        return response.text

    async def post_review(self, repo: str, number: int, body: str) -> None:
        response = await self.client.post(f"/repos/{repo}/pulls/{number}/reviews", json={"body": body, "event": "COMMENT"})
        response.raise_for_status()
