package com.webcommon.extraction;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExtractionOptions model class.
 */
class ExtractionOptionsTest {

    @Test
    @DisplayName("Default constructor creates empty options")
    void testDefaultConstructor() {
        ExtractionOptions options = new ExtractionOptions();

        assertNotNull(options.getAttrs());
        assertTrue(options.getAttrs().isEmpty());
        assertFalse(options.isHtml());
        assertEquals(0, options.getLimit());
    }

    @Test
    @DisplayName("Constructor with all fields sets correctly")
    void testFullConstructor() {
        List<String> attrs = Arrays.asList("href", "src", "data-id");
        ExtractionOptions options = new ExtractionOptions(attrs, true, 10);

        assertEquals(attrs, options.getAttrs());
        assertTrue(options.isHtml());
        assertEquals(10, options.getLimit());
    }

    @Test
    @DisplayName("Constructor handles null attrs")
    void testConstructorNullAttrs() {
        ExtractionOptions options = new ExtractionOptions(null, true, 5);

        assertNotNull(options.getAttrs());
        assertTrue(options.getAttrs().isEmpty());
        assertTrue(options.isHtml());
        assertEquals(5, options.getLimit());
    }

    @Test
    @DisplayName("Setter and getter work correctly")
    void testSettersAndGetters() {
        ExtractionOptions options = new ExtractionOptions();

        options.setAttrs(Arrays.asList("class", "id"));
        options.setHtml(true);
        options.setLimit(20);

        assertEquals(Arrays.asList("class", "id"), options.getAttrs());
        assertTrue(options.isHtml());
        assertEquals(20, options.getLimit());
    }

    @Test
    @DisplayName("toQuery converts to ExtractionQuery")
    void testToQuery() {
        ExtractionOptions options = new ExtractionOptions(
            Arrays.asList("href", "src"),
            true,
            10
        );

        ExtractionQuery query = options.toQuery("a.link");

        assertEquals("a.link", query.getQuery());
        assertEquals(Arrays.asList("href", "src"), query.getAttrs());
        assertTrue(query.isHtml());
        assertEquals(10, query.getLimit());
    }

    @Test
    @DisplayName("toQuery uses default values when not set")
    void testToQueryDefaults() {
        ExtractionOptions options = new ExtractionOptions();

        ExtractionQuery query = options.toQuery("h1");

        assertEquals("h1", query.getQuery());
        assertTrue(query.getAttrs().isEmpty());
        assertFalse(query.isHtml());
        assertEquals(0, query.getLimit());
    }

    @Test
    @DisplayName("Equals and hashCode work correctly")
    void testEqualsAndHashCode() {
        ExtractionOptions o1 = new ExtractionOptions(Arrays.asList("href"), true, 5);
        ExtractionOptions o2 = new ExtractionOptions(Arrays.asList("href"), true, 5);
        ExtractionOptions o3 = new ExtractionOptions(Arrays.asList("src"), true, 5);

        assertEquals(o1, o2);
        assertEquals(o1.hashCode(), o2.hashCode());
        assertNotEquals(o1, o3);
    }

    @Test
    @DisplayName("ToString contains fields")
    void testToString() {
        ExtractionOptions options = new ExtractionOptions(Arrays.asList("href"), true, 10);
        String toString = options.toString();

        assertTrue(toString.contains("href"));
        assertTrue(toString.contains("true"));
        assertTrue(toString.contains("10"));
        assertTrue(toString.contains("ExtractionOptions"));
    }
}