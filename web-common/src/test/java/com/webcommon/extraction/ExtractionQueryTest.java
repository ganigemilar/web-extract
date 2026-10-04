package com.webcommon.extraction;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExtractionQuery model class.
 */
class ExtractionQueryTest {

    @Test
    @DisplayName("Default constructor creates empty query")
    void testDefaultConstructor() {
        ExtractionQuery query = new ExtractionQuery();

        assertNull(query.getQuery());
        assertNotNull(query.getAttrs());
        assertTrue(query.getAttrs().isEmpty());
        assertFalse(query.isHtml());
        assertEquals(0, query.getLimit());
    }

    @Test
    @DisplayName("Constructor with query string sets query")
    void testQueryConstructor() {
        ExtractionQuery query = new ExtractionQuery("h1");

        assertEquals("h1", query.getQuery());
        assertNotNull(query.getAttrs());
        assertTrue(query.getAttrs().isEmpty());
        assertFalse(query.isHtml());
        assertEquals(0, query.getLimit());
    }

    @Test
    @DisplayName("Full constructor sets all fields")
    void testFullConstructor() {
        List<String> attrs = Arrays.asList("href", "src", "data-id");
        ExtractionQuery query = new ExtractionQuery("a.link", attrs, true, 10);

        assertEquals("a.link", query.getQuery());
        assertEquals(attrs, query.getAttrs());
        assertTrue(query.isHtml());
        assertEquals(10, query.getLimit());
    }

    @Test
    @DisplayName("Full constructor handles null attrs")
    void testFullConstructorNullAttrs() {
        ExtractionQuery query = new ExtractionQuery("h1", null, true, 5);

        assertEquals("h1", query.getQuery());
        assertNotNull(query.getAttrs());
        assertTrue(query.getAttrs().isEmpty());
        assertTrue(query.isHtml());
        assertEquals(5, query.getLimit());
    }

    @Test
    @DisplayName("Setter and getter work correctly")
    void testSettersAndGetters() {
        ExtractionQuery query = new ExtractionQuery();

        query.setQuery("div.content");
        query.setAttrs(Arrays.asList("class", "id"));
        query.setHtml(true);
        query.setLimit(20);

        assertEquals("div.content", query.getQuery());
        assertEquals(Arrays.asList("class", "id"), query.getAttrs());
        assertTrue(query.isHtml());
        assertEquals(20, query.getLimit());
    }

    @Test
    @DisplayName("Equals and hashCode work correctly")
    void testEqualsAndHashCode() {
        ExtractionQuery q1 = new ExtractionQuery("h1", Arrays.asList("href"), true, 5);
        ExtractionQuery q2 = new ExtractionQuery("h1", Arrays.asList("href"), true, 5);
        ExtractionQuery q3 = new ExtractionQuery("h2", Arrays.asList("href"), true, 5);

        assertEquals(q1, q2);
        assertEquals(q1.hashCode(), q2.hashCode());
        assertNotEquals(q1, q3);
    }

    @Test
    @DisplayName("ToString contains query")
    void testToString() {
        ExtractionQuery query = new ExtractionQuery("h1");
        String toString = query.toString();

        assertTrue(toString.contains("h1"));
        assertTrue(toString.contains("ExtractionQuery"));
    }
}