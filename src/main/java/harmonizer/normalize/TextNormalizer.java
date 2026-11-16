package harmonizer.normalize;

import harmonizer.algo.trie.Trie;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns messy material descriptions into a canonical form so equivalent items look identical.
 *
 * <p>Pipeline: lower-case, convert lengths to millimetres, strip punctuation, split into tokens,
 * expand abbreviations (looked up in a {@link Trie}), drop stop-words.
 * {@link #signature(String)} then sorts and de-duplicates the tokens, so word order does not matter.
 *
 * <p>Example: "Pipe, seamless, 6 inch, ASTM A106 Gr.B" and "SEAMLESS PIPE 152.4 mm A106-B"
 * both produce the signature "152.4mm a106 b pipe seamless".
 *
 * <p>Known limits (state these in the report): no spelling correction, no synonyms beyond the
 * abbreviation table, and inch-to-mm is a plain x25.4 (real pipe "nominal sizes" such as
 * 6 inch = DN150 need an engineering table). This is deterministic matching, not machine learning.
 */
public final class TextNormalizer {

    /** Inputs longer than this are truncated before processing (guards against huge cells). */
    public static final int MAX_TEXT_LENGTH = 2_000;

    private static final String NUM = "(\\d+/\\d+|\\d+(?:\\.\\d+)?)";
    // (?<![a-z0-9.]) stops "a106 in stock" from being read as "106 in".
    private static final Pattern INCH = Pattern.compile("(?<![a-z0-9.])" + NUM + "\\s*(?:inches|inch|in|\")(?![a-z0-9])");
    private static final Pattern CM = Pattern.compile("(?<![a-z0-9.])" + NUM + "\\s*cm(?![a-z0-9])");
    private static final Pattern MM = Pattern.compile("(?<![a-z0-9.])" + NUM + "\\s*mm(?![a-z0-9])");
    private static final Pattern NON_TOKEN_CHARS = Pattern.compile("[^a-z0-9.]");
    private static final Pattern LOOSE_DOT = Pattern.compile("(?<!\\d)\\.|\\.(?!\\d)");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final Trie<String> abbreviations = new Trie<>();
    private final Set<String> stopWords;

    public TextNormalizer(Map<String, String> abbreviationTable, Set<String> stopWords) {
        for (Map.Entry<String, String> e : abbreviationTable.entrySet()) {
            abbreviations.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().toLowerCase(Locale.ROOT));
        }
        this.stopWords = Set.copyOf(stopWords);
    }

    /** Sensible defaults for engineering materials. Extend the table to widen coverage. */
    public static TextNormalizer withDefaults() {
        Map<String, String> abbreviations = Map.ofEntries(
                Map.entry("smls", "seamless"),
                Map.entry("erw", "electric resistance welded"),
                Map.entry("cs", "carbon steel"),
                Map.entry("ss", "stainless steel"),
                Map.entry("gi", "galvanized iron"),
                Map.entry("ms", "mild steel"),
                Map.entry("sch", "schedule"),
                Map.entry("cl", "class"),
                Map.entry("dia", "diameter"),
                Map.entry("nb", "nominal bore"),
                Map.entry("qty", "quantity"),
                Map.entry("pcs", "pieces"));
        Set<String> stopWords = Set.of("astm", "gr", "grade", "as", "per", "the", "of", "and", "for", "with");
        return new TextNormalizer(abbreviations, stopWords);
    }

    /**
     * Readable cleaned text (lower-case, lengths in mm, no punctuation) with word order kept.
     * Used for autocomplete keys, so it must stay human-friendly.
     */
    public String clean(String text) {
        if (text == null) {
            return "";
        }
        String s = text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
        s = s.toLowerCase(Locale.ROOT);
        s = INCH.matcher(s).replaceAll(r -> toMillimetres(r, 25.4));
        s = CM.matcher(s).replaceAll(r -> toMillimetres(r, 10.0));
        s = MM.matcher(s).replaceAll(r -> toMillimetres(r, 1.0));
        s = NON_TOKEN_CHARS.matcher(s).replaceAll(" ");
        s = LOOSE_DOT.matcher(s).replaceAll(" ");
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    /** Cleaned, abbreviation-expanded tokens with stop-words removed, in original order. */
    public List<String> tokens(String text) {
        String cleaned = clean(text);
        List<String> out = new ArrayList<>();
        if (cleaned.isEmpty()) {
            return out;
        }
        for (String token : cleaned.split(" ")) {
            String expansion = abbreviations.get(token); // Trie lookup
            if (expansion == null) {
                addIfUseful(out, token);
            } else {
                for (String word : expansion.split(" ")) {
                    addIfUseful(out, word);
                }
            }
        }
        return out;
    }

    /**
     * Canonical key for "is this the same item?": sorted, de-duplicated tokens joined by spaces.
     * Returns "" when nothing usable remains; callers must treat that as invalid, never as a match.
     */
    public String signature(String text) {
        return String.join(" ", new TreeSet<>(tokens(text)));
    }

    // ---------------------------------------------------------------- internals

    private void addIfUseful(List<String> out, String token) {
        if (!token.isEmpty() && !stopWords.contains(token)) {
            out.add(token);
        }
    }

    private static String toMillimetres(MatchResult match, double factor) {
        String raw = match.group(1);
        double value;
        int slash = raw.indexOf('/');
        if (slash > 0) {
            double denominator = Double.parseDouble(raw.substring(slash + 1));
            if (denominator == 0.0) {
                return Matcher.quoteReplacement(match.group());
            }
            value = Double.parseDouble(raw.substring(0, slash)) / denominator;
        } else {
            value = Double.parseDouble(raw);
        }
        double mm = value * factor;
        if (!Double.isFinite(mm) || mm > 1.0e7) {
            return Matcher.quoteReplacement(match.group());
        }
        String formatted = BigDecimal.valueOf(mm).setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
        return formatted + "mm";
    }
}
