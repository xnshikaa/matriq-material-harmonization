package harmonizer.harmonization;

import harmonizer.matching.EquivalenceMatcher;
import harmonizer.matching.VerifiedEdge;
import harmonizer.model.MaterialRecord;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Stateful façade around the harmonization pipeline, used by the REST API.
 *
 * <p>It remembers the last dataset, the reviewer's rejected relationships, component-level review
 * separation constraints, and the national code registry, so a rejection is not undone by subsequent
 * runs: every run filters the verified edges through {@link #rejectedRelationships} and
 * {@link #reviewConstraints} before grouping.
 *
 * <p>Rejections and review constraints are keyed by stable <em>material identities</em> ({@code CPSE|CODE}),
 * not by raw record ids, so re-uploading the same catalogues preserves reviewer decisions and fresh
 * datasets cannot silently bypass separation constraints with bridging records (DEF-3).
 *
 * <p>Nothing here mutates the uploaded records: original company codes live in {@link MaterialRecord}
 * and are copied into the groups unchanged.
 */
@Component
public class HarmonizationEngine {

    private final HarmonizationPipeline pipeline;

    private List<MaterialRecord> records = List.of();
    private final Set<String> rejectedRelationships = new LinkedHashSet<>();
    private final List<ReviewConstraint> reviewConstraints = new ArrayList<>();
    private HarmonizationPipeline.HarmonizationResult lastResult;

    public HarmonizationEngine() {
        this(new HarmonizationPipeline(new EquivalenceMatcher(),
                new MaterialGroupingService(new NationalCodeRegistry(Year.now().getValue()))));
    }

    public HarmonizationEngine(HarmonizationPipeline pipeline) {
        this.pipeline = pipeline;
    }

    /** Runs the full pipeline over a dataset, honouring previously rejected relationships and review constraints. */
    public synchronized HarmonizationPipeline.HarmonizationResult run(List<MaterialRecord> dataset) {
        this.records = dataset == null ? List.of() : List.copyOf(dataset);
        HarmonizationPipeline.HarmonizationResult full = pipeline.run(records);
        this.lastResult = applyRejectionsAndConstraints(full);
        return lastResult;
    }

    /**
     * Rejects the relationship between two records and rebuilds the groups from the remaining
     * relationships. If this rejection causes two components/groups to separate, an explicit
     * reviewer separation constraint is established to prevent bridging records from reconnecting them (DEF-3).
     *
     * @throws IllegalStateException    when no run has happened yet
     * @throws IllegalArgumentException when there is no verified relationship between the two records
     */
    public synchronized HarmonizationPipeline.HarmonizationResult reject(int recordA, int recordB) {
        if (lastResult == null) {
            throw new IllegalStateException("Run harmonization before rejecting a relationship.");
        }
        MaterialRecord a = requireRecord(recordA);
        MaterialRecord b = requireRecord(recordB);
        String key = pairKey(a, b);

        boolean isCurrentEdge = lastResult.verifiedEdges().stream()
                .anyMatch(edge -> isPair(edge, recordA, recordB));
        if (!isCurrentEdge) {
            if (rejectedRelationships.contains(key)) {
                return lastResult; // already rejected: idempotent
            }
            throw new IllegalArgumentException(
                    "No verified relationship between records " + recordA + " and " + recordB + ".");
        }

        rejectedRelationships.add(key);

        HarmonizationPipeline.HarmonizationResult full = pipeline.run(records);
        this.lastResult = applyRejectionsAndConstraints(full);

        // Check if rejecting this edge separated recordA and recordB into distinct groups
        MaterialGroup afterGroupA = groupContaining(lastResult.groups(), recordA);
        MaterialGroup afterGroupB = groupContaining(lastResult.groups(), recordB);

        if (afterGroupA != null && afterGroupB != null && !afterGroupA.id().equals(afterGroupB.id())) {
            Set<String> keysA = memberKeys(afterGroupA.members());
            Set<String> keysB = memberKeys(afterGroupB.members());
            if (!keysA.isEmpty() && !keysB.isEmpty()) {
                addReviewConstraint(new ReviewConstraint(keysA, keysB,
                        "Reviewer separated groups by rejecting relationship between "
                                + materialKey(a) + " and " + materialKey(b)));
                // Re-apply in case additional bridging edges need to be flagged against the new constraint
                this.lastResult = applyRejectionsAndConstraints(full);
            }
        }

        return lastResult;
    }

    /** Recomputes the groups from the relationships that are still approved. */
    public synchronized HarmonizationPipeline.HarmonizationResult regroup() {
        if (lastResult == null) {
            throw new IllegalStateException("Run harmonization before regrouping.");
        }
        HarmonizationPipeline.HarmonizationResult full = pipeline.run(records);
        this.lastResult = applyRejectionsAndConstraints(full);
        return lastResult;
    }

    /** The most recent result, if the pipeline has run at least once. */
    public synchronized Optional<HarmonizationPipeline.HarmonizationResult> lastResult() {
        return Optional.ofNullable(lastResult);
    }

    /** How many relationships a reviewer has rejected in this process. */
    public synchronized int rejectedRelationshipCount() {
        return rejectedRelationships.size();
    }

    /** All active reviewer separation constraints. */
    public synchronized List<ReviewConstraint> reviewConstraints() {
        return List.copyOf(reviewConstraints);
    }

    /** How many reviewer separation constraints are active. */
    public synchronized int reviewConstraintCount() {
        return reviewConstraints.size();
    }

    /** Explicitly introduces a reviewer separation constraint between two sets of material keys (DEF-3). */
    public synchronized void addReviewConstraint(ReviewConstraint constraint) {
        if (constraint != null && !reviewConstraints.contains(constraint)) {
            reviewConstraints.add(constraint);
            if (lastResult != null) {
                HarmonizationPipeline.HarmonizationResult full = pipeline.run(records);
                this.lastResult = applyRejectionsAndConstraints(full);
            }
        }
    }

    /** Explicitly introduces a separation constraint between two groups by member keys (DEF-3). */
    public synchronized void addSeparationConstraint(Set<String> groupAKeys, Set<String> groupBKeys, String reason) {
        addReviewConstraint(new ReviewConstraint(groupAKeys, groupBKeys, reason));
    }

    // ------------------------------------------------------------------ internals

    private HarmonizationPipeline.HarmonizationResult applyRejectionsAndConstraints(
            HarmonizationPipeline.HarmonizationResult full) {
        List<VerifiedEdge> allowed = new ArrayList<>(full.verifiedEdges().size());
        int suppressed = 0;
        for (VerifiedEdge edge : full.verifiedEdges()) {
            MaterialRecord a = recordById(edge.recordA());
            MaterialRecord b = recordById(edge.recordB());
            if (a != null && b != null && rejectedRelationships.contains(pairKey(a, b))) {
                suppressed++;
                continue;
            }
            allowed.add(edge);
        }

        ConstraintFilterOutcome filtered = filterReviewConstraints(allowed, records, reviewConstraints);
        List<VerifiedEdge> approved = filtered.approved();
        List<VerifiedEdge> flagged = filtered.flagged();

        if (suppressed == 0 && flagged.isEmpty() && reviewConstraints.isEmpty()) {
            return full;
        }

        return pipeline.regroup(full, records, approved, suppressed, flagged, reviewConstraints.size());
    }

    /** Applies existing cannot-link rules to reviewer-approved operational edges. */
    public static List<VerifiedEdge> compatibleWithReviewConstraints(
            List<VerifiedEdge> edges, List<MaterialRecord> records, List<ReviewConstraint> constraints) {
        return List.copyOf(filterReviewConstraints(edges, records, constraints).approved());
    }

    private static class ConstraintFilterOutcome {
        private final List<VerifiedEdge> approved;
        private final List<VerifiedEdge> flagged;

        public ConstraintFilterOutcome(List<VerifiedEdge> approved, List<VerifiedEdge> flagged) {
            this.approved = approved;
            this.flagged = flagged;
        }

        public List<VerifiedEdge> approved() {
            return approved;
        }

        public List<VerifiedEdge> flagged() {
            return flagged;
        }
    }

    /**
     * Enforces reviewer separation constraints (DEF-3).
     *
     * <p>Any direct cross-group relationship or new bridging record that would transitively merge
     * separated components is prevented from merging, and the conflicting relationships are
     * flagged for manual review while preserving grouping within each separated component.
     */
    private static ConstraintFilterOutcome filterReviewConstraints(
            List<VerifiedEdge> edges, List<MaterialRecord> records, List<ReviewConstraint> constraints) {
        if (constraints == null || constraints.isEmpty() || edges == null || edges.isEmpty()) {
            return new ConstraintFilterOutcome(edges == null ? List.of() : edges, List.of());
        }

        Map<Integer, String> keyById = new LinkedHashMap<>();
        for (MaterialRecord r : records) {
            if (r != null) {
                keyById.put(r.id(), materialKey(r));
            }
        }

        // Build adjacency graph of proposed edges for path search
        Map<Integer, Set<Integer>> adj = new LinkedHashMap<>();
        for (MaterialRecord r : records) {
            if (r != null) {
                adj.put(r.id(), new HashSet<>());
            }
        }
        for (VerifiedEdge e : edges) {
            adj.computeIfAbsent(e.recordA(), k -> new HashSet<>()).add(e.recordB());
            adj.computeIfAbsent(e.recordB(), k -> new HashSet<>()).add(e.recordA());
        }

        Set<VerifiedEdge> flaggedSet = new HashSet<>();

        for (ReviewConstraint rc : constraints) {
            Set<Integer> setA = new HashSet<>();
            Set<Integer> setB = new HashSet<>();
            for (Map.Entry<Integer, String> entry : keyById.entrySet()) {
                if (rc.groupA().contains(entry.getValue())) {
                    setA.add(entry.getKey());
                }
                if (rc.groupB().contains(entry.getValue())) {
                    setB.add(entry.getKey());
                }
            }
            if (setA.isEmpty() || setB.isEmpty()) {
                continue;
            }

            // Nodes that can reach setA without entering setB
            Set<Integer> reachesA = bfsReachable(setA, setB, adj);
            // Nodes that can reach setB without entering setA
            Set<Integer> reachesB = bfsReachable(setB, setA, adj);

            // Nodes reachable from both sides without entering the other are bridging nodes
            Set<Integer> bridgingNodes = new HashSet<>(reachesA);
            bridgingNodes.retainAll(reachesB);

            for (VerifiedEdge e : edges) {
                String kA = keyById.get(e.recordA());
                String kB = keyById.get(e.recordB());

                // Direct conflict: edge between groupA and groupB
                if (rc.conflicts(kA, kB)) {
                    flaggedSet.add(e);
                    continue;
                }

                // Bridging conflict: edge touches a bridging node and connects towards A or B
                if (bridgingNodes.contains(e.recordA()) || bridgingNodes.contains(e.recordB())) {
                    boolean aTouchesA = reachesA.contains(e.recordA());
                    boolean aTouchesB = reachesB.contains(e.recordA());
                    boolean bTouchesA = reachesA.contains(e.recordB());
                    boolean bTouchesB = reachesB.contains(e.recordB());

                    if ((aTouchesA && bTouchesB) || (aTouchesB && bTouchesA)) {
                        flaggedSet.add(e);
                    }
                }
            }
        }

        List<VerifiedEdge> approved = new ArrayList<>();
        List<VerifiedEdge> flagged = new ArrayList<>();
        for (VerifiedEdge e : edges) {
            if (flaggedSet.contains(e)) {
                flagged.add(e);
            } else {
                approved.add(e);
            }
        }

        return new ConstraintFilterOutcome(approved, flagged);
    }

    private static Set<Integer> bfsReachable(
            Set<Integer> startNodes, Set<Integer> blockedNodes, Map<Integer, Set<Integer>> adj) {
        Set<Integer> visited = new HashSet<>(startNodes);
        ArrayDeque<Integer> queue = new ArrayDeque<>(startNodes);
        while (!queue.isEmpty()) {
            int curr = queue.poll();
            Set<Integer> neighbors = adj.get(curr);
            if (neighbors != null) {
                for (int next : neighbors) {
                    if (!visited.contains(next) && !blockedNodes.contains(next)) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return visited;
    }

    private static MaterialGroup groupContaining(List<MaterialGroup> groups, int recordId) {
        if (groups == null) {
            return null;
        }
        for (MaterialGroup g : groups) {
            for (MaterialRecord r : g.members()) {
                if (r.id() == recordId) {
                    return g;
                }
            }
        }
        return null;
    }

    private static Set<String> memberKeys(List<MaterialRecord> members) {
        Set<String> keys = new TreeSet<>();
        for (MaterialRecord r : members) {
            if (r != null) {
                keys.add(materialKey(r));
            }
        }
        return keys;
    }

    private MaterialRecord requireRecord(int recordId) {
        MaterialRecord record = recordById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("Unknown record id " + recordId
                    + "; upload a dataset and run harmonization first.");
        }
        return record;
    }

    private MaterialRecord recordById(int recordId) {
        for (MaterialRecord record : records) {
            if (record.id() == recordId) {
                return record;
            }
        }
        return null;
    }

    private static boolean isPair(VerifiedEdge edge, int a, int b) {
        return (edge.recordA() == a && edge.recordB() == b)
                || (edge.recordA() == b && edge.recordB() == a);
    }

    /** Canonical, order-independent identity of a material pair. */
    private static String pairKey(MaterialRecord a, MaterialRecord b) {
        String left = materialKey(a);
        String right = materialKey(b);
        return left.compareTo(right) <= 0 ? left + "\u0000" + right : right + "\u0000" + left;
    }

    private static String materialKey(MaterialRecord record) {
        return record.cpse().toUpperCase(Locale.ROOT) + "|" + record.code().toUpperCase(Locale.ROOT);
    }
}
