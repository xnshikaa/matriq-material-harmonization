package harmonizer.integration;

import harmonizer.harmonization.HarmonizationEngine;
import harmonizer.harmonization.HarmonizationPipeline.HarmonizationResult;
import harmonizer.harmonization.MaterialGroup;
import harmonizer.matching.AttributeState;
import harmonizer.matching.MatchEvidence;
import harmonizer.matching.VerifiedEdge;
import harmonizer.model.MaterialRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the defects found in the independent audit:
 * <ol>
 *   <li>a chain of verified edges merged materials with contradictory attributes;</li>
 *   <li>a rejected relationship reappeared after the next harmonization run;</li>
 *   <li>adding a record could change a group's national code.</li>
 * </ol>
 */
class HarmonizationRegressionTest {

    private HarmonizationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new HarmonizationEngine();
    }

    // ------------------------------------------------------------------ Phase 1

    @Test
    @DisplayName("Audit regression: A-B-C with unknown attributes must not merge carbon steel 6in with stainless 4in")
    void auditRegressionUnknownAttributesCannotBridgeConflictingMaterials() {
        List<MaterialRecord> records = List.of(
                record(1, "CPCL", "A-1", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"),
                record(2, "IOCL", "B-1", "Seamless pipe, ASTM A106"),
                record(3, "ONGC", "C-1", "Stainless steel seamless pipe, 4 inch, ASTM A106 Gr.B"));

        HarmonizationResult result = engine.run(records);

        assertEquals(0, result.verifiedPairs(),
                "a record with no metal, grade or size cannot be an equivalence bridge");
        assertEquals(3, result.groupCount());
        assertTrue(result.nationalGroups().isEmpty());
        assertNotEquals(groupContaining(result, 1).id(), groupContaining(result, 3).id(),
                "carbon steel and stainless steel must never share a group");

        for (MatchEvidence rejection : result.rejectedCandidates()) {
            assertTrue(rejection.attributeStates().hasConflict()
                            || rejection.rejectionReason().contains("insufficient evidence"),
                    "unexpected reason: " + rejection.rejectionReason());
        }
    }

    @Test
    @DisplayName("A bridge with enough evidence is split so contradictory members are not grouped")
    void bridgeWithSufficientEvidenceIsSplitBeforeGrouping() {
        List<MaterialRecord> records = List.of(
                record(1, "CPCL", "A-2", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"),
                record(2, "IOCL", "B-2", "Seamless pipe, 6 inch, ASTM A106 Gr.B"),
                record(3, "ONGC", "C-2", "Stainless steel seamless pipe, 6 inch, ASTM A106 Gr.B"));

        HarmonizationResult result = engine.run(records);

        assertEquals(2, result.verifiedPairs(), "A-B and B-C both have strong evidence");
        assertEquals(1, result.rejectedPairs(), "A-C conflicts on metal");
        assertEquals(1, result.splitComponents(),
                "the DSU component contains a contradiction and must be split");
        assertEquals(2, result.groupCount());

        MaterialGroup withCarbonSteel = groupContaining(result, 1);
        assertEquals(List.of(1, 2),
                withCarbonSteel.members().stream().map(MaterialRecord::id).toList());
        assertNotNull(withCarbonSteel.nationalCode());

        MaterialGroup stainless = groupContaining(result, 3);
        assertEquals(1, stainless.size());
        assertNull(stainless.nationalCode(), "an isolated record gets no national code");
        assertNotEquals(withCarbonSteel.id(), stainless.id());
    }

    @Test
    @DisplayName("Missing attributes alone are not evidence of equivalence")
    void missingAttributesAloneAreNotEnoughEvidence() {
        List<MaterialRecord> records = List.of(
                record(1, "CPCL", "A-3", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"),
                record(2, "IOCL", "B-3", "Seamless pipe, ASTM A106"));

        HarmonizationResult result = engine.run(records);

        assertEquals(1, result.candidatePairs());
        assertEquals(0, result.verifiedPairs());
        assertEquals(1, result.rejectedPairs());
        assertTrue(result.rejectedCandidates().get(0).rejectionReason()
                        .contains("insufficient evidence"),
                "reason was: " + result.rejectedCandidates().get(0).rejectionReason());
        assertEquals(AttributeState.UNKNOWN,
                result.rejectedCandidates().get(0).attributeStates().metal());
    }

    @Test
    @DisplayName("Conflicting metals are rejected with a CONFLICT state, not silently accepted")
    void conflictingMetalsAreRejected() {
        List<MaterialRecord> records = List.of(
                record(1, "CPCL", "A-4", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"),
                record(2, "IOCL", "B-4", "Stainless steel seamless pipe, 6 inch, ASTM A106 Gr.B"));

        HarmonizationResult result = engine.run(records);

        assertEquals(0, result.verifiedPairs());
        MatchEvidence rejection = result.rejectedCandidates().get(0);
        assertEquals(AttributeState.CONFLICT, rejection.attributeStates().metal());
        assertEquals(AttributeState.MATCH, rejection.attributeStates().grade());
        assertFalse(rejection.checks().metal());
        assertTrue(rejection.rejectionReason().contains("metal"));
    }

    @Test
    @DisplayName("Valid transitive grouping is preserved when every attribute agrees")
    void validTransitiveGroupingIsPreserved() {
        List<MaterialRecord> records = List.of(
                record(1, "CPCL", "A-5", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"),
                record(2, "IOCL", "B-5", "Carbon steel seamless pipe, 150 mm, ASTM A106 Gr.B"),
                record(3, "ONGC", "C-5", "Carbon steel seamless pipe, 6 in, ASTM A106-B"));

        HarmonizationResult result = engine.run(records);

        assertEquals(3, result.verifiedPairs());
        assertEquals(0, result.splitComponents());
        assertEquals(1, result.groupCount());
        assertEquals(3, result.nationalGroups().get(0).size());
        assertEquals(3, result.groupedRecords());
    }

    // ------------------------------------------------------------------ Phase 2

    @Test
    @DisplayName("A rejection survives repeated harmonization runs and unrelated edges remain")
    void rejectionSurvivesRepeatedRuns() {
        List<MaterialRecord> records = List.of(
                record(1, "CPCL", "A-6", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"),
                record(2, "IOCL", "B-6", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"),
                record(3, "ONGC", "C-6", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"));

        HarmonizationResult first = engine.run(records);
        String code = first.nationalGroups().get(0).nationalCode();
        assertEquals(3, first.verifiedPairs());

        HarmonizationResult afterReject = engine.reject(1, 3);
        assertEquals(2, afterReject.verifiedPairs());

        HarmonizationResult rerun = engine.run(records);
        assertEquals(2, rerun.verifiedPairs(), "the rejected A-C edge must stay suppressed");
        assertEquals(1, rerun.suppressedPairs());
        assertTrue(rerun.verifiedEdges().stream().noneMatch(edge -> isPair(edge, 1, 3)));
        assertTrue(rerun.verifiedEdges().stream().anyMatch(edge -> isPair(edge, 1, 2)),
                "unrelated verified edges must survive");
        assertTrue(rerun.verifiedEdges().stream().anyMatch(edge -> isPair(edge, 2, 3)),
                "unrelated verified edges must survive");
        assertEquals(1, rerun.groupCount());
        assertEquals(3, rerun.nationalGroups().get(0).size(),
                "A and C are still connected through B");
        assertEquals(code, rerun.nationalGroups().get(0).nationalCode());
        assertEquals(1, engine.rejectedRelationshipCount());
    }

    @Test
    @DisplayName("A rejection is keyed by material identity, so a re-upload keeps it")
    void rejectionIsKeyedByMaterialIdentityNotRecordIds() {
        HarmonizationResult first = engine.run(List.of(
                record(1, "CPCL", "P-9", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"),
                record(2, "IOCL", "Q-9", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B")));
        assertEquals(1, first.verifiedPairs());
        engine.reject(1, 2);

        HarmonizationResult reuploaded = engine.run(List.of(
                record(7, "CPCL", "P-9", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"),
                record(8, "IOCL", "Q-9", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B")));

        assertEquals(0, reuploaded.verifiedPairs(), "the same material pair stays rejected");
        assertEquals(1, reuploaded.suppressedPairs());
        assertEquals(2, reuploaded.groupCount());
        assertTrue(reuploaded.nationalGroups().isEmpty());
    }

    // ------------------------------------------------------------------ Phase 3

    @Test
    @DisplayName("Adding an equivalent record keeps the group's national code")
    void addingEquivalentRecordKeepsNationalCode() {
        HarmonizationResult before = engine.run(List.of(
                record(1, "CPCL", "P-10", "Pipe, seamless, 6 inch, ASTM A106 Gr.B"),
                record(2, "IOCL", "Q-10", "Seamless pipe, 150 mm, A106 Grade B")));
        String code = before.nationalGroups().get(0).nationalCode();
        assertNotNull(code);

        HarmonizationResult after = engine.run(List.of(
                record(1, "CPCL", "P-10", "Pipe, seamless, 6 inch, ASTM A106 Gr.B"),
                record(2, "IOCL", "Q-10", "Seamless pipe, 150 mm, A106 Grade B"),
                record(3, "ONGC", "R-10", "Carbon steel seamless pipe, 6 in, ASTM A106-B")));

        assertEquals(1, after.groupCount());
        assertEquals(3, after.nationalGroups().get(0).size());
        assertEquals(code, after.nationalGroups().get(0).nationalCode(),
                "a group that only grew must keep the code it already had");
    }

    @Test
    @DisplayName("Splitting a group after rejections issues distinct codes and preserves original codes")
    void splitAfterRejectionKeepsCodesUnique() {
        List<MaterialRecord> records = List.of(
                record(1, "CPCL", "P-11", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"),
                record(2, "IOCL", "Q-11", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"),
                record(3, "ONGC", "R-11", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"),
                record(4, "SAIL", "S-11", "Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"));

        String originalCode = engine.run(records).nationalGroups().get(0).nationalCode();

        engine.reject(1, 3);
        engine.reject(1, 4);
        engine.reject(2, 3);
        HarmonizationResult split = engine.reject(2, 4);

        assertEquals(2, split.groupCount());
        assertEquals(2, split.nationalGroups().size());
        List<String> codes = split.nationalGroups().stream()
                .map(MaterialGroup::nationalCode)
                .toList();
        assertEquals(2, codes.stream().distinct().count(),
                "two live groups must not share a national code");
        assertEquals(originalCode, codes.get(0), "the continuing material keeps its code");
        assertNotEquals(codes.get(0), codes.get(1));

        assertEquals(List.of("P-11", "Q-11", "R-11", "S-11"),
                split.groups().stream()
                        .flatMap(group -> group.originalCodes().stream())
                        .sorted()
                        .toList(),
                "every original company code is preserved exactly once");
    }

    // ------------------------------------------------------------------ helpers

    private static MaterialRecord record(int id, String cpse, String code, String description) {
        return new MaterialRecord(id, cpse, code, description, "EA");
    }

    private static MaterialGroup groupContaining(HarmonizationResult result, int recordId) {
        return result.groups().stream()
                .filter(group -> group.members().stream().anyMatch(member -> member.id() == recordId))
                .findFirst()
                .orElseThrow();
    }

    private static boolean isPair(VerifiedEdge edge, int a, int b) {
        return (edge.recordA() == a && edge.recordB() == b)
                || (edge.recordA() == b && edge.recordB() == a);
    }
}
