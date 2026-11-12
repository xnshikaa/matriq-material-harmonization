# MATRIQ — Algorithm Specification & Viva Preparation Guide
## Advanced Algorithms CIA 3 | Academic Year 2025–2026
**Problem Statement:** Smart India Hackathon 2026 (SIH26099) — Ministry of Petroleum & Natural Gas / CPCL  
**Team Members:** Anshika Gupta, Hetanshi Vora  
**Assigned Individual Responsibilities:**
- **Anshika Gupta:** Knuth-Morris-Pratt (KMP) Algorithm & Disjoint Set Union (DSU) Algorithm (Implementation, Unit Testing, Benchmarking, Engine Integration, Algorithm Lab Visualizations, Viva Preparation)
- **Hetanshi Vora:** Bloom Filter (CO5) & Trie (Prefix Tree & Abbreviation Engine)

---

## 1. Executive Summary: Core Principle
$$\text{ONE MATERIAL} \longleftrightarrow \text{ONE IDENTITY}$$

In large public sector undertakings (IOCL, CPCL, ONGC, HPCL, BPCL, SAIL, NTPC), identical industrial supplies are cataloged with distinct internal part numbers and phrasing:
- **CPCL:** `PIPE-001` → *"Pipe, seamless, 6 inch, ASTM A106 Gr.B"*
- **IOCL:** `P-9281` → *"SEAMLESS PIPE 150mm CS A106-B"*
- **ONGC:** `MAT-1003` → *"Carbon steel seamless pipe, 6 in, A106 Grade B"*

MATRIQ automates cross-organization reconciliation without destroying company-specific codes, culminating in the issuance of a stable **National Material Code (NMC)**.

---

## 2. Algorithm 1: Knuth-Morris-Pratt (KMP) Specification Matcher
*Primary Viva Focus: Anshika Gupta*

### 2.1 Why Selected Over Naive & Rabin-Karp
1. **Deterministic Guarantees:** Industrial specification tokens (e.g. `ASTM A106`, `API 5L PSL2`, `ANSI B16.5`, `SS316L`) frequently possess repeated sub-patterns (e.g., `A106`, `A105`, `A335`). Naive string search can degrade to $O(n \times m)$ character comparisons on repetitive or semi-repetitive texts.
2. **Text Pointer Monotonicity:** In streaming or memory-mapped inspection of huge material catalogs, KMP never backtracks the text index $i$. The pointer $i$ strictly increments from $0$ to $n-1$.
3. **No Hash Collision Degeneration:** Unlike Rabin-Karp, KMP relies on structural prefix-suffix matching rather than rolling hashes, avoiding false positive hash collisions or worst-case degradation.

### 2.2 Mathematical Definition of the LPS (Failure Function)
Let $P[0 \dots m-1]$ be the pattern of length $m$.
The **Longest Proper Prefix which is also Suffix** array, $\pi$ (or `lps[]`), is defined as:
$$\text{lps}[i] = \max \{ k \in \{0, \dots, i\} : P[0 \dots k-1] \text{ is a suffix of } P[0 \dots i] \land k < i + 1 \}$$
By definition, $\text{lps}[0] = 0$.

### 2.3 Java Implementation Details (`KMPMatcher.java`)
- **Preprocessing (`buildLPS`):**
  Uses a two-pointer approach ($len$ and $i$). If $P[i] == P[len]$, extend $len = len + 1$ and advance $i$. If mismatch occurs and $len > 0$, fall back to $len = \text{lps}[len - 1]$ without moving $i$.
- **Search (`searchAll` / `countComparisons`):**
  Traverses $T$ with index $i$ and $P$ with index $j$. On match, advance both. When $j == m$, record start index $i - j$ and shift $j = \text{lps}[j - 1]$ to detect overlapping occurrences. On character mismatch when $j > 0$, shift $j = \text{lps}[j - 1]$; when $j == 0$, increment $i$.
- **Explicit Comparison Counter:** Every character evaluation increments an internal counter, proving empirically that KMP achieves far fewer comparisons than the naive $O(n \cdot m)$ baseline.

### 2.4 Complexity Derivation
- **Time Complexity:**
  - `buildLPS`: Each iteration of the while loop either advances $i$ (which occurs at most $m$ times) or decreases $len$ via $\text{lps}[len-1]$. Since $len$ can only increase as many times as $i$ advances, $len$ decreases at most $m$ times. Hence, $\Theta(m)$.
  - `search`: Similarly, the text index $i$ increments exactly $n$ times. The pattern index $j$ increments when characters match and decrements on fallbacks. Total character comparisons are bounded by $2n$.
  - **Overall Time:** $O(n + m)$.
- **Space Complexity:** $O(m)$ auxiliary space for the `int[] lps` table.

### 2.5 Role in the MATRIQ Engine & Strict Verification Rule
> **Critical Architectural Rule:** KMP locates the technical specification inside noisy, unformatted descriptions. **Finding a matching specification alone NEVER establishes material equivalence.**
>
> Two materials sharing `ASTM A106` cannot be merged if their nominal diameter conflicts (e.g., 6" vs 2") or if one is Grade A and the other is Grade C. Specification match is an attribute-level input to the multi-criteria verification gate.

---

## 3. Algorithm 2: Disjoint Set Union (DSU) Grouping Engine
*Primary Viva Focus: Anshika Gupta*

### 3.1 Why Selected Over Connected Components via BFS/DFS
1. **Incremental Online Updates:** In procurement operations, record matches arrive incrementally as batches are imported or reviewers approve proposals. DSU supports $O(\alpha(n))$ union operations without re-traversing the entire relationship graph.
2. **Minimal Memory Overhead:** Graph adjacency lists require $O(V + E)$ pointer-heavy node objects. DSU uses two compact hash tables: `parent` and `size`, requiring only $O(V)$ space.
3. **Deterministic Canonical Root:** Finding the root of any record provides an immediate, unique cluster representative for National Code assignment.

### 3.2 Dual Optimizations: Path Compression + Union by Size
1. **Path Compression (in `find(x)`):**
   ```
   find(x):
       if parent[x] != x:
           parent[x] = find(parent[x])  // Flatten tree recursively
       return parent[x]
   ```
   Every node on the query path from $x$ up to the root is reparented directly to the root. Future queries on these nodes execute in $O(1)$.
2. **Union by Size (in `union(a, b)`):**
   ```
   union(a, b):
       rootA = find(a)
       rootB = find(b)
       if rootA == rootB: return false
       if size[rootA] < size[rootB]:
           swap(rootA, rootB)
       parent[rootB] = rootA
       size[rootA] += size[rootB]
       componentCount--
       return true
   ```
   The root of the smaller tree is attached under the root of the larger tree. This prevents skewed, degenerate linear trees.

### 3.3 Complexity & Inverse Ackermann Function
When both Path Compression and Union by Size are utilized, any sequence of $m$ operations on $n$ elements executes in:
$$T(m, n) = O(m \cdot \alpha(n))$$
where $\alpha(n)$ is the extremely slow-growing **Inverse Ackermann function**:
$$\alpha(n) \le 4 \quad \text{for all } n \le 10^{80} \text{ (atoms in the observable universe)}$$
Amortized time per operation is practically constant: $\approx O(1)$.

### 3.4 Reviewer Rejection & Graph Rebuilding
> **Viva Defense Point:** Standard DSU is a monotonic data structure that does not support an $O(1)$ "un-union" or edge deletion operation.
>
> When a human reviewer or auditor rejects a previously proposed match:
> 1. The edge $(A, B)$ is marked as `REJECTED` in the repository.
> 2. The DSU structure is cleared via `reset()`.
> 3. Each active material record is re-registered as a singleton via `makeSet(id)`.
> 4. All remaining `APPROVED` edges are re-unioned.
>
> Because DSU operations run in sub-millisecond time for thousands of nodes, this complete rebuild executes in under $5\text{ ms}$, preserving total mathematical consistency.

---

## 4. Algorithm 3: Bloom Filter Pre-Screening (CO5)
*Team Module: Hetanshi Vora*

### 4.1 Purpose & Mathematical Guarantees
The Bloom Filter provides ultra-fast $O(k)$ pre-screening before expensive string parsing.
- **Definite No:** If `mightContain(sig)` returns `false`, the signature is **guaranteed** not to be in the set (Zero False Negatives).
- **Possible Yes:** If `mightContain(sig)` returns `true`, the item might be in the set or might be a false positive.
- **Rule:** A Bloom filter match **never** merges records. It merely triggers full downstream KMP and attribute verification.

### 4.2 Optimal Parameter Formulas
For $n$ expected elements and target false positive probability $p$:
$$\text{Bit array size } m = -\frac{n \ln p}{(\ln 2)^2} \approx -1.4427 \cdot n \ln p$$
$$\text{Optimal hash functions } k = \frac{m}{n} \ln 2 \approx 0.693 \cdot \frac{m}{n}$$
Current false positive probability based on $X$ set bits in array of size $M$:
$$P(\text{FP}) \approx \left( \frac{X}{M} \right)^k$$

---

## 5. Algorithm 4: Industrial Abbreviation & Prefix Trie
*Team Module: Hetanshi Vora*

### 5.1 Purpose & Operations
1. **Abbreviation Normalization:** Maps short industrial designations to canonical forms (e.g., `CS` → `CARBON STEEL`, `SS` → `STAINLESS STEEL`, `ERW` → `WELDED`, `NB` → `NOMINAL BORE`).
2. **Interactive Autocomplete:** Supplies prefix suggestions in the UI in $O(p + r)$ time, where $p$ is prefix length and $r$ is the number of emitted recommendations.

---

## 6. Viva Preparation Questions & Answers (For Anshika Gupta)

**Q1: Why did you use KMP instead of `String.indexOf` or regular expressions?**
> *"In industrial text harmonization, we are evaluating thousands of material records against known engineering standards. Java's `String.indexOf` uses a modified naive algorithm, while regex engines can encounter catastrophic backtracking or high compilation overhead. KMP offers a provable upper bound of $O(n+m)$ comparisons with strictly linear text traversal. Moreover, our custom Java implementation explicitly counts character comparisons, providing the auditability and benchmark metrics required for technical evaluation."*

**Q2: What is the significance of the LPS array in KMP?**
> *"The LPS (Longest Proper Prefix which is also Suffix) array encodes the symmetry of the pattern. When a character mismatch occurs at pattern index $j$, instead of restarting the comparison from text index $i - j + 1$ and pattern index 0 (as naive search does), KMP consults $\text{lps}[j-1]$. This tells us the longest prefix of the pattern that already matches the suffix of the text up to that mismatch. The text pointer $i$ never retreats, saving redundant comparisons."*

**Q3: How does DSU handle transitive relationships across three or more companies?**
> *"Suppose CPCL imports `A`, IOCL imports `B`, and ONGC imports `C`. Our engine verifies that $A \equiv B$ and calls `dsu.union(A, B)`. Later, the engine verifies $B \equiv C$ and calls `dsu.union(B, C)`. DSU automatically merges the sets so that `find(A) == find(C)` without needing direct pairwise comparison between $A$ and $C$. All three elements share the same representative root, which maps directly to a single National Material Code."*

**Q4: Can DSU undo a union if a reviewer rejects a proposed equivalence?**
> *"Standard disjoint set union with path compression cannot easily rollback an arbitrary edge because path compression flattens the internal tree pointers irreversibly. To guarantee 100% correctness without heuristic errors, MATRIQ tracks all approved edges in an approved edge list. When an edge is rejected, we re-initialize the DSU in $O(V)$ time and re-apply the approved edges in $O(E \cdot \alpha(V))$. With thousands of records, this rebuild completes in mere milliseconds."*

**Q5: What prevents two pipes of different diameters from merging even if KMP finds `ASTM A106` in both?**
> *"KMP is only the specification pattern detector. Its output is piped into our Attribute Verification Gate (`verifyAttributes`). This gate enforces strict criteria: category matching, grade compatibility, and dimension tolerance conversion. A 6-inch pipe (152.4 mm) and a 2-inch pipe (50.8 mm) will immediately fail the dimension compatibility check, preventing a candidate proposal from ever reaching the DSU."*
