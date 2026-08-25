package dev.forgeai.gateway.api;

import dev.forgeai.gateway.resilience.RateLimitExceededException;
import dev.forgeai.gateway.security.CredentialResolver.MissingCredentialException;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(RateLimitExceededException.class)
  ResponseEntity<ProblemDetail> rateLimited(
      RateLimitExceededException ex, ServerWebExchange exchange) {
    var p = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    p.setTitle("Rate limit exceeded");
    p.setInstance(URI.create(exchange.getRequest().getPath().value()));
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(p);
  }

  @ExceptionHandler(ServerWebInputException.class)
  ResponseEntity<ProblemDetail> invalidInput(
      ServerWebInputException ex, ServerWebExchange exchange) {
    var p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
    p.setTitle("Invalid request");
    p.setInstance(URI.create(exchange.getRequest().getPath().value()));
    return ResponseEntity.badRequest().body(p);
  }

  @ExceptionHandler({IllegalArgumentException.class, MissingCredentialException.class})
  ResponseEntity<ProblemDetail> badRequest(RuntimeException ex, ServerWebExchange exchange) {
    var p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    p.setTitle("Request rejected");
    p.setInstance(URI.create(exchange.getRequest().getPath().value()));
    return ResponseEntity.badRequest().body(p);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> downstream(Exception ex, ServerWebExchange exchange) {
    var p =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "A downstream dependency failed");
    p.setTitle("Gateway dependency failure");
    p.setInstance(URI.create(exchange.getRequest().getPath().value()));
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(p);
  }
}
