package harmonizer.harmonization;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * An explicit reviewer separation constraint between material groups or their stable member identities.
 *
 * <p>When a reviewer separates two groups by rejecting cross-group relationships, this constraint
 * ensures that new bridging records cannot bypass the reviewer's decision by transitively merging them.
 *
 * @param id      canonical constraint identifier
 * @param groupA  stable member identities (CPSE|CODE) of the first separated component
 * @param groupB  stable member identities (CPSE|CODE) of the second separated component
 * @param reason  explanation of why the separation constraint was established
 */
public record ReviewConstraint(String id, Set<String> groupA, Set<String> groupB, String reason) {

    public ReviewConstraint {
        groupA = Collections.unmodifiableSet(new TreeSet<>(groupA));
        groupB = Collections.unmodifiableSet(new TreeSet<>(groupB));
        Objects.requireNonNull(id, "id");
    }

    public ReviewConstraint(Set<String> groupA, Set<String> groupB, String reason) {
        this(generateId(groupA, groupB), groupA, groupB, reason);
    }

    /** Returns true if keyA belongs to groupA and keyB belongs to groupB (or vice versa). */
    public boolean conflicts(String keyA, String keyB) {
        if (keyA == null || keyB == null) {
            return false;
        }
        return (groupA.contains(keyA) && groupB.contains(keyB))
                || (groupA.contains(keyB) && groupB.contains(keyA));
    }

    /** Returns true if the given member key belongs to either group. */
    public boolean containsMember(String key) {
        return groupA.contains(key) || groupB.contains(key);
    }

    /** Returns true if a set of member keys contains at least one member from both separated groups. */
    public boolean conflictsWithComponent(Set<String> componentMembers) {
        if (componentMembers == null || componentMembers.isEmpty()) {
            return false;
        }
        boolean hasA = false;
        boolean hasB = false;
        for (String m : componentMembers) {
            if (groupA.contains(m)) {
                hasA = true;
            }
            if (groupB.contains(m)) {
                hasB = true;
            }
            if (hasA && hasB) {
                return true;
            }
        }
        return false;
    }

    private static String generateId(Set<String> a, Set<String> b) {
        String strA = String.join(",", new TreeSet<>(a));
        String strB = String.join(",", new TreeSet<>(b));
        return strA.compareTo(strB) <= 0 ? strA + "<->" + strB : strB + "<->" + strA;
    }
}
