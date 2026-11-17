package com.docqa.document;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DocumentRepository {

    private static final RowMapper<Document> MAPPER = (rs, i) -> new Document(
            rs.getObject("id", UUID.class),
            rs.getString("filename"),
            rs.getString("content_type"),
            rs.getLong("size_bytes"),
            DocumentStatus.valueOf(rs.getString("status")),
            rs.getInt("chunk_count"),
            rs.getString("error_message"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("last_used_at", OffsetDateTime.class)
    );

    private final JdbcTemplate jdbc;

    public DocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, String filename, String contentType, long size) {
        jdbc.update("""
                INSERT INTO documents (id, filename, content_type, size_bytes, status)
                VALUES (?, ?, ?, ?, 'PROCESSING')
                """, id, filename, contentType, size);
    }

    public List<Document> findAll() {
        // Most recently used (or uploaded) first.
        return jdbc.query("SELECT * FROM documents ORDER BY COALESCE(last_used_at, created_at) DESC", MAPPER);
    }

    /** Files attached to a chat, in the order they were added. */
    public List<Document> findByConversation(UUID conversationId) {
        return jdbc.query("""
                SELECT d.* FROM documents d
                JOIN conversation_documents cd ON cd.document_id = d.id
                WHERE cd.conversation_id = ?
                ORDER BY cd.added_at
                """, MAPPER, conversationId);
    }

    public List<UUID> idsByConversation(UUID conversationId) {
        return jdbc.queryForList(
                "SELECT document_id FROM conversation_documents WHERE conversation_id = ? ORDER BY added_at",
                UUID.class, conversationId);
    }

    public void attach(UUID conversationId, UUID documentId) {
        jdbc.update("""
                INSERT INTO conversation_documents (conversation_id, document_id) VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """, conversationId, documentId);
    }

    public boolean detach(UUID conversationId, UUID documentId) {
        return jdbc.update("DELETE FROM conversation_documents WHERE conversation_id = ? AND document_id = ?",
                conversationId, documentId) > 0;
    }

    /** Marks documents as just used (they were cited in an answer). */
    public void touch(Collection<UUID> ids) {
        if (ids.isEmpty()) return;
        String placeholders = String.join(",", ids.stream().map(x -> "?").toList());
        jdbc.update("UPDATE documents SET last_used_at = now() WHERE id IN (" + placeholders + ")", ids.toArray());
    }

    public Optional<Document> findById(UUID id) {
        return jdbc.query("SELECT * FROM documents WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public void markReady(UUID id, int chunkCount) {
        jdbc.update("""
                UPDATE documents SET status = 'READY', chunk_count = ?, error_message = NULL, updated_at = now()
                WHERE id = ?
                """, chunkCount, id);
    }

    public void markFailed(UUID id, String error) {
        jdbc.update("""
                UPDATE documents SET status = 'FAILED', error_message = ?, updated_at = now()
                WHERE id = ?
                """, error, id);
    }

    public boolean delete(UUID id) {
        return jdbc.update("DELETE FROM documents WHERE id = ?", id) > 0;
    }
}
