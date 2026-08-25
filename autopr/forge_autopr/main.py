import json
from fastapi import BackgroundTasks, FastAPI, Header, HTTPException, Request, Response
from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest
from .config import Settings
from .github import GitHubClient
from .reviewer import Reviewer
from .security import verify_signature

app = FastAPI(title="ForgeAI AutoPR", version="0.1.0", docs_url=None, redoc_url=None)
settings = Settings()
reviews = Counter("autopr_reviews_total", "Reviews", ["status"])
duration = Histogram("autopr_review_duration_seconds", "Review duration")

async def process(payload: dict) -> None:
    with duration.time():
        try:
            repo = payload["repository"]["full_name"]
            number = payload["pull_request"]["number"]
            installation = payload["installation"]["id"]
            github = GitHubClient(settings.github_token)
            diff = (await github.diff(repo, number))[: settings.max_diff_bytes]
            review, envelope = await Reviewer(settings.gateway_url, settings.forgeai_internal_token, settings.review_model).review(installation, diff)
            body = f"## ForgeAI review\n\n**Risk: {review.risk_level} ({review.risk_score:.1f}/10)**\n\n{review.summary}\n\n" + "\n".join(f"- **{f.severity}: {f.title}** — {f.explanation} _{f.recommendation}_" for f in review.findings) + f"\n\n_Request `{envelope['requestId']}` · {envelope['provider']} · estimated ${envelope['usage']['estimatedCost']}_"
            await github.post_review(repo, number, body)
            reviews.labels("success").inc()
        except Exception:
            reviews.labels("failure").inc()
            raise

@app.post("/webhooks/github", status_code=202)
async def webhook(request: Request, tasks: BackgroundTasks, x_hub_signature_256: str | None = Header(None), x_github_event: str | None = Header(None)) -> dict:
    body = await request.body()
    if not verify_signature(settings.github_webhook_secret, body, x_hub_signature_256):
        raise HTTPException(401, "Invalid webhook signature")
    payload = json.loads(body)
    if x_github_event == "pull_request" and payload.get("action") in {"opened", "synchronize", "reopened"}:
        tasks.add_task(process, payload)
    return {"accepted": True}

@app.get("/healthz")
def health() -> dict: return {"status": "ok"}

@app.get("/metrics")
def metrics() -> Response: return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
