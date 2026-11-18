package harmonizer.matching;

/**
 * Outcome of comparing one attribute of two material records.
 *
 * <p>The three states exist because "we do not know" is not the same as "they agree". Before this
 * distinction the matcher treated a missing value as compatible, which let a record with no
 * attributes act as a bridge between two conflicting materials
 * (carbon steel 6 inch — unknown — stainless steel 4 inch). See {@link AttributeComparison} and
 * {@code docs/INTEGRATION.md}.
 */
public enum AttributeState {

    /** Both records state the attribute and the values agree (dimensions within tolerance). */
    MATCH,

    /** Both records state the attribute and the values disagree: the pair can never be equivalent. */
    CONFLICT,

    /** At least one record does not state the attribute: no evidence either way. */
    UNKNOWN;

    public boolean isMatch() {
        return this == MATCH;
    }

    public boolean isConflict() {
        return this == CONFLICT;
    }
}
