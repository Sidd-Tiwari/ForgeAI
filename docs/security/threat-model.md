# BYOK threat model

| Threat | Boundary | Control | Residual risk |
|---|---|---|---|
| Credential appears in logs | Client → gateway | Dedicated header, HTTP wire logging disabled, redaction policy, security test | Host memory/core dumps |
| Cross-tenant cache disclosure | Gateway → Redis | Tenant ID is mandatory in keys; private-by-default cache namespace | Incorrect future cache-key change |
| Credential persistence | Gateway → PostgreSQL/Redis | Session credentials only; ledger stores ownership enum, never secret | Provider SDK diagnostic behavior |
| Forged GitHub event | Internet → AutoPR | HMAC-SHA256 over raw body with constant-time comparison | Stolen webhook secret |
| Prompt injection from repository | Repository → reviewer | Repository content marked untrusted; tools allowlisted; no shell tool exposed | Model may still follow adversarial content |
| SSRF/tool abuse | AutoPR → network | Direct GitHub API only in MVP; egress allowlist recommended | DNS/proxy misconfiguration |
| Tenant quota abuse | Client → gateway | Redis-backed per-tenant fixed-window request limit | Tenant rotation, distributed bursts, and absence of token-cost limits |
| Browser key theft | Browser session | CSP, no localStorage, short lifetime, TLS, XSS prevention | Compromised device/extensions |

Production must use TLS 1.2+, a managed WAF/API gateway, workload identity, secret-manager injection, encrypted backups, egress policies, dependency scanning, and incident-response rotation procedures. CSRF applies if cookie authentication is later added. CORS is not authentication.
