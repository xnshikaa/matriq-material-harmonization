package com.matriq.algorithms.trie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * TRIE (Prefix Tree) — MATRIQ Algorithm Module
 * Team Responsibility: Hetanshi Vora
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * PURPOSE:
 *   1. Prefix search for material codes and descriptions during lookup.
 *   2. Abbreviation dictionary — maps short forms to full forms
 *      (e.g. "SS" → "STAINLESS STEEL", "CS" → "CARBON STEEL").
 *
 * COMPLEXITY:
 *   Insert     : O(m)   — m = key length
 *   Search     : O(m)
 *   StartsWith : O(m)
 *   Suggestions: O(m + results)
 */
public class MaterialTrie {

    private final TrieNode root = new TrieNode();

    // ─────────────────────────────────────────────────────────────────────────
    // TrieNode
    // ─────────────────────────────────────────────────────────────────────────

    public static class TrieNode {
        /** Child nodes indexed by character. */
        final Map<Character, TrieNode> children = new HashMap<>();

        /** True if this node marks the end of an inserted key. */
        boolean isEnd = false;

        /** If this node ends a key, store the associated value (e.g. full form of abbreviation). */
        String value = null;

        /** Original casing of the inserted key */
        String originalKey = null;

        /** The key character this node represents (for display / traversal). */
        char character;

        public TrieNode() {}

        public TrieNode(char c) {
            this.character = c;
        }

        public Map<Character, TrieNode> getChildren() { return children; }
        public boolean isEnd() { return isEnd; }
        public String getValue() { return value; }
        public String getOriginalKey() { return originalKey; }
        public char getCharacter() { return character; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Insert — O(m)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Inserts a key into the trie.
     *
     * @param key   the string to insert (normalised to lowercase internally)
     * @param value associated value (e.g. full form of an abbreviation); may be null
     */
    public void insert(String key, String value) {
        if (key == null || key.isEmpty()) return;
        String k = key.toLowerCase();
        TrieNode current = root;
        for (char c : k.toCharArray()) {
            current.children.putIfAbsent(c, new TrieNode(c));
            current = current.children.get(c);
        }
        current.isEnd = true;
        current.value = value;
        current.originalKey = key;
    }

    public void insert(String key) {
        insert(key, key);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Search — O(m)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns true if the exact key exists in the trie.
     */
    public boolean search(String key) {
        TrieNode node = getNode(key);
        return node != null && node.isEnd;
    }

    /**
     * Returns the value associated with the key (e.g. the expanded abbreviation).
     * Returns null if the key is not in the trie.
     */
    public String getValue(String key) {
        TrieNode node = getNode(key);
        return (node != null && node.isEnd) ? node.value : null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // StartsWith — O(m)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns true if any inserted key starts with the given prefix.
     */
    public boolean startsWith(String prefix) {
        return getNode(prefix) != null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Prefix suggestions — O(m + k) where k = number of results
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all keys in the trie that start with the given prefix.
     * Useful for autocomplete and partial code matching.
     *
     * @param prefix the prefix to search for
     * @return list of matching keys (up to 20 results)
     */
    public List<String> getSuggestions(String prefix) {
        List<String> results = new ArrayList<>();
        TrieNode node = getNode(prefix);
        if (node == null) return results;

        // DFS from the node where prefix ends
        collectKeys(node, prefix.toLowerCase(), results, 20);
        return results;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private TrieNode getNode(String key) {
        if (key == null || key.isEmpty()) return null;
        TrieNode current = root;
        for (char c : key.toLowerCase().toCharArray()) {
            if (!current.children.containsKey(c)) return null;
            current = current.children.get(c);
        }
        return current;
    }

    private void collectKeys(TrieNode node, String prefix, List<String> results, int limit) {
        if (results.size() >= limit) return;
        if (node.isEnd) results.add(node.originalKey != null ? node.originalKey : prefix);
        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            collectKeys(entry.getValue(), prefix + entry.getKey(), results, limit);
        }
    }

    public TrieNode getRoot() { return root; }
}
