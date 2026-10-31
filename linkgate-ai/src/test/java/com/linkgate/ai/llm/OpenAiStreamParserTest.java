package com.linkgate.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpenAiStreamParserTest {

    private final OpenAiStreamParser parser = new OpenAiStreamParser();

    @Test
    void extractsTokenFromDelta() {
        assertEquals("Hel", parser.tokenOf("data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}"));
    }

    @Test
    void ignoresRoleOnlyFrame() {
        assertNull(parser.tokenOf("data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}"));
    }

    @Test
    void detectsDone() {
        assertTrue(parser.isDone("data: [DONE]"));
        assertFalse(parser.isDone("data: {\"choices\":[]}"));
    }

    @Test
    void ignoresNonDataLines() {
        assertNull(parser.tokenOf(""));
        assertNull(parser.tokenOf(": keep-alive"));
        assertNull(parser.tokenOf("event: message"));
    }

    @Test
    void malformedJsonThrows() {
        assertThrows(IllegalArgumentException.class, () -> parser.tokenOf("data: {oops"));
    }
}
