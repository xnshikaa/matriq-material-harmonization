package harmonizer.algo;

import harmonizer.algo.bloom.BloomFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Correctness tests for the Bloom filter (at least five, including boundary and invalid cases). */
class BloomFilterTest {

    @Test
    void neverReportsFalseNegatives() {
        BloomFilter f = new BloomFilter(5_000, 0.01);
        for (int i = 0; i < 5_000; i++) {
            f.add("item-" + i);
        }
        for (int i = 0; i < 5_000; i++) {
            assertTrue(f.mightContain("item-" + i), "inserted item must always be reported as possibly present");
        }
    }

    @Test
    void emptyFilterRejectsEverything() { // boundary: nothing inserted
        BloomFilter f = new BloomFilter(100, 0.01);
        assertFalse(f.mightContain("anything"));
        assertFalse(f.mightContain(""));
        assertEquals(0, f.bitsSet());
        assertEquals(0L, f.insertions());
    }

    @Test
    void measuredFalsePositiveRateMatchesTheory() {
        int n = 10_000;
        double target = 0.01;
        BloomFilter f = new BloomFilter(n, target);
        for (int i = 0; i < n; i++) {
            f.add("mat:" + i);
        }
        int trials = 100_000;
        int hits = 0;
        for (int j = 0; j < trials; j++) {
            if (f.mightContain("unseen:" + j)) {
                hits++;
            }
        }
        double measured = (double) hits / trials;
        assertEquals(f.expectedFpr(), measured, 0.004, "measured rate should track (1 - e^(-kn/m))^k");
        assertTrue(measured < 0.02, "should stay near the 1% target, got " + measured);
    }

    @Test
    void sizingMatchesTheFormulas() {
        // m = ceil(-n ln p / (ln 2)^2) = ceil(9585.06) = 9586 ; k = round(m/n ln 2) = round(6.64) = 7
        assertEquals(9586, BloomFilter.optimalBits(1000, 0.01));
        assertEquals(7, BloomFilter.optimalHashes(9586, 1000));
        BloomFilter f = new BloomFilter(1000, 0.01);
        assertEquals(9586, f.numBits());
        assertEquals(7, f.numHashes());
    }

    @Test
    void rejectsInvalidParametersAndNullItems() { // invalid-input cases
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(0, 0.01));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(-5, 0.01));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(100, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(100, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(100, -0.2));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(100, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(0, 3));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(64, 0));
        BloomFilter f = new BloomFilter(100, 0.01);
        assertThrows(IllegalArgumentException.class, () -> f.add(null));
        assertThrows(IllegalArgumentException.class, () -> f.mightContain(null));
    }

    @Test
    void absurdlyLargeFilterIsRejectedInsteadOfExhaustingMemory() {
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(Integer.MAX_VALUE, 1e-9));
    }

    @Test
    void handlesEmptyStringAndUnicode() { // boundary: odd strings
        BloomFilter f = new BloomFilter(100, 0.01);
        f.add("");
        f.add("\u00B5-\u00D8-\u4E2D\u6587");
        assertTrue(f.mightContain(""));
        assertTrue(f.mightContain("\u00B5-\u00D8-\u4E2D\u6587"));
    }

    @Test
    void sameInputsGiveSameBits() { // determinism: required for reproducible experiments
        BloomFilter a = new BloomFilter(500, 0.01);
        BloomFilter b = new BloomFilter(500, 0.01);
        for (int i = 0; i < 500; i++) {
            a.add("k" + i);
            b.add("k" + i);
        }
        assertEquals(a.bitsSet(), b.bitsSet());
    }

    @Test
    void overfilledFilterDegradesTowardAlwaysYes() { // stress: why sizing matters
        BloomFilter f = new BloomFilter(959, 7); // sized for ~100 items, given 1000
        for (int i = 0; i < 1_000; i++) {
            f.add("x" + i);
        }
        assertTrue(f.expectedFpr() > 0.9);
        assertTrue(f.fillRatio() > 0.95);
    }

    @Test
    void duplicateInsertsDoNotChangeBits() {
        BloomFilter f = new BloomFilter(100, 0.01);
        f.add("same");
        long once = f.bitsSet();
        f.add("same");
        assertEquals(once, f.bitsSet());
        assertEquals(2L, f.insertions());
    }
}
