package com.docqa.conversation;

import com.docqa.common.NotFoundException;
import com.docqa.conversation.ConversationRepository.Conversation;
import com.docqa.conversation.ConversationRepository.ConversationDetail;
import jakarta.validation.Valid;
import com.docqa.document.Document;
import com.docqa.document.DocumentRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Saved chats: list, open, rename, delete. New chats are created by the first question (see ChatController). */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    public record RenameRequest(@NotBlank @Size(max = 120) String title) {}

    public record AttachRequest(@NotNull UUID documentId) {}

    private final ConversationRepository repo;
    private final DocumentRepository documents;

    public ConversationController(ConversationRepository repo, DocumentRepository documents) {
        this.repo = repo;
        this.documents = documents;
    }

    @GetMapping
    public List<Conversation> list() {
        return repo.findAll();
    }

    /** Creates an empty chat (used when the first file is added before any question). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Conversation create() {
        return repo.create(ConversationRepository.DEFAULT_TITLE);
    }

    /** Attaches an already-uploaded file to this chat. */
    @PostMapping("/{id}/documents")
    public List<Document> attach(@PathVariable UUID id, @Valid @RequestBody AttachRequest req) {
        requireChat(id);
        documents.findById(req.documentId())
                .orElseThrow(() -> new NotFoundException("Document not found: " + req.documentId()));
        documents.attach(id, req.documentId());
        return documents.findByConversation(id);
    }

    /** Removes a file from this chat (the file itself stays in your library). */
    @DeleteMapping("/{id}/documents/{documentId}")
    public List<Document> detach(@PathVariable UUID id, @PathVariable UUID documentId) {
        requireChat(id);
        documents.detach(id, documentId);
        return documents.findByConversation(id);
    }

    private void requireChat(UUID id) {
        if (repo.find(id).isEmpty()) throw new NotFoundException("Chat not found: " + id);
    }

    @GetMapping("/{id}")
    public ConversationDetail get(@PathVariable UUID id) {
        return repo.findDetail(id).orElseThrow(() -> new NotFoundException("Chat not found: " + id));
    }

    @PatchMapping("/{id}")
    public Conversation rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest req) {
        if (!repo.rename(id, req.title().trim())) throw new NotFoundException("Chat not found: " + id);
        return repo.find(id).orElseThrow();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        if (!repo.delete(id)) throw new NotFoundException("Chat not found: " + id);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAll() {
        repo.deleteAll();
    }
}
