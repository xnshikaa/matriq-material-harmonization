package harmonizer.harmonization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Code lifecycle tests: stability when a group grows, explicit behaviour when it shrinks, uniqueness
 * within a run, and a history that cannot be silently re-pointed.
 */
class NationalCodeRegistryTest {

    private NationalCodeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NationalCodeRegistry(2026);
    }

    @Test
    @DisplayName("The same group always gets the same code")
    void sameGroupSameCode() {
        String first = registry.assign("GRP-a", "fp-1", List.of("CPCL|A", "IOCL|B"), Set.of());
        String second = registry.assign("GRP-a", "fp-1", List.of("CPCL|A", "IOCL|B"), Set.of());

        assertEquals(first, second);
        assertEquals(1, registry.issuedCount());
    }

    @Test
    @DisplayName("A group that gains an equivalent record keeps its code (inheritance)")
    void growthInheritsCode() {
        String before = registry.assign("GRP-ab", "fp-metal-unknown",
                List.of("CPCL|A", "IOCL|B"), Set.of());

        // Membership grew and an attribute that was previously unknown is now known, so the
        // fingerprint also changed. The code must still be inherited.
        String after = registry.assign("GRP-abc", "fp-metal-carbon-steel",
                List.of("CPCL|A", "IOCL|B", "ONGC|C"), Set.of());

        assertEquals(before, after);
    }

    @Test
    @DisplayName("A split subgroup with a different material fingerprint gets a new code")
    void splitSubgroupWithDifferentFingerprintGetsNewCode() {
        String original = registry.assign("GRP-abc", "fp-original",
                List.of("CPCL|A", "IOCL|B", "ONGC|C"), Set.of());

        // A rejection split the group and the survivor's attributes differ from the original
        // material (for example, the member that stated the metal was separated).
        String split = registry.assign("GRP-ab", "fp-different",
                List.of("CPCL|A", "IOCL|B"), Set.of());

        assertNotEquals(original, split, "a different material identity must not reuse the code");
        assertEquals(2, registry.history().size(), "both issuances must remain in the history");
        assertEquals("fp-original", registry.materialFingerprintForCode(original).orElseThrow());
        assertEquals("fp-different", registry.materialFingerprintForCode(split).orElseThrow());
    }

    @Test
    @DisplayName("A split subgroup that keeps the material fingerprint keeps the material code")
    void splitSubgroupWithSameFingerprintKeepsCode() {
        String original = registry.assign("GRP-abc", "fp-1",
                List.of("CPCL|A", "IOCL|B", "ONGC|C"), Set.of());

        String survivor = registry.assign("GRP-ab", "fp-1",
                List.of("CPCL|A", "IOCL|B"), Set.of());

        assertEquals(original, survivor, "the same material keeps its code after a split");
        assertEquals(1, registry.issuedCount());
    }

    @Test
    @DisplayName("Two groups with the same fingerprint in one run get distinct codes")
    void claimedCodesStayUnique() {
        Set<String> claimed = new HashSet<>();
        String first = registry.assign("GRP-ab", "fp-1", List.of("CPCL|A", "IOCL|B"), claimed);
        claimed.add(first);
        String second = registry.assign("GRP-cd", "fp-1", List.of("SAIL|C", "NTPC|D"), claimed);

        assertNotEquals(first, second, "two live groups must never share a national code");
    }

    @Test
    @DisplayName("Different materials get different codes and formats are correct")
    void differentMaterialsDifferentCodes() {
        String first = registry.assign("GRP-1", "fp-carbon", List.of("CPCL|A", "IOCL|B"), Set.of());
        String second = registry.assign("GRP-2", "fp-stainless", List.of("SAIL|C", "NTPC|D"), Set.of());

        assertNotEquals(first, second);
        assertTrue(first.matches("NMC-2026-\\d{8}"), "unexpected format: " + first);
        assertTrue(second.matches("NMC-2026-\\d{8}"), "unexpected format: " + second);
        assertEquals(2026, registry.year());
    }

    @Test
    @DisplayName("History keeps every issuance and its association")
    void historyPreservesAssociations() {
        String carbon = registry.assign("GRP-1", "fp-carbon", List.of("CPCL|A", "IOCL|B"), Set.of());
        String stainless = registry.assign("GRP-2", "fp-stainless", List.of("SAIL|C", "NTPC|D"), Set.of());

        assertEquals(2, registry.history().size());
        assertEquals(List.of(carbon, stainless), registry.issuedCodes());
        assertEquals("fp-carbon", registry.materialFingerprintForCode(carbon).orElseThrow());
        assertEquals("fp-stainless", registry.materialFingerprintForCode(stainless).orElseThrow());
        assertEquals(List.of("CPCL|A", "IOCL|B"),
                registry.history().get(0).memberKeys());
    }

    @Test
    @DisplayName("An unknown code has no fingerprint association")
    void unknownCodeHasNoAssociation() {
        assertTrue(registry.materialFingerprintForCode("NMC-2026-99999999").isEmpty());
    }

    @Test
    @DisplayName("Blank keys are rejected instead of producing an anonymous code")
    void blankKeysAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.assign(" ", "fp", List.of("A|B"), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> registry.assign("GRP-1", " ", List.of("A|B"), Set.of()));
    }
}
