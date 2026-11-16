package harmonizer.bench;

import harmonizer.model.MaterialRecord;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Generates realistic synthetic material masters for four fictional companies.
 *
 * <p>Every record belongs to a hidden ground-truth group (the real-world material it describes).
 * Records in the same group are written differently: abbreviations, inch/mm/cm sizes, casing,
 * punctuation, word order. Because the truth is known, precision and recall can be measured.
 *
 * <p>Honest caveat for the report: the variations are the ones the normaliser is designed to handle,
 * so near-perfect scores here show the pipeline works as designed, not that it would be perfect on
 * messy real-world data (typos, reworded descriptions).
 *
 * <p>CLI: {@code java -cp target/classes harmonizer.bench.SyntheticMaterialGenerator 10000 0.3 data/sample_10k.csv}
 * (records, duplicate rate between 0 and 1, output path).
 */
public final class SyntheticMaterialGenerator {

    /** Generated records plus the ground truth: groupOfRecord[id - 1] is the group of record {@code id}. */
    public record GeneratedData(List<MaterialRecord> records, int[] groupOfRecord, int groupCount) {
    }

    private static final String[] CPSES = {"CPCL", "NTPC", "SAIL", "COALIND"};
    private static final String[] ITEMS = {"pipe", "valve", "flange", "elbow", "tee", "reducer", "gasket", "bolt"};
    private static final String[] FORMS = {"seamless", "welded"};
    private static final String[][] METALS = {
            {"carbon steel", "cs"}, {"stainless steel", "ss"}, {"galvanized iron", "gi"}, {"mild steel", "ms"}};
    private static final double[] SIZES_INCH = {0.5, 1, 1.5, 2, 3, 4, 6, 8, 10, 12};
    private static final String[][] SPECS = {
            {"a106", "b"}, {"a53", "a"}, {"a106", "c"}, {"a105", null}, {"a182", "f304"}, {"a333", "6"}};
    private static final int[] SCHEDULES = {40, 80, 160};
    private static final int[] CLASSES = {150, 300, 600, 900, 1500};
    private static final String[] UOMS = {"EA", "M", "KG"};

    private SyntheticMaterialGenerator() {
    }

    /**
     * @param n             number of records
     * @param duplicateRate fraction of records that repeat an earlier material (0 = all distinct)
     * @param seed          random seed, so experiments are reproducible
     */
    public static GeneratedData generate(int n, double duplicateRate, long seed) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive");
        }
        if (!(duplicateRate >= 0.0 && duplicateRate < 1.0)) {
            throw new IllegalArgumentException("duplicateRate must be in [0, 1)");
        }
        Random rnd = new Random(seed);
        int groups = Math.max(1, (int) Math.round(n * (1.0 - duplicateRate)));

        // Distinct base materials (a drawing number keeps them unique at any scale).
        List<int[]> bases = new ArrayList<>(groups);
        Set<String> seen = new HashSet<>();
        while (bases.size() < groups) {
            int[] b = {
                    rnd.nextInt(ITEMS.length), rnd.nextInt(FORMS.length), rnd.nextInt(METALS.length),
                    rnd.nextInt(SIZES_INCH.length), rnd.nextInt(SPECS.length), rnd.nextInt(SCHEDULES.length),
                    rnd.nextInt(CLASSES.length), 1000 + rnd.nextInt(9000)};
            if (seen.add(java.util.Arrays.toString(b))) {
                bases.add(b);
            }
        }

        // Each group appears at least once; the rest repeat random groups; then shuffle.
        List<Integer> assignment = new ArrayList<>(n);
        for (int g = 0; g < groups; g++) {
            assignment.add(g);
        }
        for (int i = groups; i < n; i++) {
            assignment.add(rnd.nextInt(groups));
        }
        Collections.shuffle(assignment, rnd);

        List<MaterialRecord> records = new ArrayList<>(n);
        int[] groupOf = new int[n];
        for (int i = 0; i < n; i++) {
            int g = assignment.get(i);
            int id = i + 1;
            String cpse = CPSES[rnd.nextInt(CPSES.length)];
            String code = cpse.substring(0, 2) + "-" + String.format(Locale.ROOT, "%06d", id);
            records.add(new MaterialRecord(id, cpse, code, render(bases.get(g), rnd), UOMS[rnd.nextInt(UOMS.length)]));
            groupOf[i] = g;
        }
        return new GeneratedData(records, groupOf, groups);
    }

    /** Writes records as CSV in the format the upload endpoint expects. */
    public static void writeCsv(List<MaterialRecord> records, Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
            out.println("cpse,code,description,uom");
            for (MaterialRecord r : records) {
                out.println(r.cpse() + "," + r.code() + ",\"" + r.description().replace("\"", "\"\"") + "\"," + r.uom());
            }
        }
    }

    public static void main(String[] args) throws IOException {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        double dup = args.length > 1 ? Double.parseDouble(args[1]) : 0.3;
        Path out = Path.of(args.length > 2 ? args[2] : "data/sample_materials.csv");
        GeneratedData data = generate(n, dup, 42L);
        writeCsv(data.records(), out);
        System.out.println("Wrote " + n + " records (" + data.groupCount() + " distinct materials) to " + out);
    }

    // ---------------------------------------------------------------- rendering one variant

    private static String render(int[] b, Random rnd) {
        List<String> parts = new ArrayList<>();
        parts.add(ITEMS[b[0]]);

        String form = FORMS[b[1]];
        parts.add(form.equals("seamless") && rnd.nextBoolean() ? "smls" : form);

        String[] metal = METALS[b[2]];
        parts.add(rnd.nextBoolean() ? metal[0] : metal[1]);

        parts.add(sizeText(SIZES_INCH[b[3]], rnd));

        String[] spec = SPECS[b[4]];
        parts.add(specText(spec[0], spec[1], rnd));

        parts.add((rnd.nextBoolean() ? "schedule " : "sch ") + SCHEDULES[b[5]]);
        parts.add((rnd.nextBoolean() ? "class " : "cl ") + CLASSES[b[6]]);
        parts.add("dwg " + b[7]);

        if (rnd.nextBoolean()) {
            Collections.shuffle(parts, rnd);
        }
        String[] separators = {", ", " ", " - ", "; "};
        String text = String.join(separators[rnd.nextInt(separators.length)], parts);
        switch (rnd.nextInt(3)) {
            case 0:
                return text.toUpperCase(Locale.ROOT);
            case 1:
                return text.toLowerCase(Locale.ROOT);
            default:
                return text;
        }
    }

    private static String sizeText(double inches, Random rnd) {
        String inchText = inches == Math.rint(inches) ? String.valueOf((long) inches) : String.valueOf(inches);
        switch (rnd.nextInt(4)) {
            case 0:
                return inchText + " inch";
            case 1:
                return inchText + "\"";
            case 2:
                return oneDecimal(inches * 25.4) + "mm";
            default:
                return twoDecimals(inches * 2.54) + " cm";
        }
    }

    private static String oneDecimal(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private static String twoDecimals(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String specText(String code, String grade, Random rnd) {
        if (grade == null) {
            return rnd.nextBoolean() ? "astm " + code : code;
        }
        switch (rnd.nextInt(4)) {
            case 0:
                return "astm " + code + " gr." + grade;
            case 1:
                return code + "-" + grade;
            case 2:
                return code + " grade " + grade;
            default:
                return "astm " + code + " gr " + grade;
        }
    }
}
