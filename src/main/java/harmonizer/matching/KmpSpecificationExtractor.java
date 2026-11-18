package harmonizer.matching;

import harmonizer.algo.kmp.KMPMatcher;
import harmonizer.algo.kmp.KMPResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Locates technical specifications inside material descriptions with KMP.
 *
 * <p>Every catalogued spelling is searched separately, so the evidence can say which literal form
 * appeared and where. All searches are case-insensitive and positions are zero-based indices into
 * the original description.
 *
 * <p>Complexity for one description: O(P * (n + m)) with P catalogue patterns, description length n
 * and pattern length m. The catalogue is small and fixed, so this is effectively linear in the text.
 */
public final class KmpSpecificationExtractor {

    private final KMPMatcher matcher;
    private final List<SpecCatalogue.SpecPattern> patterns;

    public KmpSpecificationExtractor() {
        this(new KMPMatcher(), SpecCatalogue.DEFAULT);
    }

    public KmpSpecificationExtractor(KMPMatcher matcher, List<SpecCatalogue.SpecPattern> patterns) {
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.patterns = List.copyOf(Objects.requireNonNull(patterns, "patterns"));
    }

    /**
     * Runs KMP for every catalogued pattern and returns one {@link SpecHit} per pattern that occurs.
     *
     * @param description free-text material description (null/blank gives an empty list)
     */
    public List<SpecHit> extract(String description) {
        if (description == null || description.isBlank()) {
            return List.of();
        }
        List<SpecHit> hits = new ArrayList<>();
        for (SpecCatalogue.SpecPattern pattern : patterns) {
            KMPResult result = matcher.countComparisons(description, pattern.pattern());
            if (result.isMatch()) {
                hits.add(new SpecHit(pattern.pattern(), pattern.canonical(),
                        result.positions(), result.comparisons()));
            }
        }
        return List.copyOf(hits);
    }

    /**
     * Canonical specification names found in the description, de-duplicated and in catalogue order.
     * Example: "Pipe 6 inch ASTM A106-A" gives {@code [ASTM A106]}.
     */
    public List<String> canonicalSpecifications(String description) {
        List<String> found = new ArrayList<>();
        for (SpecHit hit : extract(description)) {
            if (!found.contains(hit.specification())) {
                found.add(hit.specification());
            }
        }
        return List.copyOf(found);
    }

    /**
     * The specification named earliest in the description (first occurrence wins; catalogue order
     * breaks ties). This is the specification MATRIQ records for the record.
     */
    public Optional<String> firstSpecification(String description) {
        SpecHit best = null;
        for (SpecHit hit : extract(description)) {
            if (best == null || hit.firstPosition() < best.firstPosition()) {
                best = hit;
            }
        }
        return best == null ? Optional.empty() : Optional.of(best.specification());
    }

    /** Total comparisons spent across a batch of hits. */
    public static long totalComparisons(List<SpecHit> hits) {
        long total = 0L;
        for (SpecHit hit : hits) {
            total += hit.comparisons();
        }
        return total;
    }
}
