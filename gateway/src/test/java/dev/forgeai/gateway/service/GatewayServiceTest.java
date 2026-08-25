package dev.forgeai.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.forgeai.gateway.domain.Models.*;
import dev.forgeai.gateway.ledger.*;
import dev.forgeai.gateway.provider.LlmProvider;
import dev.forgeai.gateway.security.CredentialResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class GatewayServiceTest {
  private LedgerRepository ledger;
  private TenantRepository tenants;
  private SimpleMeterRegistry metrics;

  @BeforeEach
  void setUp() {
    ledger = mock(LedgerRepository.class);
    tenants = mock(TenantRepository.class);
    metrics = new SimpleMeterRegistry();
    when(ledger.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(tenants.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void explicitRequestUsesByokCredentialAndWritesLedger() {
    var openai = provider(Provider.OPENAI, true, "answer");
    var service = service(List.of(openai));

    var response =
        service
            .generate(
                "tenant-a",
                "api",
                "request-key",
                request(Provider.OPENAI, "gpt-test", RoutingMode.EXPLICIT))
            .block();

    assertThat(response).isNotNull();
    assertThat(response.credentialSource()).isEqualTo("REQUEST_BYOK");
    assertThat(response.provider()).isEqualTo(Provider.OPENAI);
    assertThat(response.usage().inputTokens()).isEqualTo(20);
    assertThat(metrics.get("gateway_requests_total").counter().count()).isEqualTo(1);
    var tenant = ArgumentCaptor.forClass(TenantEntity.class);
    verify(tenants).save(tenant.capture());
    assertThat(tenant.getValue().id).isEqualTo("tenant-a");
    assertThat(tenant.getValue().kind).isEqualTo("DEVELOPER");
    var entry = ArgumentCaptor.forClass(LlmRequestEntity.class);
    verify(ledger).save(entry.capture());
    assertThat(entry.getValue().tenantId).isEqualTo("tenant-a");
    assertThat(entry.getValue().credentialSource).isEqualTo("REQUEST_BYOK");
    assertThat(entry.getValue().status).isEqualTo("SUCCESS");
  }

  @Test
  void absentHeaderUsesConfiguredPlatformCredential() {
    var anthropic = provider(Provider.ANTHROPIC, true, "answer");
    when(tenants.existsById("existing-tenant")).thenReturn(true);
    var service = service(List.of(anthropic));

    var response =
        service
            .generate(
                "existing-tenant",
                "api",
                null,
                request(Provider.ANTHROPIC, "claude-test", RoutingMode.EXPLICIT))
            .block();

    assertThat(response).isNotNull();
    assertThat(response.credentialSource()).isEqualTo("PLATFORM");
    verify(tenants, never()).save(any());
    verify(ledger).save(any(LlmRequestEntity.class));
  }

  @Test
  void autoRoutingSelectsFirstCapableProvider() {
    var openai = provider(Provider.OPENAI, false, "unused");
    var anthropic = provider(Provider.ANTHROPIC, true, "routed answer");
    var service = service(List.of(openai, anthropic));

    var response =
        service
            .generate(
                "github-installation-42",
                "autopr",
                "request-key",
                request(null, "claude-test", RoutingMode.AUTO))
            .block();

    assertThat(response).isNotNull();
    assertThat(response.provider()).isEqualTo(Provider.ANTHROPIC);
    verify(anthropic).generate(any(), any(char[].class));
    verify(openai, never()).generate(any(), any(char[].class));
    var tenant = ArgumentCaptor.forClass(TenantEntity.class);
    verify(tenants).save(tenant.capture());
    assertThat(tenant.getValue().kind).isEqualTo("AUTOPR");
  }

  @Test
  void explicitRoutingRejectsUnsupportedModel() {
    var openai = provider(Provider.OPENAI, false, "unused");
    var service = service(List.of(openai));

    assertThatThrownBy(
            () ->
                service.generate(
                    "tenant-a",
                    "api",
                    "request-key",
                    request(Provider.OPENAI, "claude-test", RoutingMode.EXPLICIT)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Model is not supported by selected provider");
    verifyNoInteractions(ledger);
  }

  @Test
  void autoRoutingRejectsWhenNoProviderSupportsModel() {
    var openai = provider(Provider.OPENAI, false, "unused");
    var service = service(List.of(openai));

    assertThatThrownBy(
            () ->
                service.generate(
                    "tenant-a",
                    "api",
                    "request-key",
                    request(null, "unknown-model", RoutingMode.AUTO)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No provider supports the requested model");
    verifyNoInteractions(ledger);
  }

  @Test
  void providerFailureIsMeasuredAndCredentialIsDiscarded() {
    var openai = mock(LlmProvider.class);
    when(openai.id()).thenReturn(Provider.OPENAI);
    when(openai.supports(anyString())).thenReturn(true);
    when(openai.generate(any(), any(char[].class)))
        .thenReturn(Mono.error(new IllegalStateException("provider unavailable")));
    var service = service(List.of(openai));

    assertThatThrownBy(
            () ->
                service
                    .generate(
                        "tenant-a",
                        "api",
                        "request-key",
                        request(Provider.OPENAI, "gpt-test", RoutingMode.EXPLICIT))
                    .block())
        .isInstanceOf(IllegalStateException.class);
    assertThat(metrics.get("gateway_provider_failures_total").counter().count()).isEqualTo(1);
    verifyNoInteractions(ledger);
  }

  private GatewayService service(List<LlmProvider> providers) {
    return new GatewayService(
        providers,
        new CredentialResolver("platform-openai", "platform-anthropic", "platform-gemini"),
        ledger,
        tenants,
        metrics);
  }

  private LlmProvider provider(Provider id, boolean supports, String content) {
    var provider = mock(LlmProvider.class);
    when(provider.id()).thenReturn(id);
    when(provider.supports(anyString())).thenReturn(supports);
    when(provider.generate(any(), any(char[].class)))
        .thenReturn(Mono.just(new ProviderResult(content, 20, 10)));
    return provider;
  }

  private GenerateRequest request(Provider provider, String model, RoutingMode routingMode) {
    return new GenerateRequest(
        List.of(new Message("user", "hello")), model, provider, routingMode, "prompt-v1", null);
  }
}
