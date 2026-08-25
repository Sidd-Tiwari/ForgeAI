package dev.forgeai.gateway.provider;

import dev.forgeai.gateway.domain.Models.*;
import reactor.core.publisher.Mono;

public interface LlmProvider {
    Provider id();
    boolean supports(String model);
    Mono<ProviderResult> generate(GenerateRequest request, char[] credential);
}
