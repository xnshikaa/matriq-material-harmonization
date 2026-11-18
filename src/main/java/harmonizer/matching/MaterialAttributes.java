package harmonizer.matching;

/**
 * The attributes MATRIQ reads out of a free-text material description.
 *
 * <p>Unknown values are {@code null} (or {@link #UNKNOWN_SIZE} for the dimension) rather than
 * guesses: a missing attribute is treated as "cannot confirm" and never as a match.
 *
 * @param itemType         pipe / valve / flange / elbow / tee / reducer / gasket / bolt, or null
 * @param form             seamless or welded, or null
 * @param metal            carbon steel / stainless steel / galvanized iron / mild steel / alloy steel, or null
 * @param sizeMillimetres  nominal size converted to millimetres, or {@link #UNKNOWN_SIZE}
 * @param specification    canonical technical specification found by KMP, or null
 * @param grade            material grade (B, C, F304, ...) upper-cased, or null
 */
public record MaterialAttributes(String itemType, String form, String metal,
                                 double sizeMillimetres, String specification, String grade) {

    /** Marker for "the description does not state a size". */
    public static final double UNKNOWN_SIZE = Double.NaN;

    public boolean hasSize() {
        return !Double.isNaN(sizeMillimetres);
    }

    public boolean hasSpecification() {
        return specification != null;
    }

    public boolean hasGrade() {
        return grade != null;
    }
}
