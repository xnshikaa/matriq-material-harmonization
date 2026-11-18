package harmonizer.matching;

import java.util.List;
import java.util.Locale;

/**
 * Compares two records attribute by attribute and returns {@link AttributeStates}.
 *
 * <p>Single source of truth for the verification rules, so the matching stage (which builds edges)
 * and the grouping stage (which validates a whole component) can never disagree about what a
 * conflict is.
 *
 * <ul>
 *   <li>text attributes: both known and equal → MATCH; both known and different → CONFLICT;
 *       missing on either side → UNKNOWN</li>
 *   <li>dimensions: both known and within 2% relative tolerance → MATCH; both known and further
 *       apart → CONFLICT; missing on either side → UNKNOWN</li>
 *   <li>specification: equal canonical value or a shared KMP hit → MATCH; both known and unrelated →
 *       CONFLICT; missing on either side → UNKNOWN</li>
 * </ul>
 */
public final class AttributeComparison {

    /** Sizes within 2% are treated as the same nominal size (6 inch = 152.4 mm vs 150 mm). */
    public static final double SIZE_TOLERANCE = 0.02;

    private AttributeComparison() {
    }

    public static AttributeStates compare(MaterialAttributes a, MaterialAttributes b,
                                          List<SpecEvidence> sharedSpecifications) {
        return new AttributeStates(
                textState(a.itemType(), b.itemType()),
                textState(a.form(), b.form()),
                textState(a.metal(), b.metal()),
                textState(a.grade(), b.grade()),
                dimensionState(a.sizeMillimetres(), b.sizeMillimetres()),
                specificationState(a.specification(), b.specification(), sharedSpecifications));
    }

    /** True when two records state at least one attribute that disagrees. */
    public static boolean conflicts(MaterialAttributes a, MaterialAttributes b) {
        return compare(a, b, List.of()).hasConflict();
    }

    // ------------------------------------------------------------------ rules

    private static AttributeState textState(String a, String b) {
        boolean aKnown = a != null && !a.isBlank();
        boolean bKnown = b != null && !b.isBlank();
        if (!aKnown || !bKnown) {
            return AttributeState.UNKNOWN;
        }
        return a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT))
                ? AttributeState.MATCH
                : AttributeState.CONFLICT;
    }

    private static AttributeState dimensionState(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return AttributeState.UNKNOWN;
        }
        double average = (a + b) / 2.0;
        if (average == 0.0) {
            return a == b ? AttributeState.MATCH : AttributeState.CONFLICT;
        }
        return Math.abs(a - b) / average <= SIZE_TOLERANCE
                ? AttributeState.MATCH
                : AttributeState.CONFLICT;
    }

    private static AttributeState specificationState(String a, String b,
                                                     List<SpecEvidence> sharedSpecifications) {
        if (a == null || b == null) {
            return AttributeState.UNKNOWN;
        }
        if (a.equalsIgnoreCase(b)) {
            return AttributeState.MATCH;
        }
        return sharedSpecifications == null || sharedSpecifications.isEmpty()
                ? AttributeState.CONFLICT
                : AttributeState.MATCH;
    }
}
