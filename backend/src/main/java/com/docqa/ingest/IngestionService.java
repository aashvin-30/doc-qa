package com.docqa.ingest;

import com.docqa.document.DocumentRepository;
import com.docqa.embedding.GeminiEmbeddingClient;
import com.docqa.retrieval.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/** Background pipeline: extract text -> chunk -> embed (Gemini) -> store (pgvector). */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final TextExtractor extractor;
    private final Chunker chunker;
    private final GeminiEmbeddingClient embeddings;
    private final ChunkRepository chunks;
    private final DocumentRepository documents;
    private final TransactionTemplate tx;

    public IngestionService(TextExtractor extractor, Chunker chunker, GeminiEmbeddingClient embeddings,
                            ChunkRepository chunks, DocumentRepository documents, TransactionTemplate tx) {
        this.extractor = extractor;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.documents = documents;
        this.tx = tx;
    }

    @Async("ingestExecutor")
    public void ingest(UUID documentId, String filename, byte[] bytes) {
        long t0 = System.currentTimeMillis();
        try {
            List<TextExtractor.PageText> pages = extractor.extract(filename, bytes);
            List<Chunker.Chunk> pieces = chunker.chunk(pages);
            if (pieces.isEmpty()) {
                throw new IllegalArgumentException("No readable text found (scanned PDFs need OCR first).");
            }
            List<float[]> vectors = embeddings.embedDocuments(pieces.stream().map(Chunker.Chunk::content).toList());

            tx.executeWithoutResult(s -> {
                chunks.deleteByDocument(documentId);
                chunks.insertAll(documentId, pieces, vectors);
                documents.markReady(documentId, pieces.size());
            });
            log.info("Ingested {} ({} chunks) in {} ms", filename, pieces.size(), System.currentTimeMillis() - t0);
        } catch (Exception e) {
            log.warn("Ingestion failed for {}: {}", filename, e.getMessage(), e);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            documents.markFailed(documentId, msg.length() > 500 ? msg.substring(0, 500) : msg);
        }
    }
}
