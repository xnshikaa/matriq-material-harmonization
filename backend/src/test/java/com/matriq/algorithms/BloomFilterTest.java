package com.matriq.algorithms;

import com.matriq.algorithms.bloom.BloomFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 test suite for BloomFilter.
 * Team module test (CO5).
 */
class BloomFilterTest {

    @Test
    @DisplayName("Bloom Filter: No false negatives for inserted elements")
    void testNoFalseNegatives() {
        BloomFilter filter = BloomFilter.withOptimalParameters(100, 0.01);
        String[] signatures = {
            "PIPE:CARBON STEEL:6 INCH:ASTM A106",
            "VALVE:STAINLESS STEEL:2 INCH:ANSI B16.5",
            "FLANGE:CS:150MM:A106-B"
        };

        for (String sig : signatures) {
            filter.insert(sig);
        }

        for (String sig : signatures) {
            assertTrue(filter.mightContain(sig), "Inserted signature must return true");
        }
    }

    @Test
    @DisplayName("Bloom Filter: Non-inserted elements mostly return false (within expected FPP)")
    void testFalsePositiveRate() {
        int expectedInsertions = 1000;
        double targetFpp = 0.05;
        BloomFilter filter = BloomFilter.withOptimalParameters(expectedInsertions, targetFpp);

        for (int i = 0; i < expectedInsertions; i++) {
            filter.insert("ITEM-" + i);
        }

        int falsePositives = 0;
        int trials = 1000;
        for (int i = 0; i < trials; i++) {
            if (filter.mightContain("NON_EXISTENT_KEY_" + i)) {
                falsePositives++;
            }
        }

        double observedFpp = (double) falsePositives / trials;
        // Observed FPP should be roughly around or below the target rate with statistical margin
        assertTrue(observedFpp < 0.15, "Observed FPP " + observedFpp + " should be close to " + targetFpp);
    }

    @Test
    @DisplayName("Bloom Filter: Optimal parameters calculation correctness")
    void testOptimalParameters() {
        BloomFilter filter = BloomFilter.withOptimalParameters(500, 0.01);
        assertTrue(filter.getBitArraySize() > 0);
        assertTrue(filter.getNumberOfHashFunctions() >= 3);
        assertEquals(0, filter.getInsertedCount());
    }

    @Test
    @DisplayName("Bloom Filter: Null and empty handling")
    void testNullAndEmpty() {
        BloomFilter filter = new BloomFilter(1024, 4);
        filter.insert(null);
        filter.insert("");
        assertFalse(filter.mightContain(null));
        assertFalse(filter.mightContain(""));
    }
}
