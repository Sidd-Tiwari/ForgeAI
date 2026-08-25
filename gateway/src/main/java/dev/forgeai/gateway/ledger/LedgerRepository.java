package dev.forgeai.gateway.ledger;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LedgerRepository extends JpaRepository<LlmRequestEntity, UUID> {
    List<LlmRequestEntity> findTop100ByTenantIdOrderByCreatedAtDesc(String tenantId);
}
