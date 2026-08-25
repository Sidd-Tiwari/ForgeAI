package dev.forgeai.gateway.api;

import dev.forgeai.gateway.security.CredentialResolver.MissingCredentialException;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler({IllegalArgumentException.class, MissingCredentialException.class})
    ResponseEntity<ProblemDetail> badRequest(RuntimeException ex, ServerWebExchange exchange){
        var p=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,ex.getMessage());p.setTitle("Request rejected");p.setInstance(URI.create(exchange.getRequest().getPath().value()));return ResponseEntity.badRequest().body(p);
    }
}
