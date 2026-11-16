package harmonizer.algo.trie;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Trie (prefix tree) written from scratch (Unit 4: text search), mapping String keys to values.
 *
 * <p>Children are kept in a {@link TreeMap} so a depth-first walk yields keys in
 * lexicographic order for free, which gives predictable autocomplete results.
 * Keys are case-sensitive; callers normalise (lower-case) before inserting or searching.
 *
 * <p><b>Complexity</b> (L = key length, k = results returned):
 * <ul>
 *   <li>put, get, containsKey, hasPrefix, remove: O(L log sigma) with TreeMap children
 *       (sigma = alphabet size, at most ~40 here, so effectively O(L)); independent of how many keys are stored.</li>
 *   <li>keysWithPrefix: O(L + visited nodes), stopping as soon as {@code limit} keys are found.</li>
 *   <li>Space: O(total characters in all keys) in the worst case; shared prefixes are stored once.</li>
 * </ul>
 */
public final class Trie<V> {

    /** Longest key accepted. Bounds recursion depth and rejects junk input. */
    public static final int MAX_KEY_LENGTH = 1_000;

    /** A stored key and its value. */
    public record Entry<V>(String key, V value) {
    }

    private static final class Node<V> {
        final TreeMap<Character, Node<V>> children = new TreeMap<>();
        boolean terminal;
        V value;
    }

    private final Node<V> root = new Node<>();
    private int size;

    // ---------------------------------------------------------------- writes

    /** Inserts or overwrites. Returns the previous value, or null if the key was new. */
    public V put(String key, V value) {
        requireValidKey(key);
        Node<V> node = root;
        for (int i = 0; i < key.length(); i++) {
            node = node.children.computeIfAbsent(key.charAt(i), c -> new Node<>());
        }
        V previous = node.terminal ? node.value : null;
        if (!node.terminal) {
            node.terminal = true;
            size++;
        }
        node.value = value;
        return previous;
    }

    /** Removes a key and prunes branches that no longer lead to any key. Returns true if it existed. */
    @SuppressWarnings("unchecked")
    public boolean remove(String key) {
        requireNotNull(key);
        Node<V>[] path = (Node<V>[]) new Node[key.length() + 1];
        path[0] = root;
        for (int i = 0; i < key.length(); i++) {
            Node<V> next = path[i].children.get(key.charAt(i));
            if (next == null) {
                return false;
            }
            path[i + 1] = next;
        }
        Node<V> last = path[key.length()];
        if (!last.terminal) {
            return false;
        }
        last.terminal = false;
        last.value = null;
        size--;
        for (int i = key.length() - 1; i >= 0; i--) {
            Node<V> child = path[i + 1];
            if (child.terminal || !child.children.isEmpty()) {
                break;
            }
            path[i].children.remove(key.charAt(i));
        }
        return true;
    }

    // ---------------------------------------------------------------- reads

    /** Value for an exact key, or null if the key is absent. */
    public V get(String key) {
        Node<V> node = find(key);
        return (node != null && node.terminal) ? node.value : null;
    }

    public boolean containsKey(String key) {
        Node<V> node = find(key);
        return node != null && node.terminal;
    }

    /** True if at least one stored key starts with {@code prefix}. */
    public boolean hasPrefix(String prefix) {
        return find(prefix) != null;
    }

    /** Up to {@code limit} entries whose key starts with {@code prefix}, in lexicographic key order. */
    public List<Entry<V>> entriesWithPrefix(String prefix, int limit) {
        requireNotNull(prefix);
        List<Entry<V>> out = new ArrayList<>();
        Node<V> start = find(prefix);
        if (start == null || limit <= 0) {
            return out;
        }
        collect(start, new StringBuilder(prefix), limit, out);
        return out;
    }

    public List<String> keysWithPrefix(String prefix, int limit) {
        List<Entry<V>> entries = entriesWithPrefix(prefix, limit);
        List<String> keys = new ArrayList<>(entries.size());
        for (Entry<V> e : entries) {
            keys.add(e.key());
        }
        return keys;
    }

    /**
     * Longest stored key that is a prefix of {@code text}, or null if none.
     * Useful for hierarchical codes: "PIP-SMLS-0042" matches the category key "PIP-SMLS".
     */
    public String longestKeyPrefixOf(String text) {
        requireNotNull(text);
        Node<V> node = root;
        int best = -1;
        for (int i = 0; i < text.length(); i++) {
            node = node.children.get(text.charAt(i));
            if (node == null) {
                break;
            }
            if (node.terminal) {
                best = i + 1;
            }
        }
        return best < 0 ? null : text.substring(0, best);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    // ---------------------------------------------------------------- internals

    private Node<V> find(String s) {
        requireNotNull(s);
        Node<V> node = root;
        for (int i = 0; i < s.length(); i++) {
            node = node.children.get(s.charAt(i));
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    private void collect(Node<V> node, StringBuilder path, int limit, List<Entry<V>> out) {
        if (out.size() >= limit) {
            return;
        }
        if (node.terminal) {
            out.add(new Entry<>(path.toString(), node.value));
        }
        for (Map.Entry<Character, Node<V>> child : node.children.entrySet()) {
            if (out.size() >= limit) {
                return;
            }
            path.append(child.getKey());
            collect(child.getValue(), path, limit, out);
            path.deleteCharAt(path.length() - 1);
        }
    }

    private static void requireNotNull(String s) {
        if (s == null) {
            throw new IllegalArgumentException("key must not be null");
        }
    }

    private static void requireValidKey(String key) {
        requireNotNull(key);
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("key longer than " + MAX_KEY_LENGTH + " characters");
        }
    }
}
