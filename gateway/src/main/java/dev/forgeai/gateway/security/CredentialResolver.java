package dev.forgeai.gateway.security;

import dev.forgeai.gateway.domain.Models.*;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CredentialResolver {
  private final String openai, anthropic, gemini;

  public CredentialResolver(
      @Value("${forgeai.providers.openai-key:}") String openai,
      @Value("${forgeai.providers.anthropic-key:}") String anthropic,
      @Value("${forgeai.providers.gemini-key:}") String gemini) {
    this.openai = openai;
    this.anthropic = anthropic;
    this.gemini = gemini;
  }

  public ResolvedCredential resolve(Provider provider, String supplied) {
    if (supplied != null && !supplied.isBlank())
      return new ResolvedCredential(supplied.toCharArray(), CredentialSource.REQUEST_BYOK);
    String platform =
        switch (provider) {
          case OPENAI -> openai;
          case ANTHROPIC -> anthropic;
          case GEMINI -> gemini;
        };
    if (platform == null || platform.isBlank()) throw new MissingCredentialException();
    return new ResolvedCredential(platform.toCharArray(), CredentialSource.PLATFORM);
  }

  public static final class ResolvedCredential implements AutoCloseable {
    private final char[] value;
    private final CredentialSource source;

    private ResolvedCredential(char[] value, CredentialSource source) {
      this.value = value;
      this.source = source;
    }

    public char[] value() {
      return value;
    }

    public CredentialSource source() {
      return source;
    }

    @Override
    public void close() {
      Arrays.fill(value, '\0');
    }

    @Override
    public String toString() {
      return "ResolvedCredential[REDACTED," + source + "]";
    }
  }

  public static class MissingCredentialException extends RuntimeException {
    public MissingCredentialException() {
      super("No credential is available for the selected provider");
    }
  }
}
