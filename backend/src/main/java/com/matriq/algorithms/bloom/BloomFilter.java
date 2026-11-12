package com.matriq.algorithms.bloom;

import java.util.BitSet;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * BLOOM FILTER — MATRIQ Algorithm Module
 * Team Responsibility: Hetanshi Vora
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * PURPOSE:
 *   Quickly determines whether a normalised material signature has been seen
 *   before. A "definitely not present" result allows skipping expensive
 *   comparisons. A "possibly present" result triggers further KMP + attribute
 *   verification.
 *
 * IMPORTANT:
 *   A Bloom Filter positive result does NOT mean two materials are equivalent.
 *   It only means their signatures share bit positions — false positives occur.
 *   Actual equivalence is determined by attribute-level verification and human
 *   review, never by the filter alone.
 *
 * COMPLEXITY:
 *   Insert      : O(k)   where k = number of hash functions
 *   Contains    : O(k)
 *   Space       : O(m)   where m = bit array size
 *   False-positive probability ≈ (1 - e^(-kn/m))^k  for n inserted elements
 */
public class BloomFilter {

    private final BitSet bitSet;
    private final int bitArraySize;
    private final int numberOfHashFunctions;
    private int insertedCount;
    private long falsePositivesTracked;

    /**
     * @param bitArraySize         size of the bit array (larger = fewer false positives)
     * @param numberOfHashFunctions number of independent hash positions per element
     */
    public BloomFilter(int bitArraySize, int numberOfHashFunctions) {
        if (bitArraySize <= 0 || numberOfHashFunctions <= 0) {
            throw new IllegalArgumentException("Bit array size and hash function count must be positive.");
        }
        this.bitArraySize = bitArraySize;
        this.numberOfHashFunctions = numberOfHashFunctions;
        this.bitSet = new BitSet(bitArraySize);
        this.insertedCount = 0;
        this.falsePositivesTracked = 0;
    }

    /**
     * Convenience constructor — computes optimal parameters from expected elements
     * and desired false-positive rate.
     *
     * Optimal m = -(n * ln(p)) / (ln(2))^2
     * Optimal k =  (m/n) * ln(2)
     *
     * @param expectedElements        estimated number of items to insert
     * @param falsePositiveProbability desired rate, e.g. 0.01 for 1%
     */
    public static BloomFilter withOptimalParameters(int expectedElements, double falsePositiveProbability) {
        int m = optimalBitArraySize(expectedElements, falsePositiveProbability);
        int k = optimalHashCount(m, expectedElements);
        return new BloomFilter(m, k);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Insert
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Inserts an element into the filter by setting k bit positions.
     * Each position is computed by a different hash function.
     */
    public void insert(String element) {
        if (element == null || element.isEmpty()) return;
        for (int seed = 0; seed < numberOfHashFunctions; seed++) {
            int position = hash(element, seed);
            bitSet.set(position);
        }
        insertedCount++;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // mightContain
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns true if the element MIGHT be present (some false positives occur).
     * Returns false if the element is DEFINITELY NOT present (no false negatives).
     *
     * If ANY of the k bit positions is 0, the element was never inserted.
     * Only if ALL k positions are 1 do we report "possibly present".
     */
    public boolean mightContain(String element) {
        if (element == null || element.isEmpty()) return false;
        for (int seed = 0; seed < numberOfHashFunctions; seed++) {
            int position = hash(element, seed);
            if (!bitSet.get(position)) {
                return false; // definitely not present
            }
        }
        return true; // possibly present
    }

    /**
     * Records a confirmed false positive. Called when mightContain returns true
     * but further verification proves the element is not actually equivalent.
     */
    public void recordFalsePositive() {
        falsePositivesTracked++;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Statistics
    // ─────────────────────────────────────────────────────────────────────────

    /** Current theoretical false-positive probability given inserted element count. */
    public double currentFalsePositiveProbability() {
        // p ≈ (1 - e^(-k*n/m))^k
        double exponent = -((double) numberOfHashFunctions * insertedCount) / bitArraySize;
        return Math.pow(1.0 - Math.exp(exponent), numberOfHashFunctions);
    }

    public int getBitArraySize() { return bitArraySize; }
    public int getNumberOfHashFunctions() { return numberOfHashFunctions; }
    public int getInsertedCount() { return insertedCount; }
    public long getFalsePositivesTracked() { return falsePositivesTracked; }
    public int getSetBitCount() { return bitSet.cardinality(); }

    // ─────────────────────────────────────────────────────────────────────────
    // Hash functions — independent positions via double-hashing with seeds
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Produces a bit-array index for (element, seed) using a combination of
     * two hash functions (double hashing). This avoids storing k independent
     * hash functions while maintaining independence.
     *
     * h(i) = |h1(element) + i * h2(element)| mod m
     */
    private int hash(String element, int seed) {
        int h1 = murmurHash(element, 0);
        int h2 = murmurHash(element, seed + 1);
        int combined = h1 + seed * h2;
        return Math.abs(combined % bitArraySize);
    }

    /**
     * A simple Murmur-inspired mixing hash. NOT a cryptographic hash.
     * Deterministic, fast, and produces good distribution for bloom filters.
     */
    private int murmurHash(String key, int seed) {
        int h = seed ^ key.length();
        for (char c : key.toCharArray()) {
            h ^= c;
            h *= 0x9e3779b9; // magic number from golden ratio
            h ^= h >>> 16;
        }
        return h;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Optimal parameter calculation helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static int optimalBitArraySize(int n, double p) {
        return (int) Math.ceil(-(n * Math.log(p)) / (Math.log(2) * Math.log(2)));
    }

    private static int optimalHashCount(int m, int n) {
        return Math.max(1, (int) Math.round(((double) m / n) * Math.log(2)));
    }
}
