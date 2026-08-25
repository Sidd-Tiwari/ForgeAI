package dev.forgeai.gateway.provider;

import com.fasterxml.jackson.databind.JsonNode;
import dev.forgeai.gateway.domain.Models.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public final class ProviderAdapters {
    private ProviderAdapters() {}
    private abstract static class Base implements LlmProvider {
        final WebClient client;
        Base(WebClient.Builder builder, String baseUrl) { client = builder.baseUrl(baseUrl).build(); }
        Mono<JsonNode> post(String uri, Map<String,Object> body, java.util.function.Consumer<HttpHeaders> auth) {
            return client.post().uri(uri).headers(auth).bodyValue(body).retrieve().bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(45));
        }
        String prompt(GenerateRequest r) { return r.messages().stream().map(m -> m.role()+": "+m.content()).reduce((a,b)->a+"\n"+b).orElse(""); }
    }
    @Component
    public static final class OpenAi extends Base {
        public OpenAi(WebClient.Builder b){ super(b,"https://api.openai.com/v1"); }
        public Provider id(){ return Provider.OPENAI; } public boolean supports(String m){ return m.startsWith("gpt-") || m.startsWith("o"); }
        public Mono<ProviderResult> generate(GenerateRequest r, char[] key){
            var msgs=r.messages().stream().map(m->Map.of("role",m.role(),"content",m.content())).toList();
            return post("/chat/completions",Map.of("model",r.model(),"messages",msgs),h->h.setBearerAuth(new String(key)))
                .map(j->new ProviderResult(j.at("/choices/0/message/content").asText(),j.at("/usage/prompt_tokens").asInt(),j.at("/usage/completion_tokens").asInt()));
        }
    }
    @Component
    public static final class Anthropic extends Base {
        public Anthropic(WebClient.Builder b){ super(b,"https://api.anthropic.com/v1"); }
        public Provider id(){ return Provider.ANTHROPIC; } public boolean supports(String m){ return m.startsWith("claude-"); }
        public Mono<ProviderResult> generate(GenerateRequest r, char[] key){
            return post("/messages",Map.of("model",r.model(),"max_tokens",2048,"messages",List.of(Map.of("role","user","content",prompt(r)))),h->{h.set("x-api-key",new String(key));h.set("anthropic-version","2023-06-01");})
                .map(j->new ProviderResult(j.at("/content/0/text").asText(),j.at("/usage/input_tokens").asInt(),j.at("/usage/output_tokens").asInt()));
        }
    }
    @Component
    public static final class Gemini extends Base {
        public Gemini(WebClient.Builder b){ super(b,"https://generativelanguage.googleapis.com/v1beta"); }
        public Provider id(){ return Provider.GEMINI; } public boolean supports(String m){ return m.startsWith("gemini-"); }
        public Mono<ProviderResult> generate(GenerateRequest r, char[] key){
            String uri="/models/"+r.model()+":generateContent";
            return post(uri,Map.of("contents",List.of(Map.of("parts",List.of(Map.of("text",prompt(r)))))),h->h.set("x-goog-api-key",new String(key)))
                .map(j->new ProviderResult(j.at("/candidates/0/content/parts/0/text").asText(),j.at("/usageMetadata/promptTokenCount").asInt(),j.at("/usageMetadata/candidatesTokenCount").asInt()));
        }
    }
}
