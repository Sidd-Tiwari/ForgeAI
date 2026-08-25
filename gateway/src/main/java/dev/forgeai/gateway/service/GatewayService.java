package dev.forgeai.gateway.service;

import dev.forgeai.gateway.domain.Models.*;
import dev.forgeai.gateway.ledger.*;
import dev.forgeai.gateway.provider.LlmProvider;
import dev.forgeai.gateway.security.CredentialResolver;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class GatewayService {
  private final Map<Provider, LlmProvider> providers;
  private final CredentialResolver credentials;
  private final LedgerRepository ledger;
  private final TenantRepository tenants;
  private final MeterRegistry metrics;

  public GatewayService(
      List<LlmProvider> ps,
      CredentialResolver credentials,
      LedgerRepository ledger,
      TenantRepository tenants,
      MeterRegistry metrics) {
    this.providers = new EnumMap<>(Provider.class);
    ps.forEach(p -> providers.put(p.id(), p));
    this.credentials = credentials;
    this.ledger = ledger;
    this.tenants = tenants;
    this.metrics = metrics;
  }

  public Mono<GenerateResponse> generate(
      String tenant, String application, String suppliedKey, GenerateRequest req) {
    Provider selected = select(req);
    var credential = credentials.resolve(selected, suppliedKey);
    UUID id = UUID.randomUUID();
    long started = System.nanoTime();
    metrics
        .counter(
            "gateway_requests_total",
            "provider",
            selected.name(),
            "credential_source",
            credential.source().name())
        .increment();
    return providers
        .get(selected)
        .generate(req, credential.value())
        .map(
            result -> {
              BigDecimal cost =
                  estimate(selected, req.model(), result.inputTokens(), result.outputTokens());
              return new GenerateResponse(
                  id.toString(),
                  selected,
                  req.model(),
                  result.content(),
                  new Usage(result.inputTokens(), result.outputTokens(), cost),
                  false,
                  false,
                  credential.source().name());
            })
        .flatMap(
            response ->
                save(id, tenant, application, response, started, "SUCCESS").thenReturn(response))
        .doOnError(
            e ->
                metrics
                    .counter(
                        "gateway_provider_failures_total",
                        "provider",
                        selected.name(),
                        "error",
                        classify(e))
                    .increment())
        .doFinally(signal -> credential.close());
  }

  private Provider select(GenerateRequest r) {
    if (r.provider() != null) {
      if (!providers.get(r.provider()).supports(r.model()))
        throw new IllegalArgumentException("Model is not supported by selected provider");
      return r.provider();
    }
    return providers.values().stream()
        .filter(p -> p.supports(r.model()))
        .map(LlmProvider::id)
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("No provider supports the requested model"));
  }

  private Mono<Void> save(
      UUID id, String tenant, String app, GenerateResponse r, long started, String status) {
    return Mono.fromRunnable(
            () -> {
              if (!tenants.existsById(tenant)) {
                var t = new TenantEntity();
                t.id = tenant;
                t.kind = tenant.startsWith("github-installation-") ? "AUTOPR" : "DEVELOPER";
                t.createdAt = Instant.now();
                tenants.save(t);
              }
              var e = new LlmRequestEntity();
              e.id = id;
              e.tenantId = tenant;
              e.application = app;
              e.provider = r.provider().name();
              e.model = r.model();
              e.credentialSource = r.credentialSource();
              e.inputTokens = r.usage().inputTokens();
              e.outputTokens = r.usage().outputTokens();
              e.estimatedCost = r.usage().estimatedCost();
              e.latencyMs = (System.nanoTime() - started) / 1_000_000;
              e.cacheHit = r.cacheHit();
              e.fallbackUsed = r.fallbackUsed();
              e.status = status;
              e.createdAt = Instant.now();
              ledger.save(e);
            })
        .subscribeOn(Schedulers.boundedElastic())
        .then();
  }

  private BigDecimal estimate(Provider p, String m, int in, int out) {
    return BigDecimal.valueOf(in)
        .multiply(new BigDecimal("0.000003"))
        .add(BigDecimal.valueOf(out).multiply(new BigDecimal("0.000015")));
  }

  private String classify(Throwable e) {
    String n = e.getClass().getSimpleName();
    return n.length() > 40 ? n.substring(0, 40) : n;
  }
}
