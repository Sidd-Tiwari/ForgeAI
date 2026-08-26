# Architecture and data flows

## C4 — containers

```mermaid
C4Container
  Person(user, "Developer", "Uses BYOK and reviews")
  System_Ext(github, "GitHub", "PRs, webhooks, reviews")
  System_Ext(llms, "LLM providers", "OpenAI, Anthropic, Gemini")
  System_Ext(grafana, "Grafana Cloud (planned integration)", "Metrics, logs, alerts")
  System_Boundary(forge, "ForgeAI") {
    Container(ui, "Control plane", "HTML/CSS", "Operational views; never renders credentials")
    Container(auto, "AutoPR", "FastAPI", "Verifies events and orchestrates review")
    Container(gw, "Gateway", "Spring Boot", "Credentials, routing, resilience, ledger")
    Container(eval, "Evaluation", "Python package/CLI", "Deterministic scoring; calibrated judge planned")
    ContainerDb(pg, "PostgreSQL", "Durable ledger and configuration")
    ContainerDb(redis, "Redis", "Rate limits and tenant-private cache")
  }
  Rel(user, ui, "HTTPS")
  Rel(github, auto, "Signed webhook")
  Rel(auto, gw, "Normalized request")
  Rel(gw, llms, "TLS + request-scoped credential")
  Rel(gw, pg, "Ledger")
  Rel(gw, redis, "Limits/cache")
  Rel(user, eval, "Runs prepared datasets containing captured review results")
  Rel(gw, grafana, "Planned low-cardinality telemetry export")
```

## DFD level 0

```mermaid
flowchart LR
  U[User / GitHub] -->|request or signed event| A[AutoPR / API]
  A -->|tenant + model request| G[Gateway]
  G -->|provider call| P[LLM provider]
  P -->|normalized answer + usage| G
  G -->|metadata, no secrets| L[(Usage ledger)]
  G -->|answer + trace ID| A
  A -. captured review output via manual dataset preparation .-> E[Evaluation]
  E -->|JSON report + process exit code| O[CLI / CI quality gate]
```

## DFD level 1 — credential resolution and inference

```mermaid
sequenceDiagram
  participant C as Client
  participant G as Gateway
  participant R as Credential resolver
  participant Q as Rate limit/cache
  participant P as Provider adapter
  participant D as Ledger
  C->>G: JSON + X-Tenant-Id + optional X-Provider-Key
  G->>Q: increment fixed-window counter for tenant
  alt limit exceeded
    Q-->>G: reject
    G-->>C: HTTP 429; no ledger row
  else allowed
    Q-->>G: allow
    G->>Q: exact lookup(tenant, selected provider, request)
    alt cache hit
      Q-->>G: cached normalized result
      G->>D: zero-cost cache-hit ledger row
      G-->>C: cached result + trace
    else cache miss
      Q-->>G: miss
      G->>R: request BYOK, otherwise platform key, otherwise reject
      R-->>G: request-scoped char[] + ownership
      G->>P: normalized request + credential; retry transient failure
      P-->>G: failure may trigger configured mapped fallback
      G->>P: fallback model + platform credential when eligible
      P-->>G: normalized result/usage
      G->>Q: cache successful normalized result with TTL
      G->>D: ownership, tokens, latency, route, status
      G-->>C: result + trace, never credential
      G->>G: zero request credential buffer
    end
  end
```

## Key decisions

| Decision | Why | Trade-off / revisit trigger |
|---|---|---|
| Monorepo | Atomic contracts and CI; easier portfolio operation | Split when teams release independently |
| Session BYOK | Smallest secret-retention surface | Add envelope-encrypted account keys only with KMS and product need |
| REST + non-streaming MVP | Clear retry and ledger semantics | Add SSE when interactive token latency is measured as material |
| PostgreSQL ledger | Durable, queryable cost ownership | Buffer writes only when measured write latency dominates |
| Redis, no Kafka | Rate/cache needs without operational excess | Add event bus when durable async consumers and replay exist |
| Deterministic eval first | Reproducible CLI/CI gate | Blend calibrated judges after human-labeled validation |
| Direct GitHub API | Narrow tool attack surface | Add MCP when multiple interchangeable tool hosts create value |

Exact caching is enabled with a configurable TTL. Tenant, selected provider, and the canonical normalized request are hashed into the Redis identity; credentials are never cache inputs or values. Unit tests and a local Compose runtime check cover cross-tenant isolation. Semantic caching remains intentionally unimplemented.
