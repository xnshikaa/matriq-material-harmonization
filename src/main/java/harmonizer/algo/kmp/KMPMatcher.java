package harmonizer.algo.kmp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Knuth-Morris-Pratt substring search, written from scratch for MATRIQ.
 *
 * <p>Why KMP: a naive search re-reads characters of the text after every mismatch, which costs
 * O(n * m). KMP pre-computes a failure table (the LPS array — Longest Prefix which is also a Suffix)
 * so that, on a mismatch, the pattern slides to the longest prefix that can still match instead of
 * restarting at index 0. The text pointer never moves backwards, so the search is O(n + m).
 *
 * <p><b>No library search is used.</b> The core loop compares characters explicitly; there is no
 * {@code String.indexOf}, {@code String.contains}, regex or {@code String.matches} anywhere in this
 * class. (The shared helpers use {@code toLowerCase} only for case normalisation.)
 *
 * <p>Complexity: LPS construction O(m), search O(n), total O(n + m), space O(m) for the LPS array.
 * Character comparisons are always counted, so the benchmark can compare KMP against a naive
 * baseline without running the search twice.
 *
 * <p>Case handling: text and pattern are lower-cased with {@link Locale#ROOT} before comparison, so
 * {@code "ASTM A106"} is found inside {@code "a106-b"} style text. The caller's strings are never
 * modified, and returned positions are zero-based indices into the <em>original</em> text.
 *
 * <p>Null and empty inputs are safe: they produce "no match" rather than an exception.
 */
public final class KMPMatcher {

    /**
     * Builds the LPS (failure) array for a pattern.
     *
     * <p>{@code lps[i]} is the length of the longest proper prefix of {@code pattern[0..i]} that is
     * also a suffix of it. Example: {@code "ABABC"} gives {@code [0, 0, 1, 2, 0]}.
     *
     * @param pattern pattern to analyse ({@code null}/empty gives an empty array)
     * @return the LPS array, never {@code null}
     */
    public int[] buildLPS(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return new int[0];
        }
        return lps(pattern.toLowerCase(Locale.ROOT), new long[1]);
    }

    /**
     * Finds the first occurrence of {@code pattern} in {@code text}.
     *
     * @return zero-based index of the first match, or -1 when there is no match
     */
    public int search(String text, String pattern) {
        List<Integer> positions = searchAll(text, pattern);
        return positions.isEmpty() ? -1 : positions.get(0);
    }

    /**
     * Finds every occurrence of {@code pattern} in {@code text}, including overlapping ones
     * ({@code "AA"} occurs three times in {@code "AAAA"}, at 0, 1 and 2).
     *
     * @return zero-based match positions in ascending order; empty when nothing matches
     */
    public List<Integer> searchAll(String text, String pattern) {
        return scan(text, pattern).positions();
    }

    /**
     * Runs the search and reports the exact number of character comparisons used for both the LPS
     * construction and the search phase, together with the matches found.
     *
     * <p>Used by the benchmark (KMP versus naive) and by specification extraction, where the
     * comparison count is part of the recorded evidence.
     */
    public KMPResult countComparisons(String text, String pattern) {
        Scan scan = scan(text, pattern);
        return new KMPResult(text, pattern, scan.positions(), scan.comparisons(), scan.lps());
    }

    // ------------------------------------------------------------------ internals

    /** Result of one internal run: matches, comparison count and the LPS array that was used. */
    private record Scan(List<Integer> positions, long comparisons, int[] lps) {
    }

    /**
     * The single implementation of the search. Both {@link #searchAll} and {@link #countComparisons}
     * call it, so the positions and the comparison counter can never disagree.
     */
    private Scan scan(String text, String pattern) {
        if (text == null || text.isEmpty() || pattern == null || pattern.isEmpty()) {
            return new Scan(List.of(), 0L, new int[0]);
        }

        String t = text.toLowerCase(Locale.ROOT);
        String p = pattern.toLowerCase(Locale.ROOT);
        int n = t.length();
        int m = p.length();

        long[] comparisons = new long[1];
        int[] lps = lps(p, comparisons);

        List<Integer> positions = new ArrayList<>();
        int i = 0; // position in the text, never decreases
        int j = 0; // position in the pattern

        while (i < n) {
            comparisons[0]++;
            if (t.charAt(i) == p.charAt(j)) {
                i++;
                j++;
                if (j == m) {
                    positions.add(i - j);   // zero-based start of this occurrence
                    j = lps[j - 1];         // fall back so overlaps are found too
                }
            } else if (j > 0) {
                j = lps[j - 1];             // reuse the matched prefix instead of restarting
            } else {
                i++;                        // no prefix to reuse
            }
        }
        return new Scan(List.copyOf(positions), comparisons[0], lps);
    }

    /** Builds the LPS array for an already lower-cased pattern, counting comparisons. */
    private static int[] lps(String p, long[] comparisons) {
        int m = p.length();
        int[] lps = new int[m];
        int length = 0; // length of the longest prefix-suffix found so far
        int i = 1;

        while (i < m) {
            comparisons[0]++;
            if (p.charAt(i) == p.charAt(length)) {
                length++;
                lps[i] = length;
                i++;
            } else if (length > 0) {
                length = lps[length - 1]; // try a shorter prefix, do not advance i
            } else {
                lps[i] = 0;
                i++;
            }
        }
        return lps;
    }
}
