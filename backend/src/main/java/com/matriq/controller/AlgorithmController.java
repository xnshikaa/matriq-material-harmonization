package com.matriq.controller;

import com.matriq.algorithms.bloom.BloomFilter;
import com.matriq.algorithms.dsu.DisjointSetUnion;
import com.matriq.algorithms.kmp.KMPMatcher;
import com.matriq.algorithms.kmp.KMPResult;
import com.matriq.algorithms.kmp.KMPTracer;
import com.matriq.algorithms.trie.MaterialTrie;
import com.matriq.harmonization.HarmonizationService;
import com.matriq.matching.MatchingService;
import com.matriq.model.BenchmarkResult;
import com.matriq.normalization.NormalizationService;
import com.matriq.repository.HarmonizationRepository;
import com.matriq.repository.MaterialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for Algorithm Lab — provides real algorithm execution results
 * for visualization in the React frontend.
 *
 * IMPORTANT: All algorithm logic runs in Java here.
 * The frontend ONLY displays results — it does NOT re-implement the algorithms.
 */
@RestController
@RequestMapping("/api/algorithms")
public class AlgorithmController {

    private final KMPMatcher kmpMatcher = new KMPMatcher();
    private final KMPTracer kmpTracer = new KMPTracer();
    private final HarmonizationService harmonizationService;
    private final MatchingService matchingService;
    private final NormalizationService normalizationService;
    private final HarmonizationRepository harmonizationRepository;
    private final MaterialRepository materialRepository;

    @Autowired
    public AlgorithmController(HarmonizationService harmonizationService,
                                MatchingService matchingService,
                                NormalizationService normalizationService,
                                HarmonizationRepository harmonizationRepository,
                                MaterialRepository materialRepository) {
        this.harmonizationService = harmonizationService;
        this.matchingService = matchingService;
        this.normalizationService = normalizationService;
        this.harmonizationRepository = harmonizationRepository;
        this.materialRepository = materialRepository;
    }

    // ── KMP ─────────────────────────────────────────────────────────────────

    /** POST /api/algorithms/kmp/search — run KMP on given text and pattern */
    @PostMapping("/kmp/search")
    public ResponseEntity<KMPResult> kmpSearch(@RequestBody Map<String, String> body) {
        String text    = body.getOrDefault("text", "");
        String pattern = body.getOrDefault("pattern", "");
        KMPResult result = kmpMatcher.countComparisons(text, pattern);
        // Also add naive comparison for benchmarking
        KMPResult naive = kmpMatcher.naiveSearch(text, pattern);
        // Annotate result with naive comparison count
        result.getSteps().add(new KMPResult.KMPStep(-1, -1, false, "NAIVE_COMPARISON",
            "Naive search used " + naive.getComparisons() + " comparisons vs KMP's " + result.getComparisons()));
        return ResponseEntity.ok(result);
    }

    /** GET /api/algorithms/kmp/trace?text=...&pattern=... — step-by-step trace */
    @GetMapping("/kmp/trace")
    public ResponseEntity<KMPResult> kmpTrace(
            @RequestParam String text,
            @RequestParam String pattern) {
        KMPResult trace = kmpTracer.trace(text, pattern);
        return ResponseEntity.ok(trace);
    }

    // ── DSU ─────────────────────────────────────────────────────────────────

    /** GET /api/algorithms/dsu/groups — current DSU groups from the harmonization engine */
    @GetMapping("/dsu/groups")
    public ResponseEntity<Map<String, Object>> getDsuGroups() {
        DisjointSetUnion dsu = harmonizationService.getDsu();
        Map<String, List<String>> groups = dsu.getGroups();

        Map<String, Object> response = new HashMap<>();
        response.put("totalElements", dsu.getTotalElements());
        response.put("componentCount", dsu.getComponentCount());

        List<Map<String, Object>> groupList = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            Map<String, Object> g = new HashMap<>();
            g.put("representative", entry.getKey());
            g.put("members", entry.getValue());
            g.put("size", entry.getValue().size());
            groupList.add(g);
        }
        response.put("groups", groupList);
        return ResponseEntity.ok(response);
    }

    /** POST /api/algorithms/dsu/demo — run a DSU demo with given IDs */
    @PostMapping("/dsu/demo")
    public ResponseEntity<Map<String, Object>> dsuDemo(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of("A", "B", "C", "D", "E"));
        @SuppressWarnings("unchecked")
        List<List<String>> unions = (List<List<String>>) body.getOrDefault("unions",
            List.of(List.of("A", "B"), List.of("B", "C"), List.of("D", "E")));

        DisjointSetUnion demoDsu = new DisjointSetUnion();
        List<Map<String, Object>> steps = new ArrayList<>();

        // makeSet for each ID
        for (String id : ids) {
            demoDsu.makeSet(id);
            steps.add(Map.of("action", "MAKE_SET", "id", id,
                "groups", snapshotGroups(demoDsu),
                "explanation", "makeSet('" + id + "') — element now a singleton set."));
        }

        // union operations
        for (List<String> pair : unions) {
            if (pair.size() < 2) continue;
            String a = pair.get(0), b = pair.get(1);
            if (!demoDsu.isRegistered(a) || !demoDsu.isRegistered(b)) continue;
            String rootA = demoDsu.find(a);
            String rootB = demoDsu.find(b);
            boolean merged = demoDsu.union(a, b);
            String explanation = merged
                ? "union('" + a + "', '" + b + "') — merged. Root of '" + a + "' was " + rootA
                  + ", root of '" + b + "' was " + rootB + "."
                : "union('" + a + "', '" + b + "') — already in same set. No change.";
            steps.add(Map.of("action", "UNION", "id1", a, "id2", b, "merged", merged,
                "groups", snapshotGroups(demoDsu),
                "explanation", explanation));
        }

        return ResponseEntity.ok(Map.of(
            "steps", steps,
            "finalGroups", snapshotGroups(demoDsu),
            "componentCount", demoDsu.getComponentCount()
        ));
    }

    // ── Bloom Filter ─────────────────────────────────────────────────────────

    /** GET /api/algorithms/bloom/statistics */
    @GetMapping("/bloom/statistics")
    public ResponseEntity<Map<String, Object>> bloomStats() {
        BloomFilter bf = matchingService.getBloomFilter();
        if (bf == null) {
            // Create a demo filter
            bf = BloomFilter.withOptimalParameters(1000, 0.01);
            return ResponseEntity.ok(Map.of(
                "message", "No Bloom Filter active. Run harmonization first.",
                "demo", Map.of(
                    "bitArraySize", bf.getBitArraySize(),
                    "hashFunctions", bf.getNumberOfHashFunctions(),
                    "expectedFPP", 0.01
                )
            ));
        }
        return ResponseEntity.ok(Map.of(
            "bitArraySize", bf.getBitArraySize(),
            "numberOfHashFunctions", bf.getNumberOfHashFunctions(),
            "insertedCount", bf.getInsertedCount(),
            "setBitCount", bf.getSetBitCount(),
            "currentFPP", bf.currentFalsePositiveProbability(),
            "falsePositivesTracked", bf.getFalsePositivesTracked()
        ));
    }

    // ── Trie ─────────────────────────────────────────────────────────────────

    /** POST /api/algorithms/trie/search */
    @PostMapping("/trie/search")
    public ResponseEntity<Map<String, Object>> trieSearch(@RequestBody Map<String, String> body) {
        String prefix = body.getOrDefault("prefix", "");
        MaterialTrie trie = normalizationService.getAbbreviationTrie();

        List<String> suggestions = trie.getSuggestions(prefix);
        boolean exactMatch = trie.search(prefix);
        String value = trie.getValue(prefix);

        return ResponseEntity.ok(Map.of(
            "prefix", prefix,
            "exactMatch", exactMatch,
            "value", value != null ? value : "",
            "suggestions", suggestions
        ));
    }

    // ── Benchmarks ───────────────────────────────────────────────────────────

    /** POST /api/benchmarks/run — run benchmarks at multiple sizes */
    @PostMapping("/benchmarks/run")
    public ResponseEntity<List<BenchmarkResult>> runBenchmarks() {
        List<BenchmarkResult> results = new ArrayList<>();

        int[] sizes = {100, 1000, 10000};
        String testPattern = "ASTM A106";

        for (int size : sizes) {
            // Build a text of given size with known occurrences
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < size; i++) {
                text.append("SEAMLESS PIPE 6 INCH ");
                if (i % 10 == 0) text.append("ASTM A106 GRADE B ");
                text.append("CS ");
            }
            String textStr = text.toString();

            // KMP benchmark
            long kmpStart = System.nanoTime();
            KMPResult kmpResult = kmpMatcher.countComparisons(textStr, testPattern);
            long kmpTime = System.nanoTime() - kmpStart;

            // Naive baseline
            long naiveStart = System.nanoTime();
            KMPResult naiveResult = kmpMatcher.naiveSearch(textStr, testPattern);
            long naiveTime = System.nanoTime() - naiveStart;

            BenchmarkResult br = new BenchmarkResult();
            br.setId(UUID.randomUUID().toString());
            br.setAlgorithmName("KMP");
            br.setDescription("KMP vs Naive for '" + testPattern + "' in " + size + " record text");
            br.setDatasetSize(size);
            br.setExecutionTimeNanos(kmpTime);
            br.setNaiveExecutionTimeNanos(naiveTime);
            br.setComparisons(kmpResult.getComparisons());
            br.setDetails(Map.of(
                "textLength", textStr.length(),
                "patternLength", testPattern.length(),
                "matchesFound", kmpResult.getPositions().size(),
                "naiveComparisons", naiveResult.getComparisons(),
                "kmpComparisons", kmpResult.getComparisons(),
                "kmpTimeMs", kmpTime / 1_000_000.0,
                "naiveTimeMs", naiveTime / 1_000_000.0
            ));

            harmonizationRepository.saveBenchmark(br);
            results.add(br);

            // DSU benchmark
            DisjointSetUnion dsuBench = new DisjointSetUnion();
            long dsuStart = System.nanoTime();
            for (int i = 0; i < size; i++) {
                dsuBench.makeSet("RECORD-" + i);
            }
            // Create size/2 union operations
            for (int i = 0; i < size - 1; i += 2) {
                dsuBench.union("RECORD-" + i, "RECORD-" + (i + 1));
            }
            long dsuTime = System.nanoTime() - dsuStart;

            BenchmarkResult dsuBr = new BenchmarkResult();
            dsuBr.setId(UUID.randomUUID().toString());
            dsuBr.setAlgorithmName("DSU");
            dsuBr.setDescription("DSU with " + size + " elements and " + (size / 2) + " unions");
            dsuBr.setDatasetSize(size);
            dsuBr.setExecutionTimeNanos(dsuTime);
            dsuBr.setOperations(size + (long) size / 2);
            dsuBr.setDetails(Map.of(
                "elements", size,
                "unions", size / 2,
                "components", dsuBench.getComponentCount(),
                "totalTimeMs", dsuTime / 1_000_000.0
            ));

            harmonizationRepository.saveBenchmark(dsuBr);
            results.add(dsuBr);
        }

        return ResponseEntity.ok(results);
    }

    /** GET /api/benchmarks/results */
    @GetMapping("/benchmarks/results")
    public ResponseEntity<List<BenchmarkResult>> getBenchmarkResults() {
        return ResponseEntity.ok(harmonizationRepository.findAllBenchmarks());
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private List<Map<String, Object>> snapshotGroups(DisjointSetUnion dsu) {
        Map<String, List<String>> groups = dsu.getGroups();
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : groups.entrySet()) {
            snapshot.add(Map.of("root", e.getKey(), "members", e.getValue()));
        }
        return snapshot;
    }
}
