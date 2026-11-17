package com.docqa.chat;

import java.util.List;
import java.util.function.Consumer;

/**
 * An LLM that writes the final answer from the RAG prompt.
 * Selected with {@code app.answer-provider} (gemini | claude).
 */
public interface AnswerGenerator {

    /** One prior chat turn; role is "user" or "assistant". */
    record Turn(String role, String content) {}

    /**
     * Streams the answer, calling {@code onText} for each text fragment.
     *
     * @return normalized stop reason: "end_turn", "max_tokens" or "refusal"
     */
    String stream(String systemPrompt, List<Turn> history, String userTurn, Consumer<String> onText);

    /** Short name shown in logs, e.g. "gemini:gemini-2.5-flash". */
    String name();
}
