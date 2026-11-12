# MATRIQ — Intelligent Material Code Harmonization Engine
> **ONE MATERIAL. ONE IDENTITY.**  
> Advanced Algorithms CIA 3 Project | Academic Year 2025–2026  
> **Problem Statement:** SIH26099 — Ministry of Petroleum & Natural Gas / CPCL  
> **Engineering Team:** Anshika Gupta, Hetanshi Vora  
> **Individual Assigned Module (Anshika Gupta):** KMP String Search & Disjoint Set Union (DSU)

---

## 1. Problem Statement & Motivation
Large public-sector organizations in oil, power, steel, and mining (CPCL, IOCL, ONGC, BPCL, SAIL, NTPC) procure identical industrial supplies. However, each organization maintains custom internal codification and phrasing:
- **CPCL:** `PIPE-001` &rarr; *"Pipe, seamless, 6 inch, ASTM A106 Gr.B"*
- **IOCL:** `P-9281` &rarr; *"SEAMLESS PIPE 150mm CS A106-B"*
- **ONGC:** `MAT-1003` &rarr; *"Carbon steel seamless pipe, 6 in, A106 Grade B"*

This causes fragmented procurement, redundant inventories, lost volume discounts, and inconsistent material master records.

**MATRIQ solves this challenge** by:
1. Identifying equivalent materials across multiple enterprise catalogs.
2. Grouping confirmed items into unified clusters using **Disjoint Set Union (DSU)**.
3. Assigning a permanent, deterministic **National Material Code (NMC)** (`NMC-YYYY-XXXXXXXX`).
4. **Preserving all original company-specific codes permanently.**

---

## 2. Core Architectural Principles & Safeguards
1. **Zero False Unions via Multi-Attribute Gate:** A Bloom Filter match or KMP pattern match alone **NEVER** merges materials. Matches must pass category alignment, grade compatibility, and strict dimension tolerance conversions (e.g., $6\text{ in} = 152.4\text{ mm} \approx 150\text{ mm} \pm 2\%$).
2. **Deterministic National Identifiers:** National Material Codes are deterministic and remain stable across evaluations.
3. **Rebuild on Rejection (DSU Consistency):** Standard DSU cannot rollback an arbitrary union in $O(1)$ due to path compression. MATRIQ maintains an approved edge list. When a match is rejected by a reviewer, the DSU resets and rebuilds in $O(E \cdot \alpha(V))$, guaranteeing zero contaminated groups.
4. **No Assessed Algorithms in JavaScript:** All core algorithms (KMP, DSU, Bloom Filter, Trie) are written in **pure Java** on the backend. The React frontend strictly consumes REST APIs to display visualizations.

---

## 3. Technology Stack

### Backend
- **Java 17 (LTS)**
- **Spring Boot 3.2.3**
- **Apache Maven 3.9.6**
- **Spring Web (REST API)**
- **Jakarta Bean Validation**
- **JUnit 5 (Automated Test Suite with 32+ unit & integration tests)**

### Frontend
- **React 19 & TypeScript**
- **Vite 8**
- **Tailwind CSS v4**
- **@xyflow/react (React Flow for material cluster network visualization)**
- **Recharts (for empirical benchmark visualizations)**
- **Lucide Icons**

---

## 4. Repository Structure

```
matriq-material-harmonization/
├── backend/
│   ├── pom.xml                                      # Maven configuration
│   └── src/
│       ├── main/java/com/matriq/
│       │   ├── MatriqApplication.java               # Spring Boot entry point
│       │   ├── algorithms/
│       │   │   ├── bloom/BloomFilter.java           # CO5 Bloom Filter implementation
│       │   │   ├── trie/MaterialTrie.java           # Prefix lookup & abbreviation engine
│       │   │   ├── kmp/KMPMatcher.java              # O(n+m) KMP with comparison metrics
│       │   │   ├── kmp/KMPResult.java               # KMP result container
│       │   │   ├── kmp/KMPTracer.java               # Step-by-step trace generator
│       │   │   └── dsu/DisjointSetUnion.java        # O(α(n)) Union-Find with path compression
│       │   ├── model/                               # Domain models & entities
│       │   ├── repository/                          # In-memory thread-safe repositories
│       │   ├── normalization/                       # Normalization & Trie integration
│       │   ├── matching/                            # Attribute verification & candidate screening
│       │   ├── harmonization/                       # Pipeline coordinator & DSU manager
│       │   └── controller/                          # REST controllers
│       └── test/java/com/matriq/
│           ├── algorithms/
│           │   ├── KMPMatcherTest.java              # 13 tests (LPS, overlapping, comparisons)
│           │   ├── DisjointSetUnionTest.java        # 10 tests (transitivity, union by size)
│           │   ├── BloomFilterTest.java             # 4 tests (FPP, optimal bits)
│           │   └── MaterialTrieTest.java            # 3 tests (prefix, abbreviations)
│           └── integration/
│               └── HarmonizationIntegrationTest.java# End-to-end pipeline & rollback verification
├── frontend/
│   ├── src/
│   │   ├── components/Header.tsx                    # Editorial industrial navigation bar
│   │   ├── pages/
│   │   │   ├── CommandCenter.tsx                    # Executive KPI dashboard & pipeline trigger
│   │   │   ├── ImportMaterials.tsx                  # CSV upload & normalization audit drawer
│   │   │   ├── HarmonizationWorkspace.tsx           # Side-by-side match cards & attribute badges
│   │   │   ├── MaterialNetwork.tsx                  # Interactive React Flow cluster graph
│   │   │   ├── NationalRegistry.tsx                 # NMC table with mapped company codes & CSV export
│   │   │   ├── ReviewApprovals.tsx                  # Human-in-the-loop review queue & DSU rollback
│   │   │   ├── AlgorithmLab.tsx                     # Step-by-step KMP player & DSU visualizer
│   │   │   └── SettingsBenchmarks.tsx               # Recharts benchmark scaling (100 / 1k / 10k)
│   │   └── services/api.ts                          # Typed API client
│   └── package.json
├── data/
│   ├── sample/                                      # CPCL, IOCL, ONGC live demonstration CSVs
│   └── generated/                                   # 105-record synthetic PSU dataset
├── docs/
│   ├── architecture.md                              # System architecture & data flow diagrams
│   ├── algorithms.md                                # Detailed algorithmic proofs & viva questions
│   └── api-documentation.md                         # Complete REST API reference
└── README.md
```

---

## 5. Quickstart & Setup Instructions

### Prerequisites
- **JDK 17** (`java -version`)
- **Apache Maven 3.8+** (`mvn -version`)
- **Node.js 18+ & npm 9+** (`node -v`, `npm -v`)

### Step 1: Run the Backend (Spring Boot)
Open a terminal in `backend/`:
```bash
cd backend
mvn clean test
mvn spring-boot:run
```
*The backend starts at `http://localhost:8080`.*

### Step 2: Run the Frontend (React + Vite)
Open a second terminal in `frontend/`:
```bash
cd frontend
npm install
npm run dev
```
*The frontend development server starts at `http://localhost:5173` with automatic API proxying to port 8080.*

---

## 6. How to Demonstrate in a Live Viva

1. **Open the Web Application:** Navigate to `http://localhost:5173`.
2. **Load Sample Catalogs:** On the **Command Center**, click **"Load PSU Demo Catalogs"** to ingest data for CPCL, IOCL, and ONGC.
3. **Execute Harmonization Engine:** Click **"Run Harmonization Engine"**.
   - Watch the backend process normalization, Bloom screening, KMP specification extraction, attribute verification, and DSU grouping.
4. **Inspect Matches in Harmonization Workspace:**
   - Review proposed pairs (e.g. CPCL `PIPE-001` vs IOCL `P-9281` vs ONGC `MAT-1003`).
   - Check the **Attribute Verification Badges** (Category PASS, Grade PASS, Dimensions COMPATIBLE, KMP Spec: ASTM A106).
5. **Demonstrate Rejection & DSU Rebuild:**
   - In **Review & Approvals**, reject an incorrect match.
   - Explain how DSU recalculates groups from remaining approved edges without invalid transitive groupings.
6. **Assign & Inspect National Codes:**
   - Go to **National Registry** to view issued codes (e.g. `NMC-2026-00000001`). Click any row to expand the mapped original organization part numbers. Export as CSV.
7. **Demonstrate Algorithm Lab (Anshika Gupta's Viva Highlight):**
   - Switch to **Algorithm Lab &rarr; KMP Matcher**.
   - Click **Play** to animate character-by-character comparison tape, LPS array lookup, and shift fallback.
   - Switch to **DSU Union-Find** to demonstrate `makeSet`, `union`, and path-compressed `find`.
8. **View Benchmarks:** Open **Benchmarks & Viva** to view real $O(n+m)$ vs $O(n \cdot m)$ execution charts.

---

## 7. Algorithms Summary & Viva Complexity Table

| Algorithm | Role in MATRIQ | Primary Developer | Time Complexity | Auxiliary Space |
| :--- | :--- | :--- | :--- | :--- |
| **KMP Matcher** | Locates technical specs in descriptions | **Anshika Gupta** | $O(n + m)$ | $O(m)$ (LPS array) |
| **DSU (Union-Find)** | Groups confirmed equivalent records | **Anshika Gupta** | $O(\alpha(n))$ amortized | $O(n)$ |
| **Bloom Filter** | Screens signature duplicates (CO5) | Hetanshi Vora | $O(k)$ | $O(m)$ (Bit array) |
| **Prefix Trie** | Industrial abbreviation expansion | Hetanshi Vora | $O(p)$ lookup | $O(\Sigma \cdot L)$ |

---

## 8. Automated Testing
Run all 32 unit and integration tests from `backend/`:
```bash
mvn test
```
- `KMPMatcherTest.java`: 13 test cases (LPS table, overlapping matches, repeated chars, comparisons counting, empty/null safety).
- `DisjointSetUnionTest.java`: 10 test cases (path compression, union by size, component sizes, idempotency, unregistered IDs).
- `BloomFilterTest.java`: 4 test cases (no false negatives, FPP bounds, optimal parameters).
- `MaterialTrieTest.java`: 3 test cases (prefix suggestions, abbreviation dictionary values).
- `HarmonizationIntegrationTest.java`: 2 tests (end-to-end 3-company harmonization workflow and attribute gate rejection).

---

## 9. Academic Attribution
This software was engineered as a comprehensive university CIA 3 Advanced Algorithms implementation for Smart India Hackathon 2026 problem SIH26099 (Ministry of Petroleum & Natural Gas / CPCL).

- **Anshika Gupta:** Knuth-Morris-Pratt Algorithm, Disjoint Set Union Algorithm, Unit Tests, Benchmarks, Engine Integration, Viva Defense Guide.
- **Hetanshi Vora:** Bloom Filter Pre-Screening, Prefix Trie & Industrial Abbreviation Engine.