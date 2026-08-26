# Provisional service objectives

These objectives are derived from the small local verification sample on 2026-08-26. They are guardrails for the next test environment, not production SLO commitments. Recalculate them from representative traffic before attaching an error budget or paging policy.

| Signal | Observed sample | Provisional objective | Reasoning and limitation |
|---|---:|---:|---|
| Gateway provider-path p95 | 250 ms ledger latency for one isolated-provider miss | ≤375 ms | Adds 50% scheduling/ledger margin to 250 ms. One local mock call cannot represent Internet provider latency. |
| Gateway exact-cache p95 | 23 ms and 11 ms ledger latency | ≤35 ms | Adds about 52% to the slower 23 ms observation. Only two local hits were measured. |
| Exact-repeat cache hit rate | 2 hits / 3 successful requests = 66.7% | ≥60% | Keeps roughly a 10% relative margin below the controlled repeated-request result. This objective applies only to repeat-heavy verification traffic, not arbitrary production prompts. |
| Unexpected Gateway 5xx rate | 0 / 3 responses in the normal cache/rate sample | 0% in verification | The forced bad-credential case intentionally returned 502 and is excluded. There is not enough natural traffic to set a production error budget. |
| Rate-limit response | Fourth request after three accepted requests returned 429 | 100% rejection beyond configured limit | Configuration-behavior objective, not availability. Multi-replica bursts are untested. |
| Fallback recovery | One forced 503 recovered with HTTP 200 in 737 ms | Successful mapped fallback within 750 ms in the isolated harness | Only one forced local failure was observed; the threshold is not valid for commercial providers. |
| AutoPR review success/latency | No live GitHub review | Unset | Provisional alert rules exist, but no SLO is justified until real reviews are measured. |

## Alert interpretation

- `GatewayP95Latency` uses the provisional 375 ms provider-path threshold. Cache-specific latency cannot yet be selected from the standard HTTP histogram, so the 35 ms cache objective is report-only.
- `GatewayCacheHitRateLow` uses the provisional 60% exact-repeat threshold and requires at least three requests in the evaluation window.
- `GatewayHighErrorRate` remains a provisional 5% diagnostic threshold. The local sample only establishes a zero-error verification expectation; it does not justify 5% as a production budget.
- `AutoPRReviewFailures` is configuration-only until live GitHub review traffic exists.
