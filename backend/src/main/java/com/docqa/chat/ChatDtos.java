package com.docqa.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class ChatDtos {

    private ChatDtos() {}

    public record HistoryMessage(@NotBlank String role, @NotBlank String content) {}

    /**
     * conversationId: continue a saved chat (history is loaded from the database).
     * Omit it to start a new saved chat. history is only used by stateless /ask calls without a conversation.
     */
    public record AskRequest(
            @NotBlank @Size(max = 4000) String question,
            List<UUID> documentIds,
            UUID conversationId,
            @Valid @Size(max = 20) List<HistoryMessage> history
    ) {}

    public record Source(
            int index,
            UUID documentId,
            String filename,
            Integer pageNumber,
            int chunkIndex,
            String snippet,
            double similarity
    ) {}

    public record AskResponse(UUID conversationId, String answer, List<Source> sources) {}

    public record ConversationEvent(UUID id, String title) {}

    public record Delta(String text) {}

    public record Done(String stopReason) {}

    public record ErrorEvent(String message) {}
}
