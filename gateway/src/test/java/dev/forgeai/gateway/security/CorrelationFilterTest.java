package dev.forgeai.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class CorrelationFilterTest {
  private final CorrelationFilter filter = new CorrelationFilter();

  @Test
  void preservesValidRequestIdAndRemovesMdcValue() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/v1/generate").header("X-Request-Id", "request-123"));
    var chain = mock(WebFilterChain.class);
    when(chain.filter(any())).thenReturn(Mono.empty());

    filter.filter(exchange, chain).block();

    assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id"))
        .isEqualTo("request-123");
    assertThat(MDC.get("requestId")).isNull();
    verify(chain).filter(exchange);
  }

  @Test
  void replacesInvalidRequestId() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/v1/generate")
                .header("X-Request-Id", "invalid request id with spaces"));
    var chain = mock(WebFilterChain.class);
    when(chain.filter(any())).thenReturn(Mono.empty());

    filter.filter(exchange, chain).block();

    assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id"))
        .matches("[0-9a-f-]{36}");
    assertThat(MDC.get("requestId")).isNull();
  }
}
