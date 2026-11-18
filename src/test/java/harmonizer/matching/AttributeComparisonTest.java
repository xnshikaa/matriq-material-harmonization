package harmonizer.matching;

import harmonizer.model.MaterialRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the MATCH / CONFLICT / UNKNOWN rules that replaced the old
 * "missing means compatible" logic.
 */
class AttributeComparisonTest {

    private final AttributeExtractor extractor = new AttributeExtractor();

    @Test
    @DisplayName("Two identical, fully specified records match on every attribute")
    void identicalRecordsMatch() {
        MaterialAttributes a = extractor.extract(
                record("Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"));
        MaterialAttributes b = extractor.extract(
                record("Seamless pipe, 150 mm, carbon steel, A106 Grade B"));

        AttributeStates states = AttributeComparison.compare(a, b, List.of());

        assertEquals(AttributeState.MATCH, states.type());
        assertEquals(AttributeState.MATCH, states.form());
        assertEquals(AttributeState.MATCH, states.metal());
        assertEquals(AttributeState.MATCH, states.grade());
        assertEquals(AttributeState.MATCH, states.dimensions());
        assertEquals(AttributeState.MATCH, states.specification());
        assertEquals(4, states.criticalMatches());
        assertTrue(states.sufficientEvidence());
        assertFalse(states.hasConflict());
    }

    @Test
    @DisplayName("Known but different values are CONFLICT, never silently compatible")
    void differingValuesConflict() {
        MaterialAttributes carbon = extractor.extract(
                record("Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"));
        MaterialAttributes stainless = extractor.extract(
                record("Stainless steel seamless pipe, 6 inch, ASTM A106 Gr.C"));

        AttributeStates states = AttributeComparison.compare(carbon, stainless, List.of());

        assertEquals(AttributeState.CONFLICT, states.metal());
        assertEquals(AttributeState.CONFLICT, states.grade());
        assertTrue(states.hasConflict());
        assertFalse(states.sufficientEvidence());
    }

    @Test
    @DisplayName("Dimensions outside the 2% tolerance conflict; inside they match")
    void dimensionTolerance() {
        MaterialAttributes sixInch = extractor.extract(record("Pipe 6 inch ASTM A106 Gr.B"));
        MaterialAttributes fiftyMm = extractor.extract(record("Pipe 150 mm ASTM A106 Gr.B"));
        MaterialAttributes fourInch = extractor.extract(record("Pipe 4 inch ASTM A106 Gr.B"));

        assertEquals(AttributeState.MATCH,
                AttributeComparison.compare(sixInch, fiftyMm, List.of()).dimensions());
        assertEquals(AttributeState.CONFLICT,
                AttributeComparison.compare(sixInch, fourInch, List.of()).dimensions());
    }

    @Test
    @DisplayName("A missing value is UNKNOWN on either side, not a match")
    void missingValuesAreUnknown() {
        MaterialAttributes known = extractor.extract(
                record("Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"));
        MaterialAttributes bare = extractor.extract(record("Seamless pipe, ASTM A106"));

        AttributeStates states = AttributeComparison.compare(known, bare, List.of());

        assertEquals(AttributeState.UNKNOWN, states.metal());
        assertEquals(AttributeState.UNKNOWN, states.grade());
        assertEquals(AttributeState.UNKNOWN, states.dimensions());
        assertEquals(AttributeState.MATCH, states.specification());
        assertEquals(1, states.criticalMatches());
        assertFalse(states.hasConflict(), "missing values are not conflicts either");
        assertFalse(states.sufficientEvidence(),
                "one critical match is not enough evidence for an equivalence");
    }

    @Test
    @DisplayName("Enough known agreement still allows an unknown attribute")
    void unknownAttributeIsAllowedWhenOtherEvidenceIsStrong() {
        MaterialAttributes known = extractor.extract(
                record("Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B"));
        MaterialAttributes metalUnknown = extractor.extract(
                record("Seamless pipe, 6 inch, ASTM A106 Gr.B"));

        AttributeStates states = AttributeComparison.compare(known, metalUnknown, List.of());

        assertEquals(AttributeState.UNKNOWN, states.metal());
        assertEquals(3, states.criticalMatches(), "grade, dimensions and specification all match");
        assertTrue(states.sufficientEvidence());
    }

    @Test
    @DisplayName("Two records with nothing known about them cannot be sufficient evidence")
    void twoEmptyRecordsAreNotEvidence() {
        MaterialAttributes empty = extractor.extract(record("Assorted workshop consumables"));
        MaterialAttributes other = extractor.extract(record("Miscellaneous store items"));

        AttributeStates states = AttributeComparison.compare(empty, other, List.of());

        assertEquals(0, states.criticalMatches());
        assertFalse(states.hasConflict());
        assertFalse(states.sufficientEvidence());
    }

    @Test
    @DisplayName("A shared KMP specification resolves an otherwise differing specification attribute")
    void sharedSpecificationEvidenceCountsAsMatch() {
        MaterialAttributes a = extractor.extract(record("Pipe ASTM A106 and API 5L"));
        MaterialAttributes b = extractor.extract(record("Pipe API 5L and ASTM A106"));

        SpecEvidence shared = new SpecEvidence("ASTM A106", 5, 13, 10, 10);
        AttributeStates states = AttributeComparison.compare(a, b, List.of(shared));

        assertEquals(AttributeState.MATCH, states.specification());
    }

    private static MaterialRecord record(String description) {
        return new MaterialRecord(1, "CPCL", "X-1", description, "EA");
    }
}
