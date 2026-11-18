# Integration report — combining both team members' work

This document is the Phase 1 / Phase 2 deliverable: what already existed, what was added, what was
touched, and where the two halves of the project meet.

---

## 1. Repository state before this integration

| Item | State |
|---|---|
| Branch | `main` |
| Remote | `origin` → `https://github.com/xnshikaa/matriq-material-harmonization.git` |
| Local vs remote | diverged: local `main` was 1 commit ahead, `origin/main` 1 commit ahead |
| Working tree | clean (no uncommitted changes to preserve) |
| Backup checkpoint | branch `backup/pre-kmp-dsu-integration` created before any edit |

The two commits each added a **different project layout**, which is why the merge only conflicted on
`.gitignore`:

* `6c88fde` (local, Anshika): `backend/` (`com.matriq.*` Spring Boot app with its own KMP/DSU),
  `frontend/` (React + TypeScript), `docs/`, sample data, and the original `README.md`.
* `1a8d545` (remote, Hetanshi): the root `harmonizer` project — `pom.xml` at the repo root,
  `src/main/java/harmonizer/**`, `src/test/java/harmonizer/**`, `data/`, `README_part1.md`.

### What the teammate's half already did (and still works)

`harmonizer` package, 47 JUnit 5 tests at the time of inspection:

| Area | Code | Tests |
|---|---|---|
| Bloom filter (FNV-1a + SplitMix64 double hashing) | `harmonizer/algo/bloom/BloomFilter.java` | `BloomFilterTest` (10) |
| Trie with prefix search | `harmonizer/algo/trie/Trie.java` | `TrieTest` (9) |
| Normaliser (units, abbreviations via Trie, signature) | `harmonizer/normalize/TextNormalizer.java` | `TextNormalizerTest` (8) |
| CSV upload parser | `harmonizer/io/CsvMaterialParser.java` | `CsvMaterialParserTest` (9) |
| Bloom screening stage | `harmonizer/screening/DuplicateScreener.java` | `DuplicateScreenerTest` (11) |
| REST API + CORS | `harmonizer/api/*`, `harmonizer/service/DatasetStore.java` | — |
| Benchmarks | `harmonizer/bench/BloomTrieBenchmark.java`, `SyntheticMaterialGenerator.java` | — |

All 47 were run before any change: `Tests run: 47, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`.
Those files were **not modified** by this integration.

### Explicit hand-off points the teammate documented

From `README_part1.md`:

* "**DSU:** every item in `confirmedPairs` means `union(recordId, matchedWithId)`. Record ids are 1-based."
* "**KMP:** `DuplicateScreener` takes the signature as a function. Spec extraction written with KMP can be
  added inside that function."

Both points are now implemented in the harmonization stage, using the record ids and the 1-based
convention exactly as described.

---

## 2. The problem that had to be solved

The remote project ended at "confirmed duplicate pairs". It had no specification matching, no attribute
verification, no grouping and no national codes — i.e. KMP and DSU had nowhere to plug in. At the same
time, the local `backend/` app already contained a working KMP/DSU, but in a **separate Spring Boot
application that duplicated** the parser, normaliser, Bloom filter and Trie.

So the integration had two goals:

1. bring the KMP and DSU implementations into the **teammate's** project, in its package and style;
2. turn them into working pipeline stages, not stand-alone demos, without touching the teammate's
   modules.

---

## 3. Decision: which tree is canonical

| Tree | Role after integration |
|---|---|
| root `harmonizer` project (teammate's) | **canonical, integrated project** — parser → normaliser → Bloom screening → KMP matching → verification → DSU grouping → national codes; 132 tests; 3 benchmark programs |
| `backend/` + `frontend/` (`com.matriq`) | kept unchanged as the standalone demo application (Spring Boot + React UI). It has its own copies of KMP/DSU and is **not** part of the root Maven build. |

Nothing in `backend/` or `frontend/` was deleted or rewritten: the frontend calls the `com.matriq` API
(`/api/materials`, `/api/harmonization`, ...), so removing that tree would break a working module. The
duplication is real and is the main remaining architectural limitation — see section 9.

---

## 4. What was added

### 4.1 Algorithms (owned by Anshika)

| File | Purpose |
|---|---|
| `src/main/java/harmonizer/algo/kmp/KMPMatcher.java` | from-scratch KMP: `buildLPS`, `search`, `searchAll`, `countComparisons` |
| `src/main/java/harmonizer/algo/kmp/KMPResult.java` | positions + comparison count + LPS array |
| `src/main/java/harmonizer/algo/dsu/DisjointSetUnion.java` | array-based DSU: `makeSet`, `find`, `union`, `connected`, `getGroups`, `getComponentSize` |

### 4.2 The stages that use them

| File | Purpose |
|---|---|
| `harmonizer/matching/SpecCatalogue.java` | the specification patterns (or "ASTM A106", "A106-B", ...) |
| `harmonizer/matching/KmpSpecificationExtractor.java` | runs KMP per pattern, returns `SpecHit`s |
| `harmonizer/matching/SpecHit.java` | one hit: pattern, canonical spec, positions, comparisons |
| `harmonizer/matching/AttributeExtractor.java` | description → item type, form, metal, size (mm), spec, grade |
| `harmonizer/matching/MaterialAttributes.java` | that attribute set |
| `harmonizer/matching/AttributeChecks.java` | per-attribute pass/fail |
| `harmonizer/matching/SpecEvidence.java` | shared specification + positions + comparison counts |
| `harmonizer/matching/MatchEvidence.java` | full explanation of a verified/rejected pair |
| `harmonizer/matching/VerifiedEdge.java` | an approved relationship (input to the DSU) |
| `harmonizer/matching/EquivalenceMatcher.java` | candidate generation + verification → verified edges |
| `harmonizer/harmonization/MaterialGroupingService.java` | DSU grouping of verified edges |
| `harmonizer/harmonization/MaterialGroup.java` | a group with original records and national code |
| `harmonizer/harmonization/NationalCodeRegistry.java` | stable `NMC-YYYY-NNNNNNNN` issuing |
| `harmonizer/harmonization/HarmonizationPipeline.java` | run / regroup orchestration |
| `harmonizer/harmonization/HarmonizationEngine.java` | stateful `@Component` used by the API |
| `harmonizer/api/HarmonizationController.java` | `POST /api/harmonize`, `POST /api/harmonize/reject` |

### 4.3 Tests and benchmarks

| File | Tests / purpose |
|---|---|
| `src/test/java/harmonizer/algo/KMPMatcherTest.java` | 16 tests |
| `src/test/java/harmonizer/algo/DisjointSetUnionTest.java` | 14 tests |
| `src/test/java/harmonizer/matching/AttributeExtractorTest.java` | 8 tests |
| `src/test/java/harmonizer/integration/HarmonizationPipelineTest.java` | 12 tests (7 scenarios + edge cases) |
| `src/main/java/harmonizer/bench/KmpBenchmark.java` | KMP vs naive, 3 experiments |
| `src/main/java/harmonizer/bench/DsuBenchmark.java` | DSU vs naive relabelling |

### 4.4 Documentation

`docs/KMP.md`, `docs/DSU.md`, `docs/INTEGRATION.md`, `docs/TESTING.md`, and an updated `README.md`.

---

## 5. Files modified

Only two existing files were touched, and neither is teammate code:

| File | Change |
|---|---|
| `.gitignore` | merge conflict resolved by keeping both sides' ignore rules |
| `README.md` | rewritten to describe the combined repository (the teammate's facts are preserved and cross-referenced) |

`README_part1.md`, `docs/algorithms.md`, `docs/architecture.md`, `docs/api-documentation.md`, all
`harmonizer` sources and all 47 pre-existing tests are unchanged.

---

## 6. Data flow after integration

```
CSV upload                     (harmonizer.io.CsvMaterialParser — teammate)
   │
   ├─ normalise descriptions    (harmonizer.normalize.TextNormalizer — teammate)
   │      units → mm, abbreviations via Trie, signature
   │
   ├─ Bloom screening           (harmonizer.screening.DuplicateScreener — teammate, unchanged)
   │      "definitely new" vs "maybe seen" + exact confirmation
   │
   └─ harmonize  ← POST /api/harmonize
          ├─ AttributeExtractor
          │     item type, form, metal, size (mm), grade          ← normaliser tokens
          │     specification                                    ← KMP + SpecCatalogue
          ├─ EquivalenceMatcher (verification gate)
          │     candidate pairs = same item-type block
          │     checks: type, form, metal, grade, size (±2%), specification
          │     verify  → VerifiedEdge (+ SpecEvidence: positions, comparisons)
          │     reject  → MatchEvidence (+ reasons)
          ├─ MaterialGroupingService  (DSU)
          │     makeSet(every record) → union(verified edges only)
          │     getGroups() → components → MaterialGroup (stable id + national code)
          └─ HarmonizationResult
                verified edges, rejected candidates, groups, KMP comparison count
```

### Candidate generation

Only pairs inside the same item-type block are compared (`pipe` with `pipe`, `valve` with `valve`), and
records whose item type is not recognised are never paired. That keeps the pair count at
`Σ Bᵢ²/2` instead of `n²/2`, but it is still quadratic inside a block — acceptable for the demo scale,
listed as a limitation.

### The verification gate (why a shared specification is not enough)

Every attribute is compared into one of three states — `MATCH`, `CONFLICT` or `UNKNOWN` — by
`AttributeComparison`, the single place where the rules live:

| Attribute | MATCH | CONFLICT | UNKNOWN |
|---|---|---|---|
| type | equal item type | different type | — (pairs are only formed inside a block) |
| form | equal (seamless / welded) | different form | either side missing |
| metal | equal (carbon steel, stainless steel, …) | different metal | either side missing |
| grade | equal after upper-casing | different grade | either side missing |
| dimensions | relative difference ≤ 2% in mm | difference > 2% | either side missing |
| specification | equal, or a KMP hit shared by both | different specification | either side missing |

An edge is verified only when `type == MATCH`, **no** attribute is `CONFLICT`, and at least
`MIN_CRITICAL_MATCHES = 2` of the four identity-defining attributes (metal, grade, dimensions,
specification) are positively `MATCH`ed. A missing attribute is therefore never evidence by itself:
`"Seamless pipe, ASTM A106"` shares the specification with a fully specified pipe but has only one
critical match, so it is rejected with an explicit *insufficient evidence* reason.

### Component validation (the transitive false-merge fix)

Pairwise checks cannot see a whole component. Three records can each pass an edge while the two ends
conflict:

```
A: carbon steel, 6 in, A106 Gr.B      (metal + size known)
B: 6 in, A106 Gr.B                    (metal unknown)
C: stainless steel, 6 in, A106 Gr.B   (metal + size known)
A–B verified, B–C verified, A–C rejected as a metal conflict
```

DSU alone would merge all three through B. So `MaterialGroupingService` validates each component after
extraction: members are placed into conflict-free subgroups with a deterministic first-fit pass (record
id order), and any component that needs more than one subgroup is reported as `splitComponents`. DSU
itself is untouched — path compression and union by size are exactly as before — and valid transitive
groups are still merged.

`HarmonizationPipelineTest` and `HarmonizationRegressionTest` prove the important cases:

* `conflictingGradesAreRejected` — same `ASTM A106`, grades `B` vs `C` → rejected, reason names the grade.
* `conflictingDimensionsAreRejected` — 152.4 mm vs 101.6 mm → rejected, reason names dimensions.
* `sharedSpecificationAloneDoesNotMerge` — pipe vs flange with the same specification → no candidate pair.
* `auditRegressionUnknownAttributesCannotBridgeConflictingMaterials` — the exact audit A-B-C case now
  yields three singletons and no national code.
* `bridgeWithSufficientEvidenceIsSplitBeforeGrouping` — when the bridge has three critical matches the
  component is split into `{A, B}` and `{C}` instead of being merged.
* `validTransitiveGroupingIsPreserved` — three fully specified equivalents still form one group.

### Units and tolerance

`TextNormalizer` converts inch/cm to millimetres (`6 inch → 152.4mm`, `150 mm → 150mm`), and the gate
accepts a 2% relative difference, so `6 inch` and `150 mm` are compatible while `6 inch` and `4 inch`
are not. Nominal pipe sizes (6 inch = DN150) are *not* modelled — that needs an engineering table and is
listed as a limitation in `README.md`.

### Rejection semantics

The approved edge list is the source of truth. A rejection removes that edge and rebuilds a fresh DSU,
because a path-compressed DSU cannot undo a union. `HarmonizationEngine#reject` is the API for this and
`HarmonizationPipelineTest#rejectingRelationshipsRebuildsGroupsCorrectly` asserts the resulting groups,
the preserved company codes and the national-code behaviour.

Rejections are **persistent application state**: `HarmonizationEngine` keeps a set of rejected
relationships, keyed by the pair of material identities (`CPSE|CODE` on both sides, order-independent)
rather than by transient record ids. Every subsequent `run()` filters those edges out before grouping and
reports how many were withheld (`suppressedPairs`), so a rejection is not undone by re-running, and two
unrelated relationships are never affected. Keying by material identity also means re-uploading the same
catalogues with new record ids keeps the decision, while a different pair reusing those ids does not
inherit it.

**Limitation, stated plainly:** the set lives in memory. Restarting the application clears it, exactly
like the uploaded dataset. Persistent review state needs a database (see §9).

### National codes

* Format `NMC-<year>-<8-digit sequence>`, e.g. `NMC-2026-00000001`.
* Only groups with at least two members receive one.
* `NationalCodeRegistry` resolves a code in this order:
  1. the **same membership** seen before → its existing code;
  2. the group **grew** (a strict subset of it already had a code) → inherit that code, even when the new
     record supplies an attribute that was previously unknown and therefore changes the fingerprint;
  3. the same **material fingerprint** (merged attributes) → that material's code, unless another group
     in the same run already claimed it;
  4. otherwise a new code is issued.
* A group's identity is a SHA-256 hash of its sorted `CPSE|CODE` members; its material identity is
  `MaterialFingerprint`, built from the merged attributes. Neither uses the DSU root, which changes under
  path compression.
* **Splitting is explicit:** after a rejection a subgroup is a subset, never a superset, so it cannot
  inherit by growth. If its attributes still match the material it keeps the material code; otherwise it
  gets a new one. The old code stays in `history()` and is never re-pointed at a different material.
* `claimedCodes` keeps two live groups from sharing a code, even when identical attributes make them the
  same material (possible only after a manual rejection).
* Original company codes are never overwritten; they stay in `MaterialRecord` and are copied into
  `MaterialGroup#originalCodes()`.
* **Persistence:** the registry is in memory. Codes are deterministic for a given sequence of calls, but
  a restart begins again at `NMC-<year>-00000001`, and a different import order can produce different
  numbers. This is not a national registry until the mapping is stored durably.

---

## 7. API added to the existing service

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/harmonize` | run KMP matching + verification + DSU grouping on the uploaded dataset |
| POST | `/api/harmonize/reject?recordA=1&recordB=2` | reject one verified relationship and rebuild the groups |

Sample run:

```bash
mvn spring-boot:run        # from the repository root, port 8081

curl -F "file=@data/demo_small.csv" http://localhost:8081/api/dataset/upload
curl -X POST http://localhost:8081/api/harmonize
curl -X POST "http://localhost:8081/api/harmonize/reject?recordA=1&recordB=3"
```

The port is 8081 so both Spring Boot applications can run at once: the legacy demo backend keeps 8080.
`frontend/vite.config.ts` proxies `/api/dataset`, `/api/harmonize`, `/api/autocomplete` and `/api/screen`
to the integrated app and every other `/api` path to the legacy backend. The existing React app has an
**Integrated Pipeline** tab (`frontend/src/pages/IntegratedPipeline.tsx`) that uploads a CSV, runs the
integrated pipeline, shows the three-state evidence per edge, lets a reviewer reject a relationship and
lists the groups with their national codes — so the website now uses the KMP/DSU pipeline directly.
No legacy endpoint was removed or renamed.

The response contains `verifiedEdges` (with `sharedSpecifications[].positionInA/positionInB` and
comparison counts), `rejectedCandidates` (with `rejectionReason`), `groups` (members, original codes,
national code) and `kmpComparisons`.

The teammate's existing endpoints (`/api/dataset/upload`, `/api/dataset/summary`, `/api/autocomplete`,
`/api/screen`) are untouched and still behave as documented in `README_part1.md`.

---

## 8. Conflicts and risks handled

| Risk | Handling |
|---|---|
| Merge conflict in `.gitignore` | union of both rules (Maven/Node/IDE + `target/`, `.DS_Store`) |
| Two `@SpringBootApplication` classes in one repo | each lives in its own Maven project (`harmonizer` vs `backend/`), so no classpath clash |
| Two `MaterialRecord` types | the new code uses `harmonizer.model.MaterialRecord` only; `com.matriq.model.MaterialRecord` stays inside `backend/` |
| DSU cannot undo a union | rebuild from approved edges (no fake delete) |
| National code duplication on rebuild | codes keyed by membership (growth inheritance) and material fingerprint, with per-run uniqueness claims |
| Original codes being overwritten by grouping | groups copy `MaterialRecord` unchanged; national code is additive |
| Test expectations weakened to make the build green | not done: one failing expectation was a **wrong LPS expectation in my own new test**, corrected against the algorithm; no existing test was edited |

---

## 9. Remaining limitations

1. **Two implementations of the same idea.** `backend/` still has its own KMP/DSU and its own pipeline.
   The root `harmonizer` project is the canonical one. The frontend now also drives the integrated
   pipeline (the **Integrated Pipeline** tab via the proxy), but the legacy backend was deliberately kept
   and not rewritten; consolidating it fully is a separate decision.
2. **Candidate generation is quadratic inside an item-type block.** Fine for the demo CSV sizes
   (`data/demo_small.csv`, `data/sample_1000.csv`); a production version needs size/grade bucketing.
3. **Attribute extraction is rule-based**, covering a fixed catalogue of item types, metals, forms and
   specifications. Unrecognised descriptions yield `null` attributes and are never paired.
4. **Nominal pipe sizes are not modelled.** 6 inch is treated as 152.4 mm with a 2% tolerance, so it
   matches 150 mm; it will not match a `DN150` record that has no millimetre value.
5. **`mvn test` at the repository root does not build `backend/`.** The standalone app is built from its
   own directory (`cd backend && mvn test`).
6. **Review is simulated.** `HarmonizationEngine#reject` is the review action; there are no users or audit
   trail in this stage.
7. **Rejections and national codes are in memory.** The rejection set and the code registry survive
   repeated calls but not an application restart: a restart clears the dataset and review state and
   renumbers national codes from `NMC-<year>-00000001`. Durable review state and a real national registry
   need a database, which this stage deliberately does not add.
