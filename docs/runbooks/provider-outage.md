# Provider outage runbook

1. Confirm the alert across gateway failure rate, latency, and provider status; never enable debug HTTP-body logging.
2. Open the provider circuit and route eligible tenants to a provider for which they have a credential.
3. Disable platform-funded traffic if fallback would exceed its daily cost budget.
4. Compare request IDs with the ledger; do not copy prompts or keys into the incident channel.
5. Recover gradually, monitor 429/5xx and p95 for 15 minutes, then document impact and ledger reconciliation.

Rollback application releases by redeploying the preceding immutable `sha-*` image. Database migrations are forward-only; use a compensating migration.
