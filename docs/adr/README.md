# Architecture decision records

| ADR | Decision | Status |
|---|---|---|
| 001 | Provider-neutral gateway ports and adapters | Accepted |
| 002 | Request-scoped session BYOK; no persistent secrets | Accepted |
| 003 | Mandatory tenant identity on every request | Accepted |
| 004 | Tenant-private exact caching before semantic caching | Proposed |
| 005 | PostgreSQL authoritative synchronous usage ledger | Accepted |
| 006 | Capability validation and policy-driven routing | Accepted |
| 007 | Gateway-owned, versioned prompt registry | Proposed |
| 008 | AutoPR calls only the Gateway | Accepted |
| 009 | Direct GitHub API until MCP adds measurable value | Accepted |
| 010 | Deterministic evaluation as CI baseline | Accepted |
| 011 | LLM judge as calibrated signal, never sole gate | Proposed |
| 012 | Prometheus-compatible metrics to Grafana Cloud | Accepted |

Each accepted decision is summarized with alternatives and revisit triggers in the architecture document; production changes should promote an entry into its own immutable ADR file.
