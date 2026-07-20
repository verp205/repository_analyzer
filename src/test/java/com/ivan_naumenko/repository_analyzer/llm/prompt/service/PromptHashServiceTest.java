package com.ivan_naumenko.repository_analyzer.llm.prompt.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptHashServiceTest {

    private final PromptHashService service = new PromptHashService();

    @Test
    void returnsSameHashForSamePrompt() {
        String first = service.calculate("test prompt");
        String second = service.calculate("test prompt");

        assertEquals(first, second);
    }

    @Test
    void returnsDifferentHashesForDifferentPrompts() {
        String first = service.calculate("first prompt");
        String second = service.calculate("second prompt");

        assertNotEquals(first, second);
    }

    @Test
    void returnsLowercaseSha256Hex() {
        String hash = service.calculate("test prompt");

        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void returnsKnownSha256Value() {
        assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e"
                        + "73043362938b9824",
                service.calculate("hello")
        );
    }

    @Test
    void rejectsNullAndBlankPrompts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculate(null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculate("   ")
        );
    }
}
