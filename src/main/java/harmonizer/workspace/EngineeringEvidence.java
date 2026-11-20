package harmonizer.workspace;

import harmonizer.matching.AttributeState;
import harmonizer.matching.MatchEvidence;
import harmonizer.matching.MaterialAttributes;
import harmonizer.normalize.TextNormalizer;
import java.util.*;
import java.util.regex.Pattern;

final class EngineeringEvidence {
    private static final TextNormalizer DIMENSION_NORMALIZER = TextNormalizer.withDefaults();
    private static final Pattern PRESSURE = Pattern.compile(
            "(?i)\\b(?:CLASS|CL[.]?|#)\\s*(150|300|400|600|900|1500|2500|4500)\\b|\\b(150|300|400|600|900|1500|2500|4500)\\s*#");
    private static final Pattern STANDARD = Pattern.compile(
            "(?i)\\b(?:API|IS|ASTM|ASME|DIN|ISO|EN|ANSI)\\s*[-:]?\\s*[A-Z]?[0-9]+(?:[.][0-9]+)?\\b");
    private static final Pattern FORM = Pattern.compile(
            "(?i)\\b(?:SEAMLESS|SMLS|ERW|EFW|SAW|LSAW|SSAW|WELDED|FORGED|CAST)\\b");

    private static final Pattern PUMP = Pattern.compile("(?i)\\bPUMP(?:S)?\\b");

    record Value(String name, String left, String right, AttributeState state) {}
    record Evidence(List<Value> attributes, List<String> sharedSpecifications, boolean approvable, String summary) {
        boolean conflict() {
            return attributes.stream().anyMatch(value -> value.state() == AttributeState.CONFLICT);
        }
    }

    static Evidence compare(CatalogueImport.Source left, CatalogueImport.Source right, MatchEvidence base) {
        MaterialAttributes a = base.attributesA();
        MaterialAttributes b = base.attributesB();
        var states = base.attributeStates();
        String textA = left.material().description();
        String textB = right.material().description();
        var dimensions = dimension(left, right, a, b, states.dimensions());
        var values = List.of(
                extracted("Material type", a.itemType(), b.itemType(), states.type()),
                extracted("Metal", a.metal(), b.metal(), states.metal()),
                dimensions,
                explicit("Catalogue grade", left.grade(), right.grade()),
                extracted("Description grade", a.grade(), b.grade(), states.grade()),
                extracted("Description standard", a.specification(), b.specification(), states.specification()),
                tokens("Declared standards", STANDARD, left.specification() + " " + textA, right.specification() + " " + textB),
                extracted("Form", a.form(), b.form(), states.form()),
                tokens("Manufacturing method", FORM, textA, textB),
                pressure(left, right));
        var conflicts = values.stream().filter(v -> v.state() == AttributeState.CONFLICT)
                .map(v -> v.name() + " (" + v.left() + " vs " + v.right() + ")").toList();
        boolean enoughDimensions = dimensions.state() == AttributeState.MATCH;
        boolean approvable = base.verified() && conflicts.isEmpty() && enoughDimensions
                && !PUMP.matcher(textA).find() && !PUMP.matcher(textB).find();
        String summary = conflicts.isEmpty()
                ? approvable ? "The technical evidence supports automatic unification." : "Technical evidence is insufficient for unification."
                : "Conflicting attributes: " + String.join(", ", conflicts);
        return new Evidence(values, base.sharedSpecifications().stream().map(spec -> spec.specification()).toList(), approvable, summary);
    }

    private static Value dimension(CatalogueImport.Source left, CatalogueImport.Source right,
                                   MaterialAttributes a, MaterialAttributes b, AttributeState state) {
        if (!left.dimensions().isBlank() && !right.dimensions().isBlank()) {
            double leftMillimetres = dimensionMillimetres(left.dimensions());
            double rightMillimetres = dimensionMillimetres(right.dimensions());
            if (state == AttributeState.CONFLICT) {
                return new Value("Dimensions", shownDimension(left, a), shownDimension(right, b), AttributeState.CONFLICT);
            }
            if (Double.isFinite(leftMillimetres) && Double.isFinite(rightMillimetres)) {
                double average = (leftMillimetres + rightMillimetres) / 2.0;
                boolean match = average == 0.0 ? leftMillimetres == rightMillimetres
                        : Math.abs(leftMillimetres - rightMillimetres) / average <= 0.02;
                return new Value("Dimensions", shownDimension(left, a), shownDimension(right, b),
                        match ? AttributeState.MATCH : AttributeState.CONFLICT);
            }
            var declared = explicit("Dimensions", left.dimensions(), right.dimensions());
            if (declared.state() == AttributeState.MATCH) return declared;
            if (state == AttributeState.MATCH) {
                return new Value("Dimensions", shownDimension(left, a), shownDimension(right, b), AttributeState.MATCH);
            }
            return new Value("Dimensions", shownDimension(left, a), shownDimension(right, b), AttributeState.UNKNOWN);
        }
        return extracted("Dimensions", shownDimension(left, a), shownDimension(right, b), state);
    }

    /** Parse explicit length fields through the same inch/cm/mm normalization used by matching. */
    private static double dimensionMillimetres(String value) {
        String normalized = DIMENSION_NORMALIZER.clean(value);
        for (String token : normalized.split(" ")) {
            if (!token.endsWith("mm") || token.length() <= 2) continue;
            try {
                double millimetres = Double.parseDouble(token.substring(0, token.length() - 2));
                if (millimetres > 0 && Double.isFinite(millimetres)) return millimetres;
            } catch (NumberFormatException ignored) {
                // This field may contain non-dimensional catalogue text; leave it unknown.
            }
        }
        return Double.NaN;
    }

    private static String shownDimension(CatalogueImport.Source source, MaterialAttributes attributes) {
        return source.dimensions().isBlank() && attributes.hasSize()
                ? attributes.sizeMillimetres() + " mm" : source.dimensions();
    }

    private static Value pressure(CatalogueImport.Source left, CatalogueImport.Source right) {
        String a = CatalogueImport.named(left.columns(), "PressureClass", "PressureRating", "Pressure Class");
        String b = CatalogueImport.named(right.columns(), "PressureClass", "PressureRating", "Pressure Class");
        return tokens("Pressure class", PRESSURE,
                left.material().description() + " " + (a.matches("[0-9]+") ? "Class " + a : a),
                right.material().description() + " " + (b.matches("[0-9]+") ? "Class " + b : b));
    }

    private static Value tokens(String name, Pattern pattern, String left, String right) {
        return explicit(name, find(pattern, left), find(pattern, right));
    }

    private static String find(Pattern pattern, String text) {
        var values = new TreeSet<String>();
        var matches = pattern.matcher(text);
        while (matches.find()) {
            String value = matches.group();
            if (pattern == PRESSURE) value = value.replaceAll("[^0-9]", "");
            if (pattern == FORM && value.equalsIgnoreCase("SMLS")) value = "SEAMLESS";
            values.add(value.toUpperCase(Locale.ROOT).replaceAll("[\\s:-]+", ""));
        }
        return String.join(", ", values);
    }

    private static Value explicit(String name, String left, String right) {
        var state = left.isBlank() || right.isBlank() ? AttributeState.UNKNOWN
                : normalize(left).equals(normalize(right)) ? AttributeState.MATCH : AttributeState.CONFLICT;
        return new Value(name, left, right, state);
    }

    private static String normalize(String value) {
        return value.replaceAll("[\\s-]+", "").toUpperCase(Locale.ROOT);
    }

    private static Value extracted(String name, String left, String right, AttributeState state) {
        return new Value(name, left == null ? "" : left, right == null ? "" : right, state);
    }
}
