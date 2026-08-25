package dev.forgeai.gateway.api;

import dev.forgeai.gateway.ledger.LedgerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/v1/ledger")
public class LedgerController {
    private final LedgerRepository ledger;

    public LedgerController(LedgerRepository ledger) {
        this.ledger = ledger;
    }

    @GetMapping
    public Mono<List<LedgerEntry>> list(@RequestHeader("X-Tenant-Id") String tenantId) {
        return Mono.fromCallable(() -> ledger.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId)
                        .stream()
                        .map(e -> new LedgerEntry(e.id, e.provider, e.model, e.credentialSource,
                                e.inputTokens, e.outputTokens, e.estimatedCost, e.latencyMs,
                                e.cacheHit, e.fallbackUsed, e.status, e.createdAt))
                        .toList())
                .subscribeOn(Schedulers.boundedElastic());
    }

    public record LedgerEntry(UUID requestId, String provider, String model, String credentialSource,
            int inputTokens, int outputTokens, BigDecimal estimatedCost, long latencyMs,
            boolean cacheHit, boolean fallbackUsed, String status, Instant createdAt) {}
}
