package harmonizer.matching;

import harmonizer.model.MaterialRecord;
import harmonizer.normalize.TextNormalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads comparable attributes out of a raw material description.
 *
 * <p>It reuses the project's {@link TextNormalizer} for the messy part — lower-casing, punctuation,
 * inch/cm/millimetre conversion and abbreviation expansion — then picks out the item type, form,
 * metal, nominal size and grade. The technical specification comes from
 * {@link KmpSpecificationExtractor}, i.e. from KMP rather than from {@code contains} checks.
 *
 * <p>Example: {@code "Pipe, seamless, 6 inch, ASTM A106 Gr.B"} becomes
 * {@code itemType=pipe, form=seamless, metal=null, size=152.4mm, specification=ASTM A106, grade=B}.
 *
 * <p>Limits (also stated in docs/INTEGRATION.md): only the catalogued item types, forms, metals and
 * standards are recognised. An unrecognised description yields {@code null} attributes, and a pair
 * with an unknown attribute is never treated as verified—it is only allowed to pass when the
 * attribute is genuinely absent on both records.
 */
public final class AttributeExtractor {

    private static final Set<String> ITEM_TYPES =
            Set.of("pipe", "valve", "flange", "elbow", "tee", "reducer", "gasket", "bolt", "pump");

    private static final Set<String> METAL_PHRASES =
            Set.of("carbon steel", "stainless steel", "galvanized iron", "mild steel", "alloy steel");

    /**
     * Single-token specification codes (a106, a53, ss316, ...). Used to read the grade that follows
     * a code written without the word "grade", e.g. "A106-B" or "A182 F304".
     */
    private static final Set<String> SPEC_CODES = SpecCatalogue.DEFAULT.stream()
            .map(SpecCatalogue.SpecPattern::pattern)
            .map(pattern -> pattern.toLowerCase(Locale.ROOT))
            .filter(pattern -> pattern.matches("[a-z0-9.]+"))
            .collect(Collectors.toUnmodifiableSet());

    private final TextNormalizer normalizer;
    private final KmpSpecificationExtractor specifications;

    public AttributeExtractor() {
        this(TextNormalizer.withDefaults(), new KmpSpecificationExtractor());
    }

    public AttributeExtractor(TextNormalizer normalizer, KmpSpecificationExtractor specifications) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.specifications = Objects.requireNonNull(specifications, "specifications");
    }

    /** Convenience overload: attributes for an uploaded record. */
    public MaterialAttributes extract(MaterialRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        return extract(record.description());
    }

    /**
     * Attributes for a free-text description.
     *
     * <p>Two token views are used on purpose: the cleaned tokens (units converted, punctuation gone,
     * stop words kept) drive item type, size and grade, while the abbreviation-expanded tokens drive
     * form and metal, so that "CS" becomes "carbon steel" and "smls" becomes "seamless".
     */
    public MaterialAttributes extract(String description) {
        String cleaned = description == null ? "" : normalizer.clean(description);
        List<String> tokens = cleaned.isEmpty() ? List.of() : List.of(cleaned.split(" "));
        List<String> expanded = description == null ? List.of() : normalizer.tokens(description);

        return new MaterialAttributes(
                itemType(tokens),
                form(expanded),
                metal(expanded),
                sizeMillimetres(tokens),
                specifications.firstSpecification(description).orElse(null),
                grade(tokens));
    }

    // ------------------------------------------------------------------ attribute readers

    private static String itemType(List<String> tokens) {
        for (String token : tokens) {
            if (ITEM_TYPES.contains(token)) {
                return token;
            }
        }
        return null;
    }

    private static String form(List<String> tokens) {
        if (tokens.contains("seamless")) {
            return "seamless";
        }
        if (tokens.contains("welded")) {
            return "welded";
        }
        return null;
    }

    private static String metal(List<String> tokens) {
        for (int i = 0; i + 1 < tokens.size(); i++) {
            String phrase = tokens.get(i) + " " + tokens.get(i + 1);
            if (METAL_PHRASES.contains(phrase)) {
                return phrase;
            }
        }
        return null;
    }

    /** TextNormalizer has already converted inches/centimetres to millimetres and appended "mm". */
    private static double sizeMillimetres(List<String> tokens) {
        for (String token : tokens) {
            if (!token.endsWith("mm") || token.length() <= 2) {
                continue;
            }
            try {
                double value = Double.parseDouble(token.substring(0, token.length() - 2));
                if (value > 0 && Double.isFinite(value)) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // not a size token; keep looking
            }
        }
        return MaterialAttributes.UNKNOWN_SIZE;
    }

    /**
     * Grade from "gr B" / "grade B", or from the token right after a bare specification code
     * ("A106-B", "A182 F304"). Returns an upper-case grade or null.
     */
    private static String grade(List<String> tokens) {
        for (int i = 0; i + 1 < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.equals("gr") || token.equals("grade")) {
                return tokens.get(i + 1).toUpperCase(Locale.ROOT);
            }
        }
        for (int i = 0; i + 1 < tokens.size(); i++) {
            if (SPEC_CODES.contains(tokens.get(i)) && isGradeToken(tokens.get(i + 1))) {
                return tokens.get(i + 1).toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    /** A grade is a short alphanumeric code such as "b", "c", "f304" — not a number or a size. */
    private static boolean isGradeToken(String token) {
        return token.length() <= 4
                && token.matches("[a-z0-9]+")
                && !token.matches("\\d+")
                && !token.endsWith("mm")
                && !ITEM_TYPES.contains(token);
    }
}
