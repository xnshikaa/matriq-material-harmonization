package com.matriq.controller;

import com.matriq.algorithms.dsu.DisjointSetUnion;
import com.matriq.algorithms.kmp.KMPMatcher;
import com.matriq.algorithms.kmp.KMPResult;
import com.matriq.model.BenchmarkResult;
import com.matriq.repository.HarmonizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for benchmark execution and history.
 * Exposes /api/benchmarks/run and /api/benchmarks/results directly as required by specification.
 */
@RestController
@RequestMapping("/api/benchmarks")
public class BenchmarkController {

    private final KMPMatcher kmpMatcher = new KMPMatcher();
    private final HarmonizationRepository harmonizationRepository;

    @Autowired
    public BenchmarkController(HarmonizationRepository harmonizationRepository) {
        this.harmonizationRepository = harmonizationRepository;
    }

    /**
     * POST /api/benchmarks/run
     * Executes real comparative benchmarks at sizes: 100, 1000, 10000.
     * Evaluates KMP vs Naive substring search and DSU amortized operations.
     */
    @PostMapping("/run")
    public ResponseEntity<List<BenchmarkResult>> runBenchmarks() {
        List<BenchmarkResult> results = new ArrayList<>();
        int[] sizes = {100, 1000, 10000};
        String testPattern = "ASTM A106";

        for (int size : sizes) {
            // Build synthetic text with controlled repetitions of specification
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < size; i++) {
                text.append("SEAMLESS PIPE 6 INCH ");
                if (i % 10 == 0) {
                    text.append("ASTM A106 GRADE B ");
                }
                text.append("CS ");
            }
            String textStr = text.toString();

            // 1. KMP Benchmark
            long kmpStart = System.nanoTime();
            KMPResult kmpResult = kmpMatcher.countComparisons(textStr, testPattern);
            long kmpTime = System.nanoTime() - kmpStart;

            // 2. Naive Benchmark
            long naiveStart = System.nanoTime();
            KMPResult naiveResult = kmpMatcher.naiveSearch(textStr, testPattern);
            long naiveTime = System.nanoTime() - naiveStart;

            BenchmarkResult kmpBr = new BenchmarkResult();
            kmpBr.setId(UUID.randomUUID().toString());
            kmpBr.setAlgorithmName("KMP");
            kmpBr.setDescription("KMP vs Naive for '" + testPattern + "' across " + size + " material description blocks");
            kmpBr.setDatasetSize(size);
            kmpBr.setExecutionTimeNanos(kmpTime);
            kmpBr.setNaiveExecutionTimeNanos(naiveTime);
            kmpBr.setComparisons(kmpResult.getComparisons());
            kmpBr.setDetails(Map.of(
                "textLength", textStr.length(),
                "patternLength", testPattern.length(),
                "matchesFound", kmpResult.getPositions().size(),
                "naiveComparisons", naiveResult.getComparisons(),
                "kmpComparisons", kmpResult.getComparisons(),
                "kmpTimeMs", kmpTime / 1_000_000.0,
                "naiveTimeMs", naiveTime / 1_000_000.0,
                "comparisonReductionPercent", naiveResult.getComparisons() > 0 ?
                    Math.round((1.0 - (double) kmpResult.getComparisons() / naiveResult.getComparisons()) * 1000.0) / 10.0 : 0.0
            ));

            harmonizationRepository.saveBenchmark(kmpBr);
            results.add(kmpBr);

            // 3. DSU Benchmark
            DisjointSetUnion dsuBench = new DisjointSetUnion();
            long dsuStart = System.nanoTime();
            for (int i = 0; i < size; i++) {
                dsuBench.makeSet("REC-" + i);
            }
            for (int i = 0; i < size - 1; i += 2) {
                dsuBench.union("REC-" + i, "REC-" + (i + 1));
            }
            long dsuTime = System.nanoTime() - dsuStart;

            BenchmarkResult dsuBr = new BenchmarkResult();
            dsuBr.setId(UUID.randomUUID().toString());
            dsuBr.setAlgorithmName("DSU");
            dsuBr.setDescription("DSU MakeSet & Union on " + size + " items with Path Compression & Union-By-Size");
            dsuBr.setDatasetSize(size);
            dsuBr.setExecutionTimeNanos(dsuTime);
            dsuBr.setOperations(size + (long) (size / 2));
            dsuBr.setDetails(Map.of(
                "elements", size,
                "unionsPerformed", size / 2,
                "resultingComponents", dsuBench.getComponentCount(),
                "totalTimeMs", dsuTime / 1_000_000.0,
                "amortizedTimePerOpNanos", (dsuTime) / (double) (size + (size / 2))
            ));

            harmonizationRepository.saveBenchmark(dsuBr);
            results.add(dsuBr);
        }

        return ResponseEntity.ok(results);
    }

    /**
     * GET /api/benchmarks/results
     */
    @GetMapping("/results")
    public ResponseEntity<List<BenchmarkResult>> getBenchmarkResults() {
        return ResponseEntity.ok(harmonizationRepository.findAllBenchmarks());
    }
}
