package harmonizer.matching;

import java.util.List;

/**
 * Technical specification patterns that MATRIQ looks for in material descriptions.
 *
 * <p>Several spellings of the same standard map to one canonical name, so "ASTM A106",
 * "A106-B" and "A106 grade B" all resolve to {@code ASTM A106}. The grade letter itself
 * (B, C, F304, ...) is a separate attribute and is extracted independently, because two pipes can
 * share a specification and still have different grades.
 *
 * <p>The list is deliberately small and explicit: it is a catalogue, not natural-language
 * understanding. Extend it when new standards appear in the catalogues.
 */
public final class SpecCatalogue {

    /**
     * @param pattern   exact text handed to KMP (matched case-insensitively)
     * @param canonical specification name used in evidence and comparisons
     */
    public record SpecPattern(String pattern, String canonical) {
    }

    /** Patterns used by default, ordered from most specific spelling to most generic. */
    public static final List<SpecPattern> DEFAULT = List.of(
            new SpecPattern("ASTM A106", "ASTM A106"),
            new SpecPattern("A106-B", "ASTM A106"),
            new SpecPattern("A106 GRADE B", "ASTM A106"),
            new SpecPattern("A106", "ASTM A106"),
            new SpecPattern("ASTM A53", "ASTM A53"),
            new SpecPattern("A53", "ASTM A53"),
            new SpecPattern("ASTM A105", "ASTM A105"),
            new SpecPattern("A105", "ASTM A105"),
            new SpecPattern("ASTM A182", "ASTM A182"),
            new SpecPattern("A182", "ASTM A182"),
            new SpecPattern("ASTM A312", "ASTM A312"),
            new SpecPattern("A312", "ASTM A312"),
            new SpecPattern("ASTM A335", "ASTM A335"),
            new SpecPattern("A335", "ASTM A335"),
            new SpecPattern("API 5L", "API 5L"),
            new SpecPattern("ANSI B16.5", "ANSI B16.5"),
            new SpecPattern("B16.5", "ANSI B16.5"),
            new SpecPattern("ANSI B16.9", "ANSI B16.9"),
            new SpecPattern("B16.9", "ANSI B16.9"),
            new SpecPattern("ANSI B16.11", "ANSI B16.11"),
            new SpecPattern("IS 1239", "IS 1239"),
            new SpecPattern("IS 3589", "IS 3589"),
            new SpecPattern("DIN 2448", "DIN 2448"),
            new SpecPattern("BS 3601", "BS 3601"),
            new SpecPattern("ASME B31.3", "ASME B31.3"),
            new SpecPattern("SS316", "SS316"),
            new SpecPattern("SS304", "SS304"));

    private SpecCatalogue() {
    }
}
