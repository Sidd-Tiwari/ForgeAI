package dev.forgeai.gateway.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class Models {
  private Models() {}

  public enum Provider {
    OPENAI,
    ANTHROPIC,
    GEMINI
  }

  public enum RoutingMode {
    EXPLICIT,
    AUTO,
    CHEAPEST,
    FASTEST,
    CAPABILITY,
    FALLBACK
  }

  public enum CredentialSource {
    REQUEST_BYOK,
    PLATFORM
  }

  public record Message(@NotBlank String role, @NotBlank @Size(max = 100_000) String content) {}

  public record GenerateRequest(
      @NotEmpty List<Message> messages,
      @NotBlank String model,
      Provider provider,
      RoutingMode routingMode,
      @Size(max = 100) String promptId,
      Map<String, Object> parameters) {
    public GenerateRequest {
      routingMode = routingMode == null ? RoutingMode.AUTO : routingMode;
      parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
  }

  public record Usage(int inputTokens, int outputTokens, BigDecimal estimatedCost) {}

  public record GenerateResponse(
      String requestId,
      Provider provider,
      String model,
      String content,
      Usage usage,
      boolean cacheHit,
      boolean fallbackUsed,
      String credentialSource) {}

  public record ProviderResult(String content, int inputTokens, int outputTokens) {}
}
