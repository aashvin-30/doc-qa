package com.docqa.chat;

import com.docqa.chat.AnswerGenerator.Turn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatServiceTest {

    @Test
    void historyIsAlternatingAndEndsOnAssistant() {
        var out = ChatService.normalize(List.of(
                new Turn("assistant", "orphan"),
                new Turn("user", "q1"),
                new Turn("assistant", "a1"),
                new Turn("user", "q2 (unanswered)")));
        assertEquals(List.of(new Turn("user", "q1"), new Turn("assistant", "a1")), out);
    }

    @Test
    void titleIsTrimmedAndShortened() {
        assertEquals("What is the policy?", ChatService.titleFrom("  What   is the\npolicy?  "));
        String longQ = "x".repeat(100);
        String title = ChatService.titleFrom(longQ);
        assertTrue(title.length() <= 60);
        assertTrue(title.endsWith("…"));
    }
}
