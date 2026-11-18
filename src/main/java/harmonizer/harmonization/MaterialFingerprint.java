package harmonizer.harmonization;

import harmonizer.matching.MaterialAttributes;

import java.util.List;
import java.util.Locale;

/**
 * Canonical identity of a material based on its <em>attributes</em>, not on which company codes
 * happen to be in the group.
 *
 * <p>National material codes are keyed by this fingerprint, so a group keeps its code when a new
 * equivalent record joins it: the attributes did not change, only the membership. See
 * {@link NationalCodeRegistry} and {@code docs/DSU.md}.
 *
 * <p>The fingerprint is deterministic: type, form, metal, specification, grade and size (mm, one
 * decimal). Unknown values are represented by {@code "?"}, so "no metal stated" is a different
 * fingerprint from "carbon steel".
 */
public final class MaterialFingerprint {

    private MaterialFingerprint() {
    }

    /** Deterministic key for one set of attributes. */
    public static String of(MaterialAttributes attributes) {
        return String.join("|",
                normalize(attributes.itemType()),
                normalize(attributes.form()),
                normalize(attributes.metal()),
                normalize(attributes.specification()),
                normalize(attributes.grade()),
                sizeKey(attributes.sizeMillimetres()));
    }

    /**
     * Combines the members of a conflict-free group into one attribute view: the first known value
     * per attribute, in member order. Because the group has been validated to contain no conflicts,
     * all known values for an attribute are equal, so the choice does not matter.
     */
    public static MaterialAttributes merge(List<MaterialAttributes> members) {
        String itemType = null;
        String form = null;
        String metal = null;
        String specification = null;
        String grade = null;
        double size = MaterialAttributes.UNKNOWN_SIZE;

        for (MaterialAttributes attributes : members) {
            if (attributes == null) {
                continue;
            }
            if (itemType == null) {
                itemType = attributes.itemType();
            }
            if (form == null) {
                form = attributes.form();
            }
            if (metal == null) {
                metal = attributes.metal();
            }
            if (specification == null) {
                specification = attributes.specification();
            }
            if (grade == null) {
                grade = attributes.grade();
            }
            if (Double.isNaN(size) && attributes.hasSize()) {
                size = attributes.sizeMillimetres();
            }
        }
        return new MaterialAttributes(itemType, form, metal, size, specification, grade);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "?" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String sizeKey(double millimetres) {
        return Double.isNaN(millimetres) ? "?" : String.format(Locale.ROOT, "%.1fmm", millimetres);
    }

    /**
     * Parses a fingerprint string back into {@link MaterialAttributes}.
     * Returns null if the fingerprint is null or invalid.
     */
    public static MaterialAttributes parse(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return null;
        }
        String[] parts = fingerprint.split("\\|", -1);
        if (parts.length < 6) {
            return null;
        }
        String itemType = "?".equals(parts[0]) ? null : parts[0];
        String form = "?".equals(parts[1]) ? null : parts[1];
        String metal = "?".equals(parts[2]) ? null : parts[2];
        String specification = "?".equals(parts[3]) ? null : parts[3];
        String grade = "?".equals(parts[4]) ? null : parts[4].toUpperCase(Locale.ROOT);
        double size = MaterialAttributes.UNKNOWN_SIZE;
        if (!"?".equals(parts[5])) {
            String clean = parts[5].replaceAll("(?i)mm$", "").trim();
            try {
                size = Double.parseDouble(clean);
            } catch (NumberFormatException ignored) {
            }
        }
        return new MaterialAttributes(itemType, form, metal, size, specification, grade);
    }
}
