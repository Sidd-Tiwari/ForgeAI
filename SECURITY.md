# Security Policy

Report vulnerabilities privately to the repository owner. Do not open public issues containing secrets or exploit details.

ForgeAI never accepts provider credentials in JSON bodies. Session BYOK keys use `X-Provider-Key`, are request-scoped, redacted from logs, excluded from metrics, never cached, and never persisted. Production deployments must terminate TLS at a trusted proxy, strip untrusted forwarding headers, rotate internal credentials, restrict egress, and use a managed secret store.

See [docs/security/threat-model.md](docs/security/threat-model.md) for trust boundaries and mitigations.
