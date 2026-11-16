package harmonizer.bench;

import harmonizer.algo.bloom.BloomFilter;
import harmonizer.algo.trie.Trie;
import harmonizer.model.MaterialRecord;
import harmonizer.normalize.TextNormalizer;
import harmonizer.screening.DuplicateScreener;
import harmonizer.screening.MatchPair;
import harmonizer.screening.ScreeningResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Reproducible experiments for the report. Run with:
 * <pre>
 *   mvn -q compile
 *   java -cp target/classes harmonizer.bench.BloomTrieBenchmark
 * </pre>
 * Optional argument "quick" runs smaller sizes. Results depend on your machine, so quote the
 * hardware and Java version next to any table you put in the report. Fixed seeds make runs repeatable.
 */
public final class BloomTrieBenchmark {

    private static final long SEED = 2026L;

    private BloomTrieBenchmark() {
    }

    public static void main(String[] args) {
        boolean quick = args.length > 0 && args[0].equalsIgnoreCase("quick");
        System.out.println("Java " + System.getProperty("java.version") + " | "
                + Runtime.getRuntime().availableProcessors() + " cores | " + (quick ? "QUICK" : "FULL") + " run\n");
        bloomFalsePositiveExperiment(quick);
        trieVersusLinearScan(quick);
        screeningVersusBaselines(quick);
    }

    // ------------------------------------------------------------------ 1. Bloom: measured vs theory

    private static void bloomFalsePositiveExperiment(boolean quick) {
        System.out.println("=== 1. Bloom filter: measured false-positive rate vs theoretical (1 - e^(-kn/m))^k ===");
        System.out.printf("%-10s %-8s %-12s %-9s %-4s %-12s %-12s %-8s%n",
                "n", "target", "bits (m)", "bits/item", "k", "measured", "theoretical", "false-neg");
        int[] sizes = quick ? new int[]{1_000, 10_000} : new int[]{1_000, 10_000, 100_000, 1_000_000};
        double[] targets = {0.05, 0.01, 0.001};
        int queries = 500_000;

        for (int n : sizes) {
            for (double target : targets) {
                BloomFilter filter = new BloomFilter(n, target);
                for (int i = 0; i < n; i++) {
                    filter.add("mat:" + i);
                }
                int falseNegatives = 0;
                for (int i = 0; i < n; i++) {
                    if (!filter.mightContain("mat:" + i)) {
                        falseNegatives++;
                    }
                }
                int hits = 0;
                for (int j = 0; j < queries; j++) {
                    if (filter.mightContain("unseen:" + j)) {
                        hits++;
                    }
                }
                System.out.printf("%-10d %-8.3f %-12d %-9.2f %-4d %-12.5f %-12.5f %-8d%n",
                        n, target, filter.numBits(), (double) filter.numBits() / n, filter.numHashes(),
                        (double) hits / queries, filter.expectedFpr(), falseNegatives);
            }
        }
        System.out.println("Expect: measured close to theoretical, and false-neg always 0.\n");
    }

    // ------------------------------------------------------------------ 2. Trie vs linear scan

    private static void trieVersusLinearScan(boolean quick) {
        System.out.println("=== 2. Trie prefix search vs linear scan (top 10 keys, lexicographic) ===");
        System.out.printf("%-10s %-14s %-18s %-18s %-8s%n",
                "n keys", "trie build ms", "trie us/query", "linear us/query", "speedup");
        int[] sizes = quick ? new int[]{1_000, 10_000} : new int[]{1_000, 10_000, 100_000};
        int queries = 200;
        TextNormalizer normalizer = TextNormalizer.withDefaults();

        for (int n : sizes) {
            List<MaterialRecord> records = SyntheticMaterialGenerator.generate(n, 0.0, SEED).records();
            List<String> keys = new ArrayList<>(n);
            Set<String> distinct = new HashSet<>();
            for (MaterialRecord r : records) {
                String key = normalizer.clean(r.description());
                if (distinct.add(key)) {
                    keys.add(key);
                }
            }

            long t0 = System.nanoTime();
            Trie<Boolean> trie = new Trie<>();
            for (String k : keys) {
                trie.put(k, Boolean.TRUE);
            }
            double buildMs = (System.nanoTime() - t0) / 1e6;

            Random rnd = new Random(SEED);
            List<String> prefixes = new ArrayList<>();
            for (int q = 0; q < queries; q++) {
                String key = keys.get(rnd.nextInt(keys.size()));
                prefixes.add(key.substring(0, Math.min(key.length(), 4 + rnd.nextInt(5))));
            }

            // warm-up, and a correctness check: both methods must return identical results
            for (String p : prefixes) {
                if (!trie.keysWithPrefix(p, 10).equals(linearTop10(keys, p))) {
                    throw new IllegalStateException("Trie and linear scan disagree for prefix '" + p + "'");
                }
            }

            long trieNanos = 0;
            long linearNanos = 0;
            int sink = 0;
            for (String p : prefixes) {
                long a = System.nanoTime();
                sink += trie.keysWithPrefix(p, 10).size();
                trieNanos += System.nanoTime() - a;
                long b = System.nanoTime();
                sink += linearTop10(keys, p).size();
                linearNanos += System.nanoTime() - b;
            }
            if (sink < 0) {
                System.out.println(sink); // keeps the JIT from discarding the work
            }
            double trieUs = trieNanos / 1e3 / queries;
            double linearUs = linearNanos / 1e3 / queries;
            System.out.printf("%-10d %-14.1f %-18.2f %-18.2f %-8.1fx%n", keys.size(), buildMs, trieUs, linearUs, linearUs / trieUs);
        }
        System.out.println("Both methods return identical results (checked). Trie memory grows with total key characters.\n");
    }

    private static List<String> linearTop10(List<String> keys, String prefix) {
        List<String> matches = new ArrayList<>();
        for (String k : keys) {
            if (k.startsWith(prefix)) {
                matches.add(k);
            }
        }
        Collections.sort(matches);
        return matches.size() > 10 ? new ArrayList<>(matches.subList(0, 10)) : matches;
    }

    // ------------------------------------------------------------------ 3. Screening vs baselines

    private static void screeningVersusBaselines(boolean quick) {
        System.out.println("=== 3. Duplicate screening: Bloom + exact check vs baselines (30% duplicates) ===");
        System.out.println("Signatures are precomputed so timings isolate the matching stage.");
        System.out.printf("%-9s %-14s %-14s %-14s %-10s %-9s %-10s %-9s %-12s %-12s%n",
                "n", "pairwise ms", "hashmap ms", "bloom+map ms", "precision", "recall",
                "bloom FPs", "skipped", "filter KB", "signatures KB");
        int[] sizes = quick ? new int[]{1_000, 5_000} : new int[]{1_000, 5_000, 10_000, 50_000, 100_000};
        int pairwiseLimit = 10_000; // O(n^2) is too slow beyond this
        TextNormalizer normalizer = TextNormalizer.withDefaults();

        for (int n : sizes) {
            SyntheticMaterialGenerator.GeneratedData data = SyntheticMaterialGenerator.generate(n, 0.3, SEED);
            List<MaterialRecord> records = data.records();
            String[] signatures = new String[n];
            for (int i = 0; i < n; i++) {
                signatures[i] = normalizer.signature(records.get(i).description());
            }

            // run each method twice and keep the second timing (JIT warm-up)
            double pairwiseMs = Double.NaN;
            if (n <= pairwiseLimit) {
                naivePairwise(signatures);
                long t = System.nanoTime();
                naivePairwise(signatures);
                pairwiseMs = (System.nanoTime() - t) / 1e6;
            }
            hashMapOnly(signatures);
            long t1 = System.nanoTime();
            hashMapOnly(signatures);
            double hashMs = (System.nanoTime() - t1) / 1e6;

            DuplicateScreener screener = new DuplicateScreener(r -> signatures[r.id() - 1]);
            screener.screen(records, 0.01);
            long t2 = System.nanoTime();
            ScreeningResult result = screener.screen(records, 0.01);
            double bloomMs = (System.nanoTime() - t2) / 1e6;

            int correct = 0;
            for (MatchPair p : result.confirmedPairs()) {
                if (data.groupOfRecord()[p.recordId() - 1] == data.groupOfRecord()[p.matchedWithId() - 1]) {
                    correct++;
                }
            }
            int expected = n - data.groupCount();
            double precision = result.confirmedPairs().isEmpty() ? 1.0 : (double) correct / result.confirmedPairs().size();
            double recall = expected == 0 ? 1.0 : (double) correct / expected;

            Set<String> distinctSignatures = new HashSet<>(List.of(signatures));
            long signatureBytes = 0;
            for (String s : distinctSignatures) {
                signatureBytes += s.getBytes(StandardCharsets.UTF_8).length;
            }
            double filterKb = new BloomFilter(n, 0.01).sizeInBytes() / 1024.0;

            System.out.printf("%-9d %-14s %-14.2f %-14.2f %-10.3f %-9.3f %-10d %-9d %-12.1f %-12.1f%n",
                    n, Double.isNaN(pairwiseMs) ? "(too slow)" : String.format("%.2f", pairwiseMs),
                    hashMs, bloomMs, precision, recall,
                    result.stats().bloomFalsePositives(), result.skipped().size(),
                    filterKb, signatureBytes / 1024.0);
        }
        System.out.println("Honest reading: in memory, a plain hash map is as fast as Bloom + map. The filter's value is");
        System.out.println("memory (filter KB vs signatures KB) and avoiding lookups in an expensive authoritative store.");
        System.out.println("Precision/recall here are on synthetic data whose variations match the normaliser by design.");
    }

    /** Baseline: compare each record with every earlier record until a match is found. O(n^2). */
    private static int naivePairwise(String[] signatures) {
        int pairs = 0;
        for (int i = 1; i < signatures.length; i++) {
            for (int j = 0; j < i; j++) {
                if (signatures[i].equals(signatures[j])) {
                    pairs++;
                    break;
                }
            }
        }
        return pairs;
    }

    /** Baseline: exact hash map only, no Bloom filter. */
    private static int hashMapOnly(String[] signatures) {
        Map<String, Integer> first = new HashMap<>();
        int pairs = 0;
        for (int i = 0; i < signatures.length; i++) {
            if (first.putIfAbsent(signatures[i], i) != null) {
                pairs++;
            }
        }
        return pairs;
    }
}
