package harmonizer.screening;

/**
 * Numbers that make the Bloom filter's behaviour visible in the UI and the report.
 *
 * @param bloomDefinitelyNew          records the filter said "definitely not seen" (exact lookup skipped)
 * @param bloomMaybe                  records the filter said "possibly seen" (exact lookup required)
 * @param confirmedDuplicates         "maybe" answers the exact check confirmed
 * @param bloomFalsePositives         "maybe" answers the exact check rejected
 * @param measuredFalsePositiveRate   falsePositives / (records whose signature was truly new)
 * @param theoreticalFalsePositiveRate formula value (1 - e^(-kn/m))^k at the end of the run
 * @param naivePairwiseComparisons    n(n-1)/2, the cost of the compare-everything baseline
 */
public record ScreeningStats(int totalRecords,
                             int screenedRecords,
                             int bloomBits,
                             int bloomHashes,
                             double targetFalsePositiveRate,
                             int bloomDefinitelyNew,
                             int bloomMaybe,
                             int confirmedDuplicates,
                             int bloomFalsePositives,
                             double measuredFalsePositiveRate,
                             double theoreticalFalsePositiveRate,
                             long naivePairwiseComparisons,
                             double elapsedMillis) {
}
