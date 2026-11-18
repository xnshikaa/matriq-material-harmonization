package harmonizer.harmonization;

import harmonizer.matching.EquivalenceMatcher;
import harmonizer.matching.MatchEvidence;
import harmonizer.matching.MaterialAttributes;
import harmonizer.matching.VerifiedEdge;
import harmonizer.model.MaterialRecord;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The harmonization pipeline: matching in, groups out.
 *
 * <p>{@link #run(List)} verifies relationships and groups the records. {@link #regroup} rebuilds the
 * groups from an already-approved edge list, which is how a rejected relationship is applied without
 * keeping a stale union.
 */
public final class HarmonizationPipeline {

    /**
     * Result of one pipeline run.
     *
     * @param totalRecords        records considered
     * @param candidatePairs      same-item-type pairs that were verified
     * @param verifiedPairs       pairs with no conflict and sufficient evidence (DSU edges)
     * @param rejectedPairs       pairs that were compared and failed, with evidence
     * @param suppressedPairs     verified pairs withheld because a reviewer rejected them earlier
     * @param splitComponents     DSU components that had to be split for containing contradictory attributes
     * @param kmpComparisons      character comparisons spent locating specifications
     * @param groupCount          material groups (including singletons)
     * @param groupedRecords      records that belong to a group with at least two members
     * @param verifiedEdges       relationships fed to the DSU (after any reviewer rejections)
     * @param rejectedCandidates  rejected pairs, kept for explanation
     * @param groups              material groups with national codes where approved
     * @param attributesByRecord  extracted attributes, reused when regrouping
     * @param elapsedMillis       wall-clock duration of this run
     */
    public record HarmonizationResult(int totalRecords, int candidatePairs, int verifiedPairs,
                                      int rejectedPairs, int suppressedPairs, int splitComponents,
                                      long kmpComparisons, int groupCount, int groupedRecords,
                                      List<VerifiedEdge> verifiedEdges,
                                      List<MatchEvidence> rejectedCandidates,
                                      List<MaterialGroup> groups,
                                      Map<Integer, MaterialAttributes> attributesByRecord,
                                      double elapsedMillis,
                                      List<VerifiedEdge> flaggedEdges,
                                      int reviewConstraintsCount) {

        public HarmonizationResult {
            verifiedEdges = List.copyOf(verifiedEdges);
            rejectedCandidates = List.copyOf(rejectedCandidates);
            groups = List.copyOf(groups);
            attributesByRecord = Map.copyOf(attributesByRecord);
            flaggedEdges = flaggedEdges == null ? List.of() : List.copyOf(flaggedEdges);
        }

        public HarmonizationResult(int totalRecords, int candidatePairs, int verifiedPairs,
                                   int rejectedPairs, int suppressedPairs, int splitComponents,
                                   long kmpComparisons, int groupCount, int groupedRecords,
                                   List<VerifiedEdge> verifiedEdges,
                                   List<MatchEvidence> rejectedCandidates,
                                   List<MaterialGroup> groups,
                                   Map<Integer, MaterialAttributes> attributesByRecord,
                                   double elapsedMillis) {
            this(totalRecords, candidatePairs, verifiedPairs, rejectedPairs, suppressedPairs,
                 splitComponents, kmpComparisons, groupCount, groupedRecords, verifiedEdges,
                 rejectedCandidates, groups, attributesByRecord, elapsedMillis, List.of(), 0);
        }

        /** Groups that carry a national material code (i.e. approved multi-record groups). */
        public List<MaterialGroup> nationalGroups() {
            return groups.stream().filter(MaterialGroup::hasNationalCode).toList();
        }

        /** Number of relationships flagged for manual review due to review constraints. */
        public int flaggedPairs() {
            return flaggedEdges.size();
        }
    }

    private final EquivalenceMatcher matcher;
    private final MaterialGroupingService grouping;

    public HarmonizationPipeline(EquivalenceMatcher matcher, MaterialGroupingService grouping) {
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.grouping = Objects.requireNonNull(grouping, "grouping");
    }

    /** Full run: extract specifications (KMP), verify attributes, then group verified pairs with DSU. */
    public HarmonizationResult run(List<MaterialRecord> records) {
        long start = System.nanoTime();
        List<MaterialRecord> dataset = records == null ? List.of() : List.copyOf(records);

        EquivalenceMatcher.Outcome outcome = matcher.match(dataset);
        MaterialGroupingService.GroupingOutcome grouped =
                grouping.groupDetailed(dataset, outcome.verified(), outcome.attributesByRecord());

        double elapsedMillis = (System.nanoTime() - start) / 1_000_000.0;
        return new HarmonizationResult(dataset.size(), outcome.candidatePairs(),
                outcome.verified().size(), outcome.rejected().size(), 0, grouped.splitComponents(),
                outcome.kmpComparisons(), grouped.groups().size(), groupedRecords(grouped.groups()),
                outcome.verified(), outcome.rejected(), grouped.groups(),
                outcome.attributesByRecord(), elapsedMillis);
    }

    /**
     * Recomputes the groups from the remaining approved relationships.
     *
     * <p>A standard DSU cannot remove an arbitrary union (path compression has already rewritten the
     * links), so the correct response to a rejected relationship is to rebuild: this method creates a
     * fresh structure from the approved edge list only.
     */
    public HarmonizationResult regroup(HarmonizationResult base, List<MaterialRecord> records,
                                       List<VerifiedEdge> approved) {
        return regroup(base, records, approved, base.suppressedPairs());
    }

    /**
     * @param suppressedPairs verified pairs withheld because a reviewer rejected them earlier
     */
    public HarmonizationResult regroup(HarmonizationResult base, List<MaterialRecord> records,
                                       List<VerifiedEdge> approved, int suppressedPairs) {
        return regroup(base, records, approved, suppressedPairs, List.of(), 0);
    }

    /**
     * Recomputes the groups from the remaining approved relationships, reporting flagged relationships
     * that conflict with review constraints.
     */
    public HarmonizationResult regroup(HarmonizationResult base, List<MaterialRecord> records,
                                       List<VerifiedEdge> approved, int suppressedPairs,
                                       List<VerifiedEdge> flaggedEdges, int reviewConstraintsCount) {
        long start = System.nanoTime();
        List<VerifiedEdge> edges = approved == null ? List.of() : List.copyOf(approved);

        MaterialGroupingService.GroupingOutcome grouped =
                grouping.groupDetailed(records, edges, base.attributesByRecord());
        double elapsedMillis = (System.nanoTime() - start) / 1_000_000.0;

        return new HarmonizationResult(base.totalRecords(), base.candidatePairs(), edges.size(),
                base.rejectedCandidates().size(), suppressedPairs, grouped.splitComponents(),
                base.kmpComparisons(), grouped.groups().size(), groupedRecords(grouped.groups()),
                edges, base.rejectedCandidates(), grouped.groups(), base.attributesByRecord(),
                elapsedMillis, flaggedEdges, reviewConstraintsCount);
    }

    private static int groupedRecords(List<MaterialGroup> groups) {
        int grouped = 0;
        for (MaterialGroup group : groups) {
            if (group.size() >= 2) {
                grouped += group.size();
            }
        }
        return grouped;
    }
}
