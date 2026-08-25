package dev.forgeai.gateway.ledger;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tenant")
public class TenantEntity {
  @Id public String id;
  public String kind;

  @Column(name = "created_at")
  public Instant createdAt;
}
