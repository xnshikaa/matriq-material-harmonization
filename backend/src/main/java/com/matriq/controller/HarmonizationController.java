package com.matriq.controller;

import com.matriq.harmonization.HarmonizationService;
import com.matriq.model.*;
import com.matriq.repository.HarmonizationRepository;
import com.matriq.repository.MaterialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for harmonization pipeline, matching review, and national codes.
 */
@org.springframework.context.annotation.Profile("legacy-tooling")
@RestController
@RequestMapping("/api")
public class HarmonizationController {

    private final HarmonizationService harmonizationService;
    private final HarmonizationRepository harmonizationRepository;
    private final MaterialRepository materialRepository;

    @Autowired
    public HarmonizationController(HarmonizationService harmonizationService,
                                    HarmonizationRepository harmonizationRepository,
                                    MaterialRepository materialRepository) {
        this.harmonizationService = harmonizationService;
        this.harmonizationRepository = harmonizationRepository;
        this.materialRepository = materialRepository;
    }

    // ── Harmonization ────────────────────────────────────────────────────────

    /** POST /api/harmonization/run — run the full harmonization pipeline */
    @PostMapping("/harmonization/run")
    public ResponseEntity<?> runHarmonization() {
        HarmonizationService.HarmonizationResult result = harmonizationService.runPipeline();
        return ResponseEntity.ok(result);
    }

    /** GET /api/harmonization/results — pending + approved matches */
    @GetMapping("/harmonization/results")
    public ResponseEntity<?> getHarmonizationResults() {
        return ResponseEntity.ok(Map.of(
            "pending", harmonizationRepository.findPendingMatches().size(),
            "approved", harmonizationRepository.findApprovedMatches().size(),
            "total", harmonizationRepository.findAllMatches().size(),
            "groups", harmonizationRepository.findAllGroups().size()
        ));
    }

    /** GET /api/harmonization/groups — all material groups */
    @GetMapping("/harmonization/groups")
    public ResponseEntity<List<MaterialGroup>> getGroups() {
        return ResponseEntity.ok(harmonizationRepository.findAllGroups());
    }

    /** GET /api/harmonization/groups/{id} — specific group with its members */
    @GetMapping("/harmonization/groups/{id}")
    public ResponseEntity<?> getGroup(@PathVariable String id) {
        Optional<MaterialGroup> groupOpt = harmonizationRepository.findGroupById(id);
        if (groupOpt.isEmpty()) return ResponseEntity.notFound().build();

        MaterialGroup group = groupOpt.get();
        List<MaterialRecord> members = new ArrayList<>();
        for (String memberId : group.getMemberIds()) {
            materialRepository.findById(memberId).ifPresent(members::add);
        }

        return ResponseEntity.ok(Map.of("group", group, "members", members));
    }

    /** GET /api/harmonization/network — React Flow graph data */
    @GetMapping("/harmonization/network")
    public ResponseEntity<Map<String, Object>> getNetwork() {
        return ResponseEntity.ok(harmonizationService.getNetworkData());
    }

    // ── Matching / Review ────────────────────────────────────────────────────

    /** GET /api/matching/pending */
    @GetMapping("/matching/pending")
    public ResponseEntity<List<MaterialMatch>> getPendingMatches() {
        return ResponseEntity.ok(harmonizationRepository.findPendingMatches());
    }

    /** GET /api/matching — all matches */
    @GetMapping("/matching")
    public ResponseEntity<List<MaterialMatch>> getAllMatches() {
        return ResponseEntity.ok(harmonizationRepository.findAllMatches());
    }

    /** POST /api/matching/compare — manually compare two records */
    @PostMapping("/matching/compare")
    public ResponseEntity<?> compareTwo(@RequestBody Map<String, String> body) {
        String idA = body.get("recordAId");
        String idB = body.get("recordBId");

        Optional<com.matriq.model.MaterialRecord> a = materialRepository.findById(idA);
        Optional<com.matriq.model.MaterialRecord> b = materialRepository.findById(idB);

        if (a.isEmpty() || b.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "One or both record IDs not found."));
        }

        return ResponseEntity.ok(Map.of(
            "recordA", a.get(),
            "recordB", b.get(),
            "message", "Use POST /api/harmonization/run to run full matching pipeline."
        ));
    }

    /** POST /api/matching/{id}/approve */
    @PostMapping("/matching/{id}/approve")
    public ResponseEntity<?> approveMatch(@PathVariable String id,
                                           @RequestBody(required = false) Map<String, String> body) {
        String reviewer = body != null ? body.getOrDefault("reviewedBy", "Reviewer") : "Reviewer";
        String note     = body != null ? body.getOrDefault("note", "") : "";
        try {
            MaterialMatch match = harmonizationService.approveMatch(id, reviewer, note);
            return ResponseEntity.ok(match);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** POST /api/matching/{id}/reject */
    @PostMapping("/matching/{id}/reject")
    public ResponseEntity<?> rejectMatch(@PathVariable String id,
                                          @RequestBody(required = false) Map<String, String> body) {
        String reviewer = body != null ? body.getOrDefault("reviewedBy", "Reviewer") : "Reviewer";
        String note     = body != null ? body.getOrDefault("note", "") : "";
        try {
            MaterialMatch match = harmonizationService.rejectMatch(id, reviewer, note);
            return ResponseEntity.ok(match);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── National Codes ───────────────────────────────────────────────────────

    /** POST /api/national-codes/assign — assign national codes to approved groups */
    @PostMapping("/national-codes/assign")
    public ResponseEntity<?> assignNationalCodes() {
        List<NationalMaterialCode> codes = harmonizationService.assignNationalCodes();
        return ResponseEntity.ok(Map.of("assigned", codes.size(), "codes", codes));
    }

    /** GET /api/national-codes */
    @GetMapping("/national-codes")
    public ResponseEntity<List<NationalMaterialCode>> getNationalCodes() {
        return ResponseEntity.ok(harmonizationRepository.findAllNationalCodes());
    }

    /** GET /api/national-codes/{code} */
    @GetMapping("/national-codes/{code}")
    public ResponseEntity<?> getNationalCode(@PathVariable String code) {
        return harmonizationRepository.findNationalCodeByCode(code)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
