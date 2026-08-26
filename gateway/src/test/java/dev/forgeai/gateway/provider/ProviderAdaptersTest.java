package dev.forgeai.gateway.provider;

import static org.assertj.core.api.Assertions.assertThat;

import dev.forgeai.gateway.domain.Models.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

class ProviderAdaptersTest {
  private MockWebServer server;

  @BeforeEach
  void startServer() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void stopServer() throws Exception {
    server.shutdown();
  }

  @Test
  void openAiNormalizesSuccessfulResponse() throws Exception {
    enqueueJson(
        200,
        """
                {"choices":[{"message":{"content":"openai answer"}}],
                 "usage":{"prompt_tokens":11,"completion_tokens":7}}
                """);
    var adapter = new ProviderAdapters.OpenAi(builder(), baseUrl(), Duration.ofSeconds(5));

    StepVerifier.create(
            adapter.generate(request(Provider.OPENAI, "gpt-test"), "openai-secret".toCharArray()))
        .assertNext(
            result -> {
              assertThat(result.content()).isEqualTo("openai answer");
              assertThat(result.inputTokens()).isEqualTo(11);
              assertThat(result.outputTokens()).isEqualTo(7);
            })
        .verifyComplete();
    var recorded = server.takeRequest();
    assertThat(recorded.getPath()).isEqualTo("/v1/chat/completions");
    assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer openai-secret");
  }

  @Test
  void openAiTimesOut() {
    enqueueDelayedJson();
    assertTimeout(
        new ProviderAdapters.OpenAi(builder(), baseUrl(), Duration.ofMillis(20)),
        request(Provider.OPENAI, "gpt-test"));
  }

  @Test
  void openAiSurfacesRateLimit() {
    enqueueJson(429, "{\"error\":{\"message\":\"rate limited\"}}");
    assertRateLimit(
        new ProviderAdapters.OpenAi(builder(), baseUrl(), Duration.ofSeconds(5)),
        request(Provider.OPENAI, "gpt-test"));
  }

  @Test
  void openAiRejectsMalformedResponse() {
    enqueueJson(200, "{}");
    assertMalformed(
        new ProviderAdapters.OpenAi(builder(), baseUrl(), Duration.ofSeconds(5)),
        request(Provider.OPENAI, "gpt-test"));
  }

  @Test
  void anthropicNormalizesSuccessfulResponse() throws Exception {
    enqueueJson(
        200,
        """
                {"content":[{"text":"anthropic answer"}],
                 "usage":{"input_tokens":13,"output_tokens":9}}
                """);
    var adapter = new ProviderAdapters.Anthropic(builder(), baseUrl(), Duration.ofSeconds(5));

    StepVerifier.create(
            adapter.generate(
                request(Provider.ANTHROPIC, "claude-test"), "anthropic-secret".toCharArray()))
        .assertNext(
            result -> {
              assertThat(result.content()).isEqualTo("anthropic answer");
              assertThat(result.inputTokens()).isEqualTo(13);
              assertThat(result.outputTokens()).isEqualTo(9);
            })
        .verifyComplete();
    var recorded = server.takeRequest();
    assertThat(recorded.getPath()).isEqualTo("/v1/messages");
    assertThat(recorded.getHeader("x-api-key")).isEqualTo("anthropic-secret");
    assertThat(recorded.getHeader("anthropic-version")).isEqualTo("2023-06-01");
  }

  @Test
  void anthropicTimesOut() {
    enqueueDelayedJson();
    assertTimeout(
        new ProviderAdapters.Anthropic(builder(), baseUrl(), Duration.ofMillis(20)),
        request(Provider.ANTHROPIC, "claude-test"));
  }

  @Test
  void anthropicSurfacesRateLimit() {
    enqueueJson(429, "{\"error\":{\"message\":\"rate limited\"}}");
    assertRateLimit(
        new ProviderAdapters.Anthropic(builder(), baseUrl(), Duration.ofSeconds(5)),
        request(Provider.ANTHROPIC, "claude-test"));
  }

  @Test
  void anthropicRejectsMalformedResponse() {
    enqueueJson(200, "{}");
    assertMalformed(
        new ProviderAdapters.Anthropic(builder(), baseUrl(), Duration.ofSeconds(5)),
        request(Provider.ANTHROPIC, "claude-test"));
  }

  @Test
  void geminiNormalizesSuccessfulResponse() throws Exception {
    enqueueJson(
        200,
        """
                {"candidates":[{"content":{"parts":[{"text":"gemini answer"}]}}],
                 "usageMetadata":{"promptTokenCount":17,"candidatesTokenCount":6}}
                """);
    var adapter = new ProviderAdapters.Gemini(builder(), baseUrl(), Duration.ofSeconds(5));

    StepVerifier.create(
            adapter.generate(
                request(Provider.GEMINI, "gemini-test"), "gemini-secret".toCharArray()))
        .assertNext(
            result -> {
              assertThat(result.content()).isEqualTo("gemini answer");
              assertThat(result.inputTokens()).isEqualTo(17);
              assertThat(result.outputTokens()).isEqualTo(6);
            })
        .verifyComplete();
    var recorded = server.takeRequest();
    assertThat(recorded.getPath()).isEqualTo("/v1/models/gemini-test:generateContent");
    assertThat(recorded.getHeader("x-goog-api-key")).isEqualTo("gemini-secret");
  }

  @Test
  void geminiTimesOut() {
    enqueueDelayedJson();
    assertTimeout(
        new ProviderAdapters.Gemini(builder(), baseUrl(), Duration.ofMillis(20)),
        request(Provider.GEMINI, "gemini-test"));
  }

  @Test
  void geminiSurfacesRateLimit() {
    enqueueJson(429, "{\"error\":{\"message\":\"rate limited\"}}");
    assertRateLimit(
        new ProviderAdapters.Gemini(builder(), baseUrl(), Duration.ofSeconds(5)),
        request(Provider.GEMINI, "gemini-test"));
  }

  @Test
  void geminiRejectsMalformedResponse() {
    enqueueJson(200, "{}");
    assertMalformed(
        new ProviderAdapters.Gemini(builder(), baseUrl(), Duration.ofSeconds(5)),
        request(Provider.GEMINI, "gemini-test"));
  }

  private void assertTimeout(LlmProvider adapter, GenerateRequest request) {
    StepVerifier.create(adapter.generate(request, "secret".toCharArray()))
        .expectError(java.util.concurrent.TimeoutException.class)
        .verify();
  }

  private void assertRateLimit(LlmProvider adapter, GenerateRequest request) {
    StepVerifier.create(adapter.generate(request, "secret".toCharArray()))
        .expectError(WebClientResponseException.TooManyRequests.class)
        .verify();
  }

  private void assertMalformed(LlmProvider adapter, GenerateRequest request) {
    StepVerifier.create(adapter.generate(request, "secret".toCharArray()))
        .expectError(ProviderAdapters.MalformedProviderResponseException.class)
        .verify();
  }

  private GenerateRequest request(Provider provider, String model) {
    return new GenerateRequest(
        List.of(new Message("user", "hello")), model, provider, RoutingMode.EXPLICIT, null, null);
  }

  private WebClient.Builder builder() {
    return WebClient.builder();
  }

  private String baseUrl() {
    return server.url("/v1/").toString();
  }

  private void enqueueJson(int status, String body) {
    server.enqueue(
        new MockResponse()
            .setResponseCode(status)
            .setHeader("Content-Type", "application/json")
            .setBody(body));
  }

  private void enqueueDelayedJson() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{}")
            .setBodyDelay(200, TimeUnit.MILLISECONDS));
  }
}
