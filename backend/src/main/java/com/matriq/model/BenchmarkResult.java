package com.matriq.model;

import java.time.Instant;
import java.util.Map;

/**
 * Stores algorithm benchmark results for display in the Algorithm Lab.
 */
public class BenchmarkResult {

    private String id;
    private String algorithmName;    // KMP, DSU, BLOOM, TRIE
    private String description;
    private int datasetSize;
    private long executionTimeNanos;
    private long naiveExecutionTimeNanos; // for KMP comparison
    private long comparisons;         // character comparisons (KMP)
    private long operations;          // find/union ops (DSU)
    private Map<String, Object> details;
    private Instant recordedAt;

    public BenchmarkResult() {
        this.recordedAt = Instant.now();
    }

    // ── Getters and Setters ───────────────────────────────────────────────────
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAlgorithmName() { return algorithmName; }
    public void setAlgorithmName(String algorithmName) { this.algorithmName = algorithmName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getDatasetSize() { return datasetSize; }
    public void setDatasetSize(int datasetSize) { this.datasetSize = datasetSize; }

    public long getExecutionTimeNanos() { return executionTimeNanos; }
    public void setExecutionTimeNanos(long executionTimeNanos) { this.executionTimeNanos = executionTimeNanos; }

    public long getNaiveExecutionTimeNanos() { return naiveExecutionTimeNanos; }
    public void setNaiveExecutionTimeNanos(long nanos) { this.naiveExecutionTimeNanos = nanos; }

    public long getComparisons() { return comparisons; }
    public void setComparisons(long comparisons) { this.comparisons = comparisons; }

    public long getOperations() { return operations; }
    public void setOperations(long operations) { this.operations = operations; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
