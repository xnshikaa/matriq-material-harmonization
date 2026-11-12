# MATRIQ — REST API Documentation
## Base URL: `http://localhost:8080`

All endpoints accept and return `application/json` unless handling `multipart/form-data` uploads.

---

## 1. Material Catalog & CSV Ingestion

### `POST /api/materials/upload`
Uploads a company CSV catalog with automatic schema mapping and validation.
- **Request Format:** `multipart/form-data`
  - `file`: CSV file (`.csv`)
  - `companyName`: Name of the issuing organization (e.g. `CPCL`, `IOCL`, `ONGC`)
- **Response (200 OK):**
```json
{
  "id": "BATCH-2026-0001",
  "companyName": "CPCL",
  "filename": "cpcl_sample.csv",
  "totalRows": 10,
  "importedCount": 10,
  "invalidCount": 0,
  "status": "COMPLETED",
  "validationWarnings": []
}
```

### `GET /api/materials`
Retrieves imported material records with optional filtering.
- **Query Parameters:**
  - `company` *(optional)*: Filter by company (e.g. `IOCL`)
  - `status` *(optional)*: `IMPORTED`, `NORMALIZED`, `GROUPED`, `APPROVED`
- **Response (200 OK):** Array of `MaterialRecord` objects.

### `GET /api/materials/{id}`
Returns the complete profile of a single material record including original code and normalized attributes.

### `GET /api/materials/stats`
Summary statistics of the imported repository.

---

## 2. Harmonization Engine

### `POST /api/harmonization/run`
Executes the complete multi-stage harmonization pipeline:
1. Normalization & abbreviation expansion via Trie
2. Bloom filter signature pre-screening
3. KMP technical specification extraction
4. Strict attribute verification (dimensions, categories, grades)
5. DSU component construction & national code staging

- **Response (200 OK):**
```json
{
  "totalRecords": 30,
  "normalizedRecords": 30,
  "candidateMatches": 12,
  "autoApprovedMatches": 8,
  "groupsFormed": 10,
  "executionDurationMs": 18
}
```

### `GET /api/harmonization/groups`
Returns all material groups discovered by DSU.

### `GET /api/harmonization/groups/{id}`
Returns group details and all linked member records from across companies.

### `GET /api/harmonization/network`
Returns graph data (nodes & edges) formatted for React Flow visualization.
- **Nodes:** Represent individual materials and National Code group hubs.
- **Edges:** Represent verified cross-company equivalences and group affiliations.

---

## 3. Match Review & Approvals

### `GET /api/matching/pending`
Returns proposed material matches awaiting human reviewer sign-off.

### `POST /api/matching/{id}/approve`
Approves a proposed material match and immediately merges the records in DSU.
- **Request Body:**
```json
{
  "reviewedBy": "Anshika Gupta",
  "note": "Verified dimensional equivalence: 6 in = 150 mm, both ASTM A106 Gr.B"
}
```

### `POST /api/matching/{id}/reject`
Rejects a proposed match. Triggers an automatic DSU rebuild from all remaining approved edges to eliminate false transitivity.

---

## 4. National Material Code Registry

### `POST /api/national-codes/assign`
Issues deterministic National Material Codes (`NMC-YYYY-XXXXXXXX`) to all approved groups containing $\ge 2$ members.
- Original organization part numbers are permanently retained.

### `GET /api/national-codes`
Lists all issued National Material Codes with company code cross-references.

### `GET /api/national-codes/{code}`
Retrieves a specific National Material Code with full provenance history.

---

## 5. Algorithm Lab & Interactive Trace Endpoints

### `POST /api/algorithms/kmp/search`
Runs Java KMP matcher on arbitrary text and pattern, returning match locations and exact comparison count.
- **Request Body:**
```json
{
  "text": "Pipe seamless 6 inch ASTM A106 Gr.B",
  "pattern": "ASTM A106"
}
```
- **Response (200 OK):**
```json
{
  "pattern": "ASTM A106",
  "match": true,
  "positions": [21],
  "comparisons": 38,
  "lps": [0, 0, 0, 0, 0, 1, 0, 0, 0],
  "executionTimeNanos": 12400
}
```

### `POST /api/algorithms/kmp/trace`
Generates a complete step-by-step execution trace (character comparisons, pointer movements, shifts) for UI step-through visualization.

### `POST /api/algorithms/dsu/demo`
Interactive demonstration of `makeSet`, `find` (with path compression animation), and `union` (union by size).

### `GET /api/algorithms/bloom/statistics`
Returns bit array size, hash count, current element count, and mathematically computed false positive probability.

### `POST /api/algorithms/trie/search`
Prefix suggestions and dictionary expansions from the industrial abbreviation Trie.

---

## 6. Benchmarks

### `POST /api/benchmarks/run`
Runs repeatable benchmarks at $N = 100, 1000, 10000$:
- **KMP vs Naive Search:** Character comparison count and execution time.
- **DSU Amortized Performance:** Union & Find throughput with path compression.

### `GET /api/benchmarks/results`
Returns historical benchmark logs.
