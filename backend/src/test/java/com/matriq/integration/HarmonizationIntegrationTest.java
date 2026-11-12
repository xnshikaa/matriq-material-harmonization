package com.matriq.integration;

import com.matriq.algorithms.kmp.KMPMatcher;
import com.matriq.harmonization.HarmonizationService;
import com.matriq.matching.MatchingService;
import com.matriq.model.MaterialGroup;
import com.matriq.model.MaterialMatch;
import com.matriq.model.MaterialRecord;
import com.matriq.model.NationalMaterialCode;
import com.matriq.normalization.NormalizationService;
import com.matriq.repository.HarmonizationRepository;
import com.matriq.repository.MaterialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test for the MATRIQ Harmonization Engine:
 * Normalization -> Bloom Screening -> KMP Matching -> Attribute Verification -> DSU Grouping -> National Code
 */
class HarmonizationIntegrationTest {

    private MaterialRepository materialRepository;
    private HarmonizationRepository harmonizationRepository;
    private NormalizationService normalizationService;
    private MatchingService matchingService;
    private HarmonizationService harmonizationService;

    @BeforeEach
    void setUp() {
        materialRepository = new MaterialRepository();
        harmonizationRepository = new HarmonizationRepository();
        normalizationService = new NormalizationService();
        matchingService = new MatchingService(normalizationService, harmonizationRepository);
        harmonizationService = new HarmonizationService(
            materialRepository, harmonizationRepository, normalizationService, matchingService
        );
    }

    @Test
    @DisplayName("End-to-End: Equivalent records from 3 companies form 1 national code group")
    void testEndToEndHarmonizationWorkflow() {
        // Company A: CPCL
        MaterialRecord recA = new MaterialRecord();
        recA.setId("REC-A");
        recA.setCompanyName("CPCL");
        recA.setOriginalMaterialCode("PIPE-001");
        recA.setOriginalDescription("Pipe, seamless, 6 inch, ASTM A106 Gr.B");
        recA.setMaterialCategory("Piping");
        recA.setMaterialType("Seamless Pipe");
        recA.setGrade("Grade B");
        recA.setTechnicalSpecification("ASTM A106");
        recA.setDimensions("6 inch");
        recA.setUnit("INCH");

        // Company B: IOCL
        MaterialRecord recB = new MaterialRecord();
        recB.setId("REC-B");
        recB.setCompanyName("IOCL");
        recB.setOriginalMaterialCode("P-9281");
        recB.setOriginalDescription("SEAMLESS PIPE 150mm CS A106-B");
        recB.setMaterialCategory("Piping");
        recB.setMaterialType("Seamless Pipe");
        recB.setGrade("Grade B");
        recB.setTechnicalSpecification("ASTM A106");
        recB.setDimensions("150 mm");
        recB.setUnit("MM");

        // Company C: ONGC
        MaterialRecord recC = new MaterialRecord();
        recC.setId("REC-C");
        recC.setCompanyName("ONGC");
        recC.setOriginalMaterialCode("MAT-1003");
        recC.setOriginalDescription("Carbon steel seamless pipe, 6 in, A106 Grade B");
        recC.setMaterialCategory("Piping");
        recC.setMaterialType("Seamless Pipe");
        recC.setGrade("Grade B");
        recC.setTechnicalSpecification("ASTM A106");
        recC.setDimensions("6 in");
        recC.setUnit("INCH");

        // Unrelated Company D: BPCL - Stainless Steel 2"
        MaterialRecord recD = new MaterialRecord();
        recD.setId("REC-D");
        recD.setCompanyName("BPCL");
        recD.setOriginalMaterialCode("PIPE-900");
        recD.setOriginalDescription("Stainless steel pipe, 2 inch, SS316");
        recD.setMaterialCategory("Piping");
        recD.setMaterialType("Stainless Steel Pipe");
        recD.setGrade("SS316");
        recD.setTechnicalSpecification("ASTM A312");
        recD.setDimensions("2 inch");
        recD.setUnit("INCH");

        materialRepository.save(recA);
        materialRepository.save(recB);
        materialRepository.save(recC);
        materialRepository.save(recD);

        // 1. Run pipeline
        HarmonizationService.HarmonizationResult res = harmonizationService.runPipeline();
        assertNotNull(res);

        // Verify matches were generated
        List<MaterialMatch> matches = harmonizationRepository.findAllMatches();
        assertFalse(matches.isEmpty());

        // Approve matches between equivalent items
        for (MaterialMatch m : matches) {
            harmonizationService.approveMatch(m.getId(), "Lead Engineer", "Verified physical equivalence");
        }

        // Assign national codes
        List<NationalMaterialCode> nationalCodes = harmonizationService.assignNationalCodes();
        assertFalse(nationalCodes.isEmpty());

        // Verify recA, recB, recC share the same national code
        MaterialRecord updatedA = materialRepository.findById("REC-A").orElseThrow();
        MaterialRecord updatedB = materialRepository.findById("REC-B").orElseThrow();
        MaterialRecord updatedC = materialRepository.findById("REC-C").orElseThrow();
        MaterialRecord updatedD = materialRepository.findById("REC-D").orElseThrow();

        // Company codes preserved
        assertEquals("PIPE-001", updatedA.getOriginalMaterialCode());
        assertEquals("P-9281", updatedB.getOriginalMaterialCode());
        assertEquals("MAT-1003", updatedC.getOriginalMaterialCode());

        // Same group national code
        assertNotNull(updatedA.getNationalMaterialCode());
        assertEquals(updatedA.getNationalMaterialCode(), updatedB.getNationalMaterialCode());
        assertEquals(updatedA.getNationalMaterialCode(), updatedC.getNationalMaterialCode());

        // Unrelated recD must NOT have the same national code
        assertNotEquals(updatedA.getNationalMaterialCode(), updatedD.getNationalMaterialCode());
    }

    @Test
    @DisplayName("Verification Gate: Conflicting grade must NOT be approved or merged")
    void testConflictingGradeDoesNotMerge() {
        MaterialRecord rec1 = new MaterialRecord();
        rec1.setId("PIPE-CS");
        rec1.setCompanyName("CPCL");
        rec1.setOriginalMaterialCode("C-01");
        rec1.setOriginalDescription("Carbon Steel Pipe 6 inch ASTM A106 Gr.B");
        rec1.setGrade("Grade B");
        rec1.setDimensions("6 inch");

        MaterialRecord rec2 = new MaterialRecord();
        rec2.setId("PIPE-SS");
        rec2.setCompanyName("IOCL");
        rec2.setOriginalMaterialCode("S-01");
        rec2.setOriginalDescription("Stainless Steel Pipe 6 inch ASTM A312 TP316");
        rec2.setGrade("SS316");
        rec2.setDimensions("6 inch");

        materialRepository.save(rec1);
        materialRepository.save(rec2);

        harmonizationService.runPipeline();

        List<MaterialMatch> matches = harmonizationRepository.findAllMatches();
        // Either no match proposed, or rejected by attribute verification
        for (MaterialMatch m : matches) {
            if ((m.getRecordAId().equals("PIPE-CS") && m.getRecordBId().equals("PIPE-SS")) ||
                (m.getRecordAId().equals("PIPE-SS") && m.getRecordBId().equals("PIPE-CS"))) {
                assertNotEquals(MaterialMatch.MatchStatus.APPROVED, m.getStatus());
            }
        }
    }
}
