package harmonizer.algo.bloom;

import java.nio.charset.StandardCharsets;

/**
 * Bloom filter implemented from scratch (CO5: probabilistic data structure).
 *
 * <p><b>Contract</b>
 * <ul>
 *   <li>{@link #mightContain} returning {@code false} is <b>always correct</b> (no false negatives).</li>
 *   <li>{@link #mightContain} returning {@code true} means "POSSIBLY present". It can be wrong
 *       (false positive), so callers needing exactness MUST verify against an authoritative store.</li>
 * </ul>
 *
 * <p><b>Design</b>
 * <ul>
 *   <li>Bit array of {@code m} bits stored in a {@code long[]}.</li>
 *   <li>{@code k} indexes per item from only two base hashes, using double hashing
 *       (Kirsch and Mitzenmacher): index_i = (h1 + i * h2) mod m.</li>
 *   <li>Base hashes: FNV-1a over the UTF-8 bytes with two different seeds, followed by a
 *       SplitMix64 finaliser so every output bit depends on every input bit.</li>
 *   <li>Sizing: m = ceil(-n ln p / (ln 2)^2), k = round((m / n) ln 2).</li>
 * </ul>
 *
 * <p><b>Complexity</b>: add and mightContain are O(k + |item|) time; space is O(m) bits.
 * Not thread-safe. Deletion is not supported (clearing a bit could erase other items).
 */
public final class BloomFilter {

    private static final double LN2 = Math.log(2.0);
    private static final long SEED_A = 0x9E3779B97F4A7C15L;
    private static final long SEED_B = 0xC2B2AE3D27D4EB4FL;
    /** Hard cap (2^30 bits = 128 MB) so a bad parameter cannot exhaust memory. */
    private static final long MAX_BITS = 1L << 30;

    private final long[] words;
    private final int numBits;
    private final int numHashes;
    private long insertions;

    /** Sizes the filter automatically for {@code expectedItems} distinct items at the target false-positive rate. */
    public BloomFilter(int expectedItems, double targetFpr) {
        this(optimalBits(expectedItems, targetFpr),
             optimalHashes(optimalBits(expectedItems, targetFpr), expectedItems));
    }

    /** Explicit sizing, useful for experiments (e.g. deliberately overfilling a filter). */
    public BloomFilter(int numBits, int numHashes) {
        if (numBits <= 0) {
            throw new IllegalArgumentException("numBits must be positive, got " + numBits);
        }
        if (numHashes <= 0) {
            throw new IllegalArgumentException("numHashes must be positive, got " + numHashes);
        }
        this.numBits = numBits;
        this.numHashes = numHashes;
        this.words = new long[(int) (((long) numBits + 63) >>> 6)];
    }

    // ---------------------------------------------------------------- sizing formulas

    /** m = ceil(-n ln p / (ln 2)^2). */
    public static int optimalBits(long expectedItems, double fpr) {
        if (expectedItems <= 0) {
            throw new IllegalArgumentException("expectedItems must be positive, got " + expectedItems);
        }
        if (!(fpr > 0.0 && fpr < 1.0)) {
            throw new IllegalArgumentException("false-positive rate must be strictly between 0 and 1, got " + fpr);
        }
        double m = Math.ceil(-expectedItems * Math.log(fpr) / (LN2 * LN2));
        if (m > MAX_BITS) {
            throw new IllegalArgumentException("filter would need " + (long) m
                    + " bits (limit " + MAX_BITS + "); use a larger false-positive rate or fewer items");
        }
        return (int) Math.max(64.0, m);
    }

    /** k = round((m / n) ln 2), at least 1. */
    public static int optimalHashes(long numBits, long expectedItems) {
        if (numBits <= 0 || expectedItems <= 0) {
            throw new IllegalArgumentException("numBits and expectedItems must be positive");
        }
        return Math.max(1, (int) Math.round((double) numBits / expectedItems * LN2));
    }

    /** Theoretical false-positive rate (1 - e^(-kn/m))^k after n insertions. */
    public static double theoreticalFpr(long numBits, int numHashes, long insertedItems) {
        return Math.pow(1.0 - Math.exp(-(double) numHashes * insertedItems / numBits), numHashes);
    }

    // ---------------------------------------------------------------- operations

    public void add(String item) {
        byte[] data = toBytes(item);
        long h1 = hash(data, SEED_A);
        long h2 = hash(data, SEED_B) | 1L; // odd, so it is never 0
        for (int i = 0; i < numHashes; i++) {
            int idx = indexFor(h1, h2, i);
            words[idx >>> 6] |= 1L << (idx & 63);
        }
        insertions++;
    }

    /** false = definitely NOT present. true = POSSIBLY present (verify if exactness matters). */
    public boolean mightContain(String item) {
        byte[] data = toBytes(item);
        long h1 = hash(data, SEED_A);
        long h2 = hash(data, SEED_B) | 1L;
        for (int i = 0; i < numHashes; i++) {
            int idx = indexFor(h1, h2, i);
            if ((words[idx >>> 6] & (1L << (idx & 63))) == 0L) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- statistics

    public int numBits() {
        return numBits;
    }

    public int numHashes() {
        return numHashes;
    }

    /** Number of add() calls so far. */
    public long insertions() {
        return insertions;
    }

    public long bitsSet() {
        long count = 0;
        for (long w : words) {
            count += Long.bitCount(w);
        }
        return count;
    }

    public double fillRatio() {
        return (double) bitsSet() / numBits;
    }

    /** Memory used by the bit array, in bytes. */
    public long sizeInBytes() {
        return (long) words.length * Long.BYTES;
    }

    /** Expected false-positive rate right now, from the theoretical formula. */
    public double expectedFpr() {
        return theoreticalFpr(numBits, numHashes, insertions);
    }

    // ---------------------------------------------------------------- internals

    private int indexFor(long h1, long h2, int i) {
        return (int) Math.floorMod(h1 + (long) i * h2, (long) numBits);
    }

    private static byte[] toBytes(String item) {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        return item.getBytes(StandardCharsets.UTF_8);
    }

    /** FNV-1a (64-bit) with a seed, then a SplitMix64 finaliser. */
    private static long hash(byte[] data, long seed) {
        long h = 0xCBF29CE484222325L ^ seed;
        for (byte b : data) {
            h ^= (b & 0xFFL);
            h *= 0x100000001B3L;
        }
        return mix(h);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
