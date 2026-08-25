package dev.forgeai.gateway.security;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@Component
public class CorrelationFilter implements WebFilter {
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String id = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
    if (id == null || !id.matches("[A-Za-z0-9._-]{1,64}")) id = UUID.randomUUID().toString();
    exchange.getResponse().getHeaders().set("X-Request-Id", id);
    MDC.put("requestId", id);
    String finalId = id;
    return chain
        .filter(exchange)
        .contextWrite(c -> c.put("requestId", finalId))
        .doFinally(s -> MDC.remove("requestId"));
  }
}
