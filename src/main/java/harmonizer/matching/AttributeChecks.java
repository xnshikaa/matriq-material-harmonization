package harmonizer.matching;

/**
 * Which attribute checks passed for one candidate pair.
 *
 * <p>Every check is a hard requirement before a pair becomes a verified equivalence: sharing a
 * technical specification on its own never merges two materials.
 */
public record AttributeChecks(boolean type, boolean form, boolean metal,
                              boolean grade, boolean dimensions, boolean specification) {

    /** True only when every attribute check passed. */
    public boolean allPass() {
        return type && form && metal && grade && dimensions && specification;
    }
}
