package dev.forgeai.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.forgeai.gateway.cache.GatewayCache;
import dev.forgeai.gateway.cache.GatewayCache.CachedCompletion;
import dev.forgeai.gateway.domain.Models.*;
import dev.forgeai.gateway.ledger.*;
import dev.forgeai.gateway.provider.LlmProvider;
import dev.forgeai.gateway.ratelimit.RequestRateLimiter;
import dev.forgeai.gateway.resilience.GatewayPolicy;
import dev.forgeai.gateway.resilience.RateLimitExceededException;
import dev.forgeai.gateway.security.CredentialResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class GatewayServiceTest {
  private LedgerRepository ledger;
  private TenantRepository tenants;
  private SimpleMeterRegistry metrics;
  private GatewayCache cache;
  private RequestRateLimiter rateLimiter;
  private GatewayPolicy policy;

  @BeforeEach
  void setUp() {
    ledger = mock(LedgerRepository.class);
    tenants = mock(TenantRepository.class);
    metrics = new SimpleMeterRegistry();
    cache = mock(GatewayCache.class);
    rateLimiter = mock(RequestRateLimiter.class);
    policy = new GatewayPolicy();
    policy.setMaxRetries(0);
    policy.setRetryBackoff(Duration.ofMillis(1));
    when(ledger.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(tenants.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(rateLimiter.allow(anyString())).thenReturn(Mono.just(true));
    when(cache.get(anyString(), any(), any())).thenReturn(Mono.just(Optional.empty()));
    when(cache.put(anyString(), any(), any(), any())).thenReturn(Mono.empty());
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

  @Test
  void transientFailureRetriesWithBackoffBeforeSuccess() {
    policy.setMaxRetries(2);
    var calls = new AtomicInteger();
    var openai = mock(LlmProvider.class);
    when(openai.id()).thenReturn(Provider.OPENAI);
    when(openai.supports(anyString())).thenReturn(true);
    when(openai.generate(any(), any(char[].class)))
        .thenReturn(
            Mono.defer(
                () ->
                    calls.incrementAndGet() < 3
                        ? Mono.error(new TimeoutException("forced timeout"))
                        : Mono.just(new ProviderResult("recovered", 20, 10))));

    var response =
        service(List.of(openai))
            .generate(
                "tenant-a",
                "api",
                "request-key",
                request(Provider.OPENAI, "gpt-test", RoutingMode.EXPLICIT))
            .block();

    assertThat(response).isNotNull();
    assertThat(response.content()).isEqualTo("recovered");
    assertThat(calls).hasValue(3);
  }

  @Test
  void transientFailureFallsBackWithoutReusingByokKey() {
    var openai = mock(LlmProvider.class);
    when(openai.id()).thenReturn(Provider.OPENAI);
    when(openai.supports(anyString())).thenReturn(true);
    when(openai.generate(any(), any(char[].class)))
        .thenReturn(Mono.error(new TimeoutException("forced timeout")));
    var anthropic = provider(Provider.ANTHROPIC, true, "fallback answer");

    var response =
        service(List.of(openai, anthropic))
            .generate(
                "tenant-a",
                "api",
                "request-key",
                request(Provider.OPENAI, "shared-test-model", RoutingMode.FALLBACK))
            .block();

    assertThat(response).isNotNull();
    assertThat(response.provider()).isEqualTo(Provider.ANTHROPIC);
    assertThat(response.fallbackUsed()).isTrue();
    assertThat(response.credentialSource()).isEqualTo("PLATFORM");
    var credential = ArgumentCaptor.forClass(char[].class);
    verify(anthropic).generate(any(), credential.capture());
    assertThat(credential.getValue()).containsOnly('\0');
    var entry = ArgumentCaptor.forClass(LlmRequestEntity.class);
    verify(ledger).save(entry.capture());
    assertThat(entry.getValue().fallbackUsed).isTrue();
  }

  @Test
  void crossProviderFallbackUsesConfiguredProviderModel() {
    policy.setFallbackAnthropicModel("claude-fallback");
    var openai = mock(LlmProvider.class);
    when(openai.id()).thenReturn(Provider.OPENAI);
    when(openai.supports("gpt-primary")).thenReturn(true);
    when(openai.generate(any(), any(char[].class)))
        .thenReturn(Mono.error(new TimeoutException("forced timeout")));
    var anthropic = mock(LlmProvider.class);
    when(anthropic.id()).thenReturn(Provider.ANTHROPIC);
    when(anthropic.supports("gpt-primary")).thenReturn(false);
    when(anthropic.supports("claude-fallback")).thenReturn(true);
    when(anthropic.generate(any(), any(char[].class)))
        .thenReturn(Mono.just(new ProviderResult("fallback answer", 20, 10)));

    var response =
        service(List.of(openai, anthropic))
            .generate(
                "tenant-a",
                "api",
                "request-key",
                request(Provider.OPENAI, "gpt-primary", RoutingMode.FALLBACK))
            .block();

    assertThat(response).isNotNull();
    assertThat(response.provider()).isEqualTo(Provider.ANTHROPIC);
    assertThat(response.model()).isEqualTo("claude-fallback");
    assertThat(response.fallbackUsed()).isTrue();
    var fallbackRequest = ArgumentCaptor.forClass(GenerateRequest.class);
    verify(anthropic).generate(fallbackRequest.capture(), any(char[].class));
    assertThat(fallbackRequest.getValue().model()).isEqualTo("claude-fallback");
  }

  @Test
  void exactCacheHitSkipsProviderAndWritesCacheLedgerEntry() {
    var openai = provider(Provider.OPENAI, true, "unused");
    when(cache.get(eq("tenant-a"), eq(Provider.OPENAI), any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    new CachedCompletion(
                        Provider.OPENAI,
                        "gpt-test",
                        "cached answer",
                        new Usage(20, 10, BigDecimal.ONE)))));

    var response =
        service(List.of(openai))
            .generate(
                "tenant-a",
                "api",
                "request-key",
                request(Provider.OPENAI, "gpt-test", RoutingMode.EXPLICIT))
            .block();

    assertThat(response).isNotNull();
    assertThat(response.content()).isEqualTo("cached answer");
    assertThat(response.cacheHit()).isTrue();
    assertThat(response.credentialSource()).isEqualTo("CACHE");
    assertThat(response.usage().estimatedCost()).isZero();
    verify(openai, never()).generate(any(), any(char[].class));
    var entry = ArgumentCaptor.forClass(LlmRequestEntity.class);
    verify(ledger).save(entry.capture());
    assertThat(entry.getValue().cacheHit).isTrue();
  }

  @Test
  void repeatedExactRequestHasLowerLatencyAndCacheLedgerFlag() {
    var stored = new AtomicReference<Optional<CachedCompletion>>(Optional.empty());
    when(cache.get(eq("tenant-cache"), eq(Provider.OPENAI), any()))
        .thenAnswer(invocation -> Mono.just(stored.get()));
    when(cache.put(eq("tenant-cache"), eq(Provider.OPENAI), any(), any()))
        .thenAnswer(
            invocation -> {
              stored.set(Optional.of(invocation.getArgument(3)));
              return Mono.empty();
            });
    var openai = mock(LlmProvider.class);
    when(openai.id()).thenReturn(Provider.OPENAI);
    when(openai.supports(anyString())).thenReturn(true);
    when(openai.generate(any(), any(char[].class)))
        .thenReturn(
            Mono.delay(Duration.ofMillis(80)).thenReturn(new ProviderResult("answer", 20, 10)));
    var service = service(List.of(openai));
    var request = request(Provider.OPENAI, "gpt-test", RoutingMode.EXPLICIT);

    long firstStarted = System.nanoTime();
    var first = service.generate("tenant-cache", "api", "request-key", request).block();
    long firstMs = Duration.ofNanos(System.nanoTime() - firstStarted).toMillis();
    long secondStarted = System.nanoTime();
    var second = service.generate("tenant-cache", "api", "request-key", request).block();
    long secondMs = Duration.ofNanos(System.nanoTime() - secondStarted).toMillis();
    System.out.printf(
        "cache_latency first_ms=%d second_ms=%d cache_hit=%s%n",
        firstMs, secondMs, second.cacheHit());

    assertThat(first.cacheHit()).isFalse();
    assertThat(second.cacheHit()).isTrue();
    assertThat(secondMs).isLessThan(firstMs);
    verify(openai, times(1)).generate(any(), any(char[].class));
    var entries = ArgumentCaptor.forClass(LlmRequestEntity.class);
    verify(ledger, times(2)).save(entries.capture());
    assertThat(entries.getAllValues())
        .extracting(entry -> entry.cacheHit)
        .containsExactly(false, true);
  }

  @Test
  void tenantRateLimitRejectsBeforeCacheOrProvider() {
    var openai = provider(Provider.OPENAI, true, "unused");
    when(rateLimiter.allow("tenant-a")).thenReturn(Mono.just(false));

    assertThatThrownBy(
            () ->
                service(List.of(openai))
                    .generate(
                        "tenant-a",
                        "api",
                        "request-key",
                        request(Provider.OPENAI, "gpt-test", RoutingMode.EXPLICIT))
                    .block())
        .isInstanceOf(RateLimitExceededException.class);
    verifyNoInteractions(cache);
    verify(openai, never()).generate(any(), any(char[].class));
  }

  private GatewayService service(List<LlmProvider> providers) {
    return new GatewayService(
        providers,
        new CredentialResolver("platform-openai", "platform-anthropic", "platform-gemini"),
        ledger,
        tenants,
        metrics,
        cache,
        rateLimiter,
        policy);
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
