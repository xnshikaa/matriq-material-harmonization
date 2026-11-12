package com.matriq.algorithms.kmp;

import java.util.ArrayList;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * KMP TRACE — Step-by-step execution for Algorithm Lab Visualization
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Runs KMP with full step recording so the React frontend can animate
 * each comparison, mismatch, LPS shift, and full match.
 *
 * The backend produces all trace steps — the frontend ONLY displays them.
 * JavaScript does not re-implement the algorithm.
 */
public class KMPTracer {

    private final KMPMatcher matcher = new KMPMatcher();

    /**
     * Execute KMP with full step-by-step trace recording.
     *
     * @param text    source string
     * @param pattern pattern to find
     * @return KMPResult with populated steps list for visualization
     */
    public KMPResult trace(String text, String pattern) {
        KMPResult result = new KMPResult();
        result.setText(text);
        result.setPattern(pattern);

        List<KMPResult.KMPStep> steps = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();

        if (text == null || text.isEmpty() || pattern == null || pattern.isEmpty()) {
            result.setSteps(steps);
            result.setPositions(positions);
            result.setComparisons(0);
            return result;
        }

        String t = text.toLowerCase();
        String p = pattern.toLowerCase();
        int n = t.length();
        int m = p.length();

        if (m > n) {
            steps.add(new KMPResult.KMPStep(0, 0, false, "PATTERN_TOO_LONG",
                "Pattern (length " + m + ") is longer than text (length " + n + "). No match possible."));
            result.setSteps(steps);
            result.setPositions(positions);
            result.setComparisons(0);
            return result;
        }

        // Build LPS and record it
        int[] lps = matcher.buildLPS(p);
        result.setLpsArray(lps);

        steps.add(new KMPResult.KMPStep(0, 0, false, "LPS_BUILT",
            "LPS array built for pattern '" + pattern + "'. LPS = " + lpsToString(lps)));

        long comparisons = 0;
        int i = 0; // text pointer
        int j = 0; // pattern pointer

        while (i < n) {
            comparisons++;
            boolean charMatch = t.charAt(i) == p.charAt(j);

            if (charMatch) {
                steps.add(new KMPResult.KMPStep(i, j, true, "MATCH",
                    "text[" + i + "]='" + text.charAt(i) + "' matches pattern[" + j + "]='" + pattern.charAt(j) + "'"));
                i++;
                j++;
            }

            if (j == m) {
                int startPos = i - j;
                positions.add(startPos);
                steps.add(new KMPResult.KMPStep(i - 1, j - 1, true, "FULL_MATCH",
                    "Full match found! Pattern occurs at index " + startPos + ". Applying LPS shift: j = lps[" + (j-1) + "] = " + lps[j - 1]));
                j = lps[j - 1];

            } else if (i < n && t.charAt(i) != p.charAt(j)) {
                if (j != 0) {
                    int prevJ = j;
                    j = lps[j - 1];
                    steps.add(new KMPResult.KMPStep(i, prevJ, false, "LPS_SHIFT",
                        "Mismatch at text[" + i + "]='" + text.charAt(i) + "' vs pattern[" + prevJ + "]='" + pattern.charAt(prevJ) + "'. "
                        + "LPS shift: j = lps[" + (prevJ-1) + "] = " + j + ". Text pointer stays at " + i));
                } else {
                    steps.add(new KMPResult.KMPStep(i, j, false, "MISMATCH",
                        "Mismatch at text[" + i + "]='" + text.charAt(i) + "' vs pattern[0]='" + pattern.charAt(0) + "'. Advance text pointer."));
                    i++;
                }
            }
        }

        result.setSteps(steps);
        result.setPositions(positions);
        result.setComparisons(comparisons);
        return result;
    }

    private String lpsToString(int[] lps) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < lps.length; i++) {
            sb.append(lps[i]);
            if (i < lps.length - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }
}
