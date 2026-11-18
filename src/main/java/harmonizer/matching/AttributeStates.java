package harmonizer.matching;

/**
 * Three-state comparison of every attribute of a candidate pair.
 *
 * <p>{@link #sufficientEvidence()} is the rule that decides whether an edge may be verified:
 * the item type must match, nothing may conflict, and at least
 * {@value #MIN_CRITICAL_MATCHES} of the four identity-defining attributes
 * (metal, grade, dimensions, specification) must be positively matched.
 *
 * <p>This is what stops a record that says only "Seamless pipe, ASTM A106" from being treated as
 * equivalent to anything: it shares the specification, but has no metal, grade or size, so the pair
 * has one critical match and is rejected as insufficient evidence.
 */
public record AttributeStates(AttributeState type,
                              AttributeState form,
                              AttributeState metal,
                              AttributeState grade,
                              AttributeState dimensions,
                              AttributeState specification) {

    /** Number of identity-defining attributes that must be positively matched. */
    public static final int MIN_CRITICAL_MATCHES = 2;

    /** Positive matches among the identity-defining attributes (metal, grade, dimensions, specification). */
    public int criticalMatches() {
        int matches = 0;
        if (metal.isMatch()) {
            matches++;
        }
        if (grade.isMatch()) {
            matches++;
        }
        if (dimensions.isMatch()) {
            matches++;
        }
        if (specification.isMatch()) {
            matches++;
        }
        return matches;
    }

    /** True when any attribute is stated on both sides and disagrees. */
    public boolean hasConflict() {
        return type.isConflict() || form.isConflict() || metal.isConflict() || grade.isConflict()
                || dimensions.isConflict() || specification.isConflict();
    }

    /** True when this pair carries enough positive evidence to become an equivalence edge. */
    public boolean sufficientEvidence() {
        return type.isMatch() && !hasConflict() && criticalMatches() >= MIN_CRITICAL_MATCHES;
    }

    /** Human-readable summary of the critical evidence, used in rejection reasons. */
    public String evidenceSummary() {
        return "critical matches=" + criticalMatches() + "/4 (metal=" + metal
                + ", grade=" + grade + ", dimensions=" + dimensions
                + ", specification=" + specification + ")";
    }
}
