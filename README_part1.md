# Material Code Harmonization Engine: Bloom filter, Trie and API layer

This folder holds one half of the project: the **Bloom filter (CO5)**, the **Trie**,
the text normaliser that feeds them, the duplicate-screening stage, and the REST API the frontend calls.
KMP and disjoint-set union (DSU) are written separately; the hand-off points are described below.

## Layout

```
src/main/java/harmonizer/
  algo/bloom/BloomFilter.java        from-scratch Bloom filter (double hashing, FNV-1a + SplitMix64)
  algo/trie/Trie.java                from-scratch trie with sorted prefix search
  normalize/TextNormalizer.java      lower-case, unit conversion, abbreviation expansion (uses the Trie), signature
  screening/DuplicateScreener.java   Bloom pre-screen + exact verification -> confirmed pairs
  io/CsvMaterialParser.java          CSV upload parsing and validation
  service/DatasetStore.java          in-memory dataset + two Trie indexes
  api/                               REST controllers, error handling, CORS
  bench/                             synthetic data generator and benchmarks
src/test/java/...                    47 JUnit 5 tests
data/                                demo_small.csv (10 rows), sample_1000.csv (generated)
```

## Run

Prerequisites: JDK 17+ and Maven 3.9+.

```bash
mvn test                                   # all tests
mvn spring-boot:run                        # API on http://localhost:8080

# benchmarks (add "quick" for smaller sizes)
mvn -q compile
java -cp target/classes harmonizer.bench.BloomTrieBenchmark

# generate a CSV: <records> <duplicate rate 0..1> <output path>
java -cp target/classes harmonizer.bench.SyntheticMaterialGenerator 10000 0.3 data/sample_10k.csv
```

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/dataset/upload` | multipart field `file`: CSV with `cpse,code,description[,uom]` |
| GET | `/api/dataset/summary` | counts per company and upload warnings |
| GET | `/api/autocomplete?prefix=seam&field=description&limit=10` | Trie prefix search (`field` = `code` or `description`) |
| POST | `/api/screen?fpr=0.01` | Bloom screening; returns confirmed pairs, skipped rows, statistics |

```bash
curl -F "file=@data/demo_small.csv" http://localhost:8080/api/dataset/upload
curl "http://localhost:8080/api/autocomplete?prefix=gate&field=description"
curl -X POST "http://localhost:8080/api/screen?fpr=0.01"
```

Errors come back as JSON `{ "error": "...", "details": [...] }` with status 400 (bad input), 409 (no dataset
uploaded yet) or 413 (file too large).

## Hand-off to the other stages

- **DSU:** every item in `confirmedPairs` means `union(recordId, matchedWithId)`. Record ids are 1-based.
- **KMP:** `DuplicateScreener` takes the signature as a function. Spec extraction written with KMP can be
  added inside that function without touching the Bloom filter code.
- **Shared types:** `MaterialRecord` lives in `model/`. Keep one copy of it, `pom.xml` and the
  `@SpringBootApplication` class when merging with the other half of the project.
- **Frontend on another origin:** CORS is configured in `application.properties`
  (`app.cors.allowed-origin-patterns`). Restrict it to the frontend's URL before any real deployment.
- **Package:** all code lives under the root package `harmonizer`. The single `@SpringBootApplication`
  class must stay directly in it so Spring finds the controllers.

## Design notes and limitations 
- A Bloom filter "yes" means *possibly present*. The screener always confirms against an exact map before
  reporting a pair, so the filter can never create a false match.
- In memory, an exact hash map is as fast as Bloom + map (the benchmark shows this). The filter's benefit is
  space and skipping lookups in an expensive authoritative store (database, disk, another company's system).
- Matching is deterministic: normalised, sorted tokens must be identical. There is no spelling correction and
  no synonym handling beyond the abbreviation table. Inch to mm is a plain x25.4; real nominal sizes
  (6 inch = DN150) need an engineering table.
- The Trie stores every key character, so memory grows with data size. A hash map would also work for the
  abbreviation lookup; the Trie's real advantage is prefix search and autocomplete.
- Synthetic-data precision and recall are high because the generator's variations are the ones the
  normaliser handles. Real data would score lower.
