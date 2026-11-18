package harmonizer.bench;

import harmonizer.algo.dsu.DisjointSetUnion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Disjoint-set union versus a naive component-labelling baseline.
 *
 * <pre>
 *   mvn -q compile
 *   java -cp target/classes harmonizer.bench.DsuBenchmark
 *   java -cp target/classes harmonizer.bench.DsuBenchmark quick
 * </pre>
 *
 * <p>Both implementations receive exactly the same union operations and must produce exactly the same
 * partition; the benchmark fails loudly if they disagree. Timings are the best of several runs after a
 * warm-up and cover three phases: building the structure with the unions, the find operations, and
 * extracting the groups.
 *
 * <p>The naive baseline relabels every member of the smaller side on each union, which is O(n) per
 * union and therefore O(n * E) overall. That is a fair "obvious" implementation, but it is not
 * practical at 100,000 records, so it is skipped there (marked in the table) rather than faked.
 */
public final class DsuBenchmark {

    private static final int REPS = 3;
    private static final long SEED = 2026L;
    private static final int NAIVE_LIMIT = 10_000;

    private DsuBenchmark() {
    }

    public static void main(String[] args) {
        boolean quick = args.length > 0 && args[0].equalsIgnoreCase("quick");
        int[] sizes = quick ? new int[]{1_000, 10_000} : new int[]{1_000, 10_000, 100_000};

        System.out.println("Java " + System.getProperty("java.version") + " | "
                + Runtime.getRuntime().availableProcessors() + " cores | "
                + (quick ? "QUICK" : "FULL") + " run");
        System.out.println();
        System.out.println("=== DSU vs naive relabelling (same union operations, best of " + REPS + " runs) ===");
        System.out.printf("%-10s %-8s %-8s %-8s %-15s %-15s %-14s %-14s %-15s %-9s%n",
                "records", "unions", "finds", "groups", "DSU unions ms", "naive unions ms",
                "DSU find ms", "DSU extract ms", "naive extract ms", "identical");

        warmUp();

        for (int records : sizes) {
            int[][] edges = randomEdges(records, SEED);

            double dsuUnionMillis = Double.MAX_VALUE;
            double dsuFindMillis = Double.MAX_VALUE;
            double dsuExtractMillis = Double.MAX_VALUE;
            List<List<Integer>> dsuGroups = List.of();
            int merges = 0;

            for (int rep = 0; rep < REPS; rep++) {
                long startUnion = System.nanoTime();
                DisjointSetUnion dsu = new DisjointSetUnion(records);
                for (int id = 1; id <= records; id++) {
                    dsu.makeSet(id);
                }
                merges = 0;
                for (int[] edge : edges) {
                    if (dsu.union(edge[0], edge[1])) {
                        merges++;
                    }
                }
                dsuUnionMillis = Math.min(dsuUnionMillis, (System.nanoTime() - startUnion) / 1e6);

                long startFind = System.nanoTime();
                long checksum = 0L;
                for (int id = 1; id <= records; id++) {
                    checksum += dsu.find(id);
                }
                dsuFindMillis = Math.min(dsuFindMillis, (System.nanoTime() - startFind) / 1e6);
                if (checksum == Long.MIN_VALUE) {
                    System.out.print(""); // keep the JIT from removing the find loop
                }

                long startExtract = System.nanoTime();
                dsuGroups = dsu.getGroups();
                dsuExtractMillis = Math.min(dsuExtractMillis, (System.nanoTime() - startExtract) / 1e6);
            }

            String naiveUnion = "(too slow)";
            String naiveExtract = "(too slow)";
            boolean identical = true;

            if (records <= NAIVE_LIMIT) {
                double naiveUnionMillis = Double.MAX_VALUE;
                double naiveExtractMillis = Double.MAX_VALUE;
                List<List<Integer>> naiveGroups = List.of();

                for (int rep = 0; rep < REPS; rep++) {
                    long startUnion = System.nanoTime();
                    int[] labels = new int[records + 1];
                    for (int id = 1; id <= records; id++) {
                        labels[id] = id;
                    }
                    for (int[] edge : edges) {
                        int from = labels[edge[0]];
                        int to = labels[edge[1]];
                        if (from != to) {
                            for (int id = 1; id <= records; id++) {
                                if (labels[id] == from) {
                                    labels[id] = to;
                                }
                            }
                        }
                    }
                    naiveUnionMillis = Math.min(naiveUnionMillis, (System.nanoTime() - startUnion) / 1e6);

                    long startExtract = System.nanoTime();
                    naiveGroups = extractByLabel(labels, records);
                    naiveExtractMillis = Math.min(naiveExtractMillis,
                            (System.nanoTime() - startExtract) / 1e6);
                }

                naiveUnion = String.format("%.3f", naiveUnionMillis);
                naiveExtract = String.format("%.3f", naiveExtractMillis);
                identical = normalise(dsuGroups).equals(normalise(naiveGroups));
                if (!identical) {
                    throw new IllegalStateException("DSU and naive grouping disagree at " + records
                            + " records: " + dsuGroups.size() + " vs " + naiveGroups.size() + " groups");
                }
            }

            System.out.printf("%-10d %-8d %-8d %-8d %-15.3f %-15s %-14.3f %-14.3f %-15s %-9s%n",
                    records, merges, records, dsuGroups.size(), dsuUnionMillis, naiveUnion,
                    dsuFindMillis, dsuExtractMillis, naiveExtract,
                    records <= NAIVE_LIMIT ? String.valueOf(identical) : "not run");
        }

        System.out.println();
        System.out.println("Groups produced by both methods are compared as sorted member lists before any timing is reported.");
        System.out.println("DSU cost per operation is effectively constant (inverse Ackermann); the naive relabelling cost grows with n,");
        System.out.println("which is why it is only run up to " + NAIVE_LIMIT + " records. These are measurements from this machine,");
        System.out.println("not a general claim about every workload.");
    }

    private static void warmUp() {
        DisjointSetUnion dsu = new DisjointSetUnion(1_000);
        for (int id = 1; id <= 1_000; id++) {
            dsu.makeSet(id);
        }
        for (int id = 2; id <= 1_000; id++) {
            dsu.union(id - 1, id);
        }
        dsu.getGroups();
    }

    /** Random relationships: enough to form real components, plus many redundant edges. */
    private static int[][] randomEdges(int records, long seed) {
        Random random = new Random(seed);
        int edgeCount = Math.max(1, records);
        int[][] edges = new int[edgeCount][2];
        for (int i = 0; i < edgeCount; i++) {
            int a = 1 + random.nextInt(records);
            int b = 1 + random.nextInt(records);
            if (a == b) {
                b = a % records + 1;
            }
            edges[i][0] = a;
            edges[i][1] = b;
        }
        return edges;
    }

    /** Naive extraction: group records by their component label. */
    private static List<List<Integer>> extractByLabel(int[] labels, int records) {
        Map<Integer, List<Integer>> byLabel = new LinkedHashMap<>();
        for (int id = 1; id <= records; id++) {
            byLabel.computeIfAbsent(labels[id], label -> new ArrayList<>()).add(id);
        }
        List<List<Integer>> groups = new ArrayList<>(byLabel.size());
        for (List<Integer> group : byLabel.values()) {
            groups.add(List.copyOf(group));
        }
        return groups;
    }

    /** Order-independent form of a partition, used to compare the two implementations. */
    private static List<List<Integer>> normalise(List<List<Integer>> groups) {
        List<List<Integer>> copy = new ArrayList<>(groups.size());
        for (List<Integer> group : groups) {
            List<Integer> sorted = new ArrayList<>(group);
            sorted.sort(Integer::compareTo);
            copy.add(sorted);
        }
        copy.sort((left, right) -> Integer.compare(left.get(0), right.get(0)));
        return copy;
    }
}
