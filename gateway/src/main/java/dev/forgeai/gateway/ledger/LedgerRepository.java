package dev.forgeai.gateway.ledger;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LedgerRepository extends JpaRepository<LlmRequestEntity, UUID> {}
