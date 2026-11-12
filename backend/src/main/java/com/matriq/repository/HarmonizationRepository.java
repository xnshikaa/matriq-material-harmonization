package com.matriq.repository;

import com.matriq.model.*;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repositories for harmonization domain objects.
 * Grouped in one file for simplicity — split if the project grows.
 */
@Repository
public class HarmonizationRepository {

    // ── Import Batches ────────────────────────────────────────────────────────
    private final Map<String, ImportBatch> batches = new ConcurrentHashMap<>();

    public void saveBatch(ImportBatch batch) { batches.put(batch.getId(), batch); }
    public Optional<ImportBatch> findBatchById(String id) { return Optional.ofNullable(batches.get(id)); }
    public List<ImportBatch> findAllBatches() { return new ArrayList<>(batches.values()); }

    // ── Material Matches ──────────────────────────────────────────────────────
    private final Map<String, MaterialMatch> matches = new ConcurrentHashMap<>();

    public void saveMatch(MaterialMatch match) { matches.put(match.getId(), match); }
    public Optional<MaterialMatch> findMatchById(String id) { return Optional.ofNullable(matches.get(id)); }
    public List<MaterialMatch> findAllMatches() { return new ArrayList<>(matches.values()); }

    public List<MaterialMatch> findPendingMatches() {
        return matches.values().stream()
            .filter(m -> MaterialMatch.MatchStatus.PENDING.equals(m.getStatus()))
            .collect(Collectors.toList());
    }

    public List<MaterialMatch> findApprovedMatches() {
        return matches.values().stream()
            .filter(m -> MaterialMatch.MatchStatus.APPROVED.equals(m.getStatus())
                      || MaterialMatch.MatchStatus.AUTO_APPROVED.equals(m.getStatus()))
            .collect(Collectors.toList());
    }

    // ── Material Groups ───────────────────────────────────────────────────────
    private final Map<String, MaterialGroup> groups = new ConcurrentHashMap<>();

    public void saveGroup(MaterialGroup group) { groups.put(group.getId(), group); }
    public Optional<MaterialGroup> findGroupById(String id) { return Optional.ofNullable(groups.get(id)); }
    public List<MaterialGroup> findAllGroups() { return new ArrayList<>(groups.values()); }

    public void clearGroups() { groups.clear(); }

    // ── National Material Codes ───────────────────────────────────────────────
    private final Map<String, NationalMaterialCode> nationalCodes = new ConcurrentHashMap<>();

    public void saveNationalCode(NationalMaterialCode code) { nationalCodes.put(code.getCode(), code); }
    public Optional<NationalMaterialCode> findNationalCodeByCode(String code) {
        return Optional.ofNullable(nationalCodes.get(code));
    }
    public List<NationalMaterialCode> findAllNationalCodes() { return new ArrayList<>(nationalCodes.values()); }
    public long nextNationalCodeSequence() { return nationalCodes.size() + 1; }

    // ── Benchmark Results ─────────────────────────────────────────────────────
    private final List<BenchmarkResult> benchmarks = Collections.synchronizedList(new ArrayList<>());

    public void saveBenchmark(BenchmarkResult result) { benchmarks.add(result); }
    public List<BenchmarkResult> findAllBenchmarks() { return new ArrayList<>(benchmarks); }
    public void clearBenchmarks() { benchmarks.clear(); }
}
