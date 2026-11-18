package harmonizer.matching;

/**
 * A relationship the matching service verified as "same physical material".
 *
 * <p>Only these edges are handed to the disjoint-set union. Rejected candidates stay in the report
 * as {@link MatchEvidence} so the decision can be explained, but they never reach the DSU.
 *
 * @param recordA  id of the first record
 * @param recordB  id of the second record
 * @param evidence the evidence that justified the relationship
 */
public record VerifiedEdge(int recordA, int recordB, MatchEvidence evidence) {
}
