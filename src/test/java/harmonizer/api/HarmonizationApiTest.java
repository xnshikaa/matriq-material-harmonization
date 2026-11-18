package harmonizer.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for the integrated pipeline: upload, harmonize, reject, error mapping and the
 * shape of the JSON the frontend consumes.
 *
 * <p>The context is dirtied before every test because the dataset store, the review rejections and
 * the national code registry are intentionally in-memory singletons.
 */
@org.springframework.test.context.ActiveProfiles("pipeline-api")
@org.springframework.test.context.TestPropertySource(properties = "matriq.state-directory=target/test-pipeline-state")
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class HarmonizationApiTest {

    private static final String CSV = """
            cpse,code,description,uom
            CPCL,PIPE-001,"Pipe, seamless, 6 inch, ASTM A106 Gr.B",EA
            IOCL,P-9281,"Seamless pipe, 150 mm, A106 Grade B",EA
            ONGC,MAT-1003,"Carbon steel seamless pipe, 6 inch, ASTM A106-B",EA
            """;

    @Autowired
    private MockMvc mvc;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private harmonizer.workspace.Accounts accounts;

    private org.springframework.test.web.servlet.ResultActions perform(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        String email = "pipeline-test@matriq.invalid";
        org.mockito.Mockito.when(accounts.lookup(email))
                .thenReturn(new harmonizer.workspace.Accounts.User("pipeline-test", "Pipeline Test", email, true));
        var session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute("accountEmail", email);
        session.setAttribute("authenticatedAt", java.time.Instant.now());
        session.setAttribute("csrfToken", "pipeline-test-csrf");
        return mvc.perform(request.session(session).header("X-CSRF-Token", "pipeline-test-csrf"));
    }

    @Test
    @DisplayName("Upload returns a dataset summary")
    void uploadReturnsSummary() throws Exception {
        upload(CSV);

        perform(get("/api/dataset/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsLoaded").value(3))
                .andExpect(jsonPath("$.recordsPerCpse.CPCL").value(1))
                .andExpect(jsonPath("$.rowsSkipped").value(0));
    }

    @Test
    @DisplayName("Harmonizing without an uploaded dataset is a conflict, not a crash")
    void harmonizeWithoutDatasetReturnsConflict() throws Exception {
        perform(post("/api/harmonize"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Not ready"))
                .andExpect(jsonPath("$.details[0]").value(containsString("Upload a CSV")));
    }

    @Test
    @DisplayName("A successful harmonization returns the full JSON contract")
    void harmonizeReturnsCompleteResponse() throws Exception {
        upload(CSV);

        perform(post("/api/harmonize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecords").value(3))
                .andExpect(jsonPath("$.candidatePairs").value(3))
                .andExpect(jsonPath("$.verifiedPairs").value(3))
                .andExpect(jsonPath("$.rejectedPairs").value(0))
                .andExpect(jsonPath("$.suppressedPairs").value(0))
                .andExpect(jsonPath("$.splitComponents").value(0))
                .andExpect(jsonPath("$.kmpComparisons").isNumber())
                .andExpect(jsonPath("$.groupCount").value(1))
                .andExpect(jsonPath("$.groupedRecords").value(3))
                .andExpect(jsonPath("$.elapsedMillis").isNumber())
                .andExpect(jsonPath("$.verifiedEdges", hasSize(3)))
                .andExpect(jsonPath("$.verifiedEdges[0].evidence.attributeStates.grade").value("MATCH"))
                // Only ONGC/MAT-1003 states the metal, so pair-wise the other side is UNKNOWN — which is
                // exactly why an unknown attribute alone is not accepted as proof of equivalence.
                .andExpect(jsonPath("$.verifiedEdges[0].evidence.attributeStates.metal").value("UNKNOWN"))
                .andExpect(jsonPath("$.verifiedEdges[0].evidence.attributeStates.dimensions").value("MATCH"))
                .andExpect(jsonPath("$.verifiedEdges[0].evidence.sharedSpecifications[0].specification")
                        .value("ASTM A106"))
                .andExpect(jsonPath("$.rejectedCandidates", hasSize(0)))
                .andExpect(jsonPath("$.groups", hasSize(1)))
                .andExpect(jsonPath("$.groups[0].nationalCode").value(containsString("NMC-")))
                .andExpect(jsonPath("$.groups[0].materialFingerprint").isString())
                .andExpect(jsonPath("$.groups[0].members", hasSize(3)))
                .andExpect(jsonPath("$.groups[0].members[0].code").value("PIPE-001"))
                .andExpect(jsonPath("$.groups[0].members[0].cpse").value("CPCL"))
                // Every company's own code is carried in the group unchanged.
                .andExpect(jsonPath("$.groups[0].members[*].code",
                        org.hamcrest.Matchers.containsInAnyOrder("PIPE-001", "P-9281", "MAT-1003")))
                .andExpect(jsonPath("$.attributesByRecord").isMap());
    }

    @Test
    @DisplayName("Conflicting records are rejected in the response and stay ungrouped")
    void conflictingRecordsAreNotGrouped() throws Exception {
        String conflicting = """
                cpse,code,description,uom
                CPCL,PIPE-100,"Carbon steel seamless pipe, 6 inch, ASTM A106 Gr.B",EA
                IOCL,P-200,"Stainless steel seamless pipe, 6 inch, ASTM A106 Gr.B",EA
                """;
        upload(conflicting);

        perform(post("/api/harmonize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedPairs").value(0))
                .andExpect(jsonPath("$.rejectedPairs").value(1))
                .andExpect(jsonPath("$.groupCount").value(2))
                .andExpect(jsonPath("$.rejectedCandidates[0].rejectionReason")
                        .value(containsString("metal")))
                .andExpect(jsonPath("$.rejectedCandidates[0].attributeStates.metal").value("CONFLICT"))
                .andExpect(jsonPath("$.groups[0].nationalCode").value(nullValue()));
    }

    @Test
    @DisplayName("Rejecting a relationship updates the groups in the same response")
    void rejectRemovesRelationship() throws Exception {
        upload(CSV);
        perform(post("/api/harmonize")).andExpect(status().isOk());

        perform(post("/api/harmonize/reject")
                        .param("recordA", "1")
                        .param("recordB", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedPairs").value(2))
                .andExpect(jsonPath("$.suppressedPairs").value(1))
                .andExpect(jsonPath("$.groups", hasSize(1)))
                .andExpect(jsonPath("$.groups[0].members", hasSize(3)))
                .andExpect(jsonPath("$.groups[0].nationalCode").value(containsString("NMC-")));
    }

    @Test
    @DisplayName("A rejection survives a repeated harmonization request")
    void rejectionSurvivesRepeatedHarmonize() throws Exception {
        upload(CSV);
        perform(post("/api/harmonize")).andExpect(status().isOk());
        perform(post("/api/harmonize/reject")
                        .param("recordA", "1")
                        .param("recordB", "3"))
                .andExpect(status().isOk());

        perform(post("/api/harmonize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedPairs").value(2))
                .andExpect(jsonPath("$.suppressedPairs").value(1))
                .andExpect(jsonPath("$.groups[0].members", hasSize(3)))
                .andExpect(jsonPath("$.verifiedEdges[?(@.recordA == 1 && @.recordB == 3)]", hasSize(0)));
    }

    @Test
    @DisplayName("Rejecting an unknown record id is a 400 with a clear message")
    void rejectWithUnknownRecordReturnsBadRequest() throws Exception {
        upload(CSV);
        perform(post("/api/harmonize")).andExpect(status().isOk());

        perform(post("/api/harmonize/reject")
                        .param("recordA", "1")
                        .param("recordB", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad request"))
                .andExpect(jsonPath("$.details[0]").value(containsString("Unknown record id 999")));
    }

    @Test
    @DisplayName("Rejecting before harmonizing is a conflict")
    void rejectBeforeHarmonizeReturnsConflict() throws Exception {
        upload(CSV);

        perform(post("/api/harmonize/reject")
                        .param("recordA", "1")
                        .param("recordB", "2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Not ready"))
                .andExpect(jsonPath("$.details[0]").value(containsString("Run harmonization")));
    }

    @Test
    @DisplayName("A header-only CSV is an empty dataset and is rejected")
    void emptyDatasetIsRejected() throws Exception {
        perform(multipart("/api/dataset/upload")
                        .file(new MockMultipartFile("file", "empty.csv", "text/csv",
                                "cpse,code,description,uom\n".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid dataset"))
                .andExpect(jsonPath("$.details[0]").value(containsString("no valid data rows")));
    }

    @Test
    @DisplayName("An upload without the required columns is rejected with the missing names")
    void uploadWithMissingColumnsIsRejected() throws Exception {
        perform(multipart("/api/dataset/upload")
                        .file(new MockMultipartFile("file", "bad.csv", "text/csv",
                                "cpse,code\nCPCL,P-1\n".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid dataset"))
                .andExpect(jsonPath("$.details[0]").value(containsString("description")));
    }

    private void upload(String csv) throws Exception {
        perform(multipart("/api/dataset/upload")
                        .file(new MockMultipartFile("file", "materials.csv", "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk());
    }
}
