# MATRIQ — Intelligent Material Code Harmonization Engine

For the authenticated application, see the [operational setup and deployment guide](docs/OPERATIONS.md). It covers the canonical backend, actual catalogue imports, reviewer assignment and test commands.

> **ONE MATERIAL. ONE IDENTITY.**
> Advanced Algorithms CIA 3 Project | Academic Year 2025–2026
> **Problem Statement:** SIH26099 — Ministry of Petroleum & Natural Gas / CPCL
> **Engineering team:** Anshika Gupta, Hetanshi Vora
> **Contribution summary:** see [Team contributions](#13-team-contributions), based on commit authorship on `main`.

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
original company code intact and adding one national identifier per verified group
(`NMC-YYYY-NNNNNNNN`).

### Design principles

1. **A match is never decided by one algorithm.** A Bloom-filter hit or a KMP specification hit is
   *evidence*, not a verdict: category, form, metal, grade and dimensions must also agree.
2. **Safe matches unify immediately.** Verified, conflict-free relationships with sufficient evidence reach the grouping stage automatically; unresolved or conflicting candidates stay in review.
3. **Rebuild instead of undo.** A standard DSU cannot remove a union, so a rejection rebuilds the groups
   from the remaining accepted relationships.
4. **Original codes are never overwritten.** The national code is additive.
5. **Material decisions stay on the backend.** The React frontend uses REST APIs and ranks text searches to help users find candidate descriptions.

---

## 2. Repository layout

The root Maven project is the canonical backend for the operational MATRIQ application. The React
frontend in `frontend/` talks to this backend. `backend/` is the earlier standalone algorithm explorer
and is retained for compatibility; it is not used by the current operational workspace.

```
matriq-material-harmonization/
├── pom.xml                              canonical Spring Boot backend (Java 17)
├── src/main/java/harmonizer/
│   ├── algo/                            Bloom filter, Trie, KMP and DSU
│   ├── matching/                        extraction, attribute evidence and match decisions
│   ├── harmonization/                   compatible grouping and MATRIQ code registry
│   ├── workspace/                       accounts, catalogue imports, review and persistence
│   └── api/                             REST APIs
├── src/test/java/harmonizer/            backend tests
├── frontend/                            React + TypeScript operational UI
├── data/catalogues/                     3 base CSVs and import notes
├── IOCL.csv                              separate 10-row live upload input
├── data/archive/catalogues-original/     retained source catalogues, excluded from demo import
├── docs/                                 operational, algorithm, architecture and test guides
└── backend/                              legacy standalone explorer (Spring Boot, com.matriq)
```

The active demo inputs are synthetic and use company-style identifiers; they are not official company
material codes or procurement records. The base set contains 46 source rows across IOCL, NTPC and OIL,
with 15 unified identities and a deliberate pipe-grade discrepancy for review. The separate
`IOCL.csv` has 10 additional IOCL rows for a walkthrough.

## 3. Matching and review flow

```text
CSV upload → validation and provenance → attribute extraction and normalization
                                           │
                          candidate pairs compared using material attributes
                          KMP specifications + description search signals
                                           │
                   ┌───────────────────────┴───────────────────────┐
                   │                                               │
       sufficient, compatible evidence                  conflict / insufficient evidence
       automatically unifies safe matches               appears in Pending Reviews
       and updates the Unified Registry                  reviewer can approve, keep separate,
                                                         or request clarification with a note
```

Dimensions with supported units are converted to millimetres for comparison (for example, 100 mm and
4 inches). Search ranks descriptions using exact phrase, longest common substring and longest common
subsequence signals; it helps find candidates and does not decide material equivalence. Missing
attributes remain unknown rather than counting as a match. A reviewer can record an auditable override
for a genuine technical conflict, with a rationale.

## 4. Algorithms and ownership

| Algorithm | Role in MATRIQ | Owner | Time complexity | Space |
|---|---|---|---|---|
| **KMP** | Locates technical specifications (`ASTM A106`, `A106-B`, `API 5L`, `SS316`, `ANSI B16.5`, `IS 1239`, …) inside descriptions and records where they were found | **Anshika Gupta** | `O(n + m)` per pattern | `O(m)` LPS |
| **DSU (Union–Find)** | Groups records joined by verified equivalence relationships; transitive groups, independent groups | **Anshika Gupta** | `O(α(n))` amortised per find/union | `O(n)` |
| **Bloom filter** | Pre-screens duplicate signatures without touching the authoritative store | **Hetanshi Vora** | `O(k)` per lookup | `O(m)` bits |
| **Prefix trie** | Industrial abbreviation expansion + prefix autocomplete | **Hetanshi Vora** | `O(p)` lookup | `O(Σ·L)` |

---

## 5. Technology stack

* Java 17, Spring Boot 3.5 and Maven for the canonical backend.
* React 19, TypeScript, Vite and Tailwind CSS for the operational UI.
* Workspace state persists locally in `.matriq-state/`; hosted deployments use PostgreSQL snapshots.
* JUnit 5 covers the root algorithm engine and operational workspace. The older `backend/` project
  has separate compatibility tests and is not part of the active local startup.

## 6. Quickstart — operational application

Prerequisites: JDK 17+, Maven, and Node.js with npm. Open two Terminal windows from the repository.
For reviewer controls on the configured demo machine, use the reviewer launcher; to run as a standard
user, start the backend with `mvn spring-boot:run` instead.

```bash
# Terminal 1, repository root
./run-reviewer-backend.sh

# Terminal 2
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. On the configured machine, choose **Reviewer sign in** to use the
allowlisted reviewer account. Public sign-ups remain standard users. Local HTTP uses
`MATRIQ_SECURE_COOKIE=false`; hosted HTTPS keeps secure cookies enabled. See
[docs/OPERATIONS.md](docs/OPERATIONS.md) for import, persistence and deployment instructions.

To load the curated demo data into an empty local workspace, import `IOCL_2025.csv`, `NTPC_2024.csv`
and `OIL_2023.csv` in **Material Catalogues**, then click **Harmonize Materials**. Upload
`IOCL.csv` separately during the walkthrough.

### Algorithm API (academic pipeline)

The earlier algorithm-focused endpoints (`/api/dataset`, `/api/screen`, `/api/harmonize`) are available
under the optional `pipeline-api` Spring profile for compatibility and algorithm demonstrations. The
current MATRIQ interface uses the authenticated operational workspace API under `/api/workspace`.
See [docs/api-documentation.md](docs/api-documentation.md) and the source controllers for route details.

---

## 7. Legacy algorithm explorer (`backend/`)

This earlier Spring Boot application remains for compatibility and algorithm-explorer work. It is
separate from the active React operational UI and canonical root backend. Use its own `pom.xml` and
legacy profile only when working on that application; it is not needed to run MATRIQ locally.

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

## 10. Live demo walkthrough

1. Start the reviewer-enabled backend and React frontend using the commands in **Quickstart**.
2. Import the three base catalogues from `data/catalogues/`, then run **Harmonize Materials**. Show
   safe matches appearing in the Unified Registry and grade discrepancies remaining in Pending Reviews.
3. Open a real pipe-grade discrepancy (`TP304L` versus `TP316L`) and show its attribute comparison.
4. Enter a rationale and demonstrate **Approve Match**, **Keep Separate**, and **Request Clarification**.
   Show the reviewer note and decision history. Approval is an explicit, auditable override.
5. Use catalogue or registry search with terms such as `PIPE` and `SEAMLESS PIPE`. Explain that substring
   and subsequence similarity rank candidates but do not decide equivalence.
6. Upload `IOCL.csv` to demonstrate ten additional IOCL source rows joining existing
   identities without adding another review discrepancy.

## 11. Current limits

* Attribute extraction uses a finite vocabulary; unsupported descriptions remain unknown and require
  review rather than being treated as equivalent.
* Candidate generation compares pairs within material-type blocks, which is quadratic in the largest
  block. The current demo data is small enough for interactive use.
* Dimensions normalize supported mm and inch values. Nominal-size codes such as `DN150` may remain
  unknown unless a numeric size can be extracted.
* National codes and review decisions are stored in the workspace. Local data persists in
  `.matriq-state/`; hosted state is held in PostgreSQL. Changing machines or deployments does not
  automatically transfer workspace data.
* The legacy `backend/` project remains alongside the canonical root backend for compatibility.
* Benchmarks are single-machine measurements; quote the comparison counts and hardware next to any
  timing table.

## 12. Documentation index

| Document | Contents |
|---|---|
| [docs/OPERATIONS.md](docs/OPERATIONS.md) | Local startup, catalogue import, reviewer access, persistence and deployment |
| [data/catalogues/README_DEMO.md](data/catalogues/README_DEMO.md) | Curated dataset provenance and demo behavior |
| [docs/KMP.md](docs/KMP.md) | KMP theory, LPS walkthrough, evidence usage, complexity and benchmark |
| [docs/DSU.md](docs/DSU.md) | DSU operations, path compression, grouping and benchmark |
| [docs/INTEGRATION.md](docs/INTEGRATION.md) | Architecture and integration decisions |
| [docs/TESTING.md](docs/TESTING.md) | Test scenarios, recorded output and limitations |
| [docs/algorithms.md](docs/algorithms.md) | Bloom filter, Trie and screening notes |
| [docs/architecture.md](docs/architecture.md) | System architecture |
| [docs/api-documentation.md](docs/api-documentation.md) | API reference |

## 13. Team contributions

This summary follows commit authorship on the repository's `main` branch. Commits group features at
different levels, so these are feature-level credits rather than a line-by-line ownership measure. See
the [full GitHub commit history](https://github.com/xnshikaa/matriq-material-harmonization/commits/main).

**Anshika Gupta**
* Built the initial standalone application and authored the integrated KMP and DSU implementation,
  specification matching, grouping pipeline, and related tests and benchmarks.
* Led the authenticated operational-workspace commit: account and review flows, catalogue import,
  registry views, navigation and pages, integration documentation, and root application structure.
* Added the Render/PostgreSQL deployment setup, Vercel routing and readable authentication errors.
  ([initial application](https://github.com/xnshikaa/matriq-material-harmonization/commit/6c88fdebb54d34c2f2140c83a41551e627246723),
  [operational workspace](https://github.com/xnshikaa/matriq-material-harmonization/commit/b0eb9f12d3dca4cd4d97163dcc6ae2b3e33c68f5),
  [hosting](https://github.com/xnshikaa/matriq-material-harmonization/commit/560c62b945714f8f175ede37baacba1cab855716))

**Hetanshi Vora**
* Built the original Bloom filter, prefix trie, text normalization, duplicate screening, CSV parser,
  dataset store and supporting API layer.
* Refined final matching and harmonization behavior: safe automatic unification, unit-aware dimension
  comparison, reviewer conflict overrides and decision withdrawal, plus description search using exact,
  longest-common-substring and longest-common-subsequence signals.
* Curated the synthetic three-organisation catalogue set and live-upload CSV, and updated the reviewer
  launcher and operational demo documentation.
  ([algorithm foundation](https://github.com/xnshikaa/matriq-material-harmonization/commit/1a8d545863f4c6f516443b50d895e0ef74350812),
  [final workflow and demo](https://github.com/xnshikaa/matriq-material-harmonization/commit/509e1c643527174fc4b31a764587f07c4c0f5026))

The operational workspace and final integration evolved across both sets of commits; the bullets
highlight work visible in each contributor's commit history.
