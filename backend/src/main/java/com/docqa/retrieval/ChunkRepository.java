package com.docqa.retrieval;

import com.docqa.ingest.Chunker;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public class ChunkRepository {

    public record ScoredChunk(
            long id,
            UUID documentId,
            String filename,
            int chunkIndex,
            Integer pageNumber,
            String content,
            double similarity
    ) {}

    private final JdbcTemplate jdbc;

    public ChunkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertAll(UUID documentId, List<Chunker.Chunk> chunks, List<float[]> embeddings) {
        List<Object[]> rows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Chunker.Chunk c = chunks.get(i);
            rows.add(new Object[]{documentId, c.index(), c.pageNumber(), c.content(), toVectorLiteral(embeddings.get(i))});
        }
        jdbc.batchUpdate("""
                INSERT INTO chunks (document_id, chunk_index, page_number, content, embedding)
                VALUES (?, ?, ?, ?, ?::vector)
                """, rows);
    }

    public void deleteByDocument(UUID documentId) {
        jdbc.update("DELETE FROM chunks WHERE document_id = ?", documentId);
    }

    /** Cosine-similarity search over READY documents, optionally restricted to some document ids. */
    public List<ScoredChunk> search(float[] query, int topK, Collection<UUID> documentIds) {
        String vec = toVectorLiteral(query);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.document_id, d.filename, c.chunk_index, c.page_number, c.content,
                       1 - (c.embedding <=> ?::vector) AS similarity
                FROM chunks c
                JOIN documents d ON d.id = c.document_id
                WHERE d.status = 'READY'
                """);
        args.add(vec);
        if (documentIds != null && !documentIds.isEmpty()) {
            sql.append(" AND c.document_id IN (")
               .append(String.join(",", documentIds.stream().map(x -> "?").toList()))
               .append(")");
            args.addAll(documentIds);
        }
        sql.append(" ORDER BY c.embedding <=> ?::vector LIMIT ?");
        args.add(vec);
        args.add(topK);

        return jdbc.query(sql.toString(), (rs, i) -> new ScoredChunk(
                rs.getLong("id"),
                rs.getObject("document_id", UUID.class),
                rs.getString("filename"),
                rs.getInt("chunk_index"),
                (Integer) rs.getObject("page_number"),
                rs.getString("content"),
                rs.getDouble("similarity")
        ), args.toArray());
    }

    static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 10).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
