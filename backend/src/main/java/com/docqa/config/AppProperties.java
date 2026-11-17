package com.docqa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String answerProvider,
        Cors cors,
        Chunking chunking,
        Retrieval retrieval,
        Gemini gemini,
        Claude claude
) {
    public record Cors(List<String> allowedOrigins) {}

    public record Chunking(int chunkSize, int chunkOverlap) {}

    public record Retrieval(int topK, double minSimilarity) {}

    public record Gemini(String apiKey, String baseUrl, String embeddingModel, int dimensions, int batchSize,
                         String chatModel, List<String> chatFallbackModels, int maxOutputTokens,
                         int responseTimeoutSeconds) {}

    public record Claude(String apiKey, String model, long maxTokens, String effort, boolean fallbacksEnabled) {}
}
