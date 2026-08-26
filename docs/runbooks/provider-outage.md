# Verified incident response

This runbook covers only failure modes exercised against the local Compose stack. Times and thresholds are verification baselines, not commercial-provider guarantees. Never enable HTTP header/body logging or paste credentials, prompts, or diffs into an incident channel.

## Provider 503, retry, and mapped fallback

Observed on 2026-08-26 with the isolated provider harness:

- OpenAI returned a forced HTTP 503.
- Gateway logged one retry and then `provider_fallback from=OPENAI to=ANTHROPIC`.
- The client received HTTP 200 in 737 ms from configured model `claude-mock`.
- PostgreSQL recorded one successful Anthropic row with `fallback_used=true`, `credential_source=PLATFORM`, and no credential material.

Operator response:

1. Search application logs for `provider_retry`, `provider_fallback`, and `provider_failure`; record provider names, error classes, timestamps, and correlation IDs only.
2. Query recent ledger rows for `fallback_used=true`. Confirm the selected fallback model and credential ownership are expected for the affected tenant/application.
3. If fallback succeeds, treat the incident as degraded service and watch latency/cost. Do not reuse a request BYOK key across providers; the implementation intentionally requires a configured platform key for fallback.
4. If fallback does not occur, confirm the request used `AUTO` or `FALLBACK`, the alternate model mapping is configured, the alternate adapter supports that model, and a platform credential exists.
5. After upstream recovery, send an authorized synthetic request through the normal Gateway route and confirm the ledger result. Automatic failback timing and commercial-provider recovery have not been tested, so require operator review before closing the incident.

## Per-tenant rate limit exceeded

Observed on 2026-08-26 with a three-request/minute verification limit:

- Three requests returned HTTP 200; the fourth returned HTTP 429.
- The first request wrote a provider ledger row; the next two wrote cache rows.
- No ledger row was written for the rejected fourth request because rejection occurs before provider/cache execution.

Operator response:

1. Confirm the response is the Gateway `Rate limit exceeded` problem response rather than a provider 429.
2. Confirm `GATEWAY_REQUESTS_PER_MINUTE` matches the deployed policy. Do not raise it until the tenant and workload are identified.
3. Ask the client to honor the one-minute window and apply backoff. A manual Redis-key deletion is not an approved recovery action.
4. Correlate accepted requests through the ledger; absence of a rejected-request ledger row is currently expected.
5. Record that rejection-specific metrics and cross-replica burst/load behavior are untested observability gaps.

## Unsigned GitHub webhook

Observed on 2026-08-26:

- A pull-request payload without `X-Hub-Signature-256` returned HTTP 401.
- No review task, GitHub API call, or Gateway request was scheduled.

Operator response:

1. Confirm GitHub is configured with the same webhook secret injected into AutoPR and sends `X-Hub-Signature-256`.
2. Retry delivery from GitHub only after configuration is corrected; never bypass signature verification.
3. Repeated 401 responses currently require access-log inspection because a dedicated invalid-signature metric has not been implemented.

## Untested failure modes

The following have not been exercised end-to-end and therefore have no confident recovery procedure yet: real provider outage/recovery, PostgreSQL outage, Redis outage, GitHub API outage, webhook-secret rotation, multi-replica rate limiting, hosted alert delivery, cloud rollback, and migration recovery. Exercise each in a controlled environment before promoting a hypothetical procedure into this runbook.
