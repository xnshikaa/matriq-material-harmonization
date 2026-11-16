package harmonizer.normalize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextNormalizerTest {

    private final TextNormalizer n = TextNormalizer.withDefaults();

    @Test
    void differentWritingsOfTheSameItemShareOneSignature() {
        String a = n.signature("Pipe, seamless, 6 inch, ASTM A106 Gr.B");
        String b = n.signature("SEAMLESS PIPE 152.4 mm A106-B");
        String c = n.signature("smls pipe 15.24 cm a106 grade b");
        assertEquals("152.4mm a106 b pipe seamless", a);
        assertEquals(a, b);
        assertEquals(a, c);
    }

    @Test
    void differentItemsGetDifferentSignatures() {
        assertNotEquals(n.signature("pipe 6 inch a106 b"), n.signature("pipe 8 inch a106 b"));
        assertNotEquals(n.signature("pipe 6 inch a106 b"), n.signature("valve 6 inch a106 b"));
    }

    @Test
    void expandsAbbreviationsThroughTheTrieDictionary() {
        assertEquals("carbon seamless steel", n.signature("CS SMLS"));
        assertEquals("300 40 class schedule", n.signature("sch 40 cl 300"));
    }

    @Test
    void convertsFractionsAndLengthUnits() {
        assertEquals("12.7mm", n.clean("1/2 inch"));
        assertEquals("25.4mm", n.clean("1\""));
        assertEquals("100mm", n.clean("10 cm"));
        assertEquals("150mm", n.clean("150 MM"));
    }

    @Test
    void doesNotMisreadUnitsInsideOtherTokens() {
        assertEquals("a106 in stock", n.clean("A106 in stock")); // "106 in" must not become mm
        assertEquals("1 0 inch", n.clean("1/0 inch")); // division by zero is left alone, not converted
    }

    @Test
    void wordOrderPunctuationAndCaseDoNotMatter() {
        assertEquals(n.signature("Gate Valve, 2 inch, CS"), n.signature("cs - 2\" - VALVE GATE"));
    }

    @Test
    void blankAndNullInputGiveEmptySignature() { // edge cases
        assertEquals("", n.signature(null));
        assertEquals("", n.signature(""));
        assertEquals("", n.signature("   ,;- "));
        assertEquals("", n.signature("ASTM grade of the")); // only stop-words
    }

    @Test
    void veryLongInputIsTruncatedNotCrashed() {
        String huge = "pipe ".repeat(100_000);
        assertDoesNotThrow(() -> n.signature(huge));
        assertEquals("pipe", n.signature(huge));
    }
}
