package harmonizer.matching;

import java.util.List;

/**
 * Full explanation of why two material records were, or were not, treated as equivalent.
 *
 * <p>This is the evidence object that travels from the matching stage to the grouping stage and to
 * the API, so a reviewer can see exactly which specification patterns were found (and where), which
 * attributes were matched, unknown or conflicting, and why a pair was rejected.
 *
 * @param recordA              id of the first record
 * @param recordB              id of the second record
 * @param sharedSpecifications specifications found by KMP in both records
 * @param checks               backward-compatible view: {@code true} means "no conflict" per attribute
 * @param attributeStates      three-state view: MATCH, CONFLICT or UNKNOWN per attribute
 * @param attributesA          attributes extracted from record A
 * @param attributesB          attributes extracted from record B
 * @param rejectionReason      human-readable reasons (empty when the pair was verified)
 */
public record MatchEvidence(int recordA, int recordB,
                            List<SpecEvidence> sharedSpecifications,
                            AttributeChecks checks,
                            AttributeStates attributeStates,
                            MaterialAttributes attributesA,
                            MaterialAttributes attributesB,
                            String rejectionReason) {

    public MatchEvidence {
        sharedSpecifications = sharedSpecifications == null ? List.of() : List.copyOf(sharedSpecifications);
        rejectionReason = rejectionReason == null ? "" : rejectionReason;
    }

    /** True only when every check passed and nothing rejected the pair. */
    public boolean verified() {
        return rejectionReason.isEmpty() && checks.allPass();
    }

    /** True when the pair involves the given record id. */
    public boolean involves(int recordId) {
        return recordA == recordId || recordB == recordId;
    }
}
