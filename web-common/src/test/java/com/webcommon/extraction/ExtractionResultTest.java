package com.webcommon.extraction;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExtractionResult model class.
 */
class ExtractionResultTest {

    @Test
    @DisplayName("Default constructor creates empty result")
    void testDefaultConstructor() {
        ExtractionResult result = new ExtractionResult();

        assertNull(result.getQuery());
        assertEquals(0, result.getCount());
        assertNotNull(result.getMatches());
        assertTrue(result.getMatches().isEmpty());
        assertNull(result.getError());
        assertFalse(result.hasError());
    }

    @Test
    @DisplayName("Constructor with query sets query")
    void testQueryConstructor() {
        ExtractionResult result = new ExtractionResult("h1");

        assertEquals("h1", result.getQuery());
        assertEquals(0, result.getCount());
        assertNotNull(result.getMatches());
        assertTrue(result.getMatches().isEmpty());
        assertNull(result.getError());
    }

    @Test
    @DisplayName("Setter and getter work correctly")
    void testSettersAndGetters() {
        ExtractionResult result = new ExtractionResult();

        result.setQuery("div.content");
        result.setCount(5);
        result.setError("Test error");

        assertEquals("div.content", result.getQuery());
        assertEquals(5, result.getCount());
        assertEquals("Test error", result.getError());
        assertTrue(result.hasError());
    }

    @Test
    @DisplayName("Matches can be added")
    void testAddMatches() {
        ExtractionResult result = new ExtractionResult("h1");

        Map<String, Object> match1 = ExtractionResult.createMatch(
            "Heading 1",
            Map.of("id", "h1", "class", "title"),
            "<h1>Heading 1</h1>"
        );
        Map<String, Object> match2 = ExtractionResult.createMatch(
            "Heading 2",
            Map.of("id", "h2"),
            null
        );

        List<Map<String, Object>> matches = new ArrayList<>();
        matches.add(match1);
        matches.add(match2);
        result.setMatches(matches);
        result.setCount(2);

        assertEquals(2, result.getCount());
        assertEquals(2, result.getMatches().size());

        Map<String, Object> first = result.getMatches().get(0);
        assertEquals("Heading 1", first.get("text"));
        assertEquals("h1", ((Map<?, ?>) first.get("attributes")).get("id"));
        assertEquals("<h1>Heading 1</h1>", first.get("html"));

        Map<String, Object> second = result.getMatches().get(1);
        assertEquals("Heading 2", second.get("text"));
        assertEquals("h2", ((Map<?, ?>) second.get("attributes")).get("id"));
        assertNull(second.get("html"));
    }

    @Test
    @DisplayName("hasError returns true when error is set")
    void testHasErrorTrue() {
        ExtractionResult result = new ExtractionResult();
        result.setError("Something went wrong");

        assertTrue(result.hasError());
    }

    @Test
    @DisplayName("hasError returns false when error is null")
    void testHasErrorFalseNull() {
        ExtractionResult result = new ExtractionResult();
        result.setError(null);

        assertFalse(result.hasError());
    }

    @Test
    @DisplayName("hasError returns false when error is empty")
    void testHasErrorFalseEmpty() {
        ExtractionResult result = new ExtractionResult();
        result.setError("");

        assertFalse(result.hasError());
    }

    @Test
    @DisplayName("hasError returns true when error is whitespace (not empty)")
    void testHasErrorTrueWhitespace() {
        ExtractionResult result = new ExtractionResult();
        result.setError("   ");

        assertTrue(result.hasError());
    }

    @Test
    @DisplayName("createMatch creates proper match map")
    void testCreateMatch() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("href", "/link");
        attrs.put("class", "btn");

        Map<String, Object> match = ExtractionResult.createMatch("Click me", attrs, "<a>Click me</a>");

        assertEquals("Click me", match.get("text"));
        assertEquals("/link", ((Map<?, ?>) match.get("attributes")).get("href"));
        assertEquals("btn", ((Map<?, ?>) match.get("attributes")).get("class"));
        assertEquals("<a>Click me</a>", match.get("html"));
    }

    @Test
    @DisplayName("createMatch handles null attributes")
    void testCreateMatchNullAttributes() {
        Map<String, Object> match = ExtractionResult.createMatch("Text only", null, null);

        assertEquals("Text only", match.get("text"));
        assertFalse(match.containsKey("attributes"));
        assertFalse(match.containsKey("html"));
    }

    @Test
    @DisplayName("createMatch handles empty attributes")
    void testCreateMatchEmptyAttributes() {
        Map<String, Object> match = ExtractionResult.createMatch("Text only", new HashMap<>(), null);

        assertEquals("Text only", match.get("text"));
        assertFalse(match.containsKey("attributes"));
        assertFalse(match.containsKey("html"));
    }

    @Test
    @DisplayName("Equals and hashCode work correctly")
    void testEqualsAndHashCode() {
        ExtractionResult r1 = new ExtractionResult("h1");
        r1.setCount(1);
        r1.setMatches(List.of(ExtractionResult.createMatch("Test", null, null)));

        ExtractionResult r2 = new ExtractionResult("h1");
        r2.setCount(1);
        r2.setMatches(List.of(ExtractionResult.createMatch("Test", null, null)));

        ExtractionResult r3 = new ExtractionResult("h2");
        r3.setCount(1);

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertNotEquals(r1, r3);
    }

    @Test
    @DisplayName("ToString contains fields")
    void testToString() {
        ExtractionResult result = new ExtractionResult("h1");
        result.setCount(2);
        result.setError("Error message");

        String toString = result.toString();

        assertTrue(toString.contains("h1"));
        assertTrue(toString.contains("2"));
        assertTrue(toString.contains("Error message"));
        assertTrue(toString.contains("ExtractionResult"));
    }
}