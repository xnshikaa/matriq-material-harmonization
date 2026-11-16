package harmonizer.screening;

import harmonizer.algo.bloom.BloomFilter;
import harmonizer.model.MaterialRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Stage 1 of the pipeline: find records that are probably the same item, fast.
 *
 * <p>For each record, in order:
 * <ol>
 *   <li>Build its signature (injectable, so KMP-based spec extraction can be plugged in later).</li>
 *   <li>Ask the Bloom filter. "Definitely not seen" means the record is new: no lookup needed.</li>
 *   <li>"Possibly seen" means we must check the authoritative store (an exact map). If the exact
 *       check confirms it, emit a {@link MatchPair}; if not, it was a Bloom false positive.</li>
 * </ol>
 * A Bloom "yes" is NEVER reported as a duplicate without the exact check.
 *
 * <p>Complexity: O(n * (k + L)) for n records, k hash functions and signature length L, versus
 * O(n^2 * L) for comparing every pair. Space: O(m) bits for the filter plus the exact store.
 *
 * <p>Honest note for the report: in memory, an exact hash map alone is just as fast. The filter pays
 * off when the authoritative store is expensive (database, disk, another company's server) or too
 * big to ship around, because most new records are rejected by a tiny bit array without touching it.
 */
public final class DuplicateScreener {

    private final Function<MaterialRecord, String> signatureFn;

    public DuplicateScreener(Function<MaterialRecord, String> signatureFn) {
        this.signatureFn = Objects.requireNonNull(signatureFn, "signatureFn");
    }

    public ScreeningResult screen(List<MaterialRecord> records, double targetFpr) {
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("Dataset is empty: there is nothing to screen.");
        }
        long start = System.nanoTime();

        BloomFilter bloom = new BloomFilter(records.size(), targetFpr); // validates targetFpr
        Map<String, MaterialRecord> exactStore = new HashMap<>();       // signature -> first record seen
        List<MatchPair> pairs = new ArrayList<>();
        List<SkippedRecord> skipped = new ArrayList<>();
        int screened = 0;
        int definitelyNew = 0;
        int maybe = 0;
        int falsePositives = 0;

        for (MaterialRecord r : records) {
            if (r == null) {
                throw new IllegalArgumentException("Record list contains a null entry.");
            }
            String signature = signatureFn.apply(r);
            if (signature == null || signature.isBlank()) {
                // Blank signatures would all "match" each other, so they are reported, never matched.
                skipped.add(new SkippedRecord(r.id(), "description has no usable content after normalisation"));
                continue;
            }
            screened++;

            boolean possiblySeen = bloom.mightContain(signature);
            MaterialRecord first = null;
            if (possiblySeen) {
                maybe++;
                first = exactStore.get(signature); // authoritative check
            }

            if (first != null) {
                pairs.add(new MatchPair(r.id(), first.id(),
                        r.cpse(), r.code(), first.cpse(), first.code(),
                        signature, !Objects.equals(r.cpse(), first.cpse())));
            } else {
                if (possiblySeen) {
                    falsePositives++;
                } else {
                    definitelyNew++;
                }
                bloom.add(signature);
                exactStore.put(signature, r);
            }
        }

        int trulyNew = definitelyNew + falsePositives;
        double measuredFpr = trulyNew == 0 ? 0.0 : (double) falsePositives / trulyNew;
        long n = records.size();
        ScreeningStats stats = new ScreeningStats(
                records.size(), screened,
                bloom.numBits(), bloom.numHashes(), targetFpr,
                definitelyNew, maybe, pairs.size(), falsePositives,
                measuredFpr, bloom.expectedFpr(),
                n * (n - 1) / 2,
                (System.nanoTime() - start) / 1_000_000.0);
        return new ScreeningResult(pairs, skipped, stats);
    }
}
