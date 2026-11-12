package com.matriq.algorithms;

import com.matriq.algorithms.dsu.DisjointSetUnion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 test suite for DisjointSetUnion (DSU).
 * Implemented for: Anshika Gupta (Primary Algorithm Viva Responsibility).
 *
 * Covers:
 * 1. Initially every element is separate
 * 2. Union two elements
 * 3. Find returns the same representative for connected elements
 * 4. Transitive grouping
 * 5. Repeated union operations (safety/idempotency)
 * 6. Union by size correctness
 * 7. Path compression correctness
 * 8. Component size tracking
 * 9. Invalid/unregistered ID handling
 * 10. Multiple independent groups
 */
class DisjointSetUnionTest {

    private DisjointSetUnion dsu;

    @BeforeEach
    void setUp() {
        dsu = new DisjointSetUnion();
    }

    @Test
    @DisplayName("1. Initially every element is separate (own set, size 1)")
    void testInitialSeparateSets() {
        dsu.makeSet("PIPE-001");
        dsu.makeSet("P-9281");
        dsu.makeSet("MAT-1003");

        assertEquals(3, dsu.getElementCount());
        assertEquals(3, dsu.getComponentCount());

        assertFalse(dsu.connected("PIPE-001", "P-9281"));
        assertFalse(dsu.connected("P-9281", "MAT-1003"));
        assertEquals(1, dsu.getComponentSize("PIPE-001"));
        assertEquals(1, dsu.getComponentSize("P-9281"));
    }

    @Test
    @DisplayName("2. Union two elements merges them into one set")
    void testUnionTwoElements() {
        dsu.makeSet("A");
        dsu.makeSet("B");

        boolean merged = dsu.union("A", "B");
        assertTrue(merged);
        assertTrue(dsu.connected("A", "B"));
        assertEquals(1, dsu.getComponentCount());
        assertEquals(2, dsu.getComponentSize("A"));
        assertEquals(2, dsu.getComponentSize("B"));
    }

    @Test
    @DisplayName("3. Find returns the identical representative for connected elements")
    void testFindReturnsSameRepresentative() {
        dsu.makeSet("PIPE-A");
        dsu.makeSet("PIPE-B");
        dsu.union("PIPE-A", "PIPE-B");

        String rootA = dsu.find("PIPE-A");
        String rootB = dsu.find("PIPE-B");

        assertNotNull(rootA);
        assertEquals(rootA, rootB);
    }

    @Test
    @DisplayName("4. Transitive grouping (A=B and B=C implies A=C)")
    void testTransitiveGrouping() {
        dsu.makeSet("PIPE-001"); // Company A
        dsu.makeSet("P-9281");   // Company B
        dsu.makeSet("MAT-1003"); // Company C

        dsu.union("PIPE-001", "P-9281");
        dsu.union("P-9281", "MAT-1003");

        assertTrue(dsu.connected("PIPE-001", "MAT-1003"));
        assertEquals(1, dsu.getComponentCount());
        assertEquals(3, dsu.getComponentSize("PIPE-001"));

        Map<String, List<String>> groups = dsu.getGroups();
        assertEquals(1, groups.size());
        List<String> members = groups.values().iterator().next();
        assertEquals(3, members.size());
        assertTrue(members.containsAll(List.of("PIPE-001", "P-9281", "MAT-1003")));
    }

    @Test
    @DisplayName("5. Repeated union operations are safe and idempotent")
    void testRepeatedUnionOperations() {
        dsu.makeSet("X");
        dsu.makeSet("Y");

        assertTrue(dsu.union("X", "Y"));
        assertFalse(dsu.union("X", "Y")); // already connected, returns false
        assertFalse(dsu.union("Y", "X")); // reverse also returns false

        assertEquals(1, dsu.getComponentCount());
        assertEquals(2, dsu.getComponentSize("X"));
    }

    @Test
    @DisplayName("6. Union by size: attaches smaller tree under larger tree")
    void testUnionBySize() {
        // Group 1: 3 elements
        dsu.makeSet("A1");
        dsu.makeSet("A2");
        dsu.makeSet("A3");
        dsu.union("A1", "A2");
        dsu.union("A1", "A3");
        String rootA = dsu.find("A1");
        assertEquals(3, dsu.getComponentSize("A1"));

        // Group 2: 2 elements
        dsu.makeSet("B1");
        dsu.makeSet("B2");
        dsu.union("B1", "B2");
        assertEquals(2, dsu.getComponentSize("B1"));

        // Merging Group 1 (size 3) with Group 2 (size 2) -> root of combined should be rootA
        dsu.union("A1", "B1");
        assertEquals(5, dsu.getComponentSize("B2"));
        assertEquals(rootA, dsu.find("B2"));
    }

    @Test
    @DisplayName("7. Path compression flattens tree during find")
    void testPathCompression() {
        // Chain a sequence of nodes
        for (int i = 1; i <= 6; i++) {
            dsu.makeSet("NODE-" + i);
        }
        dsu.union("NODE-1", "NODE-2");
        dsu.union("NODE-2", "NODE-3");
        dsu.union("NODE-3", "NODE-4");
        dsu.union("NODE-4", "NODE-5");
        dsu.union("NODE-5", "NODE-6");

        String root = dsu.find("NODE-6");
        assertNotNull(root);

        // After find with path compression, direct parent of NODE-6 should be root
        assertEquals(root, dsu.find("NODE-6"));
        assertEquals(root, dsu.find("NODE-4"));
    }

    @Test
    @DisplayName("8. Component size updates accurately after multiple operations")
    void testComponentSizeAccuracy() {
        for (int i = 0; i < 10; i++) {
            dsu.makeSet("ID-" + i);
        }

        dsu.union("ID-0", "ID-1");
        dsu.union("ID-2", "ID-3");
        dsu.union("ID-4", "ID-5");

        assertEquals(2, dsu.getComponentSize("ID-0"));
        assertEquals(2, dsu.getComponentSize("ID-2"));
        assertEquals(1, dsu.getComponentSize("ID-6"));

        dsu.union("ID-1", "ID-3"); // merges 2 + 2 = 4
        assertEquals(4, dsu.getComponentSize("ID-0"));
        assertEquals(4, dsu.getComponentSize("ID-2"));
    }

    @Test
    @DisplayName("9. Invalid or unregistered ID handling")
    void testInvalidIdHandling() {
        // Find on unregistered element throws IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> dsu.find("UNKNOWN-XYZ"));
        assertThrows(IllegalArgumentException.class, () -> dsu.getComponentSize("UNKNOWN-XYZ"));

        // Union with unregistered items fails gracefully or throws
        assertThrows(IllegalArgumentException.class, () -> dsu.union("UNKNOWN-1", "UNKNOWN-2"));
    }

    @Test
    @DisplayName("10. Multiple independent equivalence groups are partitioned correctly")
    void testMultipleIndependentGroups() {
        // Group 1: Carbon Steel pipes
        dsu.makeSet("CS-1");
        dsu.makeSet("CS-2");
        dsu.makeSet("CS-3");
        dsu.union("CS-1", "CS-2");
        dsu.union("CS-2", "CS-3");

        // Group 2: Stainless Steel valves
        dsu.makeSet("SS-VALVE-1");
        dsu.makeSet("SS-VALVE-2");
        dsu.union("SS-VALVE-1", "SS-VALVE-2");

        // Group 3: Standalone gasket
        dsu.makeSet("GASKET-SPIRAL-01");

        assertEquals(6, dsu.getElementCount());
        assertEquals(3, dsu.getComponentCount());

        assertFalse(dsu.connected("CS-1", "SS-VALVE-1"));
        assertFalse(dsu.connected("CS-1", "GASKET-SPIRAL-01"));
        assertFalse(dsu.connected("SS-VALVE-1", "GASKET-SPIRAL-01"));

        Map<String, List<String>> groups = dsu.getGroups();
        assertEquals(3, groups.size());
    }
}
