package harmonizer.matching;

import java.util.List;

/**
 * One specification pattern that KMP located inside a description.
 *
 * @param pattern       the literal pattern that was searched for (for example {@code "A106-B"})
 * @param specification canonical specification the pattern belongs to (for example {@code "ASTM A106"})
 * @param positions     zero-based start indices of every occurrence in the description
 * @param comparisons   character comparisons KMP spent finding it (LPS build + search)
 */
public record SpecHit(String pattern, String specification, List<Integer> positions, long comparisons) {

    public SpecHit {
        positions = positions == null ? List.of() : List.copyOf(positions);
    }

    /** Zero-based index of the first occurrence, or -1 when the pattern does not occur. */
    public int firstPosition() {
        return positions.isEmpty() ? -1 : positions.get(0);
    }
}
