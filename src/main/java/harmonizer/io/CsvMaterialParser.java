package harmonizer.io;

import harmonizer.model.InvalidDatasetException;
import harmonizer.model.MaterialRecord;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads an uploaded CSV of material records and validates it.
 *
 * <p>Expected header (any column order, case-insensitive): {@code cpse, code, description} required,
 * {@code uom} optional. Descriptions often contain commas, so quoted fields ("a, b") and doubled
 * quotes ("") are supported. A quote in the middle of an unquoted field (an inch mark such as 2") is kept as text.
 *
 * <p>Edge cases handled explicitly (nothing is silently dropped):
 * empty file, missing required columns, unterminated quotes, blank code or description,
 * duplicate (cpse, code) rows, too many rows. Bad rows are skipped and reported as warnings;
 * structural problems abort with an {@link InvalidDatasetException}.
 */
public final class CsvMaterialParser {

    public static final int MAX_ROWS = 500_000;
    private static final int MAX_WARNINGS_KEPT = 50;

    /** Parsed records plus human-readable warnings about rows that were skipped. */
    public record ParseResult(List<MaterialRecord> records, List<String> warnings, int totalWarnings) {
    }

    public ParseResult parse(InputStream in) throws IOException {
        String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1); // byte-order mark added by Excel
        }
        return parseText(text);
    }

    public ParseResult parseText(String text) {
        List<List<String>> rows = readRows(text);
        if (rows.isEmpty()) {
            throw new InvalidDatasetException("The file is empty.");
        }

        List<String> header = rows.get(0);
        int cpseCol = indexOf(header, "cpse", "organization", "organisation", "company", "source");
        int codeCol = indexOf(header, "code", "itemcode", "material_code", "materialcode", "item_code");
        int descCol = indexOf(header, "description", "itemdescription", "item_description", "material_description");
        int uomCol = indexOf(header, "uom", "unit", "unit_of_measure", "unitofmeasure");
        List<String> missing = new ArrayList<>();
        if (cpseCol < 0) missing.add("cpse");
        if (codeCol < 0) missing.add("code");
        if (descCol < 0) missing.add("description");
        if (!missing.isEmpty()) {
            throw new InvalidDatasetException("Missing required column(s): " + String.join(", ", missing)
                    + ". Columns found: " + header + ".");
        }
        if (rows.size() - 1 > MAX_ROWS) {
            throw new InvalidDatasetException("Too many rows (" + (rows.size() - 1) + "); the limit is " + MAX_ROWS + ".");
        }

        List<MaterialRecord> records = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int totalWarnings = 0;
        Set<String> seenKeys = new HashSet<>();

        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            int fileRow = r + 1; // header is row 1
            String cpse = cell(row, cpseCol);
            String code = cell(row, codeCol);
            String description = cell(row, descCol);
            String uom = uomCol < 0 ? "" : cell(row, uomCol);

            String problem = null;
            if (cpse.isEmpty()) {
                problem = "cpse is blank";
            } else if (code.isEmpty()) {
                problem = "code is blank";
            } else if (description.isEmpty()) {
                problem = "description is blank";
            } else if (!seenKeys.add(cpse.toLowerCase(Locale.ROOT) + "\u0000" + code.toLowerCase(Locale.ROOT))) {
                problem = "duplicate code '" + code + "' for " + cpse;
            }

            if (problem != null) {
                totalWarnings++;
                if (warnings.size() < MAX_WARNINGS_KEPT) {
                    warnings.add("Row " + fileRow + " skipped: " + problem + ".");
                }
                continue;
            }
            records.add(new MaterialRecord(records.size() + 1, cpse, code, description, uom));
        }

        if (records.isEmpty()) {
            throw new InvalidDatasetException("The file has a header but no valid data rows ("
                    + totalWarnings + " row(s) were skipped).");
        }
        return new ParseResult(records, warnings, totalWarnings);
    }

    // ---------------------------------------------------------------- internals

    private static int indexOf(List<String> header, String... names) {
        for (int i = 0; i < header.size(); i++) {
            String col = header.get(i).trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            for (String name : names) {
                String cleanName = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (col.equals(cleanName)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String cell(List<String> row, int index) {
        return index < row.size() ? row.get(index).trim() : "";
    }

    /** Small state machine: handles quoted fields, escaped quotes, CRLF, and blank lines. */
    public static List<List<String>> readRows(String text) {
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int n = text.length();

        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.length() == 0) {
                inQuotes = true; // a quote only opens a quoted field at the START of a field;
                                 // elsewhere (e.g. 2" valve) it is just an inch mark
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                addUnlessBlank(rows, row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (inQuotes) {
            throw new InvalidDatasetException("A quoted field is never closed (unterminated quote). Check the file for a stray \" character.");
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            addUnlessBlank(rows, row);
        }
        return rows;
    }

    private static void addUnlessBlank(List<List<String>> rows, List<String> row) {
        boolean blank = true;
        for (String f : row) {
            if (!f.isBlank()) {
                blank = false;
                break;
            }
        }
        if (!blank) {
            rows.add(row);
        }
    }
}
