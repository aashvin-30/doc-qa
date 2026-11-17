package com.docqa.ingest;

import com.docqa.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkerTest {

    private final Chunker chunker = new Chunker(new AppProperties(
            null, null, new AppProperties.Chunking(200, 40), null, null, null));

    @Test
    void shortTextIsOneChunk() {
        var chunks = chunker.chunk(List.of(new TextExtractor.PageText(3, "Hello world.")));
        assertEquals(1, chunks.size());
        assertEquals("Hello world.", chunks.getFirst().content());
        assertEquals(3, chunks.getFirst().pageNumber());
    }

    @Test
    void longTextIsSplitWithBoundedSizeAndCoversAllWords() {
        String sentence = "The quick brown fox jumps over the lazy dog. ";
        String text = sentence.repeat(40);
        var chunks = chunker.chunk(List.of(new TextExtractor.PageText(null, text)));

        assertTrue(chunks.size() > 5);
        for (var c : chunks) {
            assertTrue(c.content().length() <= 200, "chunk too long: " + c.content().length());
            assertFalse(c.content().isBlank());
        }
        assertTrue(chunks.getLast().content().endsWith("lazy dog."));
        for (int i = 0; i < chunks.size(); i++) assertEquals(i, chunks.get(i).index());
    }
}
