package dev.forgeai.gateway.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.forgeai.gateway.domain.Models.GenerateRequest;
import dev.forgeai.gateway.domain.Models.Provider;
import dev.forgeai.gateway.resilience.GatewayPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RedisGatewayCache implements GatewayCache {
  private final ReactiveStringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final GatewayPolicy policy;

  public RedisGatewayCache(
      ReactiveStringRedisTemplate redis, ObjectMapper objectMapper, GatewayPolicy policy) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.policy = policy;
  }

  @Override
  public Mono<Optional<CachedCompletion>> get(
      String tenantId, Provider provider, GenerateRequest request) {
    return redis
        .opsForValue()
        .get(key(tenantId, provider, request))
        .map(this::deserialize)
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty());
  }

  @Override
  public Mono<Void> put(
      String tenantId, Provider provider, GenerateRequest request, CachedCompletion completion) {
    return redis
        .opsForValue()
        .set(key(tenantId, provider, request), serialize(completion), policy.getCacheTtl())
        .then();
  }

  String key(String tenantId, Provider provider, GenerateRequest request) {
    try {
      String canonical =
          tenantId + "\n" + provider.name() + "\n" + objectMapper.writeValueAsString(request);
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return "forgeai:cache:v1:" + HexFormat.of().formatHex(digest);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to build cache identity", exception);
    }
  }

  private String serialize(CachedCompletion completion) {
    try {
      return objectMapper.writeValueAsString(completion);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to encode cache entry", exception);
    }
  }

  private CachedCompletion deserialize(String value) {
    try {
      return objectMapper.readValue(value, CachedCompletion.class);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to decode cache entry", exception);
    }
  }
}
