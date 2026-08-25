package dev.forgeai.gateway.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiAuthenticationFilter implements WebFilter {
  private final byte[] token;

  public ApiAuthenticationFilter(@Value("${forgeai.internal-token:}") String token) {
    this.token = token.getBytes(StandardCharsets.UTF_8);
  }

  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!exchange.getRequest().getPath().value().startsWith("/v1/") || token.length == 0)
      return chain.filter(exchange);
    String value = exchange.getRequest().getHeaders().getFirst("Authorization");
    byte[] supplied =
        value != null && value.startsWith("Bearer ")
            ? value.substring(7).getBytes(StandardCharsets.UTF_8)
            : new byte[0];
    if (!MessageDigest.isEqual(token, supplied)) {
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return exchange.getResponse().setComplete();
    }
    return chain.filter(exchange);
  }
}
