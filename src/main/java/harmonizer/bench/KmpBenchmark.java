package harmonizer.bench;

import harmonizer.algo.kmp.KMPMatcher;
import harmonizer.algo.kmp.KMPResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * KMP versus a hand-written naive search, on the same generated text.
 *
 * <pre>
 *   mvn -q compile
 *   java -cp target/classes harmonizer.bench.KmpBenchmark
 *   java -cp target/classes harmonizer.bench.KmpBenchmark quick
 * </pre>
 *
 * <p>Both algorithms run on identical input, the match lists are compared before timing, and each
 * measurement is the best of several runs after a warm-up. Comparison counts are counted inside the
 * algorithms themselves, not derived from the timings.
 *
 * <p>Honest reading: KMP does less work (fewer comparisons) as the text grows, but it also builds the
 * LPS array up front. For short texts and non-repetitive patterns the naive loop can be competitive or
 * even faster in wall-clock time because it has a smaller constant factor. The results below are what
 * this machine measured; they are not a universal claim.
 */
public final class KmpBenchmark {

    private static final String PATTERN = "ASTM A106";
    private static final int REPS = 3;

    private KmpBenchmark() {
    }

    public static void main(String[] args) {
        boolean quick = args.length > 0 && args[0].equalsIgnoreCase("quick");
        int[] lengths = quick
                ? new int[]{1_000, 10_000}
                : new int[]{1_000, 10_000, 100_000, 1_000_000};

        System.out.println("Java " + System.getProperty("java.version") + " | "
                + Runtime.getRuntime().availableProcessors() + " cores | "
                + (quick ? "QUICK" : "FULL") + " run");
        System.out.println();
        System.out.println("=== KMP vs naive pattern search, pattern \"" + PATTERN
                + "\" (best of " + REPS + " runs) ===");
        System.out.printf("%-12s %-8s %-9s %-17s %-18s %-11s %-11s %-8s%n",
                "text chars", "pattern", "matches", "KMP comparisons", "naive comparisons",
                "KMP ms", "naive ms", "ratio");

        KMPMatcher matcher = new KMPMatcher();

        for (int length : lengths) {
            String text = buildText(length, 2026L);

            // Warm-up so the reported timings are not dominated by JIT compilation.
            matcher.countComparisons(text, PATTERN);
            naiveSearch(text, PATTERN);

            // Correctness first: both algorithms must report exactly the same occurrences.
            List<Integer> kmpPositions = matcher.searchAll(text, PATTERN);
            List<Integer> naivePositions = naiveSearch(text, PATTERN).positions();
            if (!kmpPositions.equals(naivePositions)) {
                throw new IllegalStateException("KMP and naive disagree at text length " + length
                        + ": KMP=" + kmpPositions.size() + " naive=" + naivePositions.size());
            }

            double kmpMillis = Double.MAX_VALUE;
            double naiveMillis = Double.MAX_VALUE;
            long kmpComparisons = 0L;
            long naiveComparisons = 0L;

            for (int rep = 0; rep < REPS; rep++) {
                long startKmp = System.nanoTime();
                KMPResult kmpResult = matcher.countComparisons(text, PATTERN);
                kmpMillis = Math.min(kmpMillis, (System.nanoTime() - startKmp) / 1e6);
                kmpComparisons = kmpResult.comparisons();

                long startNaive = System.nanoTime();
                NaiveRun naive = naiveSearch(text, PATTERN);
                naiveMillis = Math.min(naiveMillis, (System.nanoTime() - startNaive) / 1e6);
                naiveComparisons = naive.comparisons();

                if (naive.matches() != kmpResult.positions().size()) {
                    throw new IllegalStateException("Algorithms disagreed during timing");
                }
            }

            double ratio = naiveComparisons == 0 ? 1.0 : (double) kmpComparisons / naiveComparisons;
            System.out.printf("%-12d %-8d %-9d %-17d %-18d %-11.3f %-11.3f %-8.2f%n",
                    text.length(), PATTERN.length(), kmpPositions.size(),
                    kmpComparisons, naiveComparisons, kmpMillis, naiveMillis, ratio);
        }

        System.out.println();
        System.out.println("ratio = KMP comparisons / naive comparisons (lower is better for KMP).");
        System.out.println("KMP comparisons grow linearly with the text; naive comparisons grow with text length times pattern length");
        System.out.println("whenever mismatches occur late in the pattern. KMP also pays O(m) once to build the LPS array, which");
        System.out.println("is why it is not automatically faster on short texts or at very small match counts.");
        System.out.println();
        nearMissExperiment(quick, matcher);
        System.out.println();
        worstCaseExperiment(quick, matcher);
    }

    /**
     * Textbook worst case for naive matching: text of one repeated character, pattern of that character
     * repeated with a different final character. Naive compares almost the whole pattern at every start
     * (O(n * m)); KMP falls back to a prefix that already matched and stays linear.
     *
     * <p>This is synthetic on purpose and is labelled as such: real material descriptions do not look
     * like this. It is included because it is the standard input that proves KMP's guarantee instead of
     * only asserting it.
     */
    private static void worstCaseExperiment(boolean quick, KMPMatcher matcher) {
        String pattern = "a".repeat(49) + "b";
        int[] lengths = quick ? new int[]{10_000} : new int[]{100_000, 1_000_000};

        System.out.println("=== Synthetic worst case for naive: pattern \"a\"x49 + \"b\" over a text of \"a\"s (no match) ===");
        System.out.printf("%-12s %-8s %-17s %-18s %-11s %-11s %-8s%n",
                "text chars", "pattern", "KMP comparisons", "naive comparisons", "KMP ms", "naive ms", "ratio");

        for (int length : lengths) {
            String text = "a".repeat(length);

            if (!matcher.searchAll(text, pattern).equals(naiveSearch(text, pattern).positions())) {
                throw new IllegalStateException("KMP and naive disagree on the worst-case text");
            }

            double kmpMillis = Double.MAX_VALUE;
            double naiveMillis = Double.MAX_VALUE;
            long kmpComparisons = 0L;
            long naiveComparisons = 0L;
            for (int rep = 0; rep < REPS; rep++) {
                long startKmp = System.nanoTime();
                KMPResult kmpResult = matcher.countComparisons(text, pattern);
                kmpMillis = Math.min(kmpMillis, (System.nanoTime() - startKmp) / 1e6);
                kmpComparisons = kmpResult.comparisons();

                long startNaive = System.nanoTime();
                NaiveRun naive = naiveSearch(text, pattern);
                naiveMillis = Math.min(naiveMillis, (System.nanoTime() - startNaive) / 1e6);
                naiveComparisons = naive.comparisons();
            }

            double ratio = naiveComparisons == 0 ? 1.0 : (double) kmpComparisons / naiveComparisons;
            System.out.printf("%-12d %-8d %-17d %-18d %-11.3f %-11.3f %-8.3f%n",
                    text.length(), pattern.length(), kmpComparisons, naiveComparisons,
                    kmpMillis, naiveMillis, ratio);
        }
        System.out.println("This is the case the theory predicts: naive comparisons scale with pattern length, KMP's do not.");
    }

    /**
     * Adversarial input for the naive loop: every block matches the pattern except its last character,
     * so a naive search walks almost the whole pattern and then restarts one position later.
     */
    private static void nearMissExperiment(boolean quick, KMPMatcher matcher) {
        String pattern = "ASTM A106 GRADE B";
        String block = "ASTM A106 GRADX ";
        int[] lengths = quick ? new int[]{10_000} : new int[]{100_000, 1_000_000};

        System.out.println("=== Near-miss text: every block matches except the last character (pattern \""
                + pattern + "\") ===");
        System.out.printf("%-12s %-8s %-17s %-18s %-11s %-11s %-8s%n",
                "text chars", "matches", "KMP comparisons", "naive comparisons", "KMP ms", "naive ms", "ratio");

        for (int length : lengths) {
            StringBuilder builder = new StringBuilder(length + block.length());
            while (builder.length() < length) {
                builder.append(block);
            }
            String text = builder.substring(0, length);

            if (!matcher.searchAll(text, pattern).equals(naiveSearch(text, pattern).positions())) {
                throw new IllegalStateException("KMP and naive disagree on the near-miss text");
            }

            double kmpMillis = Double.MAX_VALUE;
            double naiveMillis = Double.MAX_VALUE;
            long kmpComparisons = 0L;
            long naiveComparisons = 0L;
            for (int rep = 0; rep < REPS; rep++) {
                long startKmp = System.nanoTime();
                KMPResult kmpResult = matcher.countComparisons(text, pattern);
                kmpMillis = Math.min(kmpMillis, (System.nanoTime() - startKmp) / 1e6);
                kmpComparisons = kmpResult.comparisons();

                long startNaive = System.nanoTime();
                NaiveRun naive = naiveSearch(text, pattern);
                naiveMillis = Math.min(naiveMillis, (System.nanoTime() - startNaive) / 1e6);
                naiveComparisons = naive.comparisons();
            }

            double ratio = naiveComparisons == 0 ? 1.0 : (double) kmpComparisons / naiveComparisons;
            System.out.printf("%-12d %-8d %-17d %-18d %-11.3f %-11.3f %-8.2f%n",
                    text.length(), matcher.searchAll(text, pattern).size(),
                    kmpComparisons, naiveComparisons, kmpMillis, naiveMillis, ratio);
        }
        System.out.println("Here the naive search re-reads most of the pattern at every start position, so its comparison count is");
        System.out.println("pattern-length times the text length, while KMP stays close to a single pass over the text.");
    }

    // ------------------------------------------------------------------ input

    /**
     * Builds a realistic description stream of the requested length. Roughly every seventh fragment
     * contains the searched specification, so both algorithms have real work to do.
     */
    private static String buildText(int length, long seed) {
        Random random = new Random(seed);
        StringBuilder builder = new StringBuilder(length + 128);

        while (builder.length() < length) {
            switch (random.nextInt(5)) {
                case 0 -> builder.append("PIPE SEAMLESS 6 INCH ASTM A106 GR.B DWG ");
                case 1 -> builder.append("flange slip-on 4 inch a105 class 150 ansi b16.5 rf ");
                case 2 -> builder.append("gate valve 2 inch carbon steel is 1239 part 1 ");
                case 3 -> builder.append("ASTM A106-B line pipe 150 mm schedule 80 ");
                default -> builder.append("seamless pipe 8 inch astm a53 grade a sch 40 ");
            }
        }
        return builder.substring(0, length);
    }

    // ------------------------------------------------------------------ naive baseline

    private record NaiveRun(List<Integer> positions, long comparisons) {
        int matches() {
            return positions.size();
        }
    }

    /** Deliberately simple O(n * m) baseline. It restarts the pattern after every mismatch. */
    private static NaiveRun naiveSearch(String text, String pattern) {
        List<Integer> positions = new ArrayList<>();
        if (text == null || pattern == null || pattern.isEmpty() || pattern.length() > text.length()) {
            return new NaiveRun(positions, 0L);
        }
        String t = text.toLowerCase(Locale.ROOT);
        String p = pattern.toLowerCase(Locale.ROOT);
        int n = t.length();
        int m = p.length();
        long comparisons = 0L;

        for (int start = 0; start + m <= n; start++) {
            int offset = 0;
            while (offset < m) {
                comparisons++;
                if (t.charAt(start + offset) != p.charAt(offset)) {
                    break;
                }
                offset++;
            }
            if (offset == m) {
                positions.add(start);
            }
        }
        return new NaiveRun(positions, comparisons);
    }
}
