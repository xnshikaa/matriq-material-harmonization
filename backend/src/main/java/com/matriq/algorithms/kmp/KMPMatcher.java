package com.matriq.algorithms.kmp;

import java.util.ArrayList;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * KMP PATTERN MATCHING — MATRIQ Algorithm Module
 * Implemented by: Anshika Gupta
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * PURPOSE:
 *   Locates technical specification patterns (e.g. "ASTM A106", "API 5L",
 *   "SS316") within industrial material descriptions using the
 *   Knuth-Morris-Pratt algorithm.
 *
 * COMPLEXITY:
 *   Pre-processing (buildLPS): O(m)   — m = pattern length
 *   Pattern search            : O(n)   — n = text length
 *   Total                     : O(n+m)
 *   Space                     : O(m)   — LPS array only
 *
 * KEY PRINCIPLE:
 *   Unlike naive matching which restarts the pattern from index 0 on every
 *   mismatch, KMP uses the LPS (Longest Proper Prefix which is also Suffix)
 *   array to skip comparisons that are guaranteed to fail. This ensures
 *   O(n) text traversal — the text pointer never moves backward.
 *
 * NOTE ON CASE NORMALISATION:
 *   All searches are performed case-insensitively. Both text and pattern are
 *   converted to lowercase before processing. Original strings are NOT
 *   modified — lower-case copies are used internally. Match positions are
 *   reported as zero-based indices into the original text.
 *
 * IMPORTANT:
 *   This implementation does NOT use String.indexOf, String.contains,
 *   String.matches, or any regex/library for the core search logic.
 *   Every character comparison is performed explicitly.
 */
public class KMPMatcher {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. buildLPS — O(m)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the Longest Proper Prefix which is also Suffix (LPS / failure function)
     * array for the given pattern.
     *
     * Definition:
     *   lps[i] = length of the longest proper prefix of pattern[0..i] that is
     *            also a suffix of pattern[0..i].
     *
     * Example:
     *   pattern = "ABABC"
     *   lps     = [0, 0, 1, 2, 0]
     *
     * Algorithm:
     *   We maintain two pointers:
     *     len  — length of the current longest prefix-suffix
     *     i    — current position being processed
     *
     *   - If pattern[i] == pattern[len], we extend the current prefix-suffix.
     *   - Otherwise, if len > 0, we fall back using lps[len-1] without
     *     incrementing i. This is the KMP insight: we don't restart from 0.
     *   - If len == 0 and mismatch, lps[i] = 0 and move forward.
     *
     * @param pattern the search pattern (must be non-null, non-empty)
     * @return the LPS array; lps[0] is always 0
     */
    public int[] buildLPS(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return new int[0];
        }

        // Work on a lowercase copy so LPS reflects normalised characters
        String p = pattern.toLowerCase();
        int m = p.length();
        int[] lps = new int[m];

        lps[0] = 0; // by definition: a single character has no proper prefix-suffix

        int len = 0; // length of the previous longest prefix-suffix
        int i = 1;

        while (i < m) {
            if (p.charAt(i) == p.charAt(len)) {
                // Characters match — extend the current prefix-suffix by 1
                len++;
                lps[i] = len;
                i++;
            } else {
                // Mismatch
                if (len != 0) {
                    // Fall back to lps[len-1]: we try to find a shorter
                    // prefix-suffix. Do NOT increment i here.
                    len = lps[len - 1];
                } else {
                    // No shorter prefix-suffix exists; lps[i] = 0
                    lps[i] = 0;
                    i++;
                }
            }
        }

        return lps;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. search — O(n+m), returns the first match position (or -1)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Searches for the first occurrence of {@code pattern} in {@code text}.
     *
     * @param text    the string to search within (industrial material description)
     * @param pattern the technical specification pattern to find
     * @return zero-based start index of the first match, or -1 if not found
     */
    public int search(String text, String pattern) {
        List<Integer> all = searchAll(text, pattern);
        return all.isEmpty() ? -1 : all.get(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. searchAll — O(n+m), returns every match position
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Finds ALL occurrences of {@code pattern} in {@code text}, including
     * overlapping occurrences.
     *
     * KMP Algorithm walkthrough:
     *   - i  : current position in text  (0 … n-1)
     *   - j  : current position in pattern (0 … m-1)
     *   - After a full match (j == m), record position and fall back using
     *     lps[j-1] to find overlapping matches.
     *   - On mismatch with j > 0, fall back using lps[j-1] without moving i.
     *   - On mismatch with j == 0, just advance i.
     *   - Text pointer i never moves backwards — O(n) guaranteed.
     *
     * @param text    source string
     * @param pattern search pattern
     * @return list of zero-based start indices where pattern occurs in text
     */
    public List<Integer> searchAll(String text, String pattern) {
        List<Integer> positions = new ArrayList<>();

        // Guard: null or empty inputs
        if (text == null || text.isEmpty() || pattern == null || pattern.isEmpty()) {
            return positions;
        }

        // Work on lowercase copies for case-insensitive matching
        String t = text.toLowerCase();
        String p = pattern.toLowerCase();

        int n = t.length();
        int m = p.length();

        // If pattern longer than text, no match is possible
        if (m > n) {
            return positions;
        }

        int[] lps = buildLPS(p);

        int i = 0; // text index
        int j = 0; // pattern index

        while (i < n) {
            if (t.charAt(i) == p.charAt(j)) {
                // Characters match — advance both pointers
                i++;
                j++;
            }

            if (j == m) {
                // Full match found — record zero-based start index
                positions.add(i - j);

                // Fall back using LPS to allow overlapping matches.
                // We use lps[j-1] because pattern[0..lps[j-1]-1] is both a
                // proper prefix and suffix of the matched segment.
                j = lps[j - 1];

            } else if (i < n && t.charAt(i) != p.charAt(j)) {
                // Mismatch after some matches
                if (j != 0) {
                    // Skip comparisons we know will fail — use the LPS value
                    // at the previous position to resume from a shorter prefix
                    j = lps[j - 1];
                } else {
                    // No prefix to fall back to — advance text pointer
                    i++;
                }
            }
        }

        return positions;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. countComparisons — returns actual comparison count for benchmarking
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Executes the KMP search and returns the total number of character
     * comparisons made. Used for benchmarking against the naive O(nm) approach.
     *
     * KMP guarantees at most 2n comparisons for the search phase alone.
     * Total with LPS construction is bounded by 2n + 2m.
     *
     * @param text    source string
     * @param pattern search pattern
     * @return a {@link KMPResult} containing match positions AND comparison count
     */
    public KMPResult countComparisons(String text, String pattern) {
        KMPResult result = new KMPResult();
        result.setText(text);
        result.setPattern(pattern);

        if (text == null || text.isEmpty() || pattern == null || pattern.isEmpty()) {
            result.setPositions(new ArrayList<>());
            result.setComparisons(0);
            return result;
        }

        String t = text.toLowerCase();
        String p = pattern.toLowerCase();
        int n = t.length();
        int m = p.length();

        if (m > n) {
            result.setPositions(new ArrayList<>());
            result.setComparisons(0);
            return result;
        }

        // ── Build LPS (count comparisons in pre-processing) ──────────────────
        int[] lps = new int[m];
        long lpsComparisons = 0;
        int len = 0;
        int i = 1;
        while (i < m) {
            lpsComparisons++;
            if (p.charAt(i) == p.charAt(len)) {
                len++;
                lps[i] = len;
                i++;
            } else {
                if (len != 0) {
                    len = lps[len - 1];
                } else {
                    lps[i] = 0;
                    i++;
                }
            }
        }

        // ── KMP Search (count comparisons) ────────────────────────────────────
        List<Integer> positions = new ArrayList<>();
        long searchComparisons = 0;
        i = 0;
        int j = 0;

        while (i < n) {
            searchComparisons++;
            if (t.charAt(i) == p.charAt(j)) {
                i++;
                j++;
            }

            if (j == m) {
                positions.add(i - j);
                j = lps[j - 1];
            } else if (i < n && t.charAt(i) != p.charAt(j)) {
                if (j != 0) {
                    j = lps[j - 1];
                } else {
                    i++;
                }
            }
        }

        result.setPositions(positions);
        result.setComparisons(lpsComparisons + searchComparisons);
        result.setLpsArray(lps);
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. naiveSearch — O(nm) baseline for benchmarking comparison
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Naive (brute-force) pattern search used ONLY as a performance baseline
     * for benchmarking comparisons.
     *
     * NOT used in production harmonization — KMP is always used there.
     *
     * @param text    source string
     * @param pattern search pattern
     * @return comparison count and positions found
     */
    public KMPResult naiveSearch(String text, String pattern) {
        KMPResult result = new KMPResult();
        result.setText(text);
        result.setPattern(pattern);
        List<Integer> positions = new ArrayList<>();
        long comparisons = 0;

        if (text == null || pattern == null || pattern.isEmpty() || pattern.length() > text.length()) {
            result.setPositions(positions);
            result.setComparisons(0);
            return result;
        }

        String t = text.toLowerCase();
        String p = pattern.toLowerCase();
        int n = t.length();
        int m = p.length();

        for (int i = 0; i <= n - m; i++) {
            int j = 0;
            while (j < m) {
                comparisons++;
                if (t.charAt(i + j) != p.charAt(j)) break;
                j++;
            }
            if (j == m) positions.add(i);
        }

        result.setPositions(positions);
        result.setComparisons(comparisons);
        return result;
    }
}
