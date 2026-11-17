package com.docqa.ingest;

import com.docqa.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits text into overlapping chunks of roughly {@code chunkSize} characters,
 * preferring to break at paragraph, sentence, then word boundaries.
 */
@Component
public class Chunker {

    public record Chunk(int index, Integer pageNumber, String content) {}

    private final int chunkSize;
    private final int overlap;

    public Chunker(AppProperties props) {
        this.chunkSize = props.chunking().chunkSize();
        this.overlap = Math.min(props.chunking().chunkOverlap(), chunkSize / 2);
    }

    public List<Chunk> chunk(List<TextExtractor.PageText> pages) {
        List<Chunk> out = new ArrayList<>();
        for (TextExtractor.PageText page : pages) {
            String text = normalize(page.text());
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + chunkSize, text.length());
                if (end < text.length()) {
                    end = findBreak(text, start, end);
                }
                String piece = text.substring(start, end).trim();
                if (!piece.isEmpty()) {
                    out.add(new Chunk(out.size(), page.pageNumber(), piece));
                }
                if (end >= text.length()) break;
                int next = end - overlap;
                start = Math.max(next, start + 1);
                // don't start mid-word
                while (start < end && !Character.isWhitespace(text.charAt(start - 1))) start++;
            }
        }
        return out;
    }

    private int findBreak(String text, int start, int end) {
        int min = start + chunkSize / 2;
        int idx = text.lastIndexOf("\n\n", end);
        if (idx >= min) return idx;
        for (String sep : new String[]{". ", "? ", "! ", "\n"}) {
            idx = text.lastIndexOf(sep, end);
            if (idx >= min) return idx + 1;
        }
        idx = text.lastIndexOf(' ', end);
        return idx >= min ? idx : end;
    }

    private static String normalize(String s) {
        return s.replace("\r\n", "\n")
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
