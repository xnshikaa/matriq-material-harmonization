package harmonizer.matching;

/**
 * Evidence that both records of a candidate pair contain the same technical specification.
 *
 * <p>Positions are zero-based indices into each original description, and the comparison counts come
 * from the KMP run that found them, so the reviewer can see <em>where</em> and <em>at what cost</em>
 * the specification was located.
 *
 * @param specification canonical specification both records mention
 * @param positionInA   first zero-based occurrence in record A
 * @param positionInB   first zero-based occurrence in record B
 * @param comparisonsA  characters compared while searching record A
 * @param comparisonsB  characters compared while searching record B
 */
public record SpecEvidence(String specification, int positionInA, int positionInB,
                           long comparisonsA, long comparisonsB) {
}
