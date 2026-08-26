package dev.forgeai.gateway.service;

import dev.forgeai.gateway.cache.GatewayCache;
import dev.forgeai.gateway.cache.GatewayCache.CachedCompletion;
import dev.forgeai.gateway.domain.Models.*;
import dev.forgeai.gateway.ledger.*;
import dev.forgeai.gateway.provider.LlmProvider;
import dev.forgeai.gateway.ratelimit.RequestRateLimiter;
import dev.forgeai.gateway.resilience.GatewayPolicy;
import dev.forgeai.gateway.resilience.RateLimitExceededException;
import dev.forgeai.gateway.security.CredentialResolver;
import dev.forgeai.gateway.security.CredentialResolver.MissingCredentialException;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Service
public class GatewayService {
  private static final Logger log = LoggerFactory.getLogger(GatewayService.class);
  private final Map<Provider, LlmProvider> providers;
  private final CredentialResolver credentials;
  private final LedgerRepository ledger;
  private final TenantRepository tenants;
  private final MeterRegistry metrics;
  private final GatewayCache cache;
  private final RequestRateLimiter rateLimiter;
  private final GatewayPolicy policy;

  public GatewayService(
      List<LlmProvider> providerList,
      CredentialResolver credentials,
      LedgerRepository ledger,
      TenantRepository tenants,
      MeterRegistry metrics,
      GatewayCache cache,
      RequestRateLimiter rateLimiter,
      GatewayPolicy policy) {
    this.providers = new EnumMap<>(Provider.class);
    providerList.forEach(provider -> providers.put(provider.id(), provider));
    this.credentials = credentials;
    this.ledger = ledger;
    this.tenants = tenants;
    this.metrics = metrics;
    this.cache = cache;
    this.rateLimiter = rateLimiter;
    this.policy = policy;
  }

  public Mono<GenerateResponse> generate(
      String tenant, String application, String suppliedKey, GenerateRequest request) {
    Provider selected = select(request);
    return rateLimiter
        .allow(tenant)
        .flatMap(
            allowed -> {
              if (!allowed) return Mono.error(new RateLimitExceededException());
              return cache
                  .get(tenant, selected, request)
                  .onErrorReturn(Optional.empty())
                  .flatMap(
                      cached ->
                          cached
                              .map(value -> cachedResponse(tenant, application, value))
                              .orElseGet(
                                  () ->
                                      execute(
                                          tenant, application, suppliedKey, selected, request)));
            });
  }

  private Mono<GenerateResponse> cachedResponse(
      String tenant, String application, CachedCompletion cached) {
    UUID id = UUID.randomUUID();
    long started = System.nanoTime();
    var response =
        new GenerateResponse(
            id.toString(),
            cached.provider(),
            cached.model(),
            cached.content(),
            new Usage(0, 0, BigDecimal.ZERO),
            true,
            false,
            "CACHE");
    metrics.counter("gateway_cache_hits_total", "provider", cached.provider().name()).increment();
    return save(id, tenant, application, response, started, "SUCCESS").thenReturn(response);
  }

  private Mono<GenerateResponse> execute(
      String tenant,
      String application,
      String suppliedKey,
      Provider selected,
      GenerateRequest request) {
    UUID id = UUID.randomUUID();
    long started = System.nanoTime();
    metrics.counter("gateway_cache_misses_total", "provider", selected.name()).increment();
    return attempt(selected, suppliedKey, request, false)
        .onErrorResume(error -> fallback(selected, request, error))
        .doOnError(
            error -> log.warn("provider_failure provider={} error={}", selected, classify(error)))
        .map(
            attempt -> {
              BigDecimal cost =
                  estimate(attempt.result().inputTokens(), attempt.result().outputTokens());
              return new GenerateResponse(
                  id.toString(),
                  attempt.provider(),
                  attempt.model(),
                  attempt.result().content(),
                  new Usage(attempt.result().inputTokens(), attempt.result().outputTokens(), cost),
                  false,
                  attempt.fallbackUsed(),
                  attempt.credentialSource().name());
            })
        .flatMap(
            response ->
                cache
                    .put(
                        tenant,
                        response.provider(),
                        request,
                        new CachedCompletion(
                            response.provider(),
                            response.model(),
                            response.content(),
                            response.usage()))
                    .onErrorResume(error -> Mono.empty())
                    .thenReturn(response))
        .flatMap(
            response ->
                save(id, tenant, application, response, started, "SUCCESS").thenReturn(response));
  }

  private Mono<Attempt> attempt(
      Provider provider, String suppliedKey, GenerateRequest request, boolean fallbackUsed) {
    return Mono.using(
        () -> credentials.resolve(provider, suppliedKey),
        credential -> {
          metrics
              .counter(
                  "gateway_requests_total",
                  "provider",
                  provider.name(),
                  "credential_source",
                  credential.source().name())
              .increment();
          return invokeWithRetry(provider, request, credential.value())
              .map(
                  result ->
                      new Attempt(
                          provider, request.model(), result, credential.source(), fallbackUsed));
        },
        CredentialResolver.ResolvedCredential::close);
  }

  private Mono<ProviderResult> invokeWithRetry(
      Provider provider, GenerateRequest request, char[] credential) {
    Mono<ProviderResult> call =
        providers
            .get(provider)
            .generate(request, credential)
            .doOnError(
                error ->
                    metrics
                        .counter(
                            "gateway_provider_failures_total",
                            "provider",
                            provider.name(),
                            "error",
                            classify(error))
                        .increment());
    if (policy.getMaxRetries() <= 0) return call;
    return call.retryWhen(
        Retry.backoff(policy.getMaxRetries(), policy.getRetryBackoff())
            .jitter(0.5)
            .filter(this::isTransient)
            .doBeforeRetry(
                signal ->
                    log.warn(
                        "provider_retry provider={} attempt={} error={}",
                        provider,
                        signal.totalRetries() + 1,
                        classify(signal.failure())))
            .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
  }

  private Mono<Attempt> fallback(Provider selected, GenerateRequest request, Throwable original) {
    if (!isTransient(original)
        || (request.routingMode() != RoutingMode.AUTO
            && request.routingMode() != RoutingMode.FALLBACK)) return Mono.error(original);
    for (LlmProvider candidate : providers.values()) {
      if (candidate.id() == selected) continue;
      String fallbackModel =
          candidate.supports(request.model())
              ? request.model()
              : policy.fallbackModel(candidate.id());
      if (fallbackModel == null || fallbackModel.isBlank() || !candidate.supports(fallbackModel))
        continue;
      try {
        credentials.resolve(candidate.id(), null).close();
        log.warn(
            "provider_fallback from={} to={} error={}",
            selected,
            candidate.id(),
            classify(original));
        metrics
            .counter("gateway_fallback_total", "from", selected.name(), "to", candidate.id().name())
            .increment();
        var fallbackRequest =
            new GenerateRequest(
                request.messages(),
                fallbackModel,
                candidate.id(),
                RoutingMode.EXPLICIT,
                request.promptId(),
                request.parameters());
        return attempt(candidate.id(), null, fallbackRequest, true);
      } catch (MissingCredentialException ignored) {
        // Try the next compatible provider; request BYOK keys are never reused across providers.
      }
    }
    return Mono.error(original);
  }

  private boolean isTransient(Throwable error) {
    if (error instanceof TimeoutException || error instanceof WebClientRequestException)
      return true;
    if (error instanceof WebClientResponseException response) {
      return response.getStatusCode().value() == 429 || response.getStatusCode().is5xxServerError();
    }
    return false;
  }

  private Provider select(GenerateRequest request) {
    if (request.provider() != null) {
      LlmProvider provider = providers.get(request.provider());
      if (provider == null || !provider.supports(request.model()))
        throw new IllegalArgumentException("Model is not supported by selected provider");
      return request.provider();
    }
    return providers.values().stream()
        .filter(provider -> provider.supports(request.model()))
        .map(LlmProvider::id)
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("No provider supports the requested model"));
  }

  private Mono<Void> save(
      UUID id,
      String tenant,
      String application,
      GenerateResponse response,
      long started,
      String status) {
    return Mono.fromRunnable(
            () -> {
              if (!tenants.existsById(tenant)) {
                var tenantEntity = new TenantEntity();
                tenantEntity.id = tenant;
                tenantEntity.kind =
                    tenant.startsWith("github-installation-") ? "AUTOPR" : "DEVELOPER";
                tenantEntity.createdAt = Instant.now();
                tenants.save(tenantEntity);
              }
              var entry = new LlmRequestEntity();
              entry.id = id;
              entry.tenantId = tenant;
              entry.application = application;
              entry.provider = response.provider().name();
              entry.model = response.model();
              entry.credentialSource = response.credentialSource();
              entry.inputTokens = response.usage().inputTokens();
              entry.outputTokens = response.usage().outputTokens();
              entry.estimatedCost = response.usage().estimatedCost();
              entry.latencyMs = (System.nanoTime() - started) / 1_000_000;
              entry.cacheHit = response.cacheHit();
              entry.fallbackUsed = response.fallbackUsed();
              entry.status = status;
              entry.createdAt = Instant.now();
              ledger.save(entry);
            })
        .subscribeOn(Schedulers.boundedElastic())
        .then();
  }

  private BigDecimal estimate(int inputTokens, int outputTokens) {
    return BigDecimal.valueOf(inputTokens)
        .multiply(new BigDecimal("0.000003"))
        .add(BigDecimal.valueOf(outputTokens).multiply(new BigDecimal("0.000015")));
  }

  private String classify(Throwable error) {
    String name = error.getClass().getSimpleName();
    return name.length() > 40 ? name.substring(0, 40) : name;
  }

  private record Attempt(
      Provider provider,
      String model,
      ProviderResult result,
      CredentialSource credentialSource,
      boolean fallbackUsed) {}
}
