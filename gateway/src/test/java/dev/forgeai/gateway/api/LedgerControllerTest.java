package dev.forgeai.gateway.api;

import static org.mockito.Mockito.*;

import dev.forgeai.gateway.ledger.*;
import dev.forgeai.gateway.security.ApiAuthenticationFilter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class LedgerControllerTest {
    private LedgerRepository repository;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        repository = mock(LedgerRepository.class);
        client = WebTestClient.bindToController(new LedgerController(repository))
                .controllerAdvice(new ApiErrors())
                .webFilter(new ApiAuthenticationFilter("test-token"))
                .build();
    }

    @Test
    void listsOnlyRequestedTenantEntries() {
        var entry = new LlmRequestEntity();
        entry.id = UUID.randomUUID(); entry.provider = "ANTHROPIC"; entry.model = "claude-test";
        entry.credentialSource = "REQUEST_BYOK"; entry.estimatedCost = BigDecimal.ONE;
        entry.status = "SUCCESS"; entry.createdAt = Instant.now();
        when(repository.findTop100ByTenantIdOrderByCreatedAtDesc("tenant-a"))
                .thenReturn(List.of(entry));
        client.get().uri("/v1/ledger").header("Authorization", "Bearer test-token")
                .header("X-Tenant-Id", "tenant-a").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[0].credentialSource").isEqualTo("REQUEST_BYOK")
                .jsonPath("$[0].provider").isEqualTo("ANTHROPIC");
        verify(repository).findTop100ByTenantIdOrderByCreatedAtDesc("tenant-a");
    }

    @Test
    void rejectsMissingTenantHeader() {
        client.get().uri("/v1/ledger").header("Authorization", "Bearer test-token")
                .exchange().expectStatus().isBadRequest();
        verifyNoInteractions(repository);
    }

    @Test
    void mapsDatabaseFailure() {
        when(repository.findTop100ByTenantIdOrderByCreatedAtDesc("tenant-a"))
                .thenThrow(new IllegalStateException("database unavailable"));
        client.get().uri("/v1/ledger").header("Authorization", "Bearer test-token")
                .header("X-Tenant-Id", "tenant-a").exchange().expectStatus().isEqualTo(502);
    }
}
