package com.matriq.normalization;

import com.matriq.algorithms.trie.MaterialTrie;
import com.matriq.model.MaterialRecord;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes material descriptions and attributes.
 *
 * IMPORTANT RULES:
 *   1. Original descriptions are NEVER modified.
 *   2. Only safe normalization is performed — technical information is preserved.
 *   3. Unit conversion uses explicit tolerances — not assumed equivalence.
 *   4. Both original and normalized values are stored.
 */
@Service
public class NormalizationService {

    /** Abbreviation trie — loaded with known industrial abbreviations. */
    private final MaterialTrie abbreviationTrie;

    /** Unit conversion: maps variant spellings to canonical form. */
    private static final Map<String, String> UNIT_ALIASES = new HashMap<>();

    /** Material category keywords. */
    private static final Map<String, String> CATEGORY_KEYWORDS = new HashMap<>();

    /** Material type keywords. */
    private static final Map<String, String> TYPE_KEYWORDS = new HashMap<>();

    /** Common industrial abbreviations: short → expanded form */
    private static final Map<String, String> ABBREVIATIONS = new HashMap<>();

    static {
        // Unit normalization (case-insensitive keys applied after lower-casing)
        UNIT_ALIASES.put("inch", "INCH");
        UNIT_ALIASES.put("inches", "INCH");
        UNIT_ALIASES.put("in", "INCH");
        UNIT_ALIASES.put("\"", "INCH");
        UNIT_ALIASES.put("mm", "MM");
        UNIT_ALIASES.put("millimeter", "MM");
        UNIT_ALIASES.put("millimetre", "MM");
        UNIT_ALIASES.put("cm", "CM");
        UNIT_ALIASES.put("meter", "M");
        UNIT_ALIASES.put("metre", "M");
        UNIT_ALIASES.put("m", "M");
        UNIT_ALIASES.put("kg", "KG");
        UNIT_ALIASES.put("kilogram", "KG");
        UNIT_ALIASES.put("nos", "NOS");
        UNIT_ALIASES.put("no.", "NOS");
        UNIT_ALIASES.put("numbers", "NOS");

        // Material categories
        CATEGORY_KEYWORDS.put("pipe", "PIPE");
        CATEGORY_KEYWORDS.put("valve", "VALVE");
        CATEGORY_KEYWORDS.put("fitting", "FITTING");
        CATEGORY_KEYWORDS.put("flange", "FLANGE");
        CATEGORY_KEYWORDS.put("pump", "PUMP");
        CATEGORY_KEYWORDS.put("bolt", "FASTENER");
        CATEGORY_KEYWORDS.put("nut", "FASTENER");
        CATEGORY_KEYWORDS.put("gasket", "GASKET");
        CATEGORY_KEYWORDS.put("elbow", "FITTING");
        CATEGORY_KEYWORDS.put("tee", "FITTING");
        CATEGORY_KEYWORDS.put("reducer", "FITTING");
        CATEGORY_KEYWORDS.put("coupling", "FITTING");
        CATEGORY_KEYWORDS.put("cable", "ELECTRICAL");
        CATEGORY_KEYWORDS.put("wire", "ELECTRICAL");

        // Material types
        TYPE_KEYWORDS.put("seamless", "SEAMLESS");
        TYPE_KEYWORDS.put("welded", "WELDED");
        TYPE_KEYWORDS.put("erw", "WELDED");
        TYPE_KEYWORDS.put("flanged", "FLANGED");
        TYPE_KEYWORDS.put("screwed", "SCREWED");
        TYPE_KEYWORDS.put("butt weld", "BUTT_WELD");
        TYPE_KEYWORDS.put("socket weld", "SOCKET_WELD");
        TYPE_KEYWORDS.put("threaded", "THREADED");

        // Industrial abbreviations
        ABBREVIATIONS.put("ss", "STAINLESS STEEL");
        ABBREVIATIONS.put("cs", "CARBON STEEL");
        ABBREVIATIONS.put("ms", "MILD STEEL");
        ABBREVIATIONS.put("gi", "GALVANIZED IRON");
        ABBREVIATIONS.put("ci", "CAST IRON");
        ABBREVIATIONS.put("di", "DUCTILE IRON");
        ABBREVIATIONS.put("nb", "NOMINAL BORE");
        ABBREVIATIONS.put("od", "OUTER DIAMETER");
        ABBREVIATIONS.put("id", "INNER DIAMETER");
        ABBREVIATIONS.put("thk", "THICKNESS");
        ABBREVIATIONS.put("sch", "SCHEDULE");
        ABBREVIATIONS.put("std", "STANDARD");
        ABBREVIATIONS.put("xl", "EXTRA LIGHT");
        ABBREVIATIONS.put("xs", "EXTRA STRONG");
        ABBREVIATIONS.put("xxs", "DOUBLE EXTRA STRONG");
        ABBREVIATIONS.put("gr", "GRADE");
        ABBREVIATIONS.put("gr.", "GRADE");
        ABBREVIATIONS.put("spec", "SPECIFICATION");
        ABBREVIATIONS.put("conf", "CONFORMING TO");
        ABBREVIATIONS.put("moc", "MATERIAL OF CONSTRUCTION");
    }

    public NormalizationService() {
        this.abbreviationTrie = new MaterialTrie();
        ABBREVIATIONS.forEach(this.abbreviationTrie::insert);
    }

    /**
     * Normalizes a MaterialRecord in-place.
     * Sets normalizedDescription, materialCategory, materialType, unit, grade,
     * technicalSpecification. Never touches the original fields.
     *
     * @param record the record to normalize (modified in-place)
     */
    public void normalize(MaterialRecord record) {
        if (record == null || record.getOriginalDescription() == null) return;

        String desc = record.getOriginalDescription();

        // Step 1: Basic text cleaning (safe operations only)
        String normalized = cleanText(desc);

        // Step 2: Expand known abbreviations using Trie
        normalized = expandAbbreviations(normalized);

        // Step 3: Extract and normalize technical specification
        String spec = extractSpecification(normalized);
        if (spec != null && (record.getTechnicalSpecification() == null || record.getTechnicalSpecification().isBlank())) {
            record.setTechnicalSpecification(spec);
        }

        // Step 4: Extract grade
        String grade = extractGrade(normalized);
        if (grade != null && (record.getGrade() == null || record.getGrade().isBlank())) {
            record.setGrade(grade);
        }

        // Step 5: Classify material category
        String category = classifyCategory(normalized);
        if (record.getMaterialCategory() == null || record.getMaterialCategory().isBlank()) {
            record.setMaterialCategory(category);
        }

        // Step 6: Classify material type
        String type = classifyType(normalized);
        if (record.getMaterialType() == null || record.getMaterialType().isBlank()) {
            record.setMaterialType(type);
        }

        // Step 7: Extract dimensions
        String dims = extractDimensions(normalized);
        if (dims != null && (record.getDimensions() == null || record.getDimensions().isBlank())) {
            record.setDimensions(dims);
        }

        // Step 8: Extract unit
        String unit = extractUnit(normalized);
        if (unit != null && (record.getUnit() == null || record.getUnit().isBlank())) {
            record.setUnit(unit);
        }

        record.setNormalizedDescription(normalized);
        record.setStatus(MaterialRecord.ImportStatus.NORMALIZED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 1: Basic text cleaning
    // ─────────────────────────────────────────────────────────────────────────

    private String cleanText(String text) {
        // Remove leading/trailing whitespace
        String result = text.strip();
        // Collapse multiple spaces into one
        result = result.replaceAll("\\s+", " ");
        // Remove trailing punctuation that adds no information
        result = result.replaceAll("[,;.]+$", "");
        // Normalize common separators (but NOT dashes, which appear in grades like A106-B)
        result = result.replaceAll("\\s*,\\s*", ", ");
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 2: Abbreviation expansion using Trie
    // ─────────────────────────────────────────────────────────────────────────

    private String expandAbbreviations(String text) {
        // Split into tokens and expand known abbreviations
        String[] tokens = text.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            String lower = token.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            String expanded = abbreviationTrie.getValue(lower);
            if (expanded != null && !expanded.equalsIgnoreCase(token)) {
                sb.append(expanded).append(" ");
            } else {
                sb.append(token).append(" ");
            }
        }
        return sb.toString().strip();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 3: Extract technical specification standard
    // ─────────────────────────────────────────────────────────────────────────

    private static final Pattern SPEC_PATTERN = Pattern.compile(
        "\\b(ASTM|ASME|API|IS|BS|DIN|JIS|ANSI|AISI|AWS|ISO)\\s*[-]?\\s*([A-Z0-9]+(?:[-.][A-Z0-9]+)?)",
        Pattern.CASE_INSENSITIVE);

    public String extractSpecification(String text) {
        Matcher m = SPEC_PATTERN.matcher(text);
        if (m.find()) {
            return (m.group(1) + " " + m.group(2)).toUpperCase().replaceAll("\\s+", " ").strip();
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 4: Extract grade
    // ─────────────────────────────────────────────────────────────────────────

    private static final Pattern GRADE_PATTERN = Pattern.compile(
        "(?:GRADE|GR\\.?|GR)\\s*[-.]?\\s*([A-Z0-9]+(?:[/-][A-Z0-9]+)?)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern GRADE_SHORT_PATTERN = Pattern.compile(
        "\\b(SS316|SS304|SS202|A106-B|A53-B|A335|A312|P5|P9|P11|P22|P91)\\b",
        Pattern.CASE_INSENSITIVE);

    public String extractGrade(String text) {
        Matcher m = GRADE_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1).toUpperCase();
        }
        m = GRADE_SHORT_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1).toUpperCase();
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 5: Classify category
    // ─────────────────────────────────────────────────────────────────────────

    private String classifyCategory(String text) {
        String lower = text.toLowerCase();
        for (Map.Entry<String, String> entry : CATEGORY_KEYWORDS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "UNCATEGORIZED";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 6: Classify type
    // ─────────────────────────────────────────────────────────────────────────

    private String classifyType(String text) {
        String lower = text.toLowerCase();
        for (Map.Entry<String, String> entry : TYPE_KEYWORDS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "UNCLASSIFIED";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 7: Extract dimensions
    // ─────────────────────────────────────────────────────────────────────────

    private static final Pattern DIM_PATTERN = Pattern.compile(
        "(\\d+(?:\\.\\d+)?)\\s*(inch|inches|in|mm|cm|meter|metre|m|\")",
        Pattern.CASE_INSENSITIVE);

    public String extractDimensions(String text) {
        Matcher m = DIM_PATTERN.matcher(text);
        StringBuilder dims = new StringBuilder();
        while (m.find()) {
            String value = m.group(1);
            String unit = normalizeUnit(m.group(2));
            if (dims.length() > 0) dims.append(" x ");
            dims.append(value).append(" ").append(unit);
        }
        return dims.length() > 0 ? dims.toString() : null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 8: Extract unit
    // ─────────────────────────────────────────────────────────────────────────

    public String extractUnit(String text) {
        Matcher m = DIM_PATTERN.matcher(text);
        if (m.find()) {
            return normalizeUnit(m.group(2));
        }
        return null;
    }

    public String normalizeUnit(String unit) {
        if (unit == null) return null;
        String key = unit.toLowerCase().strip();
        return UNIT_ALIASES.getOrDefault(key, unit.toUpperCase());
    }

    /**
     * Builds a normalized signature for a material record.
     * Used as the Bloom Filter key.
     */
    public String buildSignature(MaterialRecord record) {
        return String.join("|",
            nvl(record.getMaterialCategory()),
            nvl(record.getMaterialType()),
            nvl(record.getGrade()),
            nvl(record.getTechnicalSpecification()),
            nvl(record.getDimensions())
        ).toLowerCase();
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }

    public MaterialTrie getAbbreviationTrie() {
        return abbreviationTrie;
    }
}
