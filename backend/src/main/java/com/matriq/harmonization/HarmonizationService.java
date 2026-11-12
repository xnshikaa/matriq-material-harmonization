package com.matriq.harmonization;

import com.matriq.algorithms.dsu.DisjointSetUnion;
import com.matriq.matching.MatchingService;
import com.matriq.model.*;
import com.matriq.normalization.NormalizationService;
import com.matriq.repository.HarmonizationRepository;
import com.matriq.repository.MaterialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * HARMONIZATION SERVICE — Central Orchestrator
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Coordinates the full pipeline:
 *
 *   Import → Normalize → Bloom Filter → Trie lookup → KMP matching
 *   → Attribute verification → Verified edges → DSU grouping
 *   → National code assignment → Review workflow
 *
 * The DSU is the final grouping step — it only receives edges that have
 * passed attribute verification AND are in APPROVED status.
 */
@Service
public class HarmonizationService {

    private final MaterialRepository materialRepository;
    private final HarmonizationRepository harmonizationRepository;
    private final NormalizationService normalizationService;
    private final MatchingService matchingService;

    /** DSU instance — rebuilt on each harmonization run. */
    private DisjointSetUnion dsu = new DisjointSetUnion();

    @Autowired
    public HarmonizationService(MaterialRepository materialRepository,
                                 HarmonizationRepository harmonizationRepository,
                                 NormalizationService normalizationService,
                                 MatchingService matchingService) {
        this.materialRepository = materialRepository;
        this.harmonizationRepository = harmonizationRepository;
        this.normalizationService = normalizationService;
        this.matchingService = matchingService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Run harmonization pipeline
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs the complete harmonization pipeline on all imported records.
     *
     * Steps:
     *  1. Normalize all records.
     *  2. Generate candidate matches (Bloom + KMP + attribute verification).
     *  3. Auto-approve high-confidence matches where all verifications pass.
     *  4. Build DSU from approved edges.
     *  5. Build MaterialGroup objects.
     *  6. Assign national codes to approved groups.
     *
     * @return summary of the run
     */
    public HarmonizationResult runPipeline() {
        List<MaterialRecord> records = materialRepository.findAll();
        HarmonizationResult result = new HarmonizationResult();
        result.setTotalRecords(records.size());

        // ── Step 1: Normalize ──────────────────────────────────────────────────
        records.forEach(normalizationService::normalize);
        records.forEach(materialRepository::save);
        result.setNormalizedRecords(records.size());

        // ── Step 2: Generate candidate matches ────────────────────────────────
        List<MaterialMatch> candidates = matchingService.generateCandidates(records);
        result.setCandidateMatches(candidates.size());

        // ── Step 3: Auto-approve strong matches ───────────────────────────────
        int autoApproved = 0;
        for (MaterialMatch match : candidates) {
            if (match.getAttributeVerification() != null
                && match.getAttributeVerification().isOverallPass()
                && match.getConfidenceScore() >= 0.80) {
                match.setStatus(MaterialMatch.MatchStatus.AUTO_APPROVED);
                harmonizationRepository.saveMatch(match);
                autoApproved++;
            }
        }
        result.setAutoApprovedMatches(autoApproved);

        // ── Step 4: Build DSU from approved edges ─────────────────────────────
        rebuildDSUFromApprovedEdges();
        result.setGroupsFormed(harmonizationRepository.findAllGroups().size());

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DSU: rebuild from approved edges
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Rebuilds the DSU from scratch using only APPROVED matches.
     * Called after any review decision (approve or reject) to ensure
     * the DSU state is always consistent with the approval set.
     *
     * This handles rejection correctly: standard DSU cannot undo a union,
     * so we always rebuild from the full approved edge set.
     */
    public void rebuildDSUFromApprovedEdges() {
        dsu.reset();

        // Register every imported record as a singleton set
        List<MaterialRecord> records = materialRepository.findAll();
        records.forEach(r -> dsu.makeSet(r.getId()));

        // Union only approved matches
        List<MaterialMatch> approved = harmonizationRepository.findApprovedMatches();
        for (MaterialMatch match : approved) {
            if (dsu.isRegistered(match.getRecordAId()) && dsu.isRegistered(match.getRecordBId())) {
                dsu.union(match.getRecordAId(), match.getRecordBId());
            }
        }

        // Build MaterialGroup objects from DSU groups
        harmonizationRepository.clearGroups();
        Map<String, List<String>> groups = dsu.getGroups();

        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            MaterialGroup group = new MaterialGroup();
            group.setId(entry.getKey());
            group.setMemberIds(entry.getValue());
            group.setDescription(buildGroupDescription(entry.getValue()));

            // If the group already has a national code, preserve it
            harmonizationRepository.findGroupById(entry.getKey())
                .ifPresent(existing -> group.setNationalCode(existing.getNationalCode()));

            harmonizationRepository.saveGroup(group);
        }

        // Update group IDs on material records
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            for (String memberId : entry.getValue()) {
                materialRepository.findById(memberId).ifPresent(r -> {
                    r.setGroupId(entry.getKey());
                    materialRepository.save(r);
                });
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Approve / Reject a pending match
    // ─────────────────────────────────────────────────────────────────────────

    public MaterialMatch approveMatch(String matchId, String reviewedBy, String note) {
        MaterialMatch match = harmonizationRepository.findMatchById(matchId)
            .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

        match.setStatus(MaterialMatch.MatchStatus.APPROVED);
        match.setReviewedBy(reviewedBy);
        match.setReviewNote(note);
        match.setReviewedAt(java.time.Instant.now());
        harmonizationRepository.saveMatch(match);

        // Rebuild DSU to incorporate new approval
        rebuildDSUFromApprovedEdges();
        return match;
    }

    public MaterialMatch rejectMatch(String matchId, String reviewedBy, String note) {
        MaterialMatch match = harmonizationRepository.findMatchById(matchId)
            .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

        match.setStatus(MaterialMatch.MatchStatus.REJECTED);
        match.setReviewedBy(reviewedBy);
        match.setReviewNote(note);
        match.setReviewedAt(java.time.Instant.now());
        harmonizationRepository.saveMatch(match);

        // Rebuild DSU without this edge — handles rejection correctly
        rebuildDSUFromApprovedEdges();
        return match;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Assign national codes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Assigns a national material code to each group that has ≥ 2 members.
     * National codes are deterministic — once issued they don't change.
     * Format: NMC-{YEAR}-{8-digit-sequence}
     */
    public List<NationalMaterialCode> assignNationalCodes() {
        List<NationalMaterialCode> issued = new ArrayList<>();
        List<MaterialGroup> groups = harmonizationRepository.findAllGroups();

        for (MaterialGroup group : groups) {
            // Skip singletons and groups that already have codes
            if (group.getMemberIds().size() < 2) continue;
            if (group.getNationalCode() != null) {
                issued.add(harmonizationRepository.findNationalCodeByCode(group.getNationalCode())
                    .orElse(null));
                continue;
            }

            long seq = harmonizationRepository.nextNationalCodeSequence() + issued.size();
            String code = String.format("NMC-%d-%08d", Year.now().getValue(), seq);

            NationalMaterialCode nmc = new NationalMaterialCode();
            nmc.setCode(code);
            nmc.setGroupId(group.getId());

            // Build company mappings (preserving all original codes)
            for (String memberId : group.getMemberIds()) {
                materialRepository.findById(memberId).ifPresent(record -> {
                    nmc.addCompanyMapping(new NationalMaterialCode.CompanyCodeMapping(
                        record.getCompanyName(),
                        record.getOriginalMaterialCode(),
                        record.getOriginalDescription()
                    ));
                    // Set national code on record — does NOT overwrite original code
                    record.setNationalMaterialCode(code);
                    record.setStatus(MaterialRecord.ImportStatus.GROUPED);
                    materialRepository.save(record);
                });
            }

            // Set canonical description from first member
            materialRepository.findById(group.getMemberIds().get(0)).ifPresent(r -> {
                nmc.setCanonicalDescription(r.getNormalizedDescription());
                nmc.setMaterialCategory(r.getMaterialCategory());
                nmc.setMaterialType(r.getMaterialType());
                nmc.setGrade(r.getGrade());
                nmc.setTechnicalSpecification(r.getTechnicalSpecification());
            });

            group.setNationalCode(code);
            group.setStatus(MaterialGroup.GroupStatus.APPROVED);
            group.setApprovedAt(java.time.Instant.now());
            harmonizationRepository.saveGroup(group);
            harmonizationRepository.saveNationalCode(nmc);
            issued.add(nmc);
        }

        return issued;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Network graph data for React Flow visualization
    // ─────────────────────────────────────────────────────────────────────────

    public Map<String, Object> getNetworkData() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        List<MaterialRecord> records = materialRepository.findAll();
        for (MaterialRecord r : records) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", r.getId());
            node.put("data", Map.of(
                "label", r.getOriginalMaterialCode(),
                "company", r.getCompanyName(),
                "category", nvl(r.getMaterialCategory()),
                "nationalCode", nvl(r.getNationalMaterialCode())
            ));
            node.put("type", "materialNode");
            nodes.add(node);
        }

        List<MaterialMatch> approved = harmonizationRepository.findApprovedMatches();
        for (MaterialMatch m : approved) {
            Map<String, Object> edge = new HashMap<>();
            edge.put("id", m.getId());
            edge.put("source", m.getRecordAId());
            edge.put("target", m.getRecordBId());
            edge.put("label", String.format("%.0f%%", m.getConfidenceScore() * 100));
            edge.put("type", "smoothstep");
            edges.add(edge);
        }

        return Map.of("nodes", nodes, "edges", edges);
    }

    private String buildGroupDescription(List<String> memberIds) {
        return materialRepository.findById(memberIds.get(0))
            .map(r -> r.getMaterialCategory() + " — " + memberIds.size() + " equivalent records")
            .orElse(memberIds.size() + " records");
    }

    public DisjointSetUnion getDsu() { return dsu; }

    private String nvl(String s) { return s != null ? s : ""; }

    /** Summary returned from runPipeline(). */
    public static class HarmonizationResult {
        private int totalRecords;
        private int normalizedRecords;
        private int candidateMatches;
        private int autoApprovedMatches;
        private int groupsFormed;

        public int getTotalRecords() { return totalRecords; }
        public void setTotalRecords(int v) { this.totalRecords = v; }
        public int getNormalizedRecords() { return normalizedRecords; }
        public void setNormalizedRecords(int v) { this.normalizedRecords = v; }
        public int getCandidateMatches() { return candidateMatches; }
        public void setCandidateMatches(int v) { this.candidateMatches = v; }
        public int getAutoApprovedMatches() { return autoApprovedMatches; }
        public void setAutoApprovedMatches(int v) { this.autoApprovedMatches = v; }
        public int getGroupsFormed() { return groupsFormed; }
        public void setGroupsFormed(int v) { this.groupsFormed = v; }
    }
}
