package dev.forgeai.gateway.resilience;

import dev.forgeai.gateway.domain.Models.Provider;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "forgeai.policy")
public class GatewayPolicy {
  private int maxRetries = 2;
  private Duration retryBackoff = Duration.ofMillis(200);
  private Duration cacheTtl = Duration.ofMinutes(10);
  private int requestsPerMinute = 60;
  private String fallbackOpenaiModel;
  private String fallbackAnthropicModel;
  private String fallbackGeminiModel;

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public Duration getRetryBackoff() {
    return retryBackoff;
  }

  public void setRetryBackoff(Duration retryBackoff) {
    this.retryBackoff = retryBackoff;
  }

  public Duration getCacheTtl() {
    return cacheTtl;
  }

  public void setCacheTtl(Duration cacheTtl) {
    this.cacheTtl = cacheTtl;
  }

  public int getRequestsPerMinute() {
    return requestsPerMinute;
  }

  public void setRequestsPerMinute(int requestsPerMinute) {
    this.requestsPerMinute = requestsPerMinute;
  }

  public String fallbackModel(Provider provider) {
    return switch (provider) {
      case OPENAI -> fallbackOpenaiModel;
      case ANTHROPIC -> fallbackAnthropicModel;
      case GEMINI -> fallbackGeminiModel;
    };
  }

  public String getFallbackOpenaiModel() {
    return fallbackOpenaiModel;
  }

  public void setFallbackOpenaiModel(String fallbackOpenaiModel) {
    this.fallbackOpenaiModel = fallbackOpenaiModel;
  }

  public String getFallbackAnthropicModel() {
    return fallbackAnthropicModel;
  }

  public void setFallbackAnthropicModel(String fallbackAnthropicModel) {
    this.fallbackAnthropicModel = fallbackAnthropicModel;
  }

  public String getFallbackGeminiModel() {
    return fallbackGeminiModel;
  }

  public void setFallbackGeminiModel(String fallbackGeminiModel) {
    this.fallbackGeminiModel = fallbackGeminiModel;
  }
}
