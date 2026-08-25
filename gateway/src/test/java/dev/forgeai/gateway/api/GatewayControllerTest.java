package dev.forgeai.gateway.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.forgeai.gateway.domain.Models.*;
import dev.forgeai.gateway.resilience.RateLimitExceededException;
import dev.forgeai.gateway.security.ApiAuthenticationFilter;
import dev.forgeai.gateway.service.GatewayService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class GatewayControllerTest {
  private GatewayService service;
  private WebTestClient client;

  @BeforeEach
  void setUp() {
    service = mock(GatewayService.class);
    client =
        WebTestClient.bindToController(new GatewayController(service))
            .controllerAdvice(new ApiErrors())
            .webFilter(new ApiAuthenticationFilter("test-token"))
            .build();
  }

  @Test
  void generateReturnsNormalizedResponse() {
    var response =
        new GenerateResponse(
            "request-1",
            Provider.OPENAI,
            "gpt-test",
            "review",
            new Usage(10, 5, new BigDecimal("0.001")),
            false,
            false,
            "REQUEST_BYOK");
    when(service.generate(eq("tenant-a"), eq("api"), eq("provider-key"), any()))
        .thenReturn(Mono.just(response));

    client
        .post()
        .uri("/v1/generate")
        .header("Authorization", "Bearer test-token")
        .header("X-Tenant-Id", "tenant-a")
        .header("X-Provider-Key", "provider-key")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"model\":\"gpt-test\",\"provider\":\"OPENAI\",\"messages\":[{\"role\":\"user\",\"content\":\"review\"}]}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.requestId")
        .isEqualTo("request-1")
        .jsonPath("$.credentialSource")
        .isEqualTo("REQUEST_BYOK");
  }

  @Test
  void generateRejectsMissingAuthentication() {
    client
        .post()
        .uri("/v1/generate")
        .header("X-Tenant-Id", "tenant-a")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"model\":\"gpt-test\",\"messages\":[{\"role\":\"user\",\"content\":\"review\"}]}")
        .exchange()
        .expectStatus()
        .isUnauthorized();
    verifyNoInteractions(service);
  }

  @Test
  void generateRejectsInvalidRequestBody() {
    client
        .post()
        .uri("/v1/generate")
        .header("Authorization", "Bearer test-token")
        .header("X-Tenant-Id", "tenant-a")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"model\":\"\",\"messages\":[]}")
        .exchange()
        .expectStatus()
        .isBadRequest();
    verifyNoInteractions(service);
  }

  @Test
  void generateMapsDownstreamFailureWithoutLeakingMessage() {
    when(service.generate(anyString(), anyString(), any(), any()))
        .thenReturn(Mono.error(new IllegalStateException("sk-secret-provider-detail")));
    client
        .post()
        .uri("/v1/generate")
        .header("Authorization", "Bearer test-token")
        .header("X-Tenant-Id", "tenant-a")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"model\":\"gpt-test\",\"messages\":[{\"role\":\"user\",\"content\":\"review\"}]}")
        .exchange()
        .expectStatus()
        .isEqualTo(502)
        .expectBody(String.class)
        .value(
            body ->
                org.assertj.core.api.Assertions.assertThat(body)
                    .doesNotContain("sk-secret-provider-detail"));
  }

  @Test
  void generateReturnsTooManyRequestsWhenTenantLimitIsExceeded() {
    when(service.generate(anyString(), anyString(), any(), any()))
        .thenReturn(Mono.error(new RateLimitExceededException()));
    client
        .post()
        .uri("/v1/generate")
        .header("Authorization", "Bearer test-token")
        .header("X-Tenant-Id", "tenant-a")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"model\":\"gpt-test\",\"messages\":[{\"role\":\"user\",\"content\":\"review\"}]}")
        .exchange()
        .expectStatus()
        .isEqualTo(429);
  }
}
