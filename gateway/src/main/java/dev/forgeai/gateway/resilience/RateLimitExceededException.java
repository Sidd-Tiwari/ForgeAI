package dev.forgeai.gateway.resilience;

public class RateLimitExceededException extends RuntimeException {
  public RateLimitExceededException() {
    super("Tenant request limit exceeded");
  }
}
