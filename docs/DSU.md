# Disjoint Set Union (Union–Find) in MATRIQ

**Module owner:** Anshika Gupta
**Code:** `src/main/java/harmonizer/algo/dsu/DisjointSetUnion.java`
**Tests:** `src/test/java/harmonizer/algo/DisjointSetUnionTest.java` (14 tests)
**Benchmark:** `src/main/java/harmonizer/bench/DsuBenchmark.java`
**Used by:** `harmonizer.harmonization.MaterialGroupingService` → harmonization pipeline → national codes

---

## 1. What problem does it solve?

The matching stage produces **verified relationships**, each one saying "record 1 and record 2 are the
same physical material". Those relationships are transitive:

```
verified:     A = B
verified:     B = C
not verified: C = D
```

The correct answer is `{A, B, C}` and `{D}` — and no pair of A/C was ever compared directly. Working
that out by repeatedly scanning the relationship list is slow and easy to get wrong. DSU answers it in
effectively constant time per relationship.

A DSU (also called Union–Find) maintains a collection of **disjoint sets** under two operations:
`union` (merge two sets) and `find` (which set is this element in?).

---

## 2. Representation

Material record ids are 1..n (`MaterialRecord.id()`), so the structure is two arrays plus counters:

```
parent[id] = id        -> id is a root (representative of its set)
parent[id] = other     -> id points towards the root
parent[id] = 0         -> id was never registered
size[root]  = number of elements in that root's tree
```

`parent[id] = 0` is used as the "not registered" marker because valid ids start at 1. An unregistered
or out-of-range id raises `IllegalArgumentException` instead of silently joining a group.

---

## 3. Operations

### 3.1 `makeSet(int id)` — O(1)

Registers the record as its own set: `parent[id] = id`, `size[id] = 1`,
`componentCount++`, `elementCount++`. Calling it twice for the same id is a no-op, so re-registering a
record cannot reset an existing group.

### 3.2 `find(int id)` — O(α(n)) amortised

Walks to the root, then **path-compresses** the whole path:

```
before:  2 -> 1 -> 3          (3 is the root)
after find(2):  2 -> 3, 1 -> 3
```

Two passes: find the root, then re-point every node on the path at it. Later lookups are one step.
`depth(id)` exposes the distance to the root; the test `pathCompressionFlattensTheTree` asserts depth 2
before the find and depth 1 after it.

### 3.3 `union(int a, int b)` — O(α(n)) amortised

Finds both roots; if they are already equal it returns `false` (repeated unions are safe). Otherwise it
attaches the **smaller** tree under the **larger** root and updates the size at the new root:

```
size[3] = 4, size[7] = 2
union(3, 7) -> parent[7] = 3, size[3] = 6      (smaller under larger)
```

Union by size keeps trees shallow, which is what makes the amortised bound work together with path
compression.

### 3.4 `connected(a, b)`

`find(a) == find(b)`.

### 3.5 `getGroups()` — O(n · α(n))

Groups every registered id by its root. Each group is sorted ascending, groups are ordered by their
smallest member, and **singletons are included** so an ungrouped material is visible as a one-element
group rather than disappearing. The test `everyRecordAppearsExactlyOnce` checks that the groups contain
each of the 6 registered records exactly once.

### 3.6 `getComponentSize(id)`

`size[find(id)]` — the number of records in that group (1 for a singleton).

### 3.7 Diagnostic helpers

`getComponentCount()`, `getElementCount()`, `capacity()`, `isRegistered(id)`, `depth(id)`.

---

## 4. Transitivity and independence

`DisjointSetUnionTest` covers exactly the cases from the specification:

* **A = B, B = C** → one component of size 3, `connected(A, C) == true` (test `transitiveGrouping`).
* **A = B, C = D** → two components of size 2, `connected(A, C) == false` (test `independentGroups`).

For material data the second case is just as important as the first: two different materials must never
collide because they were processed next to each other.

---

## 5. No delete / no undo — recompute instead

A standard DSU **cannot** remove an arbitrary union: after path compression the original links are
gone, and splitting a set would require scanning every element. MATRIQ does not pretend otherwise and
does not implement a misleading `delete` or `undo`.

Instead, rejection works by rebuilding:

```
1. keep the list of approved relationships (verified edges)
2. a reviewer rejects one relationship
3. drop that edge from the list
4. build a NEW DSU from the remaining edges and re-extract the components
```

`HarmonizationPipeline#regroup` and `HarmonizationEngine#reject` implement this. Cost:
`O((V + E) · α(V))` per rebuild, which is affordable because rebuilds happen only on human review
decisions, not per record.

`HarmonizationPipelineTest#rejectingRelationshipsRebuildsGroupsCorrectly` proves the behaviour:

* After rejecting the direct A=C edge, A, B and C are **still one group** via A=B and B=C.
* After rejecting B=C as well, C leaves the group: groups are `{A, B}` and `{C}`, C has no national
  code, and C's original company code is still `MAT-1003`.

### Rejections are remembered

`HarmonizationEngine` keeps the rejected relationships as explicit state, keyed by the pair of material
identities (`CPSE|CODE`, order-independent) rather than by record id. Every `run()` filters those edges out
before rebuilding the DSU, and the result reports them as `suppressedPairs`, so a rejection is not undone
by the next harmonization request and unrelated relationships are untouched.

This state is **in memory**: it survives repeated calls but not an application restart. Persistent review
state would need a database and is listed as a limitation in `README.md` and `docs/INTEGRATION.md`.

---

## 6. Where DSU is used in MATRIQ

`MaterialGroupingService.group(records, approvedEdges)`:

1. creates a DSU with capacity = highest record id,
2. `makeSet` for every imported record (including ones with no relationship),
3. `union` for each **verified** edge only (rejected candidates never reach it),
4. `getGroups()` → connected components,
5. turns each component into a `MaterialGroup` with a stable id and (for groups of 2+) a national code.

`MaterialGroup` keeps every `MaterialRecord` unchanged, so each company's original code is preserved.
The national code is added alongside it, never instead of it.

### Stable national codes

The DSU root is **not** a stable identity: path compression changes it. Two independent identities are
therefore derived from the members instead: a group id (SHA-256 of the sorted `CPSE|CODE` pairs) and a
`MaterialFingerprint` (the merged attributes). `NationalCodeRegistry` resolves a code in this order:

1. the same membership seen before → its existing code (test `sameGroupSameCode`);
2. the group **grew** — the old member set is a strict subset of the new one → inherit the code
   (`growthInheritsCode`), even if the new record supplies an attribute that was previously unknown;
3. the same material fingerprint → that material's code, unless a live group in the same run already
   claimed it (`claimedCodesStayUnique`);
4. otherwise a new code is issued.

Consequences:

* rebuilding the same group returns the same code (`nationalCodesAreStableAcrossRuns`),
* adding an equivalent record keeps the code,
* a split subgroup is a **subset**, so it cannot inherit by growth: it keeps the material code only while
  its attributes still match that material, otherwise it gets a new one
  (`splitSubgroupWithDifferentFingerprintGetsNewCode`),
* a singleton gets no code at all.

The registry is in memory: a restart starts again at `NMC-<year>-00000001`, and codes are deterministic
only for a given sequence of calls.

---

## 7. Complexity analysis

| Operation | Time |
|---|---|
| `makeSet` | `O(1)` |
| `find` | `O(α(n))` amortised |
| `union` | `O(α(n))` amortised |
| `connected` | `O(α(n))` amortised |
| `getGroups` | `O(n · α(n))` |
| `getComponentSize` | `O(α(n))` |
| Space | `O(n)` for `parent` + `size` |

α is the inverse Ackermann function; for any practical n it is at most 4. Rebuild after a rejection:
`O((V + E) · α(V))`.

---

## 8. Measured benchmark

Command:

```bash
mvn -q compile
java -cp target/classes harmonizer.bench.DsuBenchmark
```

Environment: Windows 11, OpenJDK 17.0.21, 12 cores. Best of 3 runs after warm-up. Both implementations
receive the same union operations and the benchmark **fails** if their partitions differ.

Naive baseline: keep a `label[]` array and, on each union, relabel every element of one component. That
is `O(n)` per union, `O(n · E)` overall — a fair "obvious" implementation, but not practical at
100,000 records, so it is marked `(too slow)` there instead of being faked.

| records | unions | finds | groups | DSU unions ms | naive unions ms | DSU find ms | DSU extract ms | naive extract ms | identical |
|---|---|---|---|---|---|---|---|---|---|
| 1,000 | 837 | 1,000 | 163 | 0.179 | 3.242 | 0.065 | 1.227 | 0.523 | true |
| 10,000 | 8,408 | 10,000 | 1,592 | 0.488 | 107.360 | 0.171 | 5.366 | 3.037 | true |
| 100,000 | 83,730 | 100,000 | 16,270 | 4.287 | (too slow) | 1.015 | 24.865 | (too slow) | not run |

Reading the table honestly:

* DSU's union phase scales almost linearly (0.179 → 0.488 → 4.287 ms for 10× more records each time),
  which matches `O((V + E) · α(V))`.
* The naive relabelling phase explodes (3.2 → 107.4 ms for 10× more records) because it is quadratic;
  that is why it is not run at 100k.
* **Group extraction is not faster for DSU** (1.23 vs 0.52 ms at 1k; 5.37 vs 3.04 ms at 10k). DSU's
  extraction performs a `find` per record plus path compression, while the naive extraction just buckets
  an already-computed label array. This is reported as measured, not hidden.
* Both produced identical partitions at every size where both ran.

---

## 9. Viva questions and short answers

1. **Why is union by size needed if path compression exists?** Compression alone does not bound tree
   height during the build phase; together they give the `O(α(n))` amortised bound. `unionBySizeKeepsLargeRoot`
   checks the root choice.
2. **Why can't a DSU delete?** Path compression overwrites the parent links needed to undo a union, and
   splitting would require finding every element of the set.
3. **How does MATRIQ handle a rejected match then?** It rebuilds a fresh DSU from the remaining approved
   edges.
4. **What does `getGroups()` guarantee?** Every registered record appears exactly once, groups are
   deterministic (members ascending, groups by smallest member), singletons included.
5. **Why are national codes keyed by membership and material fingerprint instead of the DSU root?** Roots
   change under path compression. Membership answers "did this group only grow?", the fingerprint answers
   "is this still the same material?", and `claimedCodes` keeps two live groups from sharing a code.
6. **What is the amortised complexity and why?** `O(α(n))`, because union by size keeps height
   logarithmic and path compression flattens paths, giving the inverse-Ackermann bound.

## 10. Limitations

* Ids must be positive and bounded by the capacity given at construction (1..maxId). This matches the
  CSV importer, which assigns 1-based row ids.
* `getGroups()` includes singletons; callers that only want approved groups filter on `size() >= 2`
  (this is what national-code assignment does).
* The structure is not thread-safe; the pipeline uses one instance per run inside a synchronized
  engine method.
