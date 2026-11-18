package harmonizer.matching;

import harmonizer.model.MaterialRecord;
import harmonizer.normalize.TextNormalizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The material verification service: decides which candidate pairs are genuinely equivalent.
 *
 * <p>Pipeline used by MATRIQ:
 * <ol>
 *   <li>Extract attributes for every record (normaliser + KMP specification extraction).</li>
 *   <li>Form candidate pairs only inside the same item-type block (pipe with pipe, valve with valve).</li>
 *   <li>Run KMP on both descriptions to collect shared specification evidence.</li>
 *   <li>Compare every attribute into three states — MATCH, CONFLICT or UNKNOWN —
 *       see {@link AttributeComparison}.</li>
 *   <li>Verify an edge only when nothing conflicts and at least
 *       {@link AttributeStates#MIN_CRITICAL_MATCHES} identity-defining attributes are positively
 *       matched. A record with no metal/grade/size cannot be an equivalence bridge on the strength of
 *       a shared specification alone.</li>
 * </ol>
 *
 * <p>A shared specification is <b>never</b> enough on its own: "ASTM A106 Gr.B" and "ASTM A106 Gr.C"
 * share a specification and are still different materials, so the grade state is CONFLICT.
 *
 * <p>Verified edges can still form a contradictory component through an unknown-attribute bridge.
 * That is handled one stage later by {@code MaterialGroupingService}, which validates each DSU
 * component and splits it into conflict-free groups.
 *
 * <p>Complexity: O(B^2) candidate pairs in the largest item-type block B, and O(P * (n + m)) KMP work
 * per record. Datasets of a few thousand records are fine; for millions the blocking key would have
 * to include size and grade ranges (stated as a limitation in docs/KMP.md).
 */
public final class EquivalenceMatcher {

    /**
     * @param candidatePairs      pairs that were compared (same item type)
     * @param kmpComparisons      character comparisons spent locating specifications in all records
     * @param verified            pairs with no conflict and sufficient positive evidence
     * @param rejected            compared pairs that failed, with the reason and the states
     * @param attributesByRecord  extracted attributes, reused by the grouping stage's validation
     */
    public record Outcome(int candidatePairs, long kmpComparisons,
                          List<VerifiedEdge> verified, List<MatchEvidence> rejected,
                          Map<Integer, MaterialAttributes> attributesByRecord) {

        public Outcome {
            verified = List.copyOf(verified);
            rejected = List.copyOf(rejected);
            attributesByRecord = Map.copyOf(attributesByRecord);
        }
    }

    private final AttributeExtractor attributes;
    private final KmpSpecificationExtractor specifications;

    public EquivalenceMatcher() {
        KmpSpecificationExtractor extractor = new KmpSpecificationExtractor();
        this.specifications = extractor;
        this.attributes = new AttributeExtractor(TextNormalizer.withDefaults(), extractor);
    }

    public EquivalenceMatcher(AttributeExtractor attributes, KmpSpecificationExtractor specifications) {
        this.attributes = Objects.requireNonNull(attributes, "attributes");
        this.specifications = Objects.requireNonNull(specifications, "specifications");
    }

    /** Compares a dataset and returns verified relationships plus the evidence for everyone. */
    public Outcome match(List<MaterialRecord> records) {
        if (records == null || records.isEmpty()) {
            return new Outcome(0, 0L, List.of(), List.of(), Map.of());
        }

        List<MaterialRecord> usable = new ArrayList<>(records.size());
        for (MaterialRecord record : records) {
            if (record != null) {
                usable.add(record);
            }
        }
        if (usable.size() < 2) {
            return new Outcome(0, 0L, List.of(), List.of(), Map.of());
        }

        Map<Integer, MaterialAttributes> attributesById = new LinkedHashMap<>();
        Map<Integer, List<SpecHit>> hitsById = new LinkedHashMap<>();
        long kmpComparisons = 0L;

        for (MaterialRecord record : usable) {
            attributesById.put(record.id(), attributes.extract(record));
            List<SpecHit> hits = specifications.extract(record.description());
            hitsById.put(record.id(), hits);
            kmpComparisons += KmpSpecificationExtractor.totalComparisons(hits);
        }

        List<VerifiedEdge> verified = new ArrayList<>();
        List<MatchEvidence> rejected = new ArrayList<>();
        int candidatePairs = 0;

        for (int i = 0; i < usable.size(); i++) {
            MaterialRecord a = usable.get(i);
            MaterialAttributes attributesA = attributesById.get(a.id());
            if (attributesA.itemType() == null) {
                continue; // not a catalogued item type -> cannot be compared to anything
            }
            for (int j = i + 1; j < usable.size(); j++) {
                MaterialRecord b = usable.get(j);
                MaterialAttributes attributesB = attributesById.get(b.id());
                if (!attributesA.itemType().equals(attributesB.itemType())) {
                    continue; // different catalogue block (pipe vs valve, ...)
                }
                candidatePairs++;
                MatchEvidence evidence = evaluate(a.id(), b.id(), attributesA, attributesB,
                        hitsById.get(a.id()), hitsById.get(b.id()));
                if (evidence.verified()) {
                    verified.add(new VerifiedEdge(a.id(), b.id(), evidence));
                } else {
                    rejected.add(evidence);
                }
            }
        }

        return new Outcome(candidatePairs, kmpComparisons,
                List.copyOf(verified), List.copyOf(rejected), attributesById);
    }

    // ------------------------------------------------------------------ verification

    private MatchEvidence evaluate(int recordA, int recordB,
                                   MaterialAttributes a, MaterialAttributes b,
                                   List<SpecHit> hitsA, List<SpecHit> hitsB) {

        List<SpecEvidence> shared = sharedSpecifications(hitsA, hitsB);
        AttributeStates states = AttributeComparison.compare(a, b, shared);

        List<String> failures = new ArrayList<>();
        if (states.type().isConflict()) {
            failures.add("material type differs (" + a.itemType() + " vs " + b.itemType() + ")");
        }
        if (states.form().isConflict()) {
            failures.add("form differs (" + a.form() + " vs " + b.form() + ")");
        }
        if (states.metal().isConflict()) {
            failures.add("metal differs (" + a.metal() + " vs " + b.metal() + ")");
        }
        if (states.grade().isConflict()) {
            failures.add("grade conflict (" + a.grade() + " vs " + b.grade() + ")");
        }
        if (states.dimensions().isConflict()) {
            failures.add("dimension mismatch (" + a.sizeMillimetres() + " mm vs "
                    + b.sizeMillimetres() + " mm)");
        }
        if (states.specification().isConflict()) {
            failures.add("specification conflict (" + a.specification() + " vs "
                    + b.specification() + ")");
        }
        if (!states.hasConflict() && !states.sufficientEvidence()) {
            failures.add("insufficient evidence for equivalence: " + states.evidenceSummary());
        }

        // Backward-compatible boolean view: true means "this attribute does not conflict".
        AttributeChecks checks = new AttributeChecks(
                !states.type().isConflict(),
                !states.form().isConflict(),
                !states.metal().isConflict(),
                !states.grade().isConflict(),
                !states.dimensions().isConflict(),
                !states.specification().isConflict());

        return new MatchEvidence(recordA, recordB, shared, checks, states, a, b,
                String.join("; ", failures));
    }

    /** Specifications KMP found in both descriptions. */
    private static List<SpecEvidence> sharedSpecifications(List<SpecHit> hitsA, List<SpecHit> hitsB) {
        List<SpecEvidence> shared = new ArrayList<>();
        for (SpecHit hitA : hitsA) {
            for (SpecHit hitB : hitsB) {
                if (!hitA.specification().equals(hitB.specification())) {
                    continue;
                }
                boolean alreadyRecorded = false;
                for (SpecEvidence existing : shared) {
                    if (existing.specification().equals(hitA.specification())) {
                        alreadyRecorded = true;
                        break;
                    }
                }
                if (!alreadyRecorded) {
                    shared.add(new SpecEvidence(hitA.specification(),
                            hitA.firstPosition(), hitB.firstPosition(),
                            hitA.comparisons(), hitB.comparisons()));
                }
            }
        }
        return List.copyOf(shared);
    }
}
