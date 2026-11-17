package com.docqa.chat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.docqa.config.AppProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Answers with Claude through the official Anthropic Java SDK. Active when app.answer-provider=claude. */
@Component
@ConditionalOnProperty(name = "app.answer-provider", havingValue = "claude")
public class ClaudeAnswerGenerator implements AnswerGenerator {

    private final AnthropicClient client;
    private final AppProperties.Claude cfg;

    public ClaudeAnswerGenerator(AppProperties props) {
        this.cfg = props.claude();
        this.client = StringUtils.hasText(cfg.apiKey())
                ? AnthropicOkHttpClient.builder().apiKey(cfg.apiKey()).build()
                : AnthropicOkHttpClient.fromEnv();
    }

    @Override
    public String stream(String systemPrompt, List<Turn> history, String userTurn, Consumer<String> onText) {
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(cfg.model())
                .maxTokens(cfg.maxTokens())
                .system(systemPrompt)
                .outputConfig(OutputConfig.builder().effort(effort(cfg.effort())).build());

        if (cfg.fallbacksEnabled()) {
            // Server-side refusal fallback: if a safety classifier declines, the API retries on a suitable model.
            b.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01");
            b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        for (Turn t : history) {
            if ("assistant".equals(t.role())) b.addAssistantMessage(t.content());
            else b.addUserMessage(t.content());
        }
        b.addUserMessage(userTurn);

        String stopReason = null;
        try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(b.build())) {
            for (RawMessageStreamEvent event : (Iterable<RawMessageStreamEvent>) stream.stream()::iterator) {
                if (event.contentBlockDelta().isPresent()) {
                    event.contentBlockDelta().get().delta().text().ifPresent(t -> onText.accept(t.text()));
                } else if (event.messageDelta().isPresent()) {
                    var sr = event.messageDelta().get().delta().stopReason();
                    if (sr.isPresent()) stopReason = sr.get().toString();
                }
            }
        }
        if ("max_tokens".equals(stopReason) || "refusal".equals(stopReason)) return stopReason;
        return "end_turn";
    }

    @Override
    public String name() {
        return "claude:" + cfg.model();
    }

    @PreDestroy
    void close() {
        client.close();
    }

    private static OutputConfig.Effort effort(String value) {
        return switch (value == null ? "medium" : value.toLowerCase(Locale.ROOT)) {
            case "low" -> OutputConfig.Effort.LOW;
            case "high" -> OutputConfig.Effort.HIGH;
            case "xhigh" -> OutputConfig.Effort.XHIGH;
            case "max" -> OutputConfig.Effort.MAX;
            default -> OutputConfig.Effort.MEDIUM;
        };
    }
}
