package dev.forgeai.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.forgeai.gateway.resilience.GatewayPolicy;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

class RedisRequestRateLimiterTest {
  private ReactiveStringRedisTemplate redis;
  private ReactiveValueOperations<String, String> values;
  private RedisRequestRateLimiter limiter;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    redis = mock(ReactiveStringRedisTemplate.class);
    values = mock(ReactiveValueOperations.class);
    var policy = new GatewayPolicy();
    policy.setRequestsPerMinute(2);
    when(redis.opsForValue()).thenReturn(values);
    limiter = new RedisRequestRateLimiter(redis, policy);
  }

  @Test
  void firstRequestCreatesWindowWithHashedTenantIdentity() {
    when(values.increment(anyString())).thenReturn(Mono.just(1L));
    when(redis.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

    assertThat(limiter.allow("sensitive-tenant-name").block()).isTrue();

    var key = ArgumentCaptor.forClass(String.class);
    verify(values).increment(key.capture());
    verify(redis).expire(anyString(), any(Duration.class));
    assertThat(key.getValue())
        .startsWith("forgeai:rate:v1:")
        .doesNotContain("sensitive-tenant-name");
  }

  @Test
  void requestWithinLimitIsAllowedWithoutResettingWindow() {
    when(values.increment(anyString())).thenReturn(Mono.just(2L));

    assertThat(limiter.allow("tenant-a").block()).isTrue();
    verify(redis, never()).expire(anyString(), any(Duration.class));
  }

  @Test
  void requestPastLimitIsRejected() {
    when(values.increment(anyString())).thenReturn(Mono.just(3L));

    assertThat(limiter.allow("tenant-a").block()).isFalse();
    verify(redis, never()).expire(anyString(), any(Duration.class));
  }
}
