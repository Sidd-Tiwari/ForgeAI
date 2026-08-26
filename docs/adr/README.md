# Architecture decision records

| ADR | Decision | Status | Implementation evidence or gap |
|---|---|---|---|
| 001 | Provider-neutral gateway ports and adapters | Implemented | OpenAI, Anthropic, and Gemini adapter contracts are tested; live APIs remain unverified. |
| 002 | Request-scoped session BYOK; no persistent secrets | Implemented locally | Credential buffers are closed/zeroed; runtime log, metric, response, ledger, and cache checks use non-production keys. Hosting logs/APM remain unverified. |
| 003 | Mandatory tenant identity on every Gateway request | Implemented | Controller requires `X-Tenant-Id`; AutoPR derives the tenant from GitHub installation ID. |
| 004 | Tenant-private exact caching before semantic caching | Implemented | Redis identity hashes tenant, provider, and request; unit and Compose isolation checks pass. Semantic caching is not implemented. |
| 005 | PostgreSQL authoritative synchronous usage ledger | Partially implemented | Successful provider, cache, and fallback results are synchronous and durable. Rejections and failed provider attempts are not ledgered. |
| 006 | Capability validation and policy-driven routing | Partially implemented | Explicit/capability selection plus configured mapped fallback exist. Cheapest/fastest policies and calibrated routing data do not. |
| 007 | Gateway-owned, versioned prompt registry | Proposed | `promptId` is accepted but no registry or experiment assignment exists. |
| 008 | AutoPR calls only the Gateway | Implemented and contract-tested | Reviewer constructs only `/v1/generate` calls; a real GitHub review and matching live ledger row remain blocked. |
| 009 | Direct GitHub API until MCP adds measurable value | Implemented and mocked | Diff and review endpoints are covered with mocked HTTP clients; live GitHub is unverified. |
| 010 | Deterministic evaluation as CI baseline | Implemented | CLI baseline exits 0 and a critical-miss regression exits 1. Live-review input is blocked. |
| 011 | LLM judge as calibrated signal, never sole gate | Proposed | No judge implementation or calibration dataset exists. |
| 012 | Prometheus-compatible metrics with planned Grafana Cloud export | Partially implemented | Local Prometheus endpoints and rule syntax are verified. Remote-write and hosted alert delivery are unverified. |

Implemented and partial decisions are summarized with trade-offs and revisit triggers in the architecture document. Before a production release, each material decision should be promoted into its own immutable ADR file with approvers and supersession history.
