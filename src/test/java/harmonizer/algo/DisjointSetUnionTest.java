package harmonizer.algo;

import harmonizer.algo.dsu.DisjointSetUnion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the disjoint-set union used to build material groups.
 *
 * <p>The tests use record ids 1..n, exactly like the pipeline. They check correctness (who is
 * connected, which groups exist, how large they are) plus the two optimisations: path compression and
 * union by size.
 */
class DisjointSetUnionTest {

    private DisjointSetUnion dsu;

    @BeforeEach
    void setUp() {
        dsu = new DisjointSetUnion(10);
    }

    @Test
    @DisplayName("1. Every record starts in its own singleton set")
    void initialSingletonSets() {
        dsu.makeSet(1);
        dsu.makeSet(2);
        dsu.makeSet(3);

        assertEquals(3, dsu.getElementCount());
        assertEquals(3, dsu.getComponentCount());
        assertFalse(dsu.connected(1, 2));
        assertFalse(dsu.connected(2, 3));
        assertEquals(1, dsu.getComponentSize(1));
    }

    @Test
    @DisplayName("2. Union merges two records into one set")
    void unionTwoRecords() {
        dsu.makeSet(1);
        dsu.makeSet(2);

        assertTrue(dsu.union(1, 2));
        assertTrue(dsu.connected(1, 2));
        assertEquals(1, dsu.getComponentCount());
        assertEquals(2, dsu.getComponentSize(1));
        assertEquals(2, dsu.getComponentSize(2));
    }

    @Test
    @DisplayName("3. find returns the same representative for connected records")
    void findReturnsSharedRepresentative() {
        dsu.makeSet(1);
        dsu.makeSet(2);
        dsu.union(1, 2);

        assertEquals(dsu.find(1), dsu.find(2));
        assertEquals(dsu.find(1), dsu.find(dsu.find(2)));
    }

    @Test
    @DisplayName("4. Transitive grouping: A = B and B = C puts A, B and C in one group")
    void transitiveGrouping() {
        int a = 1;
        int b = 2;
        int c = 3;
        dsu.makeSet(a);
        dsu.makeSet(b);
        dsu.makeSet(c);

        dsu.union(a, b);
        dsu.union(b, c);

        assertTrue(dsu.connected(a, c));
        assertEquals(1, dsu.getComponentCount());
        assertEquals(3, dsu.getComponentSize(a));

        List<List<Integer>> groups = dsu.getGroups();
        assertEquals(1, groups.size());
        assertEquals(List.of(a, b, c), groups.get(0));
    }

    @Test
    @DisplayName("5. Independent relationships stay independent: A = B and C = D gives two groups")
    void independentGroups() {
        int a = 1;
        int b = 2;
        int c = 3;
        int d = 4;
        dsu.makeSet(a);
        dsu.makeSet(b);
        dsu.makeSet(c);
        dsu.makeSet(d);

        dsu.union(a, b);
        dsu.union(c, d);

        assertTrue(dsu.connected(a, b));
        assertTrue(dsu.connected(c, d));
        assertFalse(dsu.connected(a, c));
        assertFalse(dsu.connected(b, d));

        assertEquals(2, dsu.getComponentCount());
        assertEquals(List.of(List.of(1, 2), List.of(3, 4)), dsu.getGroups());
    }

    @Test
    @DisplayName("6. Repeated unions are safe and never split or duplicate a group")
    void repeatedUnionsAreIdempotent() {
        dsu.makeSet(1);
        dsu.makeSet(2);

        assertTrue(dsu.union(1, 2));
        assertFalse(dsu.union(1, 2));
        assertFalse(dsu.union(2, 1));

        assertEquals(1, dsu.getComponentCount());
        assertEquals(2, dsu.getComponentSize(1));
        assertEquals(List.of(List.of(1, 2)), dsu.getGroups());
    }

    @Test
    @DisplayName("7. find compresses the path (depth drops to 1)")
    void pathCompressionFlattensTheTree() {
        for (int id = 1; id <= 4; id++) {
            dsu.makeSet(id);
        }
        dsu.union(1, 2);   // {1, 2}, root 1
        dsu.union(3, 4);   // {3, 4}, root 3
        dsu.union(3, 1);   // equal sizes: root 3 absorbs root 1, so 2 now sits two levels deep

        assertEquals(2, dsu.depth(2), "record 2 should be 2 -> 1 -> 3 before compression");

        dsu.find(2);

        assertEquals(1, dsu.depth(2), "find must point record 2 straight at the root");
        assertEquals(1, dsu.depth(1));
    }

    @Test
    @DisplayName("8. Union by size keeps the larger set as the root")
    void unionBySizeKeepsLargeRoot() {
        dsu.makeSet(1);
        dsu.makeSet(2);
        dsu.makeSet(3);
        dsu.makeSet(4);
        dsu.makeSet(5);

        dsu.union(1, 2);
        dsu.union(1, 3);      // set {1,2,3} of size 3, root 1
        int largeRoot = dsu.find(1);

        dsu.union(4, 5);      // set {4,5} of size 2
        dsu.union(4, 1);      // must attach the smaller set under the larger one

        assertEquals(largeRoot, dsu.find(4));
        assertEquals(5, dsu.getComponentSize(5));
    }

    @Test
    @DisplayName("9. Component sizes stay accurate through a sequence of unions")
    void componentSizesAreAccurate() {
        for (int id = 1; id <= 8; id++) {
            dsu.makeSet(id);
        }
        dsu.union(1, 2);
        dsu.union(3, 4);
        dsu.union(5, 6);

        assertEquals(2, dsu.getComponentSize(1));
        assertEquals(2, dsu.getComponentSize(3));
        assertEquals(1, dsu.getComponentSize(7));

        dsu.union(2, 4);      // 2 + 2 = 4
        assertEquals(4, dsu.getComponentSize(1));

        dsu.union(1, 6);      // 4 + 2 = 6
        assertEquals(6, dsu.getComponentSize(6));
        assertEquals(6, dsu.getComponentSize(5));
    }

    @Test
    @DisplayName("10. Invalid or unregistered ids are rejected with a clear error")
    void invalidIdsAreRejected() {
        dsu.makeSet(1);

        assertThrows(IllegalArgumentException.class, () -> dsu.find(2), "unregistered id");
        assertThrows(IllegalArgumentException.class, () -> dsu.find(0), "id below range");
        assertThrows(IllegalArgumentException.class, () -> dsu.find(-3), "negative id");
        assertThrows(IllegalArgumentException.class, () -> dsu.find(11), "id above capacity");
        assertThrows(IllegalArgumentException.class, () -> dsu.union(1, 5), "union with unregistered id");
        assertThrows(IllegalArgumentException.class, () -> dsu.makeSet(0), "makeSet outside range");
        assertThrows(IllegalArgumentException.class, () -> dsu.getComponentSize(9), "size of unknown id");
    }

    @Test
    @DisplayName("11. A DSU needs a positive capacity")
    void capacityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new DisjointSetUnion(0));
        assertThrows(IllegalArgumentException.class, () -> new DisjointSetUnion(-1));
    }

    @Test
    @DisplayName("12. getGroups lists every record exactly once, singletons included")
    void everyRecordAppearsExactlyOnce() {
        for (int id = 1; id <= 6; id++) {
            dsu.makeSet(id);
        }
        dsu.union(1, 2);
        dsu.union(2, 3);
        dsu.union(4, 5);
        // record 6 is independent on purpose

        List<List<Integer>> groups = dsu.getGroups();

        Set<Integer> seen = new HashSet<>();
        int total = 0;
        for (List<Integer> group : groups) {
            for (Integer id : group) {
                assertTrue(seen.add(id), "record " + id + " appears more than once");
                total++;
            }
        }
        assertEquals(6, total);
        assertEquals(3, groups.size());
        assertTrue(groups.contains(List.of(6)), "an independent record must be its own group");
    }

    @Test
    @DisplayName("13. makeSet is idempotent and does not reset existing groups")
    void makeSetIsIdempotent() {
        dsu.makeSet(1);
        dsu.makeSet(2);
        dsu.union(1, 2);

        dsu.makeSet(1);
        dsu.makeSet(2);

        assertEquals(2, dsu.getElementCount());
        assertEquals(1, dsu.getComponentCount());
        assertTrue(dsu.connected(1, 2));
    }

    @Test
    @DisplayName("14. Group extraction is deterministic (ascending members, groups by smallest member)")
    void groupOrderIsDeterministic() {
        for (int id = 1; id <= 7; id++) {
            dsu.makeSet(id);
        }
        dsu.union(7, 6);
        dsu.union(4, 5);
        dsu.union(2, 1);

        assertEquals(List.of(List.of(1, 2), List.of(3), List.of(4, 5), List.of(6, 7)),
                dsu.getGroups());
    }
}
