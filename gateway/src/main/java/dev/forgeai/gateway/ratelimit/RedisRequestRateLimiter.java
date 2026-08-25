package dev.forgeai.gateway.ratelimit;

import dev.forgeai.gateway.resilience.GatewayPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RedisRequestRateLimiter implements RequestRateLimiter {
  private static final Duration WINDOW = Duration.ofMinutes(1);
  private final ReactiveStringRedisTemplate redis;
  private final GatewayPolicy policy;

  public RedisRequestRateLimiter(ReactiveStringRedisTemplate redis, GatewayPolicy policy) {
    this.redis = redis;
    this.policy = policy;
  }

  @Override
  public Mono<Boolean> allow(String tenantId) {
    String key = "forgeai:rate:v1:" + hash(tenantId);
    return redis
        .opsForValue()
        .increment(key)
        .flatMap(
            count ->
                (count == 1 ? redis.expire(key, WINDOW) : Mono.just(true))
                    .thenReturn(count <= policy.getRequestsPerMinute()));
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to build rate-limit identity", exception);
    }
  }
}
