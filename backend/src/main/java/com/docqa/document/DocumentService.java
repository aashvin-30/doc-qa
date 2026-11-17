package com.docqa.document;

import com.docqa.common.NotFoundException;
import com.docqa.conversation.ConversationRepository;
import com.docqa.ingest.IngestionService;
import com.docqa.ingest.TextExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository repo;
    private final IngestionService ingestion;
    private final ConversationRepository conversations;

    public DocumentService(DocumentRepository repo, IngestionService ingestion, ConversationRepository conversations) {
        this.repo = repo;
        this.ingestion = ingestion;
        this.conversations = conversations;
    }

    /** Uploads a file and, if conversationId is given, attaches it to that chat. */
    public Document upload(MultipartFile file, UUID conversationId) throws IOException {
        if (conversationId != null && conversations.find(conversationId).isEmpty()) {
            throw new NotFoundException("Chat not found: " + conversationId);
        }
        Document doc = upload(file);
        if (conversationId != null) {
            repo.attach(conversationId, doc.id());
        }
        return doc;
    }

    public Document upload(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String filename = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        if (!TextExtractor.isSupported(filename)) {
            throw new IllegalArgumentException("Unsupported file type. Use PDF, DOCX, TXT, MD, CSV, JSON or HTML.");
        }
        UUID id = UUID.randomUUID();
        byte[] bytes = file.getBytes();
        repo.insert(id, filename, file.getContentType(), file.getSize());
        ingestion.ingest(id, filename, bytes); // async
        return get(id);
    }

    public List<Document> list() {
        return repo.findAll();
    }

    public Document get(UUID id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Document not found: " + id));
    }

    public void delete(UUID id) {
        if (!repo.delete(id)) {
            throw new NotFoundException("Document not found: " + id);
        }
    }
}
