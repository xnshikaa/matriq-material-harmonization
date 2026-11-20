package harmonizer.harmonization;

import harmonizer.algo.dsu.DisjointSetUnion;
import harmonizer.matching.AttributeComparison;
import harmonizer.matching.AttributeExtractor;
import harmonizer.matching.MaterialAttributes;
import harmonizer.matching.VerifiedEdge;
import harmonizer.model.MaterialRecord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds material groups from verified equivalence relationships using disjoint-set union.
 *
 * <p>The service is stateless: every call builds a fresh DSU, registers each record as its own set,
 * applies only the approved edges, extracts the connected components and turns them into
 * {@link MaterialGroup}s. That is how a rejection is handled — the caller passes the remaining
 * approved edges and the groups are recomputed from scratch, so a rejected relationship can never
 * leave a stale union behind (a standard DSU cannot undo one in O(1)).
 *
 * <h2>Component validation (why this class does more than union)</h2>
 * Pairwise verification cannot see the whole picture. Three records can each pass an edge while the
 * two ends conflict:
 * <pre>
 *   A: carbon steel, 6 inch, ASTM A106 Gr.B     (metal + size known)
 *   B: 6 inch, ASTM A106 Gr.B                    (metal unknown)
 *   C: stainless steel, 6 inch, ASTM A106 Gr.B   (metal + size known)
 *   A–B verified, B–C verified, A–C rejected as metal conflict
 * </pre>
 * DSU would merge all three through B. So after the components are extracted, each component is
 * checked with the same {@link AttributeComparison} rules and split into conflict-free subgroups
 * (deterministic first-fit over members in record-id order). Safety wins over maximal grouping: the
 * split only ever separates records that state contradictory attributes, and never joins anything
 * that the DSU did not already consider connected.
 *
 * <h2>National codes</h2>
 * A code is keyed by {@link MaterialFingerprint} (the merged attributes of a conflict-free group), so
 * adding an equivalent record keeps the group's code. If two disjoint groups share a fingerprint
 * (possible only after a manual rejection) the second gets a group-specific code, keeping codes
 * unique.
 *
 * <p>Complexity: O((V + E) * alpha(V)) for the DSU plus O(k^2) per component for validation, where k
 * is the component size.
 */
public final class MaterialGroupingService {

    /** Result of one grouping pass. */
    public record GroupingOutcome(List<MaterialGroup> groups, int components, int splitComponents) {
        public GroupingOutcome {
            groups = List.copyOf(groups);
        }
    }

    private final NationalCodeRegistry registry;

    public MaterialGroupingService(NationalCodeRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Groups records using the given edges, extracting attributes on the fly.
     * Convenience overload for callers that do not already have attributes.
     */
    public List<MaterialGroup> group(List<MaterialRecord> records, List<VerifiedEdge> edges) {
        Map<Integer, MaterialAttributes> attributes = new LinkedHashMap<>();
        AttributeExtractor extractor = new AttributeExtractor();
        if (records != null) {
            for (MaterialRecord record : records) {
                if (record != null) {
                    attributes.put(record.id(), extractor.extract(record));
                }
            }
        }
        return groupDetailed(records, edges, attributes).groups();
    }

    /**
     * @param records    all imported records (they become singleton groups unless an edge joins them)
     * @param edges      verified equivalence relationships to union
     * @param attributes attributes per record id, reused to validate components
     */
    public GroupingOutcome groupDetailed(List<MaterialRecord> records, List<VerifiedEdge> edges,
                                         Map<Integer, MaterialAttributes> attributes) {
        return groupDetailed(records, edges, attributes, Set.of());
    }

    /** Like {@link #groupDetailed(List, List, Map)}, with explicit reviewer-approved conflict pairs. */
    public GroupingOutcome groupDetailed(List<MaterialRecord> records, List<VerifiedEdge> edges,
                                         Map<Integer, MaterialAttributes> attributes, Set<String> conflictOverrides) {
        if (records == null || records.isEmpty()) {
            return new GroupingOutcome(List.of(), 0, 0);
        }
        Map<Integer, MaterialAttributes> facts = attributes == null ? Map.of() : attributes;

        Map<Integer, MaterialRecord> byId = new LinkedHashMap<>();
        int highestId = 0;
        for (MaterialRecord record : records) {
            if (record != null) {
                byId.put(record.id(), record);
                highestId = Math.max(highestId, record.id());
            }
        }
        if (byId.isEmpty()) {
            return new GroupingOutcome(List.of(), 0, 0);
        }

        DisjointSetUnion dsu = new DisjointSetUnion(highestId);
        for (Integer id : byId.keySet()) {
            dsu.makeSet(id);
        }

        if (edges != null) {
            for (VerifiedEdge edge : edges) {
                if (edge == null) {
                    continue;
                }
                if (!dsu.isRegistered(edge.recordA()) || !dsu.isRegistered(edge.recordB())) {
                    continue; // an edge pointing at a record that is no longer imported
                }
                dsu.union(edge.recordA(), edge.recordB());
            }
        }

        List<List<Integer>> components = dsu.getGroups();
        List<MaterialGroup> groups = new ArrayList<>();
        Set<String> claimedCodes = new HashSet<>();
        int splitComponents = 0;

        for (List<Integer> componentIds : components) {
            List<MaterialRecord> componentMembers = new ArrayList<>(componentIds.size());
            for (Integer id : componentIds) {
                componentMembers.add(byId.get(id));
            }

            List<List<MaterialRecord>> subgroups = conflictFreeSubgroups(componentMembers, facts,
                    conflictOverrides == null ? Set.of() : conflictOverrides);
            if (subgroups.size() > 1) {
                splitComponents++;
            }

            for (List<MaterialRecord> members : subgroups) {
                List<MaterialAttributes> memberFacts = new ArrayList<>(members.size());
                for (MaterialRecord member : members) {
                    MaterialAttributes memberAttributes = facts.get(member.id());
                    if (memberAttributes != null) {
                        memberFacts.add(memberAttributes);
                    }
                }
                String fingerprint = MaterialFingerprint.of(MaterialFingerprint.merge(memberFacts));
                String groupId = stableGroupId(members);

                String nationalCode = null;
                if (members.size() >= 2) {
                    nationalCode = registry.assign(groupId, fingerprint, memberKeys(members), claimedCodes);
                    claimedCodes.add(nationalCode);
                }
                groups.add(new MaterialGroup(groupId, fingerprint, nationalCode, members));
            }
        }

        return new GroupingOutcome(groups, components.size(), splitComponents);
    }

    /**
     * Deterministic first-fit split: each member joins the first subgroup it does not conflict with,
     * otherwise it starts a new one. Members are processed in record-id order and every subgroup keeps
     * that order, so the result is reproducible.
     */
    private static List<List<MaterialRecord>> conflictFreeSubgroups(
            List<MaterialRecord> members, Map<Integer, MaterialAttributes> attributes, Set<String> conflictOverrides) {
        List<List<MaterialRecord>> subgroups = new ArrayList<>();
        for (MaterialRecord member : members) {
            MaterialAttributes candidate = attributes.get(member.id());
            List<MaterialRecord> target = null;
            for (List<MaterialRecord> subgroup : subgroups) {
                boolean compatible = true;
                for (MaterialRecord existing : subgroup) {
                    MaterialAttributes other = attributes.get(existing.id());
                    if (candidate == null || other == null) {
                        continue; // without facts we cannot prove a conflict here
                    }
                    if (AttributeComparison.conflicts(candidate, other)
                            && !conflictOverrides.contains(pair(existing.id(), member.id()))) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) {
                    target = subgroup;
                    break;
                }
            }
            if (target == null) {
                target = new ArrayList<>();
                subgroups.add(target);
            }
            target.add(member);
        }
        return subgroups;
    }

    private static String pair(int a, int b) {
        return Math.min(a, b) + "-" + Math.max(a, b);
    }

    /** {@code CPSE|CODE} keys of the given members, used for code inheritance and history. */
    static List<String> memberKeys(List<MaterialRecord> members) {
        List<String> keys = new ArrayList<>(members.size());
        for (MaterialRecord member : members) {
            keys.add(member.cpse().toUpperCase(Locale.ROOT) + "|" + member.code().toUpperCase(Locale.ROOT));
        }
        Collections.sort(keys);
        return keys;
    }

    /**
     * Stable identity for a set of records: a hash over the sorted {@code CPSE|CODE} pairs.
     *
     * <p>Independent of record ids and of DSU roots, so the same membership keeps the same group id
     * across rebuilds. National codes are keyed by material fingerprint instead, so this id is only
     * used for the group-specific fallback and for display.
     */
    public static String stableGroupId(List<MaterialRecord> members) {
        List<String> keys = new ArrayList<>(members.size());
        for (MaterialRecord member : members) {
            keys.add(member.cpse().toUpperCase(Locale.ROOT) + "|" + member.code().toUpperCase(Locale.ROOT));
        }
        Collections.sort(keys);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.join(",", keys).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("GRP-");
            for (int i = 0; i < 6; i++) {
                hex.append(String.format(Locale.ROOT, "%02x", hash[i] & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required to build stable group ids", e);
        }
    }
}
