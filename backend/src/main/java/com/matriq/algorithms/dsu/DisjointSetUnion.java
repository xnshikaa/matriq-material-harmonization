package com.matriq.algorithms.dsu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * DISJOINT SET UNION (Union-Find) — MATRIQ Algorithm Module
 * Implemented by: Anshika Gupta
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * PURPOSE:
 *   Groups material records that have been verified as physically equivalent.
 *   Each group gets a single national material code.
 *
 *   Example of transitive grouping:
 *     union(A, B) → {A, B}
 *     union(B, C) → {A, B, C}      ← A and C are now in the same group
 *                                      even though they were never directly compared
 *
 * OPERATIONS:
 *   makeSet(id)        — registers a new material record as its own set  O(1)
 *   find(id)           — returns the representative of the set           O(α(n)) amortised
 *   union(id1, id2)    — merges the sets containing id1 and id2          O(α(n)) amortised
 *   connected(a, b)    — true if a and b are in the same set
 *   getGroups()        — returns all disjoint sets as lists
 *   getComponentSize() — returns the size of the set containing id
 *
 * OPTIMISATIONS:
 *   1. PATH COMPRESSION  — during find(), every node on the path to the root
 *      is updated to point directly to the root. Future finds are O(1).
 *   2. UNION BY SIZE     — always attach the smaller tree under the larger.
 *      This keeps tree height at O(log n) in the worst case.
 *   Together these achieve amortised O(α(n)) per operation, where α is the
 *   inverse Ackermann function — effectively constant for all practical n.
 *
 * COMPLEXITY:
 *   Time  : amortised O(α(n)) per find/union
 *   Space : O(n)
 *
 * IMPORTANT APPLICATION RULE:
 *   Only verified/approved material matches may be passed to union().
 *   The DSU does not decide equivalence — it only records decisions
 *   made by the MatchingService and the reviewer.
 *
 * REJECTION HANDLING:
 *   Standard DSU cannot undo a union. When a reviewer rejects an approved
 *   match, the DSU state is rebuilt from scratch using only the remaining
 *   approved edges (see DSUService.rebuildFromApprovedEdges).
 */
public class DisjointSetUnion {

    /**
     * Maps each record ID to its parent ID.
     * If parent[id] == id, then id is the root (representative) of its set.
     */
    private final Map<String, String> parent = new HashMap<>();

    /**
     * Maps each root ID to the number of elements in its set.
     * Only the root's size entry is accurate; non-root entries are stale.
     */
    private final Map<String, Integer> size = new HashMap<>();

    /** Total number of distinct sets currently in the structure. */
    private int componentCount = 0;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. makeSet — O(1)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Registers {@code id} as a new singleton set containing only itself.
     * If the ID is already registered, this call is a safe no-op.
     *
     * Called once per imported material record before any unions are performed.
     *
     * @param id material record ID
     * @throws IllegalArgumentException if id is null or blank
     */
    public void makeSet(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Record ID must not be null or blank.");
        }
        if (!parent.containsKey(id)) {
            parent.put(id, id);  // each element is its own parent initially
            size.put(id, 1);
            componentCount++;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. find — O(α(n)) amortised with path compression
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the representative (root) of the set containing {@code id}.
     *
     * PATH COMPRESSION:
     *   Every node visited on the path from {@code id} to the root is
     *   re-parented directly to the root. This flattens the tree so that
     *   subsequent finds are O(1).
     *
     *   Example before compression:  A → B → C → D (root)
     *   After find(A):               A → D, B → D, C → D  (all flat)
     *
     * @param id material record ID
     * @return the representative ID of the set
     * @throws IllegalArgumentException if id is not registered
     */
    public String find(String id) {
        validateId(id);

        if (!parent.get(id).equals(id)) {
            // Path compression: recursively find root and update parent directly
            parent.put(id, find(parent.get(id)));
        }
        return parent.get(id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. union — O(α(n)) amortised with union by size
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Merges the sets containing {@code id1} and {@code id2}.
     *
     * PRECONDITION: This method must ONLY be called after the corresponding
     * MaterialMatch has been approved (status = APPROVED or AUTO_APPROVED).
     *
     * UNION BY SIZE:
     *   We always attach the root of the smaller set under the root of the
     *   larger set. This keeps the tree shallow — height stays O(log n).
     *
     *   If sizes are equal, we attach id2's root under id1's root arbitrarily.
     *
     * @param id1 first material record ID
     * @param id2 second material record ID
     * @return true if the sets were actually merged; false if already in the same set
     * @throws IllegalArgumentException if either ID is not registered
     */
    public boolean union(String id1, String id2) {
        validateId(id1);
        validateId(id2);

        String root1 = find(id1);
        String root2 = find(id2);

        // Already in the same set — repeated union is safe
        if (root1.equals(root2)) {
            return false;
        }

        int size1 = size.get(root1);
        int size2 = size.get(root2);

        // Attach smaller tree under larger tree (union by size)
        if (size1 >= size2) {
            parent.put(root2, root1);           // root2 now points to root1
            size.put(root1, size1 + size2);     // update size at the new root
        } else {
            parent.put(root1, root2);
            size.put(root2, size1 + size2);
        }

        componentCount--;
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. connected — O(α(n))
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns true if {@code id1} and {@code id2} belong to the same set.
     *
     * @param id1 first material record ID
     * @param id2 second material record ID
     * @return true if connected (same representative)
     */
    public boolean connected(String id1, String id2) {
        validateId(id1);
        validateId(id2);
        return find(id1).equals(find(id2));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. getGroups — O(n · α(n))
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all disjoint sets as a map from representative ID → list of members.
     *
     * Every registered record appears in exactly one group.
     *
     * @return map of root → member IDs
     */
    public Map<String, List<String>> getGroups() {
        Map<String, List<String>> groups = new HashMap<>();

        for (String id : parent.keySet()) {
            String root = find(id); // path compression applied here too
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(id);
        }

        return groups;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. getComponentSize — O(α(n))
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the number of elements in the set containing {@code id}.
     *
     * @param id any member of the set
     * @return component size (1 for singletons)
     */
    public int getComponentSize(String id) {
        validateId(id);
        return size.get(find(id));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility methods
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns the total number of registered records. */
    public int getTotalElements() {
        return parent.size();
    }

    /** Alias for getTotalElements() */
    public int getElementCount() {
        return parent.size();
    }

    /** Returns the number of distinct sets (components). */
    public int getComponentCount() {
        return componentCount;
    }

    /** Returns true if the given ID has been registered via makeSet. */
    public boolean isRegistered(String id) {
        return id != null && parent.containsKey(id);
    }

    /**
     * Clears the entire structure. Used when rebuilding from approved edges
     * after a reviewer rejection.
     */
    public void reset() {
        parent.clear();
        size.clear();
        componentCount = 0;
    }

    /**
     * Validates that an ID is registered; throws if not.
     */
    private void validateId(String id) {
        if (id == null || !parent.containsKey(id)) {
            throw new IllegalArgumentException(
                "Material record ID '" + id + "' is not registered in the DSU. Call makeSet() first.");
        }
    }
}
