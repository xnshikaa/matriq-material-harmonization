package harmonizer.harmonization;

import harmonizer.model.MaterialRecord;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One material group produced by the DSU: records that were verified as the same physical material.
 *
 * <p>All original company records are kept, including each company's own material code — MATRIQ never
 * replaces a legacy code, it only adds the national identifier. A singleton (no verified relationship)
 * has no national code, because nothing was approved for it.
 *
 * @param id                    stable identity derived from the member company codes
 * @param materialFingerprint   attribute-based identity used to keep the national code stable
 * @param nationalCode          issued national material code, or null for a singleton
 * @param members               the original records, in ascending record-id order
 */
public record MaterialGroup(String id, String materialFingerprint,
                            String nationalCode, List<MaterialRecord> members) {

    public MaterialGroup {
        members = List.copyOf(members);
    }

    public int size() {
        return members.size();
    }

    public boolean hasNationalCode() {
        return nationalCode != null;
    }

    /** Original company material codes, preserved from the source catalogues. */
    public List<String> originalCodes() {
        return members.stream().map(MaterialRecord::code).toList();
    }

    /** Companies contributing to this group. */
    public Set<String> companies() {
        return members.stream().map(MaterialRecord::cpse).collect(Collectors.toUnmodifiableSet());
    }
}
