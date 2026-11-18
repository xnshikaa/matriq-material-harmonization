package harmonizer.integration;

import harmonizer.harmonization.HarmonizationEngine;
import harmonizer.harmonization.HarmonizationPipeline.HarmonizationResult;
import harmonizer.harmonization.MaterialGroup;
import harmonizer.harmonization.MaterialGroupingService;
import harmonizer.harmonization.NationalCodeRegistry;
import harmonizer.io.CsvMaterialParser;
import harmonizer.matching.MatchEvidence;
import harmonizer.matching.SpecEvidence;
import harmonizer.matching.VerifiedEdge;
import harmonizer.model.MaterialRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for the harmonization stage:
 * normalise -> extract specifications with KMP -> verify attributes -> group verified pairs with DSU
 * -> issue national codes.
 *
 * <p>The scenarios below are the acceptance criteria of the project: equivalent records group,
 * conflicting grades and dimensions do not, transitive relationships form one component, an unrelated
 * material stays alone, a rejected relationship is rebuilt away, and a duplicate import is rejected
 * without damaging existing records.
 */
class HarmonizationPipelineTest {

    private HarmonizationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new HarmonizationEngine();
    }

    // ------------------------------------------------------------------ scenarios

    @Test
    @DisplayName("Scenario 1: three equivalent records form one group with one national code")
    void threeEquivalentRecordsFormOneGroup() {
        List<MaterialRecord> records = threeEquivalentPipes();

        HarmonizationResult result = engine.run(records);

        assertEquals(3, result.totalRecords());
        assertEquals(3, result.candidatePairs(), "every pair of pipes is compared");
        assertEquals(3, result.verifiedPairs());
        assertEquals(0, result.rejectedPairs());
        assertEquals(1, result.groupCount());
        assertEquals(1, result.nationalGroups().size());
        assertEquals(3, result.groupedRecords());

        MaterialGroup group = result.nationalGroups().get(0);
        assertEquals(3, group.size());
        assertTrue(group.nationalCode().matches("NMC-\\d{4}-\\d{8}"),
                "national code should look like NMC-2026-00000001, was " + group.nationalCode());
        assertEquals(Set.of("CPCL", "IOCL", "ONGC"), group.companies());

        // Original company codes must survive untouched.
        assertEquals(List.of("PIPE-001", "P-9281", "MAT-1003"), group.originalCodes());
        assertEquals("Pipe, seamless, 6 inch, ASTM A106 Gr.B", group.members().get(0).description());
    }

    @Test
    @DisplayName("Scenario 1b: KMP evidence records where each specification was found, with comparison counts")
    void kmpEvidenceIsRecordedInTheWorkflow() {
        List<MaterialRecord> records = threeEquivalentPipes();

        HarmonizationResult result = engine.run(records);

        VerifiedEdge edge = result.verifiedEdges().stream()
                .filter(candidate -> isPair(candidate, 1, 2))
                .findFirst()
                .orElseThrow();
        MatchEvidence evidence = edge.evidence();

        assertTrue(evidence.verified());
        assertTrue(evidence.checks().allPass());
        assertTrue(result.kmpComparisons() > 0, "the pipeline must report KMP comparison counts");

        SpecEvidence specification = evidence.sharedSpecifications().stream()
                .filter(shared -> shared.specification().equals("ASTM A106"))
                .findFirst()
                .orElseThrow();

        assertEquals(records.get(0).description().indexOf("ASTM A106"), specification.positionInA());
        assertEquals(records.get(1).description().indexOf("A106"), specification.positionInB());
        assertTrue(specification.comparisonsA() > 0);
        assertTrue(specification.comparisonsB() > 0);
    }

    @Test
    @DisplayName("Scenario 2: conflicting grades are rejected even though the specification matches")
    void conflictingGradesAreRejected() {
        List<MaterialRecord> records = List.of(
                record(1, "CPCL", "C-01", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"),
                record(2, "IOCL", "S-01", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.C"));

        HarmonizationResult result = engine.run(records);

        assertEquals(1, result.candidatePairs());
        assertEquals(0, result.verifiedPairs());
        assertEquals(1, result.rejectedPairs());
        assertEquals(2, result.groupCount());
        assertTrue(result.nationalGroups().isEmpty(), "no group may be approved");

        MatchEvidence rejection = result.rejectedCandidates().get(0);
        assertFalse(rejection.checks().grade(), "grade check must fail");
        assertTrue(rejection.checks().specification(), "the specification itself does match");
        assertTrue(rejection.rejectionReason().contains("grade"),
                "reason should mention the grade, was: " + rejection.rejectionReason());
    }

    @Test
    @DisplayName("Scenario 3: same specification but incompatible dimensions are rejected")
    void conflictingDimensionsAreRejected() {
        List<MaterialRecord> records = List.of(
                record(1, "CPCL", "C-02", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"),
                record(2, "IOCL", "S-02", "Carbon steel seamless pipe, 4 inch, ASTM A106 Gr.B"));

        HarmonizationResult result = engine.run(records);

        assertEquals(1, result.candidatePairs());
        assertEquals(0, result.verifiedPairs());
        assertTrue(result.nationalGroups().isEmpty());

        MatchEvidence rejection = result.rejectedCandidates().get(0);
        assertFalse(rejection.checks().dimensions());
        assertTrue(rejection.rejectionReason().contains("dimension"),
                "reason should mention dimensions, was: " + rejection.rejectionReason());
        assertEquals(152.4, rejection.attributesA().sizeMillimetres(), 0.05);
        assertEquals(101.6, rejection.attributesB().sizeMillimetres(), 0.05);
    }

    @Test
    @DisplayName("Scenario 4: A = B and B = C place A, B and C in one DSU component")
    void transitiveRelationshipsFormOneComponent() {
        List<MaterialRecord> records = threeEquivalentPipes();
        HarmonizationResult full = engine.run(records);

        // Keep only the A=B and B=C edges, as if the direct A=C relationship had never been approved.
        List<VerifiedEdge> chain = full.verifiedEdges().stream()
                .filter(edge -> !isPair(edge, 1, 3))
                .toList();
        assertEquals(2, chain.size());

        MaterialGroupingService grouping =
                new MaterialGroupingService(new NationalCodeRegistry(2026));
        List<MaterialGroup> groups = grouping.group(records, chain);

        assertEquals(1, groups.size(), "A-B and B-C must produce a single component");
        assertEquals(3, groups.get(0).size());
        assertEquals(List.of(1, 2, 3),
                groups.get(0).members().stream().map(MaterialRecord::id).toList());
        assertEquals("NMC-2026-00000001", groups.get(0).nationalCode());
    }

    @Test
    @DisplayName("Scenario 5: an unrelated material stays in its own group without a national code")
    void independentMaterialStaysSeparate() {
        List<MaterialRecord> records = List.of(
                record(1, "CPCL", "PIPE-001", "Pipe, seamless, 6 inch, ASTM A106 Gr.B"),
                record(2, "IOCL", "P-9281", "Seamless pipe, 150 mm, A106 Grade B"),
                record(3, "ONGC", "MAT-1003", "Carbon steel seamless pipe, 6 inch, ASTM A106-B"),
                record(4, "BPCL", "PIPE-900", "Stainless steel gate valve, 2 inch, SS316"));

        HarmonizationResult result = engine.run(records);

        assertEquals(3, result.candidatePairs(), "the valve is in a different item-type block");
        assertEquals(1, result.nationalGroups().size());
        assertEquals(3, result.nationalGroups().get(0).size());

        MaterialGroup valve = groupContaining(result.groups(), 4);
        assertEquals(1, valve.size());
        assertNull(valve.nationalCode(), "an ungrouped material must not receive a national code");
        assertTrue(result.verifiedEdges().stream()
                        .noneMatch(edge -> edge.recordA() == 4 || edge.recordB() == 4),
                "the valve must not be joined to any pipe");
    }

    @Test
    @DisplayName("Scenario 6: rejecting relationships rebuilds the groups from the remaining edges")
    void rejectingRelationshipsRebuildsGroupsCorrectly() {
        List<MaterialRecord> records = threeEquivalentPipes();
        HarmonizationResult first = engine.run(records);
        String originalCode = first.nationalGroups().get(0).nationalCode();
        assertEquals(3, first.verifiedPairs());

        // Reject the direct A=C relationship: A,B,C must still be one group through A=B and B=C.
        HarmonizationResult afterFirstRejection = engine.reject(1, 3);
        assertEquals(2, afterFirstRejection.verifiedPairs());
        assertEquals(1, afterFirstRejection.nationalGroups().size());
        assertEquals(3, afterFirstRejection.nationalGroups().get(0).size());
        assertEquals(originalCode, afterFirstRejection.nationalGroups().get(0).nationalCode(),
                "the same members must keep the national code already issued");

        // Reject B=C too: only A=B remains, so C must leave the group.
        HarmonizationResult afterSecondRejection = engine.reject(2, 3);
        assertEquals(1, afterSecondRejection.verifiedPairs());
        assertEquals(2, afterSecondRejection.groupCount());

        MaterialGroup remaining = afterSecondRejection.nationalGroups().get(0);
        assertEquals(List.of(1, 2), remaining.members().stream().map(MaterialRecord::id).toList());
        assertNotEquals(originalCode, remaining.nationalCode(),
                "a group whose membership changed is a different material identity");

        MaterialGroup alone = groupContaining(afterSecondRejection.groups(), 3);
        assertEquals(1, alone.size());
        assertNull(alone.nationalCode(), "a record left alone must not keep a group code");
        assertEquals("MAT-1003", alone.members().get(0).code(),
                "the original company code is preserved after rejection");
    }

    @Test
    @DisplayName("Scenario 7: a duplicate company code is rejected on import without corrupting the dataset")
    void duplicateImportIsRejected() {
        String csv = """
                cpse,code,description,uom
                CPCL,PIPE-001,"Pipe, seamless, 6 inch, ASTM A106 Gr.B",EA
                CPCL,PIPE-001,"Duplicate row with the same company code",EA
                IOCL,P-9281,"Seamless pipe, 150 mm, A106 Grade B",EA
                """;

        CsvMaterialParser.ParseResult parsed = new CsvMaterialParser().parseText(csv);

        assertEquals(2, parsed.records().size(), "the duplicate row must be dropped");
        assertEquals(1, parsed.totalWarnings());
        assertTrue(parsed.warnings().get(0).contains("duplicate code"),
                "warning should name the problem, was: " + parsed.warnings().get(0));

        HarmonizationResult result = engine.run(parsed.records());

        assertEquals(1, result.nationalGroups().size());
        MaterialGroup group = result.nationalGroups().get(0);
        assertEquals(2, group.size());
        assertEquals(List.of("PIPE-001", "P-9281"), group.originalCodes());
        assertEquals(2, result.totalRecords(), "the surviving records are intact");
    }

    // ------------------------------------------------------------------ supporting rules

    @Test
    @DisplayName("A shared specification alone never merges different item types")
    void sharedSpecificationAloneDoesNotMerge() {
        List<MaterialRecord> records = List.of(
                record(1, "CPCL", "C-10", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"),
                record(2, "IOCL", "S-10", "Carbon steel forged flange, 6 inch, ASTM A106 Gr.B"));

        HarmonizationResult result = engine.run(records);

        assertEquals(0, result.candidatePairs(), "pipe and flange are different item-type blocks");
        assertEquals(0, result.verifiedPairs());
        assertEquals(2, result.groupCount());
        assertTrue(result.nationalGroups().isEmpty());
    }

    @Test
    @DisplayName("Running the pipeline twice keeps the same national code for the same group")
    void nationalCodesAreStableAcrossRuns() {
        List<MaterialRecord> records = threeEquivalentPipes();

        String first = engine.run(records).nationalGroups().get(0).nationalCode();
        String second = engine.run(records).nationalGroups().get(0).nationalCode();

        assertEquals(first, second, "an existing group must not receive a second national code");
    }

    @Test
    @DisplayName("Rejecting an unknown relationship or rejecting before a run is reported clearly")
    void invalidRejectionIsReported() {
        assertThrows(IllegalStateException.class, () -> engine.reject(1, 2),
                "rejecting before running must be an error");

        engine.run(threeEquivalentPipes());
        assertThrows(IllegalArgumentException.class, () -> engine.reject(1, 99),
                "there is no verified relationship with record 99");
    }

    @Test
    @DisplayName("An empty dataset produces an empty report instead of failing")
    void emptyDatasetIsSafe() {
        HarmonizationResult result = engine.run(List.of());

        assertEquals(0, result.totalRecords());
        assertEquals(0, result.groupCount());
        assertTrue(result.groups().isEmpty());
        assertTrue(result.verifiedEdges().isEmpty());
    }

    // ------------------------------------------------------------------ helpers

    private static List<MaterialRecord> threeEquivalentPipes() {
        return List.of(
                record(1, "CPCL", "PIPE-001", "Pipe, seamless, 6 inch, ASTM A106 Gr.B"),
                record(2, "IOCL", "P-9281", "Seamless pipe, 150 mm, A106 Grade B"),
                record(3, "ONGC", "MAT-1003", "Carbon steel seamless pipe, 6 inch, ASTM A106-B"));
    }

    private static MaterialRecord record(int id, String cpse, String code, String description) {
        return new MaterialRecord(id, cpse, code, description, "EA");
    }

    private static boolean isPair(VerifiedEdge edge, int a, int b) {
        return (edge.recordA() == a && edge.recordB() == b)
                || (edge.recordA() == b && edge.recordB() == a);
    }

    private static MaterialGroup groupContaining(List<MaterialGroup> groups, int recordId) {
        return groups.stream()
                .filter(group -> group.members().stream().anyMatch(member -> member.id() == recordId))
                .findFirst()
                .orElseThrow();
    }
}
