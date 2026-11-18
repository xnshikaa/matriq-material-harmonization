package harmonizer.algo.dsu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * Disjoint Set Union (Union-Find) over material record ids, used to turn verified equivalence
 * relationships into material groups.
 *
 * <p>The structure holds the <em>approved</em> "same physical material" relationships, nothing else.
 * It never decides equivalence itself: only pairs that the matching service verified (and a reviewer
 * did not reject) are passed to {@link #union(int, int)}.
 *
 * <p>Two standard optimisations keep operations effectively constant:
 * <ul>
 *   <li><b>Path compression</b> — {@link #find(int)} points every node on the path directly at the
 *       root, so later lookups are one step.</li>
 *   <li><b>Union by size</b> — the smaller tree is attached under the larger root, keeping the tree
 *       shallow (and the size of each root's tree exact).</li>
 * </ul>
 * Amortised cost per operation is O(alpha(n)) (inverse Ackermann), space O(n).
 *
 * <p>Ids are 1-based and bounded by the capacity given to the constructor, which matches
 * {@code MaterialRecord.id()} in this project. Ids outside the capacity, or ids that were never
 * registered with {@link #makeSet(int)}, raise {@link IllegalArgumentException} instead of silently
 * corrupting a group.
 *
 * <p><b>No delete/undo.</b> A standard DSU cannot split a set after path compression. If an approved
 * relationship is rejected, the group builder recomputes everything from the remaining approved
 * relationships with a fresh instance (see {@code MaterialGroupingService}).
 */
public final class DisjointSetUnion {

    /** parent[id] == id for a root; parent[id] == 0 means "never registered" (ids start at 1). */
    private final int[] parent;

    /** size[root] is the number of elements in that root's tree; other entries are stale. */
    private final int[] size;

    private final int capacity;
    private int componentCount;
    private int elementCount;

    /**
     * @param capacity highest record id that may be registered (ids are 1..capacity)
     */
    public DisjointSetUnion(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1, got " + capacity);
        }
        this.capacity = capacity;
        this.parent = new int[capacity + 1];
        this.size = new int[capacity + 1];
    }

    /** Registers {@code id} as its own singleton set. Calling it again for the same id is a no-op. */
    public void makeSet(int id) {
        validateRange(id);
        if (parent[id] == 0) {
            parent[id] = id;
            size[id] = 1;
            componentCount++;
            elementCount++;
        }
    }

    /**
     * Returns the representative (root) of the set containing {@code id}, compressing the path so
     * that repeated lookups are O(1).
     */
    public int find(int id) {
        requireRegistered(id);
        int root = id;
        while (parent[root] != root) {
            root = parent[root];
        }
        // Second pass: point every node on the path straight at the root.
        int node = id;
        while (parent[node] != root) {
            int next = parent[node];
            parent[node] = root;
            node = next;
        }
        return root;
    }

    /**
     * Merges the sets containing {@code a} and {@code b}.
     *
     * @return true if the sets were merged, false if they were already connected (safe to repeat)
     */
    public boolean union(int a, int b) {
        int rootA = find(a);
        int rootB = find(b);
        if (rootA == rootB) {
            return false;
        }
        // Union by size: attach the smaller tree under the larger one.
        if (size[rootA] >= size[rootB]) {
            parent[rootB] = rootA;
            size[rootA] += size[rootB];
        } else {
            parent[rootA] = rootB;
            size[rootB] += size[rootA];
        }
        componentCount--;
        return true;
    }

    /** True when both ids belong to the same set. */
    public boolean connected(int a, int b) {
        return find(a) == find(b);
    }

    /**
     * Returns every set as a list of member ids.
     *
     * <p>Each group is sorted ascending, groups are ordered by their smallest member, and every
     * registered id appears exactly once. Singletons are included, so an ungrouped material is
     * visible as a one-element group rather than disappearing.
     */
    public List<List<Integer>> getGroups() {
        TreeMap<Integer, List<Integer>> byRoot = new TreeMap<>();
        for (int id = 1; id <= capacity; id++) {
            if (parent[id] != 0) {
                byRoot.computeIfAbsent(find(id), root -> new ArrayList<>()).add(id);
            }
        }
        List<List<Integer>> groups = new ArrayList<>(byRoot.size());
        for (List<Integer> group : byRoot.values()) {
            groups.add(List.copyOf(group)); // ids were inserted in ascending order
        }
        groups.sort(Comparator.comparingInt(group -> group.get(0)));
        return List.copyOf(groups);
    }

    /** Number of elements in the set containing {@code id} (1 for a singleton). */
    public int getComponentSize(int id) {
        return size[find(id)];
    }

    /** Number of disjoint sets currently in the structure. */
    public int getComponentCount() {
        return componentCount;
    }

    /** Number of registered elements. */
    public int getElementCount() {
        return elementCount;
    }

    /** Highest id this instance can hold. */
    public int capacity() {
        return capacity;
    }

    /** True when {@code id} was registered with {@link #makeSet(int)}. */
    public boolean isRegistered(int id) {
        return id >= 1 && id <= capacity && parent[id] != 0;
    }

    /**
     * Distance from {@code id} to its root (0 for a root).
     *
     * <p>Diagnostic helper: the tests use it to show that {@link #find(int)} really compresses the
     * path, and a visualiser can use it to draw the current tree height.
     */
    public int depth(int id) {
        requireRegistered(id);
        int distance = 0;
        int node = id;
        while (parent[node] != node) {
            node = parent[node];
            distance++;
        }
        return distance;
    }

    // ------------------------------------------------------------------ validation

    private void validateRange(int id) {
        if (id < 1 || id > capacity) {
            throw new IllegalArgumentException(
                    "record id " + id + " is outside the supported range 1.." + capacity);
        }
    }

    private void requireRegistered(int id) {
        validateRange(id);
        if (parent[id] == 0) {
            throw new IllegalArgumentException(
                    "record id " + id + " is not registered; call makeSet(" + id + ") first");
        }
    }
}
