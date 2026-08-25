package dev.forgeai.gateway.provider;

import com.fasterxml.jackson.databind.JsonNode;
import dev.forgeai.gateway.domain.Models.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public final class ProviderAdapters {
  private ProviderAdapters() {}

  private abstract static class Base implements LlmProvider {
    final WebClient client;
    final Duration timeout;

    Base(WebClient.Builder builder, String baseUrl, Duration timeout) {
      client = builder.baseUrl(baseUrl).build();
      this.timeout = timeout;
    }

    Mono<JsonNode> post(
        String uri, Map<String, Object> body, java.util.function.Consumer<HttpHeaders> auth) {
      return client
          .post()
          .uri(uri)
          .headers(auth)
          .bodyValue(body)
          .retrieve()
          .bodyToMono(JsonNode.class)
          .timeout(timeout);
    }

    String prompt(GenerateRequest r) {
      return r.messages().stream()
          .map(m -> m.role() + ": " + m.content())
          .reduce((a, b) -> a + "\n" + b)
          .orElse("");
    }

    String requiredText(JsonNode body, String pointer) {
      JsonNode value = body.at(pointer);
      if (!value.isTextual() || value.asText().isBlank()) {
        throw new MalformedProviderResponseException(pointer);
      }
      return value.asText();
    }

    int requiredInt(JsonNode body, String pointer) {
      JsonNode value = body.at(pointer);
      if (!value.canConvertToInt()) throw new MalformedProviderResponseException(pointer);
      return value.asInt();
    }
  }

  public static final class MalformedProviderResponseException extends RuntimeException {
    public MalformedProviderResponseException(String field) {
      super("Provider response omitted required field: " + field);
    }
  }

  @Component
  public static final class OpenAi extends Base {
    @Autowired
    public OpenAi(
        WebClient.Builder b,
        @Value("${forgeai.providers.openai-url:https://api.openai.com/v1/}") String baseUrl) {
      this(b, baseUrl, Duration.ofSeconds(45));
    }

    OpenAi(WebClient.Builder b, String baseUrl, Duration timeout) {
      super(b, baseUrl, timeout);
    }

    public Provider id() {
      return Provider.OPENAI;
    }

    public boolean supports(String m) {
      return m.startsWith("gpt-") || m.startsWith("o");
    }

    public Mono<ProviderResult> generate(GenerateRequest r, char[] key) {
      var msgs =
          r.messages().stream().map(m -> Map.of("role", m.role(), "content", m.content())).toList();
      return post(
              "chat/completions",
              Map.of("model", r.model(), "messages", msgs),
              h -> h.setBearerAuth(new String(key)))
          .map(
              j ->
                  new ProviderResult(
                      requiredText(j, "/choices/0/message/content"),
                      requiredInt(j, "/usage/prompt_tokens"),
                      requiredInt(j, "/usage/completion_tokens")));
    }
  }

  @Component
  public static final class Anthropic extends Base {
    @Autowired
    public Anthropic(
        WebClient.Builder b,
        @Value("${forgeai.providers.anthropic-url:https://api.anthropic.com/v1/}") String baseUrl) {
      this(b, baseUrl, Duration.ofSeconds(45));
    }

    Anthropic(WebClient.Builder b, String baseUrl, Duration timeout) {
      super(b, baseUrl, timeout);
    }

    public Provider id() {
      return Provider.ANTHROPIC;
    }

    public boolean supports(String m) {
      return m.startsWith("claude-");
    }

    public Mono<ProviderResult> generate(GenerateRequest r, char[] key) {
      return post(
              "messages",
              Map.of(
                  "model",
                  r.model(),
                  "max_tokens",
                  2048,
                  "messages",
                  List.of(Map.of("role", "user", "content", prompt(r)))),
              h -> {
                h.set("x-api-key", new String(key));
                h.set("anthropic-version", "2023-06-01");
              })
          .map(
              j ->
                  new ProviderResult(
                      requiredText(j, "/content/0/text"),
                      requiredInt(j, "/usage/input_tokens"),
                      requiredInt(j, "/usage/output_tokens")));
    }
  }

  @Component
  public static final class Gemini extends Base {
    @Autowired
    public Gemini(
        WebClient.Builder b,
        @Value("${forgeai.providers.gemini-url:https://generativelanguage.googleapis.com/v1beta/}")
            String baseUrl) {
      this(b, baseUrl, Duration.ofSeconds(45));
    }

    Gemini(WebClient.Builder b, String baseUrl, Duration timeout) {
      super(b, baseUrl, timeout);
    }

    public Provider id() {
      return Provider.GEMINI;
    }

    public boolean supports(String m) {
      return m.startsWith("gemini-");
    }

    public Mono<ProviderResult> generate(GenerateRequest r, char[] key) {
      String uri = "models/" + r.model() + ":generateContent";
      return post(
              uri,
              Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt(r)))))),
              h -> h.set("x-goog-api-key", new String(key)))
          .map(
              j ->
                  new ProviderResult(
                      requiredText(j, "/candidates/0/content/parts/0/text"),
                      requiredInt(j, "/usageMetadata/promptTokenCount"),
                      requiredInt(j, "/usageMetadata/candidatesTokenCount")));
    }
  }
}
