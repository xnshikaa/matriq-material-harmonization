package harmonizer.algo;

import harmonizer.algo.kmp.KMPMatcher;
import harmonizer.algo.kmp.KMPResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the KMP implementation.
 *
 * <p>Every test asserts the actual expected positions, LPS values or comparison counts — a test that
 * only checks "no exception" would not prove the algorithm correct. The randomised test compares KMP
 * against an independent naive scan written inside this test class.
 */
class KMPMatcherTest {

    private final KMPMatcher kmp = new KMPMatcher();

    @Test
    @DisplayName("1. LPS construction matches the textbook failure function")
    void lpsConstruction() {
        assertArrayEqualsInts(new int[]{0, 0, 1, 2, 0}, kmp.buildLPS("ABABC"));
        assertArrayEqualsInts(new int[]{0, 1, 2, 3}, kmp.buildLPS("AAAA"));
        assertArrayEqualsInts(new int[]{0}, kmp.buildLPS("A"));
        assertArrayEqualsInts(new int[]{0, 0, 0, 0, 0}, kmp.buildLPS("ABCDE"));
        assertArrayEqualsInts(new int[]{0, 0, 1, 2, 3, 4}, kmp.buildLPS("ABABAB"));
    }

    @Test
    @DisplayName("2. Single occurrence returns its zero-based start index")
    void singleMatch() {
        String text = "Pipe seamless 6 inch ASTM A106 Gr.B";
        String pattern = "ASTM A106";

        assertEquals(21, kmp.search(text, pattern));
        assertEquals(List.of(21), kmp.searchAll(text, pattern));
    }

    @Test
    @DisplayName("3. Absent pattern returns -1 and an empty list")
    void noMatch() {
        String text = "Pipe seamless 6 inch ASTM A106 Gr.B";

        assertEquals(-1, kmp.search(text, "SS316"));
        assertTrue(kmp.searchAll(text, "SS316").isEmpty());
    }

    @Test
    @DisplayName("4. Multiple non-overlapping occurrences are all reported")
    void multipleMatches() {
        String text = "ASTM A106 seamless pipe certified to ASTM A106 standard";

        assertEquals(List.of(0, 37), kmp.searchAll(text, "ASTM A106"));
        assertEquals(0, kmp.search(text, "ASTM A106"));
    }

    @Test
    @DisplayName("5. Overlapping occurrences are reported (AA in AAAA at 0, 1, 2)")
    void overlappingMatches() {
        assertEquals(List.of(0, 1, 2), kmp.searchAll("AAAA", "AA"));
        assertEquals(List.of(0, 1, 2, 3, 4), kmp.searchAll("AAAAAA", "AA"));
    }

    @Test
    @DisplayName("6. Pattern longer than the text never matches")
    void patternLongerThanText() {
        assertEquals(-1, kmp.search("ASTM", "ASTM A106 Grade B Standard"));
        assertTrue(kmp.searchAll("ASTM", "ASTM A106 Grade B Standard").isEmpty());
    }

    @Test
    @DisplayName("7. Empty pattern is handled safely")
    void emptyPattern() {
        assertEquals(-1, kmp.search("Seamless pipe", ""));
        assertTrue(kmp.searchAll("Seamless pipe", "").isEmpty());
        assertEquals(0, kmp.buildLPS("").length);
        assertEquals(0, kmp.countComparisons("Seamless pipe", "").comparisons());
    }

    @Test
    @DisplayName("8. Empty text is handled safely")
    void emptyText() {
        assertEquals(-1, kmp.search("", "ASTM A106"));
        assertTrue(kmp.searchAll("", "ASTM A106").isEmpty());
        assertEquals(0, kmp.countComparisons("", "ASTM A106").comparisons());
    }

    @Test
    @DisplayName("9. Repeated characters produce every occurrence")
    void repeatedCharacters() {
        List<Integer> matches = kmp.searchAll("BAAAAAAAAAAB", "AAAA");

        assertEquals(7, matches.size());
        assertEquals(1, matches.get(0));
        assertEquals(7, matches.get(matches.size() - 1));
    }

    @Test
    @DisplayName("10. Technical specifications are matched case-insensitively")
    void caseInsensitiveSpecifications() {
        assertEquals(23, kmp.search("seamless pipe 150mm cs a106-b", "A106-B"));
        assertEquals(21, kmp.search("SEAMLESS PIPE 6 INCH ASTM A106 GR.B", "astm a106"));
        assertEquals(List.of(0, 17), kmp.searchAll("SS316 flange and SS316 valve", "ss316"));
    }

    @Test
    @DisplayName("11. Returned positions actually point at the pattern")
    void positionsAreExact() {
        String text = "Flange 4 inch A105 ANSI B16.5 RF, spare A105 flange";
        String pattern = "A105";

        List<Integer> matches = kmp.searchAll(text, pattern);
        assertEquals(2, matches.size());
        for (int position : matches) {
            assertEquals(pattern.toLowerCase(Locale.ROOT),
                    text.toLowerCase(Locale.ROOT)
                            .substring(position, position + pattern.length()));
        }
    }

    @Test
    @DisplayName("12. Null inputs are safe and report no match")
    void nullSafety() {
        assertEquals(-1, kmp.search(null, "ASTM"));
        assertEquals(-1, kmp.search("ASTM", null));
        assertTrue(kmp.searchAll(null, "ASTM").isEmpty());
        assertTrue(kmp.searchAll("ASTM", null).isEmpty());
        assertEquals(0, kmp.buildLPS(null).length);
        assertEquals(0, kmp.countComparisons(null, null).comparisons());
    }

    @Test
    @DisplayName("13. Comparison counting matches the reported positions and LPS")
    void comparisonCounting() {
        String text = "SEAMLESS PIPE 150MM CS ASTM A106 GRADE B";
        String pattern = "ASTM A106";

        KMPResult result = kmp.countComparisons(text, pattern);

        assertTrue(result.isMatch());
        assertEquals(23, result.firstPosition());
        assertEquals(kmp.searchAll(text, pattern), result.positions());
        assertArrayEqualsInts(kmp.buildLPS(pattern), result.lps());
        assertTrue(result.comparisons() > 0, "comparisons must be counted");
        assertTrue(result.comparisons() >= text.length(),
                "a full scan compares at least one character per text position");
    }

    @Test
    @DisplayName("14. KMP finds exactly the occurrences a naive scan finds (randomised)")
    void agreesWithNaiveScan() {
        Random random = new Random(42L);
        for (int trial = 0; trial < 500; trial++) {
            String text = randomText(random, random.nextInt(41));
            String pattern = randomText(random, 1 + random.nextInt(5));

            assertEquals(naiveMatches(text, pattern), kmp.searchAll(text, pattern),
                    "text=\"" + text + "\" pattern=\"" + pattern + "\"");
        }
    }

    @Test
    @DisplayName("15. Comparison count stays inside the KMP linear bound")
    void comparisonBound() {
        Random random = new Random(7L);
        for (int trial = 0; trial < 100; trial++) {
            String text = randomText(random, 200 + random.nextInt(801));
            String pattern = randomText(random, 1 + random.nextInt(8));

            long comparisons = kmp.countComparisons(text, pattern).comparisons();
            long bound = 2L * (text.length() + pattern.length()) + 2L;

            assertTrue(comparisons <= bound,
                    "KMP used " + comparisons + " comparisons, bound was " + bound);
        }
    }

    @Test
    @DisplayName("16. Result object exposes matches and is not affected by caller mutation")
    void resultObjectIsSafe() {
        KMPResult result = kmp.countComparisons("AAAA", "AA");
        List<Integer> positions = result.positions();

        assertTrue(result.isMatch());
        assertEquals(3, positions.size());
        assertFalse(positions.isEmpty());
        assertTrue(result.lps().length == 2);

        int[] lps = result.lps();
        lps[0] = 99;
        assertEquals(0, result.lps()[0], "caller must not be able to mutate the stored LPS array");
    }

    // ------------------------------------------------------------------ helpers

    /** Independent naive scan, used only to check KMP's answers. */
    private static List<Integer> naiveMatches(String text, String pattern) {
        List<Integer> matches = new ArrayList<>();
        if (text == null || pattern == null || pattern.isEmpty() || pattern.length() > text.length()) {
            return matches;
        }
        String t = text.toLowerCase(Locale.ROOT);
        String p = pattern.toLowerCase(Locale.ROOT);
        for (int start = 0; start + p.length() <= t.length(); start++) {
            int offset = 0;
            while (offset < p.length() && t.charAt(start + offset) == p.charAt(offset)) {
                offset++;
            }
            if (offset == p.length()) {
                matches.add(start);
            }
        }
        return matches;
    }

    private static String randomText(Random random, int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ('a' + random.nextInt(3)));
        }
        return builder.toString();
    }

    private static void assertArrayEqualsInts(int[] expected, int[] actual) {
        org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual);
    }
}
