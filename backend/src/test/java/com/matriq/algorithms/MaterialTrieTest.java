package com.matriq.algorithms;

import com.matriq.algorithms.trie.MaterialTrie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 test suite for MaterialTrie.
 * Tests prefix lookup and industrial abbreviation mapping.
 */
class MaterialTrieTest {

    @Test
    @DisplayName("Trie: Insert and exact search")
    void testInsertAndSearch() {
        MaterialTrie trie = new MaterialTrie();
        trie.insert("ASTM A106", "Standard for Seamless Carbon Steel Pipe");
        trie.insert("SS316", "Stainless Steel Grade 316");

        assertTrue(trie.search("ASTM A106"));
        assertTrue(trie.search("SS316"));
        assertFalse(trie.search("ASTM"));
        assertFalse(trie.search("API 5L"));
    }

    @Test
    @DisplayName("Trie: StartsWith and prefix suggestions")
    void testPrefixSuggestions() {
        MaterialTrie trie = new MaterialTrie();
        trie.insert("ASTM A106", "Carbon Steel Pipe");
        trie.insert("ASTM A333", "Low Temperature Pipe");
        trie.insert("ASTM A312", "Stainless Steel Pipe");
        trie.insert("API 5L", "Line Pipe");

        assertTrue(trie.startsWith("ASTM"));
        assertTrue(trie.startsWith("API"));
        assertFalse(trie.startsWith("ANSI"));

        List<String> suggestions = trie.getSuggestions("ASTM");
        assertEquals(3, suggestions.size());
        assertTrue(suggestions.contains("ASTM A106"));
        assertTrue(suggestions.contains("ASTM A333"));
        assertTrue(suggestions.contains("ASTM A312"));
    }

    @Test
    @DisplayName("Trie: Value retrieval for abbreviation expansion")
    void testAbbreviationExpansion() {
        MaterialTrie trie = new MaterialTrie();
        trie.insert("CS", "CARBON STEEL");
        trie.insert("SS", "STAINLESS STEEL");
        trie.insert("ERW", "ELECTRIC RESISTANCE WELDED");

        assertEquals("CARBON STEEL", trie.getValue("CS"));
        assertEquals("STAINLESS STEEL", trie.getValue("SS"));
        assertNull(trie.getValue("UNKNOWN"));
    }
}
