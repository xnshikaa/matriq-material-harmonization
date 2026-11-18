# MATRIQ — Intelligent Material Code Harmonization Engine

For the authenticated application, see the [operational setup and deployment guide](docs/OPERATIONS.md). It covers the canonical backend, actual catalogue imports, reviewer assignment and test commands.

> **ONE MATERIAL. ONE IDENTITY.**
> Advanced Algorithms CIA 3 Project | Academic Year 2025–2026
> **Problem Statement:** SIH26099 — Ministry of Petroleum & Natural Gas / CPCL
> **Engineering team:** Anshika Gupta, Hetanshi Vora
> **Individual module (Anshika Gupta):** Knuth–Morris–Pratt (KMP) and Disjoint Set Union (DSU)

---

## 1. Problem statement & motivation

Public-sector organisations in oil, power, steel and mining (CPCL, IOCL, ONGC, BPCL, SAIL, NTPC) buy
the same industrial items but describe and code them differently:

| Company | Code | Description |
|---|---|---|
| CPCL | `PIPE-001` | Pipe, seamless, 6 inch, ASTM A106 Gr.B |
| IOCL | `P-9281` | SEAMLESS PIPE 150mm CS A106-B |
| ONGC | `MAT-1003` | Carbon steel seamless pipe, 6 in, A106 Grade B |

Three codes, one physical material. The result is fragmented procurement, duplicated inventory and lost
volume discounts.

MATRIQ reads each company's catalogue, finds equivalent items and groups them, while keeping every
original company code intact and adding one national identifier per approved group
(`NMC-YYYY-NNNNNNNN`).

### Design principles

1. **A match is never decided by one algorithm.** A Bloom-filter hit or a KMP specification hit is
   *evidence*, not a verdict: category, form, metal, grade and dimensions must also agree.
2. **Human approval decides.** Only approved relationships reach the grouping stage.
3. **Rebuild instead of undo.** A standard DSU cannot remove a union, so a rejection rebuilds the groups
   from the remaining approved relationships.
4. **Original codes are never overwritten.** The national code is additive.
5. **All assessed algorithms are pure Java on the backend.** The React frontend only consumes REST APIs.

---

## 2. Repository layout

The repository contains two Maven projects. **The root `harmonizer` project is the canonical,
integrated one** (both team members' algorithms, 132 tests, 3 benchmark programs).

```
matriq-material-harmonization/
├── pom.xml                             ← canonical project (root)
├── src/main/java/harmonizer/
│   ├── algo/bloom/BloomFilter.java      Bloom filter (FNV-1a + SplitMix64)          [Hetanshi]
│   ├── algo/trie/Trie.java              prefix trie incl. autocomplete              [Hetanshi]
│   ├── algo/kmp/KMPMatcher.java         KMP: buildLPS / search / searchAll          [Anshika]
│   ├── algo/kmp/KMPResult.java          matches + comparison count + LPS            [Anshika]
│   ├── algo/dsu/DisjointSetUnion.java   Union-Find: path compression + union by size[Anshika]
│   ├── normalize/TextNormalizer.java    units → mm, abbreviations, signature        [Hetanshi]
│   ├── screening/DuplicateScreener.java Bloom pre-screen + exact confirmation       [Hetanshi]
│   ├── io/CsvMaterialParser.java        CSV parsing + validation                    [Hetanshi]
│   ├── matching/                        KMP spec extraction + verification gate     [Anshika]
│   ├── harmonization/                   DSU grouping + national codes + pipeline    [Anshika]
│   ├── api/                             REST controllers                            [both]
│   ├── service/DatasetStore.java        in-memory dataset + Trie indexes            [Hetanshi]
│   └── bench/                           KmpBenchmark, DsuBenchmark, BloomTrieBenchmark,
│                                        SyntheticMaterialGenerator
├── src/test/java/harmonizer/            132 JUnit 5 tests
├── data/                                demo + generated CSV datasets
├── docs/
│   ├── KMP.md                           algorithm explanation + measured benchmark
│   ├── DSU.md                           algorithm explanation + measured benchmark
│   ├── INTEGRATION.md                   how the two halves were combined
│   ├── TESTING.md                       test/benchmark report with actual output
│   ├── algorithms.md, architecture.md, api-documentation.md      [existing]
│   └── …
├── backend/                             standalone demo app (Spring Boot, com.matriq)  [Anshika]
├── frontend/                            React + TypeScript UI for that app            [Anshika]
└── README_part1.md                      the teammate's original notes                 [Hetanshi]
```

`backend/` and `frontend/` are a **separate, kept-as-is demo application** with its own copy of the
algorithms and its own 32-test suite. It is not part of the root Maven build. See
`docs/INTEGRATION.md` §3 and §9 for why both trees exist and what the options are.

---

## 3. Pipeline (canonical project)

```
CSV upload  →  parse & validate  →  normalise  →  Bloom screening
                                                        │
                                              confirmed duplicates
                                                        │
POST /api/harmonize ────────────────────────────────────┴──────────►
   │
   ├─ attribute extraction        item type, form, metal, size (mm), grade
   │      specification  ← KMP over the catalogue (positions + comparison counts recorded)
   │
   ├─ verification gate           MATCH / CONFLICT / UNKNOWN per attribute
   │      type matches, nothing conflicts, ≥2 of (metal, grade, size, spec) positively match
   │      → VerifiedEdge; otherwise MatchEvidence with a human-readable reason
   │      (a missing attribute is UNKNOWN — never proof of equivalence)
   │
   ├─ DSU grouping                makeSet(all records) → union(verified edges only)
   │      components still containing contradictory attributes are split first
   │      national code per group of 2+ (kept when an equivalent record joins)
   │
   └─ HarmonizationResult         approved edges, rejected candidates, groups, KMP comparison count

Reviewer rejects a relationship → DSU is rebuilt from the remaining approved edges
```

---

## 4. Algorithms and ownership

| Algorithm | Role in MATRIQ | Owner | Time complexity | Space |
|---|---|---|---|---|
| **KMP** | Locates technical specifications (`ASTM A106`, `A106-B`, `API 5L`, `SS316`, `ANSI B16.5`, `IS 1239`, …) inside descriptions and records where they were found | **Anshika Gupta** | `O(n + m)` per pattern | `O(m)` LPS |
| **DSU (Union–Find)** | Groups records joined by verified equivalence relationships; transitive groups, independent groups | **Anshika Gupta** | `O(α(n))` amortised per find/union | `O(n)` |
| **Bloom filter** | Pre-screens duplicate signatures without touching the authoritative store | Hetanshi Vora | `O(k)` per lookup | `O(m)` bits |
| **Prefix trie** | Industrial abbreviation expansion + prefix autocomplete | Hetanshi Vora | `O(p)` lookup | `O(Σ·L)` |

### KMP in one paragraph

A naive search is `O(n·m)`. KMP pre-computes the LPS array (longest prefix that is also a suffix) in
`O(m)` and uses it to slide the pattern on a mismatch without re-reading text, giving `O(n + m)` and
supporting overlapping matches. In MATRIQ it runs per catalogued pattern over every description; each
hit is recorded as `SpecHit(pattern, canonical specification, positions, comparisons)`. A shared
specification is only evidence — the verification gate still decides. Full viva explanation, proofs and
benchmark: **[docs/KMP.md](docs/KMP.md)**.

### DSU in one paragraph

`makeSet` puts every record in its own set; `union` merges the sets of a verified pair (smaller tree
under larger); `find` returns the representative and compresses the path, giving `O(α(n))` amortised.
`getGroups()` returns every component deterministically, singletons included. Because a DSU cannot undo a
union, a rejected relationship triggers a rebuild from the remaining approved edges. Full explanation
and benchmark: **[docs/DSU.md](docs/DSU.md)**.

---

## 5. Technology stack

**Canonical project (root)**
* Java 17, Spring Boot 3.5 (web), Maven 3.9
* JUnit 5 (`spring-boot-starter-test`), 132 tests
* No database: datasets and results are in memory

**Standalone demo app (`backend/` + `frontend/`)**
* Java 17, Spring Boot 3.2.5, OpenCSV, Lombok
* React 19 + TypeScript + Vite + Tailwind CSS v4, React Flow, Recharts

---

## 6. Quickstart — canonical project (repository root)

Prerequisites: JDK 17+, Maven 3.9+.

```bash
cd matriq-material-harmonization        # repository root

mvn clean test                          # 132 tests
mvn spring-boot:run                     # API on http://localhost:8081
```

### REST API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/dataset/upload` | multipart field `file`: CSV with `cpse,code,description[,uom]` |
| GET | `/api/dataset/summary` | counts per company + upload warnings |
| GET | `/api/autocomplete?prefix=seam&field=description&limit=10` | Trie prefix search |
| POST | `/api/screen?fpr=0.01` | Bloom screening: confirmed pairs, skipped rows, statistics |
| POST | `/api/harmonize` | **KMP matching + verification + DSU grouping** |
| POST | `/api/harmonize/reject?recordA=1&recordB=3` | **reject a relationship and rebuild groups** |

### Sample execution

```bash
curl -F "file=@data/demo_small.csv" http://localhost:8081/api/dataset/upload
curl -X POST http://localhost:8081/api/harmonize
```

Against `data/demo_small.csv` (8 valid rows; 2 rows rejected on import as a duplicate code and a blank
description) the harmonization stage currently returns:

```json
{
  "totalRecords": 8,
  "candidatePairs": 5,
  "verifiedPairs": 5,
  "rejectedPairs": 0,
  "kmpComparisons": 363,
  "groupCount": 4,
  "groupedRecords": 7,
  "groups": [
    {
      "id": "GRP-…",
      "nationalCode": "NMC-2026-00000001",
      "members": [
        { "id": 1, "cpse": "CPCL", "code": "MAT-10234", "description": "Pipe, seamless, 6 inch, ASTM A106 Gr.B" },
        { "id": 2, "cpse": "NTPC", "code": "PX-889",   "description": "SEAMLESS PIPE 150mm CS A106-B" },
        { "id": 3, "cpse": "SAIL", "code": "4410022",  "description": "Pipe smls 6\" ASTM A106 grade B" }
      ]
    },
    { "id": "GRP-…", "nationalCode": "NMC-2026-00000002", "members": [ { "id": 4 }, { "id": 5 } ] },
    { "id": "GRP-…", "nationalCode": "NMC-2026-00000003", "members": [ { "id": 6 }, { "id": 7 } ] },
    { "id": "GRP-…", "nationalCode": null,              "members": [ { "id": 8 } ] }
  ],
  "verifiedEdges": [
    {
      "recordA": 1,
      "recordB": 2,
      "evidence": {
        "sharedSpecifications": [
          { "specification": "ASTM A106", "positionInA": 24, "positionInB": 23,
            "comparisonsA": 48, "comparisonsB": 35 }
        ],
        "checks": { "type": true, "form": true, "metal": true, "grade": true,
                    "dimensions": true, "specification": true },
        "rejectionReason": ""
      }
    }
  ]
}
```

(The counts are from an actual run and depend on the uploaded file; members, edges and other groups are
abbreviated above for readability.)

---

## 7. Quickstart — standalone demo application (`backend/` + `frontend/`)

```bash
cd backend
mvn test                 # 32 existing tests
mvn spring-boot:run      # http://localhost:8080

cd ../frontend
npm install
npm run dev              # http://localhost:5173
```

The React app consumes the `com.matriq` API (`/api/materials`, `/api/harmonization`, `/api/matching`,
`/api/national-codes`, `/api/algorithms/...`, `/api/benchmarks`) and visualises the pipeline, the KMP
trace player and the DSU demo.

### Using the integrated pipeline from the same frontend

The website can also drive the canonical root application. Start **both** backends, then the Vite dev
server:

```bash
mvn spring-boot:run                 # repository root  → integrated harmonizer on 8081
cd backend && mvn spring-boot:run   # legacy demo app                  → 8080
cd frontend && npm run dev          # UI on 5173
```

`frontend/vite.config.ts` proxies `/api/dataset`, `/api/harmonize`, `/api/autocomplete` and `/api/screen`
to **8081** and every other `/api` path to **8080**, so one frontend serves both applications. The
**Integrated Pipeline** tab uploads a CSV to the root app, runs KMP + evidence gate + DSU, shows the
three-state attribute evidence per edge, rejects a relationship, and lists groups with their national
codes. No legacy endpoint was removed or renamed.

```bash
#!/usr/bin/env bash
# or on Windows: run-backend.bat / run-frontend.bat at the repository root
```

---

## 8. Testing

```bash
mvn clean test                       # root project: 132 tests
cd backend && mvn test               # standalone app: 32 tests
```

| Area | Tests |
|---|---|
| KMP (`KMPMatcherTest`) | 16 |
| DSU (`DisjointSetUnionTest`) | 14 |
| Attribute & specification extraction | 8 |
| Attribute states & evidence rule (`AttributeComparisonTest`) | 7 |
| End-to-end pipeline (7 scenarios) | 12 |
| Regression: false merges, persistent rejections, code lifecycle (`HarmonizationRegressionTest`) | 9 |
| National code registry (`NationalCodeRegistryTest`) | 9 |
| REST API: upload, harmonize, reject, error mapping (`HarmonizationApiTest`) | 10 |
| Bloom filter, Trie, normaliser, CSV parser, screening (pre-existing) | 47 |
| **Total (root project)** | **132 — all passing** |

Actual per-class results, the seven acceptance scenarios and the honest notes about what is not covered:
**[docs/TESTING.md](docs/TESTING.md)**.

---

## 9. Benchmarks

```bash
mvn -q compile
java -cp target/classes harmonizer.bench.KmpBenchmark            # KMP vs naive
java -cp target/classes harmonizer.bench.DsuBenchmark            # DSU vs naive relabelling
java -cp target/classes harmonizer.bench.BloomTrieBenchmark      # existing Bloom / Trie / screening
java -cp target/classes harmonizer.bench.SyntheticMaterialGenerator 10000 0.3 data/sample_10k.csv
```

Add `quick` for smaller sizes. All measurements in this repository are real runs on the development
machine (JDK 17.0.21, 12 cores); every benchmark verifies that the alternative implementation produces
the same result before reporting timings.

Headlines from the recorded run (details in [docs/TESTING.md](docs/TESTING.md)):

* KMP used ~1.06M comparisons on 1M characters of realistic text vs ~1.17M for naive; on a synthetic
  worst case it used 2.0M vs 50.0M (ratio 0.04, ~14× faster wall-clock). KMP is **not** claimed to be
  faster in every case — at 1M characters of realistic text, naive was marginally faster.
* DSU unions took 0.179 / 0.488 / 4.287 ms for 1k / 10k / 100k records, while naive relabelling grew
  quadratically (3.2 / 107.4 ms at 1k / 10k and was not practical at 100k). Both produced identical
  partitions wherever both ran.

---

## 10. Live viva walkthrough

**A. Canonical pipeline (terminal, no UI needed)**

1. `mvn clean test` — 132 green tests.
2. `mvn spring-boot:run` (port 8081), then upload a CSV and `POST /api/harmonize`.
3. Show `verifiedEdges[].evidence.sharedSpecifications` — the KMP positions and comparison counts that
   justified each pair.
4. Show a rejected pair in `rejectedCandidates` with its `rejectionReason` (grade or dimension).
5. `POST /api/harmonize/reject?recordA=1&recordB=3` and show the rebuilt groups.

**B. React demo app (visual)**

1. Open `http://localhost:5173`, load the PSU demo catalogues on the **Command Center**.
2. **Run Harmonization Engine** — watch normalisation, Bloom screening, KMP specification extraction,
   attribute verification and DSU grouping.
3. Inspect pairs in the **Harmonization Workspace** (attribute badges: category, grade, dimensions,
   KMP spec found).
4. Reject a match in **Review & Approvals** and explain the DSU rebuild.
5. Assign and inspect national codes in the **National Registry**; expand a row to see the original
   company part numbers.
6. **Algorithm Lab** → KMP player (character comparisons, LPS fallback) and DSU visualiser.
7. **Benchmarks & Viva** → measured scaling charts.
8. **Integrated Pipeline** → upload a CSV, run the root harmonizer (KMP + evidence gate + DSU), reject
   an edge and watch the rebuilt groups and their national codes.

---

## 11. Limits (stated, not hidden)

* Rule-based attribute extraction: only the catalogued item types, forms, metals, grades and standards
  are recognised; unrecognised descriptions are never paired.
* Candidate generation compares every pair inside an item-type block — quadratic in the largest block,
  fine for the demo datasets.
* Nominal pipe sizes are not modelled: 6 inch is treated as 152.4 mm and accepted against 150 mm with a
  2% tolerance, but a bare `DN150` record has no millimetre value to compare.
* National codes are deterministic for a given sequence of calls, not globally: a group that grows keeps
  its code, a split subgroup keeps it only while its material identity is unchanged, and the history keeps
  every issuance. The registry — like the dataset and the rejection set — is **in memory**, so restarting
  renumbers from `NMC-<year>-00000001` and a different import order gives different numbers.
* Rejected relationships survive repeated `run()` calls in the same process, but not a restart. Durable
  review state requires a database.
* Two parallel implementations exist until the team consolidates `backend/` into the root project.
* Benchmarks are single-machine measurements; quote the comparison counts (deterministic) and the
  hardware next to any timing table.

---

## 12. Documentation index

| Document | Contents |
|---|---|
| [docs/KMP.md](docs/KMP.md) | KMP theory, LPS walkthrough, API, evidence usage, complexity, benchmark, viva Q&A |
| [docs/DSU.md](docs/DSU.md) | DSU structure, operations, path compression, union by size, rebuild-on-rejection, benchmark, viva Q&A |
| [docs/INTEGRATION.md](docs/INTEGRATION.md) | Phase 1/2 integration report: what existed, what was added, decisions, risks, limitations |
| [docs/TESTING.md](docs/TESTING.md) | Per-class test results, seven scenarios, actual benchmark output, what is not tested |
| [docs/algorithms.md](docs/algorithms.md) | Existing algorithm notes (Bloom filter, Trie, screening) |
| [docs/architecture.md](docs/architecture.md) | Existing architecture notes |
| [docs/api-documentation.md](docs/api-documentation.md) | Existing API reference |
| [README_part1.md](README_part1.md) | The teammate's original notes for the Bloom/Trie/API half |

---

## 13. Team contributions

**Anshika Gupta**
* KMP: implementation, unit tests, benchmarks, and integration into specification matching.
* DSU: implementation, unit tests, benchmarks, grouping service, rebuild-on-rejection.
* Verification gate (attribute extraction + evidence), national code registry, harmonization pipeline
  and `POST /api/harmonize`, rejection API.
* Integration of both halves into one project, documentation (`docs/KMP.md`, `docs/DSU.md`,
  `docs/INTEGRATION.md`, `docs/TESTING.md`), this README.
* Standalone demo application (`backend/`) and its React UI (`frontend/`).

**Hetanshi Vora**
* Bloom filter (double hashing, FNV-1a + SplitMix64), false-positive analysis and benchmarks.
* Prefix trie with autocomplete.
* Text normaliser (unit conversion, abbreviation expansion, canonical signature).
* Duplicate screener (Bloom pre-screen + exact confirmation), CSV parser, dataset store, REST API layer
  and CORS.
* The 47-test baseline this integration builds on.

---

*Academic attribution: MATRIQ is a university CIA 3 Advanced Algorithms project for Smart India
Hackathon 2026 problem statement SIH26099 (Ministry of Petroleum & Natural Gas / CPCL).*
