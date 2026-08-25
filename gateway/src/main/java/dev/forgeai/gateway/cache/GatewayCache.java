package dev.forgeai.gateway.cache;

import dev.forgeai.gateway.domain.Models.*;
import java.util.Optional;
import reactor.core.publisher.Mono;

public interface GatewayCache {
  Mono<Optional<CachedCompletion>> get(String tenantId, Provider provider, GenerateRequest request);

  Mono<Void> put(
      String tenantId, Provider provider, GenerateRequest request, CachedCompletion completion);

  record CachedCompletion(Provider provider, String model, String content, Usage usage) {}
}
