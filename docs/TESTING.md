# Test and benchmark report

All numbers below are copied from actual runs on this machine, not estimates.

* **OS:** Windows 11 (amd64)
* **JDK:** OpenJDK 17.0.21 (Microsoft build)
* **Maven:** Apache Maven 3.9.6
* **Date of the recorded run:** 2026-11-17

---

## 1. How to run the tests

```bash
# canonical, integrated project (repository root)
mvn clean test

# standalone demo application (separate Maven project)
cd backend
mvn test
```

---

## 2. Canonical project — `mvn clean test`

```
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0  -- harmonizer.algo.BloomFilterTest
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0  -- harmonizer.algo.DisjointSetUnionTest
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0  -- harmonizer.algo.KMPMatcherTest
Tests run: 9,  Failures: 0, Errors: 0, Skipped: 0  -- harmonizer.algo.TrieTest
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0  -- harmonizer.api.HarmonizationApiTest
Tests run: 9,  Failures: 0, Errors: 0, Skipped: 0  -- harmonizer.harmonization.NationalCodeRegistryTest
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0  -- harmonizer.integration.HarmonizationPipelineTest
Tests run: 9,  Failures: 0, Errors: 0, Skipped: 0  -- harmonizer.integration.HarmonizationRegressionTest
Tests run: 9,  Failures: 0, Errors: 0, Skipped: 0  -- harmonizer.io.CsvMaterialParserTest
Tests run: 7,  Failures: 0, Errors: 0, Skipped: 0  -- harmonizer.matching.AttributeComparisonTest
Tests run: 8,  Failures: 0, Errors: 0, Skipped: 0  -- harmonizer.matching.AttributeExtractorTest
Tests run: 8,  Failures: 0, Errors: 0, Skipped: 0  -- harmonizer.normalize.TextNormalizerTest
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0  -- harmonizer.screening.DuplicateScreenerTest

Tests run: 132, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Per-class table

| Test class | Area | Owner | Tests | Passed | Failed | Skipped |
|---|---|---|---|---|---|---|
| `harmonizer.algo.KMPMatcherTest` | KMP | Anshika | 16 | 16 | 0 | 0 |
| `harmonizer.algo.DisjointSetUnionTest` | DSU | Anshika | 14 | 14 | 0 | 0 |
| `harmonizer.matching.AttributeExtractorTest` | attribute + spec extraction | Anshika | 8 | 8 | 0 | 0 |
| `harmonizer.matching.AttributeComparisonTest` | MATCH / CONFLICT / UNKNOWN + evidence rule | Anshika | 7 | 7 | 0 | 0 |
| `harmonizer.integration.HarmonizationPipelineTest` | end-to-end pipeline | Anshika | 12 | 12 | 0 | 0 |
| `harmonizer.integration.HarmonizationRegressionTest` | audit regressions: false merges, persistent rejections, code lifecycle | Anshika | 9 | 9 | 0 | 0 |
| `harmonizer.harmonization.NationalCodeRegistryTest` | national code lifecycle | Anshika | 9 | 9 | 0 | 0 |
| `harmonizer.api.HarmonizationApiTest` | REST API (MockMvc) | Anshika | 10 | 10 | 0 | 0 |
| `harmonizer.algo.BloomFilterTest` | Bloom filter | Hetanshi | 10 | 10 | 0 | 0 |
| `harmonizer.algo.TrieTest` | Trie | Hetanshi | 9 | 9 | 0 | 0 |
| `harmonizer.normalize.TextNormalizerTest` | normaliser | Hetanshi | 8 | 8 | 0 | 0 |
| `harmonizer.io.CsvMaterialParserTest` | CSV parser | Hetanshi | 9 | 9 | 0 | 0 |
| `harmonizer.screening.DuplicateScreenerTest` | Bloom screening | Hetanshi | 11 | 11 | 0 | 0 |
| **Total** | | | **132** | **132** | **0** | **0** |

The teammate's 47 pre-existing tests were run **before** any change (`47 / 0 / 0 / 0`) and are still
green afterwards. No existing test was modified.

### What the new tests prove

| Requirement | Test(s) |
|---|---|
| KMP: at least 10 meaningful tests | `KMPMatcherTest` — 16 |
| LPS correctness | `lpsConstruction` (ABABC, AAAA, A, ABCDE, ABABAB) |
| single / no / multiple / overlapping matches | `singleMatch`, `noMatch`, `multipleMatches`, `overlappingMatches` |
| pattern longer than text, empty pattern, empty text | `patternLongerThanText`, `emptyPattern`, `emptyText` |
| repeated characters, case-insensitive specs, null safety | `repeatedCharacters`, `caseInsensitiveSpecifications`, `nullSafety` |
| zero-based positions are exact | `positionsAreExact` |
| comparison counting and the `2(n+m)` bound | `comparisonCounting`, `comparisonBound` |
| KMP equals an independent naive scan on 500 random inputs | `agreesWithNaiveScan` |
| DSU: at least 10 meaningful tests | `DisjointSetUnionTest` — 14 |
| A=B, B=C → one group | `transitiveGrouping` |
| A=B, C=D → two independent groups | `independentGroups` |
| path compression, union by size | `pathCompressionFlattensTheTree`, `unionBySizeKeepsLargeRoot` |
| repeated unions, component sizes, invalid ids | `repeatedUnionsAreIdempotent`, `componentSizesAreAccurate`, `invalidIdsAreRejected` |
| every record appears exactly once in `getGroups()` | `everyRecordAppearsExactlyOnce` |
| Scenario 1 — three equivalent records → one group | `threeEquivalentRecordsFormOneGroup` |
| KMP evidence (positions + comparisons) in the workflow | `kmpEvidenceIsRecordedInTheWorkflow` |
| Scenario 2 — conflicting grades → not grouped | `conflictingGradesAreRejected` |
| Scenario 3 — conflicting dimensions → not grouped | `conflictingDimensionsAreRejected` |
| Scenario 4 — transitive grouping | `transitiveRelationshipsFormOneComponent` |
| Scenario 5 — independent material stays separate | `independentMaterialStaysSeparate` |
| Scenario 6 — rejected relationship rebuilt correctly | `rejectingRelationshipsRebuildsGroupsCorrectly` |
| Scenario 7 — duplicate company code rejected on import | `duplicateImportIsRejected` |
| A shared specification alone never merges | `sharedSpecificationAloneDoesNotMerge` |
| National codes are stable across runs | `nationalCodesAreStableAcrossRuns` |
| Rejection before a run / unknown pair is reported | `invalidRejectionIsReported` |
| Empty dataset is safe | `emptyDatasetIsSafe` |
| Audit A-B-C false merge is prevented | `auditRegressionUnknownAttributesCannotBridgeConflictingMaterials` |
| A bridging component with contradictory ends is split | `bridgeWithSufficientEvidenceIsSplitBeforeGrouping` |
| Missing attributes are UNKNOWN, not a match | `missingAttributesAloneAreNotEnoughEvidence`, `missingValuesAreUnknown` |
| Conflicting metals are CONFLICT | `conflictingMetalsAreRejected`, `conflictingMetalsAreRejected` (comparison test) |
| Valid transitive grouping still merges | `validTransitiveGroupingIsPreserved` |
| Rejection survives repeated runs, unrelated edges kept | `rejectionSurvivesRepeatedRuns` |
| Rejection keyed by material identity, not record id | `rejectionIsKeyedByMaterialIdentityNotRecordIds` |
| Adding a record keeps the national code | `addingEquivalentRecordKeepsNationalCode`, `growthInheritsCode` |
| A split issues distinct codes and keeps original codes | `splitAfterRejectionKeepsCodesUnique` |
| Registry history, uniqueness and validation | `NationalCodeRegistryTest` — 9 tests |
| API: upload, harmonize, reject, repeat, invalid ids, empty dataset, JSON shape | `HarmonizationApiTest` — 10 tests |

### Corrections made while adding the audit-fix tests

Two assertions in the **new** tests were wrong on their first run and were corrected; no production
behaviour was changed to make them pass:

* `harmonizeReturnsCompleteResponse` asserted `groups[0].originalCodes`, which is a derived accessor and
  therefore not part of the JSON. It now asserts the members' own `code` values.
* `harmonizeReturnsCompleteResponse` also expected `attributeStates.metal == MATCH` for the first edge;
  in that dataset only one record states the metal, so the pair-wise state is correctly `UNKNOWN`. The
  expectation was corrected (and is itself a useful demonstration of the rule).

### Honest note about one failure during development

The first run had 1 failing assertion: `lpsConstruction` expected `[0,0,0,0,1,2]` for `"ABABAB"`.
The correct LPS is `[0,0,1,2,3,4]`; the **test expectation** was wrong, the implementation was right
(verified by hand and by the naive comparison test). The expectation was corrected. No implementation
shortcut was taken to make it pass.

---

## 3. Standalone demo application — `cd backend && mvn test`

```
[INFO] Tests run: 4,  Failures: 0, Errors: 0, Skipped: 0 -- in com.matriq.algorithms.BloomFilterTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 -- in com.matriq.algorithms.DisjointSetUnionTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 -- in com.matriq.algorithms.KMPMatcherTest
[INFO] Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- in com.matriq.algorithms.MaterialTrieTest
[INFO] Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0 -- in com.matriq.integration.HarmonizationIntegrationTest
[INFO] Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

These are the pre-existing tests of the `com.matriq` demo app (`backend/`); none were changed.

---

## 4. Benchmarks

Benchmarks are `main(...)` programs, deliberately separate from the correctness tests. They compile as
part of the build but do not run under `mvn test`.

```bash
mvn -q compile
java -cp target/classes harmonizer.bench.KmpBenchmark
java -cp target/classes harmonizer.bench.DsuBenchmark
java -cp target/classes harmonizer.bench.BloomTrieBenchmark        # pre-existing
java -cp target/classes harmonizer.bench.SyntheticMaterialGenerator 10000 0.3 data/sample_10k.csv
```

Add `quick` as the argument to any benchmark for smaller sizes.

### 4.1 KMP vs naive — realistic description stream (`ASTM A106`)

```
text chars   pattern  matches   KMP comparisons   naive comparisons  KMP ms      naive ms    ratio
1000         9        9         1076              1179               0.246       0.255       0.91
10000        9        90        10658             11768              1.289       1.515       0.91
100000       9        897       106316            117081             2.039       3.191       0.91
1000000      9        8904      1062963           1169951            6.144       5.912       0.91
```

### 4.2 KMP vs naive — near-miss text (pattern `ASTM A106 GRADE B`)

```
text chars   matches  KMP comparisons   naive comparisons  KMP ms      naive ms    ratio
100000       0        106268            199968             0.588       0.777       0.53
1000000      0        1062518           1999968            5.811       7.696       0.53
```

### 4.3 KMP vs naive — synthetic worst case (`"a"×49 + "b"` over `"a"`s, no match)

```
text chars   pattern  KMP comparisons   naive comparisons  KMP ms      naive ms    ratio
100000       50       200048            4997550            0.929       13.400      0.040
1000000      50       2000048           49997550           9.208       131.946     0.040
```

Observed complexity: KMP comparisons grow linearly with text length in every experiment
(~1.06M, ~1.06M, ~2.0M for 1M characters); naive comparisons grow with the pattern length in the
worst case (≈ 50 × n). Wall-clock speed is **not** uniformly better for KMP: in 4.1 at 1,000,000
characters naive was marginally faster (5.912 vs 6.144 ms) because KMP also built the LPS array.
Every run asserts that both algorithms produce identical match lists before reporting timings.

### 4.4 DSU vs naive relabelling

```
records    unions   finds    groups   DSU unions ms   naive unions ms DSU find ms    DSU extract ms naive extract ms identical
1000       837      1000     163      0.179           3.242           0.065          1.227          0.523           true
10000      8408     10000    1592     0.488           107.360         0.171          5.366          3.037           true
100000     83730    100000   16270    4.287           (too slow)      1.015          24.865         (too slow)      not run
```

Observed behaviour: the DSU union phase scales close to linearly
(0.179 → 0.488 → 4.287 ms), the naive relabelling phase is quadratic (3.242 → 107.360 ms) and is not run
at 100k. Group extraction is measured as **slower** for DSU than for the naive label bucketing
(5.366 vs 3.037 ms at 10k); this is reported as measured. The benchmark fails if the two methods ever
produce different partitions.

### 4.5 Pre-existing Bloom / Trie / screening benchmark (preserved, still runs)

```
=== 1. Bloom filter: measured false-positive rate vs theoretical ===
n          target   bits (m)     bits/item k    measured     theoretical  false-neg
1000000    0.010    9585059      9.59      7    0.00997      0.01004      0
...
=== 2. Trie prefix search vs linear scan (top 10 keys, lexicographic) ===
n keys     trie build ms  trie us/query      linear us/query    speedup
100000     556.5          58.83              4546.92            77.3    x
...
=== 3. Duplicate screening: Bloom + exact check vs baselines (30% duplicates) ===
n         pairwise ms    hashmap ms     bloom+map ms   precision  recall    bloom FPs  skipped
100000    (too slow)     22.17          116.32         1.000      1.000     15         0
```

(Full output available by running the command; truncated here for readability.)

---

## 5. What is not covered

* **Web layer (covered):** `HarmonizationApiTest` exercises upload, harmonize, reject, repeated
  harmonization, invalid record ids, an empty dataset and the JSON contract through MockMvc with a fresh
  Spring context per test. It does not exercise the Vite proxy or the browser UI.
* **Persistence and users:** this stage keeps everything in memory; there is nothing to test against a
  database.
* **Concurrency:** `HarmonizationEngine` methods are `synchronized`, but no concurrent load test exists.
* **Real-world data quality:** precision/recall figures come from synthetic data whose variations match
  the normaliser by design (the teammate states the same caveat for the Bloom stage).
* **Benchmark stability:** timings are single-machine measurements (best of 3); comparison counts are
  deterministic and should be quoted as the reproducible result.
