package harmonizer.screening;

import harmonizer.bench.SyntheticMaterialGenerator;
import harmonizer.model.MaterialRecord;
import harmonizer.normalize.TextNormalizer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateScreenerTest {

    private final TextNormalizer normalizer = TextNormalizer.withDefaults();
    private final DuplicateScreener screener = new DuplicateScreener(r -> normalizer.signature(r.description()));

    private static MaterialRecord rec(int id, String cpse, String code, String description) {
        return new MaterialRecord(id, cpse, code, description, "EA");
    }

    @Test
    void findsTheProposalExampleAcrossCompanies() {
        List<MaterialRecord> data = List.of(
                rec(1, "CPCL", "MAT-10234", "Pipe, seamless, 6 inch, ASTM A106 Gr.B"),
                rec(2, "NTPC", "PX-889", "SEAMLESS PIPE 152.4mm A106-B"),
                rec(3, "SAIL", "4410022", "Gate valve 2 inch CS"));
        ScreeningResult result = screener.screen(data, 0.01);
        assertEquals(1, result.confirmedPairs().size());
        MatchPair p = result.confirmedPairs().get(0);
        assertEquals(2, p.recordId());
        assertEquals(1, p.matchedWithId());
        assertTrue(p.crossCpse());
    }

    @Test
    void sameCompanyDuplicateIsNotMarkedCrossCpse() {
        ScreeningResult result = screener.screen(List.of(
                rec(1, "CPCL", "A", "valve gate 2 inch cs"),
                rec(2, "CPCL", "B", "CS GATE VALVE 2\"")), 0.01);
        assertEquals(1, result.confirmedPairs().size());
        assertFalse(result.confirmedPairs().get(0).crossCpse());
    }

    @Test
    void emptyDatasetIsRejectedNotSilentlyAccepted() { // edge case
        assertThrows(IllegalArgumentException.class, () -> screener.screen(List.of(), 0.01));
        assertThrows(IllegalArgumentException.class, () -> screener.screen(null, 0.01));
    }

    @Test
    void invalidFalsePositiveRateIsRejected() {
        List<MaterialRecord> data = List.of(rec(1, "A", "1", "pipe"));
        assertThrows(IllegalArgumentException.class, () -> screener.screen(data, 0.0));
        assertThrows(IllegalArgumentException.class, () -> screener.screen(data, 1.5));
    }

    @Test
    void blankDescriptionsAreReportedAndNeverMatchEachOther() { // would otherwise all collide
        ScreeningResult result = screener.screen(List.of(
                rec(1, "A", "1", "   "),
                rec(2, "B", "2", ",,;"),
                rec(3, "C", "3", "pipe 6 inch")), 0.01);
        assertTrue(result.confirmedPairs().isEmpty());
        assertEquals(2, result.skipped().size());
        assertEquals(1, result.stats().screenedRecords());
    }

    @Test
    void allDistinctRecordsProduceNoPairs() {
        List<MaterialRecord> data = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            data.add(rec(i, "A", "C" + i, "item number " + i));
        }
        ScreeningResult result = screener.screen(data, 0.01);
        assertTrue(result.confirmedPairs().isEmpty(), "a Bloom 'maybe' must never become a pair without the exact check");
    }

    @Test
    void allIdenticalRecordsAllPairWithTheFirst() { // boundary: everything is a duplicate
        List<MaterialRecord> data = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            data.add(rec(i, "A", "C" + i, "Valve 2 inch CS"));
        }
        ScreeningResult result = screener.screen(data, 0.01);
        assertEquals(49, result.confirmedPairs().size());
        assertTrue(result.confirmedPairs().stream().allMatch(p -> p.matchedWithId() == 1));
    }

    @Test
    void singleRecordDatasetWorks() { // boundary
        ScreeningResult result = screener.screen(List.of(rec(1, "A", "1", "pipe")), 0.01);
        assertTrue(result.confirmedPairs().isEmpty());
        assertEquals(1, result.stats().bloomDefinitelyNew());
    }

    @Test
    void statisticsAreInternallyConsistent() {
        SyntheticMaterialGenerator.GeneratedData data = SyntheticMaterialGenerator.generate(3_000, 0.3, 5L);
        ScreeningStats s = screener.screen(data.records(), 0.01).stats();
        assertEquals(s.screenedRecords(), s.bloomDefinitelyNew() + s.bloomMaybe());
        assertEquals(s.bloomMaybe(), s.confirmedDuplicates() + s.bloomFalsePositives());
        assertEquals(3_000L * 2_999L / 2L, s.naivePairwiseComparisons());
    }

    @Test
    void perfectPrecisionAndRecallOnSyntheticGroundTruth() { // end-to-end correctness against known truth
        SyntheticMaterialGenerator.GeneratedData data = SyntheticMaterialGenerator.generate(5_000, 0.3, 11L);
        ScreeningResult result = screener.screen(data.records(), 0.01);
        int correct = 0;
        for (MatchPair p : result.confirmedPairs()) {
            if (data.groupOfRecord()[p.recordId() - 1] == data.groupOfRecord()[p.matchedWithId() - 1]) {
                correct++;
            }
        }
        assertEquals(result.confirmedPairs().size(), correct, "every reported pair must be a true duplicate");
        assertEquals(5_000 - data.groupCount(), correct, "every true duplicate must be found");
    }

    @Test
    void largerFilterGivesFewerOrEqualFalsePositives() { // sound probabilistic behaviour
        SyntheticMaterialGenerator.GeneratedData data = SyntheticMaterialGenerator.generate(20_000, 0.0, 3L);
        int loose = screener.screen(data.records(), 0.2).stats().bloomFalsePositives();
        int tight = screener.screen(data.records(), 0.001).stats().bloomFalsePositives();
        assertTrue(loose > tight, "loose=" + loose + " tight=" + tight);
    }
}
