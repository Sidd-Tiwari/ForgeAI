package dev.forgeai.gateway.ratelimit;

import reactor.core.publisher.Mono;

public interface RequestRateLimiter {
  Mono<Boolean> allow(String tenantId);
}
