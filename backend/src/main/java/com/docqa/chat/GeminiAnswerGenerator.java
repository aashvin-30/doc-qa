package com.docqa.chat;

import com.docqa.config.AppProperties;
import com.docqa.config.HttpClients;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Answers with Gemini via the REST streamGenerateContent endpoint (Server-Sent Events).
 * Active when app.answer-provider=gemini (the default).
 * If the main model is overloaded (429/5xx) or slow to respond, the fallback models are tried in order.
 * Docs: https://ai.google.dev/api/generate-content
 */
@Component
@ConditionalOnProperty(name = "app.answer-provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiAnswerGenerator implements AnswerGenerator {

    private static final Logger log = LoggerFactory.getLogger(GeminiAnswerGenerator.class);

    private static final Set<String> BLOCKED = Set.of(
            "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY");
    private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);

    /** Thrown when this model is busy and nothing was streamed yet, so another model can be tried. */
    private static final class ModelBusyException extends RuntimeException {
        ModelBusyException(String message) {
            super(message);
        }
    }

    private final RestClient http;
    private final AppProperties.Gemini cfg;
    private final ObjectMapper json;
    private final List<String> models;
    /** Models that recently failed are skipped until this time, so users don't wait on a busy model. */
    private final Map<String, Instant> coolingDown = new ConcurrentHashMap<>();
    private static final Duration COOL_DOWN = Duration.ofMinutes(5);

    public GeminiAnswerGenerator(AppProperties props, RestClient.Builder builder, ObjectMapper json) {
        this.cfg = props.gemini();
        this.http = builder.baseUrl(cfg.baseUrl())
                .requestFactory(HttpClients.withTimeouts(Duration.ofSeconds(cfg.responseTimeoutSeconds())))
                .build();
        this.json = json;
        LinkedHashSet<String> m = new LinkedHashSet<>();
        m.add(cfg.chatModel());
        if (cfg.chatFallbackModels() != null) {
            cfg.chatFallbackModels().stream().filter(StringUtils::hasText).forEach(m::add);
        }
        this.models = List.copyOf(m);
    }

    @Override
    public String stream(String systemPrompt, List<Turn> history, String userTurn, Consumer<String> onText) {
        if (!StringUtils.hasText(cfg.apiKey())) {
            throw new IllegalStateException("GEMINI_API_KEY is not set");
        }
        Map<String, Object> body = buildBody(systemPrompt, history, userTurn);

        String lastError = null;
        Instant now = Instant.now();
        List<String> order = new ArrayList<>(models.stream()
                .filter(m -> coolingDown.getOrDefault(m, Instant.MIN).isBefore(now)).toList());
        if (order.isEmpty()) order.addAll(models); // everything is cooling down: try them all anyway
        for (String model : order) {
            boolean[] emitted = {false};
            try {
                String stop = streamOnce(model, body, text -> {
                    emitted[0] = true;
                    onText.accept(text);
                });
                coolingDown.remove(model);
                return stop;
            } catch (ModelBusyException e) {
                lastError = e.getMessage();
                coolingDown.put(model, Instant.now().plus(COOL_DOWN));
                log.warn("Gemini model {} unavailable ({}), trying next model", model, e.getMessage());
            } catch (ResourceAccessException e) { // timeout / connection problem
                if (emitted[0]) throw e;
                lastError = "no response in time";
                coolingDown.put(model, Instant.now().plus(COOL_DOWN));
                log.warn("Gemini model {} timed out, trying next model", model);
            }
        }
        throw new IllegalStateException("Gemini is busy right now (" + lastError + "). Please try again in a minute.");
    }

    private String streamOnce(String model, Map<String, Object> body, Consumer<String> onText) {
        return http.post()
                .uri("/models/{model}:streamGenerateContent?alt=sse", model)
                .header("x-goog-api-key", cfg.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(body)
                .exchange((req, res) -> {
                    int status = res.getStatusCode().value();
                    if (res.getStatusCode().isError()) {
                        String err = extractMessage(new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8));
                        if (RETRYABLE.contains(status)) throw new ModelBusyException(status + " " + err);
                        throw new IllegalStateException("Gemini API error " + status + ": " + err);
                    }
                    String stop = "end_turn";
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(res.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data:")) continue;
                            String data = line.substring(5).trim();
                            if (data.isEmpty()) continue;
                            String s = handleChunk(json.readTree(data), onText);
                            if (s != null) stop = s;
                        }
                    }
                    return stop;
                });
    }

    private Map<String, Object> buildBody(String systemPrompt, List<Turn> history, String userTurn) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (Turn t : history) {
            contents.add(content("assistant".equals(t.role()) ? "model" : "user", t.content()));
        }
        contents.add(content("user", userTurn));
        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", contents,
                "generationConfig", Map.of("maxOutputTokens", cfg.maxOutputTokens()));
    }

    /** Emits text from one streamed chunk; returns a stop reason if this chunk carries one. */
    private String handleChunk(JsonNode chunk, Consumer<String> onText) {
        JsonNode blocked = chunk.path("promptFeedback").path("blockReason");
        if (!blocked.isMissingNode() && !blocked.asText().isEmpty()) return "refusal";

        JsonNode cand = chunk.path("candidates").path(0);
        for (JsonNode part : cand.path("content").path("parts")) {
            if (part.path("thought").asBoolean(false)) continue; // skip reasoning summaries
            String text = part.path("text").asText("");
            if (!text.isEmpty()) onText.accept(text);
        }
        String finish = cand.path("finishReason").asText("");
        if (finish.isEmpty()) return null;
        if (finish.equals("MAX_TOKENS")) return "max_tokens";
        if (BLOCKED.contains(finish)) return "refusal";
        return "end_turn";
    }

    private String extractMessage(String errorBody) {
        try {
            return json.readTree(errorBody).path("error").path("message").asText(errorBody);
        } catch (Exception e) {
            return errorBody;
        }
    }

    private static Map<String, Object> content(String role, String text) {
        return Map.of("role", role, "parts", List.of(Map.of("text", text)));
    }

    @Override
    public String name() {
        return "gemini:" + String.join(" -> ", models);
    }
}
