package dev.forgeai.gateway.ledger;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "llm_request")
public class LlmRequestEntity {
  @Id public UUID id;

  @Column(name = "tenant_id")
  public String tenantId;

  public String application, provider, model;

  @Column(name = "credential_source")
  public String credentialSource;

  @Column(name = "prompt_version")
  public String promptVersion;

  @Column(name = "input_tokens")
  public int inputTokens;

  @Column(name = "output_tokens")
  public int outputTokens;

  @Column(name = "estimated_cost")
  public BigDecimal estimatedCost;

  @Column(name = "latency_ms")
  public long latencyMs;

  @Column(name = "cache_hit")
  public boolean cacheHit;

  @Column(name = "fallback_used")
  public boolean fallbackUsed;

  public String status;

  @Column(name = "created_at")
  public Instant createdAt;
}
