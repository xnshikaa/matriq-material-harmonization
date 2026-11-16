package harmonizer.io;

import harmonizer.model.InvalidDatasetException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CsvMaterialParserTest {

    private final CsvMaterialParser parser = new CsvMaterialParser();

    @Test
    void parsesQuotedDescriptionsContainingCommasAndQuotes() {
        String csv = "cpse,code,description,uom\n"
                + "CPCL,M1,\"Pipe, seamless, 6 inch\",EA\n"
                + "NTPC,M2,\"Bolt 1\"\" long\",EA\n";
        CsvMaterialParser.ParseResult r = parser.parseText(csv);
        assertEquals(2, r.records().size());
        assertEquals("Pipe, seamless, 6 inch", r.records().get(0).description());
        assertEquals("Bolt 1\" long", r.records().get(1).description());
        assertEquals(1, r.records().get(0).id());
        assertEquals(2, r.records().get(1).id());
    }

    @Test
    void emptyFileIsRejected() { // edge case
        assertThrows(InvalidDatasetException.class, () -> parser.parseText(""));
        assertThrows(InvalidDatasetException.class, () -> parser.parseText("\n\n  \n"));
    }

    @Test
    void missingRequiredColumnIsRejectedWithAClearMessage() {
        InvalidDatasetException e = assertThrows(InvalidDatasetException.class,
                () -> parser.parseText("cpse,code\nA,1\n"));
        assertTrue(e.getMessage().contains("description"));
    }

    @Test
    void headerOnlyFileIsRejected() {
        assertThrows(InvalidDatasetException.class, () -> parser.parseText("cpse,code,description\n"));
    }

    @Test
    void blankFieldsAndDuplicateCodesAreSkippedWithWarnings() {
        String csv = "cpse,code,description\n"
                + "A,1,pipe\n"
                + "A,1,pipe again\n"      // duplicate (cpse, code)
                + "A,2,\n"                 // blank description
                + ",3,valve\n"             // blank cpse
                + "B,1,pipe\n";            // same code, different company: allowed
        CsvMaterialParser.ParseResult r = parser.parseText(csv);
        assertEquals(2, r.records().size());
        assertEquals(3, r.totalWarnings());
        assertTrue(r.warnings().get(0).contains("Row 3"));
    }

    @Test
    void columnOrderAndCaseDoNotMatter_andUomIsOptional() {
        CsvMaterialParser.ParseResult r = parser.parseText("Description,CODE,CPSE\nvalve,9,SAIL\n");
        assertEquals("SAIL", r.records().get(0).cpse());
        assertEquals("", r.records().get(0).uom());
    }

    @Test
    void unterminatedQuoteIsRejected() {
        assertThrows(InvalidDatasetException.class,
                () -> parser.parseText("cpse,code,description\nA,1,\"never closed\n"));
    }

    @Test
    void handlesWindowsLineEndingsAndByteOrderMark() throws Exception {
        byte[] bytes = ("\uFEFFcpse,code,description\r\nA,1,pipe\r\nB,2,valve\r\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        CsvMaterialParser.ParseResult r = parser.parse(new java.io.ByteArrayInputStream(bytes));
        assertEquals(2, r.records().size());
    }

    @Test
    void inchMarkInsideAnUnquotedFieldIsKeptAsText() {
        CsvMaterialParser.ParseResult r = parser.parseText("cpse,code,description\nNTPC,GV-1,GATE VALVE CS 2\" CL 300\n");
        assertEquals("GATE VALVE CS 2\" CL 300", r.records().get(0).description());
    }
}
