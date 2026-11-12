package com.matriq.algorithms.kmp;

import java.util.ArrayList;
import java.util.List;

/**
 * Result container returned by KMPMatcher.
 * Carries all information needed by both the harmonization engine
 * and the Algorithm Lab visualization.
 */
public class KMPResult {

    private String text;
    private String pattern;
    private List<Integer> positions = new ArrayList<>();
    private long comparisons;
    private int[] lpsArray;

    // For visualization: step-by-step trace
    private List<KMPStep> steps = new ArrayList<>();

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public List<Integer> getPositions() { return positions; }
    public void setPositions(List<Integer> positions) { this.positions = positions; }

    public long getComparisons() { return comparisons; }
    public void setComparisons(long comparisons) { this.comparisons = comparisons; }

    public int[] getLpsArray() { return lpsArray; }
    public void setLpsArray(int[] lpsArray) { this.lpsArray = lpsArray; }

    public List<KMPStep> getSteps() { return steps; }
    public void setSteps(List<KMPStep> steps) { this.steps = steps; }

    public boolean isMatch() { return !positions.isEmpty(); }

    /**
     * A single step in the KMP algorithm trace for visualization.
     */
    public static class KMPStep {
        private int textIndex;      // current text pointer (i)
        private int patternIndex;   // current pattern pointer (j)
        private boolean matched;    // was this comparison a match?
        private String action;      // "MATCH", "MISMATCH", "FULL_MATCH", "LPS_SHIFT"
        private String explanation;

        public KMPStep(int textIndex, int patternIndex, boolean matched, String action, String explanation) {
            this.textIndex = textIndex;
            this.patternIndex = patternIndex;
            this.matched = matched;
            this.action = action;
            this.explanation = explanation;
        }

        public int getTextIndex() { return textIndex; }
        public int getPatternIndex() { return patternIndex; }
        public boolean isMatched() { return matched; }
        public String getAction() { return action; }
        public String getExplanation() { return explanation; }
    }
}
