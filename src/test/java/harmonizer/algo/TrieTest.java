package harmonizer.algo;

import harmonizer.algo.trie.Trie;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Correctness tests for the Trie (at least five, including boundary, invalid and randomised cases). */
class TrieTest {

    @Test
    void putAndGetExactKeys() {
        Trie<Integer> t = new Trie<>();
        assertNull(t.put("pipe", 1));
        t.put("pipes", 2);
        assertEquals(1, t.get("pipe"));
        assertEquals(2, t.get("pipes"));
        assertEquals(2, t.size());
    }

    @Test
    void missingKeysAreNotFound() {
        Trie<Integer> t = new Trie<>();
        t.put("pipe", 1);
        assertNull(t.get("pip"));      // a prefix is not a key
        assertNull(t.get("pipes"));
        assertFalse(t.containsKey("pip"));
        assertTrue(t.hasPrefix("pip")); // ...but it is a prefix
        assertFalse(t.hasPrefix("valve"));
    }

    @Test
    void prefixSearchIsSortedAndRespectsLimit() {
        Trie<Integer> t = new Trie<>();
        for (String k : new String[]{"valve", "pipe", "pipeline", "pipe fitting", "pig", "pipes"}) {
            t.put(k, 0);
        }
        assertEquals(List.of("pipe", "pipe fitting", "pipeline", "pipes"), t.keysWithPrefix("pipe", 10));
        assertEquals(List.of("pipe", "pipe fitting"), t.keysWithPrefix("pipe", 2));
        assertEquals(List.of(), t.keysWithPrefix("zzz", 10));   // no match
        assertEquals(List.of(), t.keysWithPrefix("pipe", 0));   // zero limit
    }

    @Test
    void emptyPrefixListsEverythingInOrder() { // boundary
        Trie<Integer> t = new Trie<>();
        t.put("b", 1);
        t.put("a", 2);
        t.put("c", 3);
        assertEquals(List.of("a", "b", "c"), t.keysWithPrefix("", 10));
        assertEquals(List.of(), new Trie<Integer>().keysWithPrefix("", 10)); // empty trie
    }

    @Test
    void overwriteReturnsOldValueAndKeepsSize() {
        Trie<String> t = new Trie<>();
        t.put("code", "old");
        assertEquals("old", t.put("code", "new"));
        assertEquals("new", t.get("code"));
        assertEquals(1, t.size());
    }

    @Test
    void removeKeepsSiblingsAndPrunesDeadBranches() {
        Trie<Integer> t = new Trie<>();
        t.put("pipe", 1);
        t.put("pipes", 2);
        t.put("valve", 3);
        assertTrue(t.remove("pipes"));
        assertFalse(t.containsKey("pipes"));
        assertTrue(t.containsKey("pipe"));      // shorter key survives
        assertEquals(2, t.size());
        assertTrue(t.remove("valve"));
        assertFalse(t.hasPrefix("v"));          // branch pruned completely
        assertFalse(t.remove("valve"));         // already gone
        assertFalse(t.remove("nothing"));
        assertFalse(t.remove("pip"));           // exists only as a prefix
    }

    @Test
    void rejectsInvalidKeys() {
        Trie<Integer> t = new Trie<>();
        assertThrows(IllegalArgumentException.class, () -> t.put(null, 1));
        assertThrows(IllegalArgumentException.class, () -> t.put("", 1));
        assertThrows(IllegalArgumentException.class, () -> t.put("x".repeat(Trie.MAX_KEY_LENGTH + 1), 1));
        assertThrows(IllegalArgumentException.class, () -> t.get(null));
        assertThrows(IllegalArgumentException.class, () -> t.keysWithPrefix(null, 5));
        t.put("x".repeat(Trie.MAX_KEY_LENGTH), 1); // exactly at the limit is fine
        assertEquals(1, t.size());
    }

    @Test
    void longestKeyPrefixOfFindsHierarchicalCategory() {
        Trie<Integer> t = new Trie<>();
        t.put("pip", 1);
        t.put("pip-smls", 2);
        assertEquals("pip-smls", t.longestKeyPrefixOf("pip-smls-0042"));
        assertEquals("pip", t.longestKeyPrefixOf("pip-weld-1"));
        assertNull(t.longestKeyPrefixOf("valve-1"));
        assertNull(t.longestKeyPrefixOf(""));
    }

    @Test
    void agreesWithBruteForceOnRandomData() { // randomised check against an obviously-correct oracle
        Random rnd = new Random(7);
        Trie<Integer> t = new Trie<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 2_000; i++) {
            StringBuilder sb = new StringBuilder();
            int len = 1 + rnd.nextInt(8);
            for (int j = 0; j < len; j++) {
                sb.append((char) ('a' + rnd.nextInt(4))); // tiny alphabet forces many shared prefixes
            }
            if (t.put(sb.toString(), i) == null) {
                keys.add(sb.toString());
            }
        }
        Collections.sort(keys);
        for (int q = 0; q < 300; q++) {
            String prefix = keys.get(rnd.nextInt(keys.size()));
            prefix = prefix.substring(0, rnd.nextInt(prefix.length() + 1));
            List<String> expected = new ArrayList<>();
            for (String k : keys) {
                if (k.startsWith(prefix) && expected.size() < 15) {
                    expected.add(k);
                }
            }
            assertEquals(expected, t.keysWithPrefix(prefix, 15), "prefix '" + prefix + "'");
        }
    }
}
