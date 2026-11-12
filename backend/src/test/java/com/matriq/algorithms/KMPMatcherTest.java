package com.matriq.algorithms;

import com.matriq.algorithms.kmp.KMPMatcher;
import com.matriq.algorithms.kmp.KMPResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 test suite for KMPMatcher.
 * Implemented for: Anshika Gupta (Primary Algorithm Viva Responsibility).
 *
 * Covers:
 * 1. Pattern occurs once
 * 2. Pattern does not exist
 * 3. Pattern appears multiple times
 * 4. Overlapping pattern matches
 * 5. Pattern longer than text
 * 6. Empty pattern
 * 7. Empty text
 * 8. Repeated characters
 * 9. Technical specification matching
 * 10. Case-normalized matching
 * 11. LPS construction tests
 */
class KMPMatcherTest {

    private KMPMatcher kmp;

    @BeforeEach
    void setUp() {
        kmp = new KMPMatcher();
    }

    @Test
    @DisplayName("1. LPS Construction: verifies standard failure function values")
    void testLpsConstruction() {
        // Pattern: "ABABC" -> LPS: [0, 0, 1, 2, 0]
        int[] lps1 = kmp.buildLPS("ABABC");
        assertArrayEquals(new int[]{0, 0, 1, 2, 0}, lps1);

        // Pattern: "AAAA" -> LPS: [0, 1, 2, 3]
        int[] lps2 = kmp.buildLPS("AAAA");
        assertArrayEquals(new int[]{0, 1, 2, 3}, lps2);

        // Single character: "A" -> [0]
        int[] lps3 = kmp.buildLPS("A");
        assertArrayEquals(new int[]{0}, lps3);

        // No repeats: "ABCDE" -> [0, 0, 0, 0, 0]
        int[] lps4 = kmp.buildLPS("ABCDE");
        assertArrayEquals(new int[]{0, 0, 0, 0, 0}, lps4);
    }

    @Test
    @DisplayName("2. Pattern occurs once in text")
    void testPatternOccursOnce() {
        String text = "Pipe seamless 6 inch ASTM A106 Gr.B";
        String pattern = "ASTM A106";
        int index = kmp.search(text, pattern);
        assertEquals(21, index);

        List<Integer> all = kmp.searchAll(text, pattern);
        assertEquals(1, all.size());
        assertEquals(21, all.get(0));
    }

    @Test
    @DisplayName("3. Pattern does not exist in text")
    void testPatternDoesNotExist() {
        String text = "Pipe seamless 6 inch ASTM A106 Gr.B";
        String pattern = "SS316";
        int index = kmp.search(text, pattern);
        assertEquals(-1, index);

        List<Integer> all = kmp.searchAll(text, pattern);
        assertTrue(all.isEmpty());
    }

    @Test
    @DisplayName("4. Pattern appears multiple times non-overlapping")
    void testPatternMultipleTimes() {
        String text = "ASTM A106 seamless pipe certified to ASTM A106 standard";
        String pattern = "ASTM A106";
        List<Integer> matches = kmp.searchAll(text, pattern);
        assertEquals(2, matches.size());
        assertEquals(0, matches.get(0));
        assertEquals(37, matches.get(1));
    }

    @Test
    @DisplayName("5. Overlapping pattern matches")
    void testOverlappingPatternMatches() {
        // "AAAA" contains "AA" at index 0, 1, 2
        String text = "AAAA";
        String pattern = "AA";
        List<Integer> matches = kmp.searchAll(text, pattern);
        assertEquals(3, matches.size());
        assertEquals(List.of(0, 1, 2), matches);
    }

    @Test
    @DisplayName("6. Pattern longer than text")
    void testPatternLongerThanText() {
        String text = "ASTM";
        String pattern = "ASTM A106 Grade B Standard";
        assertEquals(-1, kmp.search(text, pattern));
        assertTrue(kmp.searchAll(text, pattern).isEmpty());
    }

    @Test
    @DisplayName("7. Empty pattern handling")
    void testEmptyPattern() {
        String text = "Seamless pipe";
        assertEquals(-1, kmp.search(text, ""));
        assertTrue(kmp.searchAll(text, "").isEmpty());
        assertEquals(0, kmp.buildLPS("").length);
    }

    @Test
    @DisplayName("8. Empty text handling")
    void testEmptyText() {
        String pattern = "ASTM A106";
        assertEquals(-1, kmp.search("", pattern));
        assertTrue(kmp.searchAll("", pattern).isEmpty());
    }

    @Test
    @DisplayName("9. Repeated characters in text and pattern")
    void testRepeatedCharacters() {
        String text = "BAAAAAAAAAAB";
        String pattern = "AAAA";
        List<Integer> matches = kmp.searchAll(text, pattern);
        // A occurs 10 times -> starts at 1, 2, 3, 4, 5, 6, 7 (7 occurrences)
        assertEquals(7, matches.size());
        assertEquals(1, matches.get(0));
        assertEquals(7, matches.get(matches.size() - 1));
    }

    @Test
    @DisplayName("10. Technical specification matching across industrial standards")
    void testTechnicalSpecificationMatching() {
        String text1 = "STAINLESS STEEL FLANGE 2 INCH 150# ANSI B16.5 RF SS316";
        assertTrue(kmp.search(text1, "ANSI B16.5") >= 0);
        assertTrue(kmp.search(text1, "SS316") >= 0);

        String text2 = "MILD STEEL TUBE ERW CONFORMING TO IS 1239 PART 1";
        assertTrue(kmp.search(text2, "IS 1239") >= 0);

        String text3 = "LINE PIPE SEAMLESS API 5L PSL2 GRADE X52";
        assertTrue(kmp.search(text3, "API 5L") >= 0);
        assertTrue(kmp.search(text3, "PSL2") >= 0);
    }

    @Test
    @DisplayName("11. Case-normalized matching")
    void testCaseNormalizedMatching() {
        String text = "seamless pipe 150mm cs a106-b";
        String pattern = "A106-B";
        int pos = kmp.search(text, pattern);
        assertTrue(pos >= 0);
        assertEquals(23, pos);

        String upperText = "SEAMLESS PIPE 6 INCH ASTM A106 GR.B";
        String lowerPattern = "astm a106";
        assertEquals(21, kmp.search(upperText, lowerPattern));
    }

    @Test
    @DisplayName("12. Comparison counting and naive baseline consistency")
    void testComparisonCounting() {
        String text = "SEAMLESS PIPE 150MM CS ASTM A106 GRADE B";
        String pattern = "ASTM A106";

        KMPResult kmpRes = kmp.countComparisons(text, pattern);
        KMPResult naiveRes = kmp.naiveSearch(text, pattern);

        assertNotNull(kmpRes);
        assertNotNull(naiveRes);
        assertEquals(1, kmpRes.getPositions().size());
        assertEquals(kmpRes.getPositions(), naiveRes.getPositions());
        assertTrue(kmpRes.getComparisons() > 0);
    }

    @Test
    @DisplayName("13. Null input safety")
    void testNullSafety() {
        assertEquals(-1, kmp.search(null, "ASTM"));
        assertEquals(-1, kmp.search("ASTM", null));
        assertTrue(kmp.searchAll(null, "ASTM").isEmpty());
        assertTrue(kmp.searchAll("ASTM", null).isEmpty());
        assertEquals(0, kmp.buildLPS(null).length);
    }
}
