# KMP (Knuth–Morris–Pratt) in MATRIQ

**Module owner:** Anshika Gupta
**Code:** `src/main/java/harmonizer/algo/kmp/KMPMatcher.java`, `KMPResult.java`
**Tests:** `src/test/java/harmonizer/algo/KMPMatcherTest.java` (16 tests)
**Benchmark:** `src/main/java/harmonizer/bench/KmpBenchmark.java`
**Used by:** `harmonizer.matching.KmpSpecificationExtractor` → `EquivalenceMatcher` → harmonization pipeline

---

## 1. What problem does it solve?

Material descriptions are free text written by different organisations:

```
Pipe, seamless, 6 inch, ASTM A106 Gr.B
SEAMLESS PIPE 150mm CS A106-B
Carbon steel seamless pipe, 6 in, A106 Grade B
```

To compare them, MATRIQ has to find the **technical specification** inside each description
(`ASTM A106`, `A106-B`, `API 5L`, `SS316`, `ANSI B16.5`, `IS 1239`, ...) and remember *where* it was
found. That is a substring-search problem: pattern of length `m` inside text of length `n`.

### Naive search and its problem

A naive search tries the pattern at every start position and restarts from the pattern's first
character after every mismatch:

```
text:    A A A A A A A A A A B
pattern: A A A A B
         ^ compare 4, mismatch at the 5th -> restart one position later, compare 4 again, ...
```

Worst case: `O(n * m)` comparisons. The text characters are re-read over and over.

### The KMP idea

KMP pre-computes, once, how much of the pattern already matched when a mismatch happens, so it never
re-reads text and never restarts from zero. Total `O(n + m)`.

---

## 2. The LPS array (the "failure function")

`lps[i]` = length of the **longest proper prefix** of `pattern[0..i]` that is also a **suffix** of
`pattern[0..i]`.

Worked example, `pattern = "ABABC"`:

| i | pattern[0..i] | longest proper prefix = suffix | lps[i] |
|---|---|---|---|
| 0 | A | (none) | 0 |
| 1 | AB | (none) | 0 |
| 2 | ABA | "A" | 1 |
| 3 | ABAB | "AB" | 2 |
| 4 | ABABC | (none) | 0 |

So `lps = [0, 0, 1, 2, 0]`. `buildLPS("ABABAB")` returns `[0, 0, 1, 2, 3, 4]` — this is asserted in
`KMPMatcherTest#lpsConstruction`, together with `AAAA → [0,1,2,3]` and `ABCDE → [0,0,0,0,0]`.

### How the LPS array is built — O(m)

```
length = 0                    // length of the current prefix-suffix
i = 1
while i < m:
    if p[i] == p[length]:     // extend the current prefix-suffix
        length++; lps[i] = length; i++
    else if length > 0:       // fall back to a shorter prefix, do NOT advance i
        length = lps[length - 1]
    else:
        lps[i] = 0; i++
```

The `length = lps[length - 1]` line is the whole trick: instead of starting over, we reuse the
longest shorter prefix that could still match. `i` is never moved backwards.

---

## 3. The search — O(n)

```
i = 0, j = 0
while i < n:
    if t[i] == p[j]:          // one character comparison
        i++; j++
        if j == m:            // full match
            record i - j      // zero-based start index
            j = lps[j - 1]    // fall back: allows OVERLAPPING matches
    else if j > 0:
        j = lps[j - 1]        // slide pattern, text pointer stays
    else:
        i++                   // nothing matched yet
```

Key properties:

* The text pointer `i` only increases, so the text is read about once → `O(n)`.
* After a full match, `j = lps[m-1]` lets the search continue inside the match it just found, so
  overlapping occurrences are reported (`"AA"` occurs three times in `"AAAA"` at 0, 1 and 2).
* One comparison is counted per `charAt` comparison, so the benchmark compares real work.

---

## 4. API and guarantees

| Method | Returns | Notes |
|---|---|---|
| `int[] buildLPS(String pattern)` | the failure array | `null`/empty → empty array |
| `int search(String text, String pattern)` | first zero-based index, or `-1` | |
| `List<Integer> searchAll(String text, String pattern)` | every zero-based index, ascending | includes overlaps |
| `KMPResult countComparisons(String text, String pattern)` | positions + comparison count + LPS | used for evidence and benchmarking |

Guarantees that are actually tested:

| Requirement | Test |
|---|---|
| Correct LPS values | `lpsConstruction` |
| Single / many / zero occurrences | `singleMatch`, `multipleMatches`, `noMatch` |
| Overlapping occurrences | `overlappingMatches` |
| Pattern longer than text | `patternLongerThanText` |
| Empty pattern, empty text | `emptyPattern`, `emptyText` |
| Repeated characters | `repeatedCharacters` |
| Case-insensitive specifications | `caseInsensitiveSpecifications` |
| Zero-based positions really point at the pattern | `positionsAreExact` |
| Null safety | `nullSafety` |
| Comparison counting | `comparisonCounting` |
| Agrees with an independent naive scan on 500 random inputs | `agreesWithNaiveScan` |
| Comparison count stays within `2(n + m)` | `comparisonBound` |
| Result object cannot be mutated by a caller | `resultObjectIsSafe` |

**No library search is used.** There is no `String.indexOf`, `String.contains` or regex in the search
path; the only String operations are `length()`, `charAt()` and `toLowerCase()` for case
normalisation.

`KMPMatcher#searchAll` and `countComparisons` share one internal `scan(...)` routine, so the positions
and the comparison counter can never disagree.

---

## 5. Where KMP is used in MATRIQ (not just a demo)

`harmonizer.matching.KmpSpecificationExtractor` runs KMP for every pattern in
`harmonizer.matching.SpecCatalogue` against each description, and produces `SpecHit` records:

```
pattern        "A106-B"
specification  "ASTM A106"      <- canonical name
positions      [23]             <- zero-based index in the original text
comparisons    41               <- characters compared to find it
```

`EquivalenceMatcher` then:

1. extracts attributes and specification hits for every record,
2. pairs records inside the same item-type block,
3. keeps only specifications found in **both** records as `SpecEvidence`
   (position in A, position in B, comparison counts for both),
4. passes that evidence to the verification gate.

`EquivalenceMatcher` never merges on a shared specification alone. In
`HarmonizationPipelineTest#conflictingGradesAreRejected`, the pair shares `ASTM A106` but the grades
`B` and `C` differ, so the pair is rejected — and the test asserts both facts
(`checks().specification() == true`, `checks().grade() == false`).

The evidence reaches the API too: `POST /api/harmonize` returns every verified edge and every rejected
candidate with the positions and comparison counts.

---

## 6. Complexity analysis

| Phase | Time | Space |
|---|---|---|
| Build LPS | `O(m)` | `O(m)` |
| Search | `O(n)` | `O(1)` extra |
| Total per pattern | `O(n + m)` | `O(m)` |
| Full description with `P` catalogue patterns | `O(P * (n + m))` | `O(m)` |

The comparison bound is `2(n + m)` because each search-loop iteration either advances `i` (at most
`n` times) or strictly decreases `j` (which can only increase when `i` advances). The test
`comparisonBound` checks this on 100 random inputs of 200–1,000 characters.

---

## 7. Measured benchmark

Command:

```bash
mvn -q compile
java -cp target/classes harmonizer.bench.KmpBenchmark
```

Environment: Windows 11, OpenJDK 17.0.21, 12 cores. Best of 3 runs after warm-up. Both algorithms run
on the same text, and the benchmark **fails** if they disagree on the match list.

### 7.1 Realistic description stream, pattern `ASTM A106`

| text chars | pattern | matches | KMP comparisons | naive comparisons | KMP ms | naive ms | ratio |
|---|---|---|---|---|---|---|---|
| 1,000 | 9 | 9 | 1,076 | 1,179 | 0.246 | 0.255 | 0.91 |
| 10,000 | 9 | 90 | 10,658 | 11,768 | 1.289 | 1.515 | 0.91 |
| 100,000 | 9 | 897 | 106,316 | 117,081 | 2.039 | 3.191 | 0.91 |
| 1,000,000 | 9 | 8,904 | 1,062,963 | 1,169,951 | 6.144 | 5.912 | 0.91 |

KMP uses about 9% fewer comparisons. On this kind of text most naive attempts fail on the first
character, so the naive loop is not far behind — and at 1M characters its wall-clock time was
*lower* than KMP's on this run, because KMP also builds the LPS array. No universal speed claim is
made.

### 7.2 Near-miss text (every block matches except the last character)

| text chars | matches | KMP comparisons | naive comparisons | KMP ms | naive ms | ratio |
|---|---|---|---|---|---|---|
| 100,000 | 0 | 106,268 | 199,968 | 0.588 | 0.777 | 0.53 |
| 1,000,000 | 0 | 1,062,518 | 1,999,968 | 5.811 | 7.696 | 0.53 |

### 7.3 Synthetic worst case for naive (pattern `"a"*49 + "b"`, text of `"a"`s)

| text chars | pattern | KMP comparisons | naive comparisons | KMP ms | naive ms | ratio |
|---|---|---|---|---|---|---|
| 100,000 | 50 | 200,048 | 4,997,550 | 0.929 | 13.400 | 0.040 |
| 1,000,000 | 50 | 2,000,048 | 49,997,550 | 9.208 | 131.946 | 0.040 |

Here the theory is visible: naive comparisons scale with the pattern length (≈ 50 × n), KMP's stay
linear (≈ 2n), and the wall-clock difference is ~14×. This input is synthetic and labelled as such —
it is not a material description.

**Summary:** the comparison count is the honest measure of the algorithm's work, and it is linear for
KMP in every case. Wall-clock speed depends on constants, match density and text shape, so KMP is
*not* claimed to be always faster.

---

## 8. Viva questions and short answers

1. **Why is KMP O(n + m)?** The text pointer never decreases; the LPS fallback never increases the
   pattern pointer without the text pointer also advancing.
2. **What exactly does `lps[i]` mean?** Length of the longest proper prefix of `pattern[0..i]` that is
   also its suffix.
3. **Why `lps[j-1]` after a full match?** It keeps the part of the current match that can start a new,
   overlapping match, so overlapping occurrences are not missed.
4. **When is naive competitive?** Short texts, or text where mismatches happen at the first pattern
   character — measured in section 7.1.
5. **What does case normalisation cost?** One lower-case copy of text and pattern, `O(n + m)` space;
   positions still refer to the original text because lower-casing here does not change length.
6. **Why record comparison counts at all?** They make the evidence auditable and let the benchmark
   compare algorithms on real work rather than only on timings.

## 9. Limitations

* Case folding uses `toLowerCase(Locale.ROOT)`; exotic characters whose lower-case form changes length
  are not handled (ASCII/engineering text only).
* The catalogue is explicit: a specification spelling that is not listed is not found.
* `O(P * (n + m))` per description is fine for a fixed small catalogue (`P = 27`).
* KMP finds *strings*, not meaning: it proves where a specification appears, not that two materials are
  equivalent. That is the verification gate's job.
