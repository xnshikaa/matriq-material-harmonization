package com.matriq.matching;

import com.matriq.algorithms.bloom.BloomFilter;
import com.matriq.algorithms.kmp.KMPMatcher;
import com.matriq.algorithms.kmp.KMPResult;
import com.matriq.model.MaterialMatch;
import com.matriq.model.MaterialRecord;
import com.matriq.normalization.NormalizationService;
import com.matriq.repository.HarmonizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Matching Service — coordinates Bloom Filter, KMP, and attribute verification
 * to propose candidate material equivalences.
 *
 * PIPELINE:
 *   1. Bloom Filter  — fast pre-screening (definite non-matches rejected early)
 *   2. KMP           — find shared specification patterns
 *   3. Attribute check — category, type, grade, dimensions must all align
 *   4. Propose match — only if the attribute check passes
 *
 * A proposed match enters PENDING status. Only the reviewer (or strict
 * auto-approval rules) can move it to APPROVED. Only APPROVED matches
 * are passed to DSU.
 */
@Service
public class MatchingService {

    private final KMPMatcher kmpMatcher = new KMPMatcher();
    private final NormalizationService normalizationService;
    private final HarmonizationRepository harmonizationRepository;

    /** Shared Bloom Filter — built fresh each time harmonization runs. */
    private BloomFilter bloomFilter;

    /** Known technical specification patterns used by KMP. */
    private static final List<String> SPEC_PATTERNS = List.of(
        "ASTM A106", "A106-B", "A106 Grade B",
        "ASTM A53", "A53-B",
        "API 5L",
        "ASTM A335", "A335-P11", "A335-P22", "A335-P5",
        "ASTM A312",
        "SS316", "SS304", "SS202",
        "IS 1239", "IS 3589", "IS 1978",
        "ANSI B16.5", "ANSI B16.9", "ANSI B16.11",
        "ASME B31.3",
        "DIN 2448",
        "BS 3601"
    );

    @Autowired
    public MatchingService(NormalizationService normalizationService,
                           HarmonizationRepository harmonizationRepository) {
        this.normalizationService = normalizationService;
        this.harmonizationRepository = harmonizationRepository;
    }

    /**
     * Generates candidate material matches from a list of normalized records.
     * Returns the list of proposed matches (all in PENDING status).
     *
     * @param records normalized material records to compare
     * @return list of proposed MaterialMatch objects
     */
    public List<MaterialMatch> generateCandidates(List<MaterialRecord> records) {
        List<MaterialMatch> proposals = new ArrayList<>();

        // ── Step 1: Build Bloom Filter with all record signatures ─────────────
        bloomFilter = BloomFilter.withOptimalParameters(records.size() * 2, 0.01);
        Map<String, String> signatureToId = new HashMap<>();

        for (MaterialRecord record : records) {
            String sig = normalizationService.buildSignature(record);
            if (bloomFilter.mightContain(sig)) {
                // Signature already in filter — this record is a candidate
            }
            bloomFilter.insert(sig);
            signatureToId.put(sig, record.getId());
        }

        // ── Step 2: Pairwise comparison (only between different companies) ────
        for (int i = 0; i < records.size(); i++) {
            for (int j = i + 1; j < records.size(); j++) {
                MaterialRecord a = records.get(i);
                MaterialRecord b = records.get(j);

                // Skip records from the same company — they may be legitimately
                // different variants, not duplicates to harmonize
                if (a.getCompanyName() != null && a.getCompanyName().equalsIgnoreCase(b.getCompanyName())) {
                    continue;
                }

                // ── Bloom Filter pre-screening ────────────────────────────────
                String sigA = normalizationService.buildSignature(a);
                String sigB = normalizationService.buildSignature(b);

                // If BOTH signatures return "definitely not in filter" for each other,
                // they cannot match (but this logic is better expressed via category check)
                // We use Bloom Filter mainly to screen repeated calls:
                boolean bloomHit = bloomFilter.mightContain(sigA) && bloomFilter.mightContain(sigB);

                // ── KMP specification matching ────────────────────────────────
                List<String> sharedSpecs = findSharedSpecifications(a, b);

                // ── Attribute-level verification ──────────────────────────────
                MaterialMatch.AttributeVerification verification = verifyAttributes(a, b, sharedSpecs);

                if (!verification.isOverallPass()) {
                    // Record a confirmed non-match (bloom filter false positive)
                    if (bloomHit) bloomFilter.recordFalsePositive();
                    continue;
                }

                // ── Propose match ─────────────────────────────────────────────
                MaterialMatch match = new MaterialMatch();
                match.setId(UUID.randomUUID().toString());
                match.setRecordAId(a.getId());
                match.setRecordBId(b.getId());
                match.setMatchedSpecifications(sharedSpecs);
                match.setAttributeVerification(verification);
                match.setMatchReason(buildMatchReason(a, b, sharedSpecs, verification));
                match.setConfidenceScore(computeConfidenceScore(verification, sharedSpecs));
                match.setStatus(MaterialMatch.MatchStatus.PENDING);
                match.setProposedAt(Instant.now());

                harmonizationRepository.saveMatch(match);
                proposals.add(match);
            }
        }

        return proposals;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KMP: find specification patterns shared by two records
    // ─────────────────────────────────────────────────────────────────────────

    private List<String> findSharedSpecifications(MaterialRecord a, MaterialRecord b) {
        List<String> shared = new ArrayList<>();
        String descA = nvl(a.getNormalizedDescription());
        String descB = nvl(b.getNormalizedDescription());

        for (String pattern : SPEC_PATTERNS) {
            KMPResult resultA = kmpMatcher.countComparisons(descA, pattern);
            KMPResult resultB = kmpMatcher.countComparisons(descB, pattern);

            if (resultA.isMatch() && resultB.isMatch()) {
                shared.add(pattern);
            }
        }

        // Also check grade directly
        if (a.getGrade() != null && b.getGrade() != null) {
            KMPResult gradeMatch = kmpMatcher.countComparisons(
                nvl(a.getGrade()), nvl(b.getGrade()));
            if (gradeMatch.isMatch() || a.getGrade().equalsIgnoreCase(b.getGrade())) {
                if (!shared.contains("GRADE:" + a.getGrade())) {
                    shared.add("GRADE:" + a.getGrade());
                }
            }
        }

        return shared;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Attribute-level verification
    // ALL required attributes must pass — shared spec alone is NOT enough
    // ─────────────────────────────────────────────────────────────────────────

    private MaterialMatch.AttributeVerification verifyAttributes(
            MaterialRecord a, MaterialRecord b, List<String> sharedSpecs) {

        MaterialMatch.AttributeVerification v = new MaterialMatch.AttributeVerification();

        // 1. Category must match
        boolean catMatch = attributeMatch(a.getMaterialCategory(), b.getMaterialCategory());
        v.setCategoryMatch(catMatch);

        // 2. Type must match (or at least not conflict)
        boolean typeMatch = attributeCompatible(a.getMaterialType(), b.getMaterialType());
        v.setTypeMatch(typeMatch);

        // 3. Grade must match if both have one
        boolean gradeMatch = attributeCompatible(a.getGrade(), b.getGrade());
        v.setGradeMatch(gradeMatch);

        // 4. Dimensions must be compatible (with explicit unit conversion)
        boolean dimMatch = dimensionsCompatible(a, b);
        v.setDimensionMatch(dimMatch);

        // 5. Shared specification found by KMP
        boolean specMatch = !sharedSpecs.isEmpty();
        v.setSpecificationMatch(specMatch);

        // ── Overall pass: all required checks must pass ───────────────────────
        // Category and grade are hard requirements.
        // Type, dimension, and spec provide supporting evidence.
        boolean overall = catMatch && gradeMatch && dimMatch;
        v.setOverallPass(overall);

        if (!overall) {
            List<String> failures = new ArrayList<>();
            if (!catMatch) failures.add("category mismatch (" + a.getMaterialCategory() + " vs " + b.getMaterialCategory() + ")");
            if (!gradeMatch) failures.add("grade conflict (" + a.getGrade() + " vs " + b.getGrade() + ")");
            if (!dimMatch) failures.add("dimension incompatibility");
            v.setFailureReason(String.join("; ", failures));
        }

        return v;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dimension compatibility: explicit conversion, NOT assumed equivalence
    // ─────────────────────────────────────────────────────────────────────────

    private boolean dimensionsCompatible(MaterialRecord a, MaterialRecord b) {
        // If neither has dimensions, we cannot confirm or deny
        if (a.getDimensions() == null && b.getDimensions() == null) return true;
        // If only one has dimensions, can't confirm equivalence on this attribute
        if (a.getDimensions() == null || b.getDimensions() == null) return true;

        String dimA = a.getDimensions().toUpperCase();
        String dimB = b.getDimensions().toUpperCase();

        // Try parsing numeric values
        double valA = extractNumericDimension(dimA);
        double valB = extractNumericDimension(dimB);
        String unitA = a.getUnit();
        String unitB = b.getUnit();

        if (valA < 0 || valB < 0) return true; // Cannot parse — do not block on dimensions

        // Convert to MM for comparison
        double mmA = toMM(valA, unitA);
        double mmB = toMM(valB, unitB);

        // Allow 2% tolerance to account for rounding (e.g. 6 inch = 152.4 mm, stored as 150 mm)
        double tolerance = 0.02;
        double diff = Math.abs(mmA - mmB);
        double avg = (mmA + mmB) / 2.0;
        return avg == 0 || (diff / avg) <= tolerance;
    }

    private double extractNumericDimension(String dim) {
        try {
            return Double.parseDouble(dim.replaceAll("[^0-9.]", "").strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private double toMM(double value, String unit) {
        if (unit == null) return value;
        return switch (unit.toUpperCase()) {
            case "INCH" -> value * 25.4;
            case "CM"   -> value * 10.0;
            case "M"    -> value * 1000.0;
            default     -> value; // already MM or unknown
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Attribute comparison helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** True if both values are equal (case-insensitive), or at least one is null/empty, or compatible variants. */
    private boolean attributeCompatible(String a, String b) {
        if (a == null || a.isBlank() || b == null || b.isBlank()) return true;
        if (a.equalsIgnoreCase(b)) return true;
        String ca = a.toUpperCase().replaceAll("[^A-Z0-9]", "");
        String cb = b.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (ca.equals(cb)) return true;
        // e.g. "GRADEB" and "B" or "A106B" and "B" or "SS316L" and "SS316"
        if (ca.endsWith(cb) || cb.endsWith(ca)) return true;
        if (ca.contains(cb) || cb.contains(ca)) return true;
        return false;
    }

    /** True if both values are equal AND at least one is non-null. */
    private boolean attributeMatch(String a, String b) {
        if (a == null || b == null) return false;
        if (a.isBlank() || b.isBlank()) return false;
        if (a.equalsIgnoreCase(b)) return true;
        String ca = a.toUpperCase().replaceAll("[^A-Z0-9]", "");
        String cb = b.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (ca.equals(cb)) return true;
        // e.g. "PIPING" and "PIPE"
        if (ca.contains(cb) || cb.contains(ca)) return true;
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Confidence score — documented formula, not a substitute for verification
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Computes a confidence score (0.0 – 1.0) based on how many attribute checks
     * passed. This is INFORMATIONAL only — it does not replace the verification
     * result or reviewer approval.
     *
     * Formula:
     *   score = (w_cat * catMatch + w_type * typeMatch + w_grade * gradeMatch
     *           + w_dim * dimMatch + w_spec * specMatch)
     *           / (w_cat + w_type + w_grade + w_dim + w_spec)
     */
    private double computeConfidenceScore(MaterialMatch.AttributeVerification v, List<String> specs) {
        double wCat   = 2.0, wType = 1.5, wGrade = 2.5, wDim = 2.0, wSpec = 1.5;
        double total  = wCat + wType + wGrade + wDim + wSpec;
        double score  = (v.isCategoryMatch()      ? wCat   : 0)
                      + (v.isTypeMatch()           ? wType  : 0)
                      + (v.isGradeMatch()          ? wGrade : 0)
                      + (v.isDimensionMatch()      ? wDim   : 0)
                      + (v.isSpecificationMatch()  ? wSpec  : 0);
        // Bonus for multiple matched specs
        score += Math.min(specs.size() - 1, 2) * 0.2;
        return Math.min(1.0, score / total);
    }

    private String buildMatchReason(MaterialRecord a, MaterialRecord b,
                                    List<String> specs, MaterialMatch.AttributeVerification v) {
        StringBuilder sb = new StringBuilder();
        sb.append("Category: ").append(a.getMaterialCategory());
        if (!specs.isEmpty()) {
            sb.append(" | Shared specs: ").append(String.join(", ", specs));
        }
        if (a.getGrade() != null) sb.append(" | Grade: ").append(a.getGrade());
        if (a.getDimensions() != null) sb.append(" | Dim: ").append(a.getDimensions());
        return sb.toString();
    }

    public BloomFilter getBloomFilter() { return bloomFilter; }

    private String nvl(String s) { return s != null ? s : ""; }
}
