package com.docqa.embedding;

import com.docqa.config.AppProperties;
import com.docqa.config.HttpClients;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Calls the Gemini REST API (batchEmbedContents) to turn text into vectors.
 * Docs: https://ai.google.dev/gemini-api/docs/embeddings
 */
@Component
public class GeminiEmbeddingClient {

    public enum TaskType { RETRIEVAL_DOCUMENT, RETRIEVAL_QUERY }

    private final RestClient http;
    private final AppProperties.Gemini cfg;

    public GeminiEmbeddingClient(AppProperties props, RestClient.Builder builder) {
        this.cfg = props.gemini();
        this.http = builder.baseUrl(cfg.baseUrl())
                .requestFactory(HttpClients.withTimeouts(Duration.ofSeconds(60)))
                .build();
    }

    public float[] embedQuery(String text) {
        return embed(List.of(text), TaskType.RETRIEVAL_QUERY).getFirst();
    }

    public List<float[]> embedDocuments(List<String> texts) {
        List<float[]> all = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += cfg.batchSize()) {
            all.addAll(embed(texts.subList(i, Math.min(i + cfg.batchSize(), texts.size())), TaskType.RETRIEVAL_DOCUMENT));
        }
        return all;
    }

    private List<float[]> embed(List<String> texts, TaskType taskType) {
        if (!StringUtils.hasText(cfg.apiKey())) {
            throw new IllegalStateException("GEMINI_API_KEY is not set");
        }
        String model = "models/" + cfg.embeddingModel();
        List<Map<String, Object>> requests = texts.stream()
                .map(t -> Map.<String, Object>of(
                        "model", model,
                        "content", Map.of("parts", List.of(Map.of("text", t))),
                        "taskType", taskType.name(),
                        "outputDimensionality", cfg.dimensions()))
                .toList();

        BatchResponse res = withRetry(() -> http.post()
                .uri("/models/{model}:batchEmbedContents", cfg.embeddingModel())
                .header("x-goog-api-key", cfg.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("requests", requests))
                .retrieve()
                .body(BatchResponse.class));

        if (res == null || res.embeddings() == null || res.embeddings().size() != texts.size()) {
            throw new IllegalStateException("Gemini returned an unexpected embedding response");
        }
        return res.embeddings().stream().map(e -> normalize(e.values())).toList();
    }

    /**
     * Retries when Gemini is rate-limited or busy (429 / 5xx), which happens when many files
     * are uploaded at once on the free tier. Waits 2s, 4s, 8s, 16s between attempts.
     */
    private static <T> T withRetry(Supplier<T> call) {
        int attempts = 5;
        for (int i = 1; ; i++) {
            try {
                return call.get();
            } catch (RestClientResponseException e) {
                int s = e.getStatusCode().value();
                boolean retryable = s == 429 || s >= 500;
                if (!retryable || i == attempts) {
                    throw new IllegalStateException("Gemini embedding failed (" + s + "): "
                            + (e.getStatusCode().value() == 429 ? "rate limit reached, try again later" : e.getStatusText()), e);
                }
                sleep(2000L << (i - 1));
            } catch (ResourceAccessException e) {
                if (i == attempts) throw new IllegalStateException("Gemini embedding timed out", e);
                sleep(2000L << (i - 1));
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry", ie);
        }
    }

    /** Truncated Gemini embeddings (< 3072 dims) are not unit length; normalize for cosine search. */
    private static float[] normalize(List<Double> values) {
        double norm = 0;
        for (double v : values) norm += v * v;
        norm = Math.sqrt(norm);
        float[] out = new float[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (float) (norm == 0 ? 0 : values.get(i) / norm);
        }
        return out;
    }

    record BatchResponse(List<Embedding> embeddings) {}

    record Embedding(List<Double> values) {}
}
