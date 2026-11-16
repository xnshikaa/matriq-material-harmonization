package harmonizer.screening;

/**
 * Two records confirmed (by exact comparison, not just the Bloom filter) to share a signature.
 * {@code recordId} is the later record; {@code matchedWithId} is the first record seen with that signature.
 * The group-building stage (disjoint-set union) consumes these as union(recordId, matchedWithId).
 */
public record MatchPair(int recordId, int matchedWithId,
                        String cpse, String code,
                        String matchedCpse, String matchedCode,
                        String signature, boolean crossCpse) {
}
