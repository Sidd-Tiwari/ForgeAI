package dev.forgeai.gateway.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.forgeai.gateway.cache.GatewayCache.CachedCompletion;
import dev.forgeai.gateway.domain.Models.GenerateRequest;
import dev.forgeai.gateway.domain.Models.Message;
import dev.forgeai.gateway.domain.Models.Provider;
import dev.forgeai.gateway.domain.Models.RoutingMode;
import dev.forgeai.gateway.domain.Models.Usage;
import dev.forgeai.gateway.resilience.GatewayPolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

class RedisGatewayCacheTest {
  private ReactiveStringRedisTemplate redis;
  private ReactiveValueOperations<String, String> values;
  private ObjectMapper mapper;
  private GatewayPolicy policy;
  private RedisGatewayCache cache;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    redis = mock(ReactiveStringRedisTemplate.class);
    values = mock(ReactiveValueOperations.class);
    mapper = new ObjectMapper();
    policy = new GatewayPolicy();
    policy.setCacheTtl(Duration.ofMinutes(7));
    when(redis.opsForValue()).thenReturn(values);
    cache = new RedisGatewayCache(redis, mapper, policy);
  }

  @Test
  void cacheIdentityIsTenantAndProviderScopedWithoutPlaintext() {
    var request = request("sensitive prompt marker");

    String tenantA = cache.key("tenant-a", Provider.OPENAI, request);
    String tenantB = cache.key("tenant-b", Provider.OPENAI, request);
    String anthropic = cache.key("tenant-a", Provider.ANTHROPIC, request);

    assertThat(tenantA)
        .startsWith("forgeai:cache:v1:")
        .doesNotContain("tenant-a", "sensitive prompt marker");
    assertThat(tenantA).isNotEqualTo(tenantB).isNotEqualTo(anthropic);
  }

  @Test
  void cacheMissReturnsEmptyOptional() {
    when(values.get(anyString())).thenReturn(Mono.empty());

    assertThat(cache.get("tenant-a", Provider.OPENAI, request("hello")).block()).isEmpty();
  }

  @Test
  void cachedCompletionRoundTrips() throws Exception {
    var completion =
        new CachedCompletion(
            Provider.OPENAI, "gpt-test", "answer", new Usage(4, 2, new BigDecimal("0.01")));
    when(values.get(anyString())).thenReturn(Mono.just(mapper.writeValueAsString(completion)));

    assertThat(cache.get("tenant-a", Provider.OPENAI, request("hello")).block())
        .contains(completion);
  }

  @Test
  void putUsesConfiguredTtlAndEncodedValue() {
    when(values.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
    var completion =
        new CachedCompletion(
            Provider.OPENAI, "gpt-test", "answer", new Usage(4, 2, BigDecimal.ZERO));

    cache.put("tenant-a", Provider.OPENAI, request("hello"), completion).block();

    var ttl = ArgumentCaptor.forClass(Duration.class);
    verify(values).set(anyString(), anyString(), ttl.capture());
    assertThat(ttl.getValue()).isEqualTo(Duration.ofMinutes(7));
  }

  private GenerateRequest request(String content) {
    return new GenerateRequest(
        List.of(new Message("user", content)),
        "gpt-test",
        Provider.OPENAI,
        RoutingMode.EXPLICIT,
        "prompt-v1",
        null);
  }
}
