package harmonizer.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for attribute extraction, i.e. the bridge between messy descriptions and the
 * verification gate. These assert concrete values (millimetres, grade, specification), not just that
 * something was parsed.
 */
class AttributeExtractorTest {

    private final AttributeExtractor extractor = new AttributeExtractor();
    private final KmpSpecificationExtractor specifications = new KmpSpecificationExtractor();

    @Test
    @DisplayName("Description A: pipe, seamless, 6 inch, ASTM A106 Gr.B")
    void parsesFirstDemoRecord() {
        MaterialAttributes attributes =
                extractor.extract("Pipe, seamless, 6 inch, ASTM A106 Gr.B");

        assertEquals("pipe", attributes.itemType());
        assertEquals("seamless", attributes.form());
        assertEquals(152.4, attributes.sizeMillimetres(), 0.05);
        assertEquals("ASTM A106", attributes.specification());
        assertEquals("B", attributes.grade());
        assertNull(attributes.metal(), "no metal is stated in this description");
    }

    @Test
    @DisplayName("Description B: abbreviations and millimetres are expanded")
    void parsesAbbreviatedRecord() {
        MaterialAttributes attributes =
                extractor.extract("SEAMLESS PIPE 150mm CS A106-B");

        assertEquals("pipe", attributes.itemType());
        assertEquals("seamless", attributes.form());
        assertEquals("carbon steel", attributes.metal());
        assertEquals(150.0, attributes.sizeMillimetres(), 0.01);
        assertEquals("ASTM A106", attributes.specification());
        assertEquals("B", attributes.grade());
    }

    @Test
    @DisplayName("Description C: carbon steel, 6 inch, ASTM A106-B")
    void parsesThirdDemoRecord() {
        MaterialAttributes attributes =
                extractor.extract("Carbon steel seamless pipe, 6 inch, ASTM A106-B");

        assertEquals("pipe", attributes.itemType());
        assertEquals("carbon steel", attributes.metal());
        assertEquals(152.4, attributes.sizeMillimetres(), 0.05);
        assertEquals("ASTM A106", attributes.specification());
        assertEquals("B", attributes.grade());
    }

    @Test
    @DisplayName("A different item type and standard are recognised")
    void parsesValve() {
        MaterialAttributes attributes =
                extractor.extract("Stainless steel gate valve, 2 inch, SS316");

        assertEquals("valve", attributes.itemType());
        assertEquals("stainless steel", attributes.metal());
        assertEquals(50.8, attributes.sizeMillimetres(), 0.05);
        assertEquals("SS316", attributes.specification());
        assertNull(attributes.grade());
    }

    @Test
    @DisplayName("Unrecognised text yields unknown attributes instead of guesses")
    void unknownDescriptionYieldsUnknowns() {
        MaterialAttributes attributes = extractor.extract("Assorted workshop consumables");

        assertNull(attributes.itemType());
        assertNull(attributes.form());
        assertNull(attributes.metal());
        assertNull(attributes.specification());
        assertNull(attributes.grade());
        assertFalse(attributes.hasSize());
        assertTrue(Double.isNaN(attributes.sizeMillimetres()));
    }

    @Test
    @DisplayName("KMP finds every catalogued specification spelling in one text")
    void kmpFindsSpecificationVariants() {
        String text = "STAINLESS STEEL FLANGE 2 INCH 150# ANSI B16.5 RF SS316";

        List<SpecHit> hits = specifications.extract(text);

        List<String> canonical = hits.stream().map(SpecHit::specification).distinct().toList();
        assertTrue(canonical.contains("ANSI B16.5"));
        assertTrue(canonical.contains("SS316"));
        assertTrue(hits.stream().allMatch(hit -> hit.firstPosition() >= 0));
        assertTrue(hits.stream().allMatch(hit -> hit.comparisons() > 0));
    }

    @Test
    @DisplayName("KMP reports the index of each specification occurrence")
    void kmpReportsPositions() {
        String text = "MILD STEEL TUBE ERW CONFORMING TO IS 1239 PART 1";
        List<SpecHit> hits = specifications.extract(text);

        SpecHit hit = hits.stream()
                .filter(candidate -> candidate.specification().equals("IS 1239"))
                .findFirst()
                .orElseThrow();

        assertEquals(List.of(text.indexOf("IS 1239")), hit.positions());
    }

    @Test
    @DisplayName("The first specification mentioned wins when several are present")
    void firstSpecificationWins() {
        assertEquals("API 5L", specifications.firstSpecification("API 5L pipe, also ASTM A106").orElseThrow());
        assertEquals("ASTM A106", specifications.firstSpecification("ASTM A106 pipe, also API 5L").orElseThrow());
        assertTrue(specifications.firstSpecification("plain steel pipe").isEmpty());
    }
}
