package com.docqa.conversation;

import com.docqa.chat.AnswerGenerator.Turn;
import com.docqa.chat.ChatDtos.Source;
import com.docqa.document.Document;
import com.docqa.document.DocumentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConversationRepository {

    public record Conversation(UUID id, String title, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                               int messageCount) {}

    public record StoredMessage(long id, String role, String content, List<Source> sources, String error,
                                OffsetDateTime createdAt) {}

    public record ConversationDetail(UUID id, String title, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                                     List<StoredMessage> messages, List<Document> documents) {}

    public static final String DEFAULT_TITLE = "New chat";

    private static final TypeReference<List<Source>> SOURCES = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final DocumentRepository documents;

    private final RowMapper<Conversation> convMapper = (rs, i) -> new Conversation(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getInt("message_count"));

    public ConversationRepository(JdbcTemplate jdbc, ObjectMapper json, DocumentRepository documents) {
        this.jdbc = jdbc;
        this.json = json;
        this.documents = documents;
    }

    public Conversation create(String title) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO conversations (id, title) VALUES (?, ?)", id, title);
        return find(id).orElseThrow();
    }

    public List<Conversation> findAll() {
        return jdbc.query("""
                SELECT c.*, (SELECT count(*) FROM messages m WHERE m.conversation_id = c.id) AS message_count
                FROM conversations c ORDER BY c.updated_at DESC
                """, convMapper);
    }

    public Optional<Conversation> find(UUID id) {
        return jdbc.query("""
                SELECT c.*, (SELECT count(*) FROM messages m WHERE m.conversation_id = c.id) AS message_count
                FROM conversations c WHERE c.id = ?
                """, convMapper, id).stream().findFirst();
    }

    public Optional<ConversationDetail> findDetail(UUID id) {
        return find(id).map(c -> new ConversationDetail(c.id(), c.title(), c.createdAt(), c.updatedAt(),
                jdbc.query("SELECT * FROM messages WHERE conversation_id = ? ORDER BY id", (rs, i) ->
                        new StoredMessage(
                                rs.getLong("id"),
                                rs.getString("role"),
                                rs.getString("content"),
                                readSources(rs.getString("sources")),
                                rs.getString("error"),
                                rs.getObject("created_at", OffsetDateTime.class)), id),
                documents.findByConversation(id)));
    }

    /** Sets the title from the first question, unless the user already renamed the chat. */
    public void setTitleIfDefault(UUID id, String title) {
        jdbc.update("UPDATE conversations SET title = ? WHERE id = ? AND title = ?", title, id, DEFAULT_TITLE);
    }

    /** The most recent {@code limit} turns, oldest first, for use as model history. */
    public List<Turn> recentTurns(UUID conversationId, int limit) {
        List<Turn> turns = new ArrayList<>(jdbc.query("""
                SELECT role, content FROM messages
                WHERE conversation_id = ? AND error IS NULL AND content <> ''
                ORDER BY id DESC LIMIT ?
                """, (rs, i) -> new Turn(rs.getString("role"), rs.getString("content")), conversationId, limit));
        Collections.reverse(turns);
        return turns;
    }

    public void addMessage(UUID conversationId, String role, String content, List<Source> sources, String error) {
        jdbc.update("""
                INSERT INTO messages (conversation_id, role, content, sources, error) VALUES (?, ?, ?, ?::jsonb, ?)
                """, conversationId, role, content, writeSources(sources), error);
        jdbc.update("UPDATE conversations SET updated_at = now() WHERE id = ?", conversationId);
    }

    public boolean rename(UUID id, String title) {
        return jdbc.update("UPDATE conversations SET title = ? WHERE id = ?", title, id) > 0;
    }

    public boolean delete(UUID id) {
        return jdbc.update("DELETE FROM conversations WHERE id = ?", id) > 0;
    }

    public int deleteAll() {
        return jdbc.update("DELETE FROM conversations");
    }

    private String writeSources(List<Source> sources) {
        if (sources == null || sources.isEmpty()) return null;
        try {
            return json.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Source> readSources(String raw) {
        if (raw == null) return List.of();
        try {
            return json.readValue(raw, SOURCES);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
