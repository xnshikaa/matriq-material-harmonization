package harmonizer.algo.kmp;

import java.util.List;

/**
 * Outcome of a KMP run.
 *
 * @param text        the original text that was searched (not the lower-cased copy)
 * @param pattern     the original pattern that was searched for
 * @param positions   zero-based start indices of every occurrence, in ascending order
 * @param comparisons character comparisons used by the LPS build plus the search phase
 * @param lps         the LPS (failure) array that was built for the pattern
 */
public record KMPResult(String text, String pattern,
                        List<Integer> positions, long comparisons, int[] lps) {

    public KMPResult {
        positions = positions == null ? List.of() : List.copyOf(positions);
        lps = lps == null ? new int[0] : lps.clone();
    }

    /** True when the pattern occurs at least once. */
    public boolean isMatch() {
        return !positions.isEmpty();
    }

    /** Zero-based index of the first occurrence, or -1 when there is no match. */
    public int firstPosition() {
        return positions.isEmpty() ? -1 : positions.get(0);
    }

    /** A copy, so callers cannot modify the stored LPS array. */
    @Override
    public int[] lps() {
        return lps.clone();
    }
}
