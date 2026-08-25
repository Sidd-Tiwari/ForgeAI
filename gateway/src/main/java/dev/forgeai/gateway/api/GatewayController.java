package dev.forgeai.gateway.api;

import dev.forgeai.gateway.domain.Models.*;
import dev.forgeai.gateway.service.GatewayService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1")
public class GatewayController {
  private final GatewayService gateway;

  public GatewayController(GatewayService gateway) {
    this.gateway = gateway;
  }

  @PostMapping(
      value = "/generate",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<GenerateResponse> generate(
      @RequestHeader("X-Tenant-Id") String tenant,
      @RequestHeader(value = "X-Application", defaultValue = "api") String application,
      @RequestHeader(value = "X-Provider-Key", required = false) String credential,
      @Valid @RequestBody GenerateRequest request) {
    return gateway.generate(tenant, application, credential, request);
  }
}
