package com.docqa.chat;

import com.anthropic.errors.AnthropicServiceException;
import com.docqa.chat.ChatDtos.*;
import com.docqa.chat.ChatService.Prepared;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chat;
    private final TaskExecutor executor;

    public ChatController(ChatService chat, @Qualifier("chatExecutor") TaskExecutor executor) {
        this.chat = chat;
        this.executor = executor;
    }

    /** Non-streaming: returns the full answer plus sources as JSON. */
    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest req) {
        Prepared p = chat.prepare(req);
        List<Source> sources = chat.retrieve(req.question(), p.documentIds());
        StringBuilder answer = new StringBuilder();
        String stop = chat.streamAnswer(req.question(), p.history(), sources, answer::append);
        if ("refusal".equals(stop) && answer.isEmpty()) {
            answer.append("Sorry, I can't help with that request.");
        }
        chat.saveAnswer(p.conversationId(), answer.toString(), sources, null);
        return new AskResponse(p.conversationId(), answer.toString(), sources);
    }

    /**
     * Streaming over Server-Sent Events. Event names:
     * conversation -> {id, title}, sources -> [Source...], delta -> {text}, done -> {stopReason}, error -> {message}
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody AskRequest req) {
        // Runs before streaming starts, so an unknown conversationId returns a normal 404.
        Prepared p = chat.prepare(req);
        SseEmitter emitter = new SseEmitter(300_000L);

        executor.execute(() -> {
            StringBuilder answer = new StringBuilder();
            List<Source> sources = List.of();
            try {
                send(emitter, "conversation", new ConversationEvent(p.conversationId(), p.title()));
                sources = chat.retrieve(req.question(), p.documentIds());
                send(emitter, "sources", sources);
                String stop = chat.streamAnswer(req.question(), p.history(), sources, text -> {
                    answer.append(text);
                    send(emitter, "delta", new Delta(text));
                });
                if ("refusal".equals(stop) && answer.isEmpty()) {
                    answer.append("Sorry, I can't help with that request.");
                }
                chat.saveAnswer(p.conversationId(), answer.toString(), sources, null);
                send(emitter, "done", new Done(stop));
                emitter.complete();
            } catch (ClientGoneException e) {
                // User pressed Stop or closed the tab: keep whatever was generated.
                chat.saveAnswer(p.conversationId(), answer.toString(), sources, answer.isEmpty() ? "Stopped" : null);
                emitter.complete();
            } catch (AnthropicServiceException e) {
                log.warn("Claude API error: {}", e.getMessage());
                fail(emitter, p, answer, sources,
                        "Claude API error (" + e.statusCode() + "). Check ANTHROPIC_API_KEY and try again.");
            } catch (Exception e) {
                if (causedByClientGone(e)) { // an HTTP client may wrap our exception
                    chat.saveAnswer(p.conversationId(), answer.toString(), sources, answer.isEmpty() ? "Stopped" : null);
                    emitter.complete();
                    return;
                }
                log.error("Chat stream failed", e);
                fail(emitter, p, answer, sources, e.getMessage() == null ? "Something went wrong" : e.getMessage());
            }
        });
        return emitter;
    }

    private void fail(SseEmitter emitter, Prepared p, StringBuilder answer, List<Source> sources, String message) {
        try {
            chat.saveAnswer(p.conversationId(), answer.toString(), sources, message);
        } catch (Exception e) {
            log.warn("Could not save failed answer", e);
        }
        try {
            emitter.send(SseEmitter.event().name("error").data(new ErrorEvent(message), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception ignored) {
            emitter.completeWithError(new RuntimeException(message));
        }
    }

    private static void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            throw new ClientGoneException();
        }
    }

    private static boolean causedByClientGone(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof ClientGoneException) return true;
        }
        return false;
    }

    /** Browser closed the connection mid-stream; stop generating. */
    private static final class ClientGoneException extends RuntimeException {}
}
