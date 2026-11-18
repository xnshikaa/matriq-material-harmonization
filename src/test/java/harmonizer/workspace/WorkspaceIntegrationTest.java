package harmonizer.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceIntegrationTest {
    private static final String PASSWORD = "integration-test-only-password";
    private static final String REVIEWER = "reviewer@matriq.invalid";
    private static final Path STATE;
    private static final String REVIEWER_ID;

    static {
        try {
            STATE = Files.createTempDirectory(Path.of("target"), "workspace-integration-").toAbsolutePath();
            try (var storage = new TestStorage(new WorkspaceStorage(STATE.toString(), new ObjectMapper().findAndRegisterModules()))) {
                REVIEWER_ID = new Accounts(storage.value(), "").register("Engineering Reviewer", REVIEWER, PASSWORD).id();
            }
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private record TestStorage(WorkspaceStorage value) implements AutoCloseable {
        public void close() throws Exception { value.close(); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("matriq.state-directory", STATE::toString);
        registry.add("matriq.reviewer-ids", () -> REVIEWER_ID);
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private WorkspaceStorage storage;

    record Session(MockHttpSession cookie, String token, JsonNode user) {}

    @Test
    void authenticatedWorkspacePresentsRealImportsAndPersistsSafeDecisions() throws Exception {
        mvc.perform(get("/api/workspace")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dataset/summary")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(credentials("procurement@matriq.invalid", PASSWORD))).andExpect(status().isForbidden());

        Session guest = anonymous();
        String procurementEmail = "procurement@matriq.invalid";
        var created = mvc.perform(post("/api/auth/signup").session(guest.cookie()).header("X-CSRF-Token", guest.token())
                .contentType(MediaType.APPLICATION_JSON).content(credentials(" Procurement@Matriq.Invalid ", PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.name").value("Procurement Analyst"))
                .andExpect(jsonPath("$.user.email").value(procurementEmail))
                .andExpect(jsonPath("$.user.reviewer").value(false)).andReturn();
        Session standard = session(created);
        assertTrue(guest.cookie().isInvalid());
        assertNotEquals(guest.token(), standard.token());
        mvc.perform(get("/api/auth/session").session(standard.cookie())).andExpect(jsonPath("$.user.name").value("Procurement Analyst"));
        String accountJson = Files.readString(STATE.resolve("accounts.json"));
        assertFalse(accountJson.contains(PASSWORD));
        JsonNode account = mapper.readTree(accountJson).path("accounts").path(procurementEmail);
        assertEquals(32, java.util.Base64.getDecoder().decode(account.path("hash").asText()).length);
        assertNotEquals(account.path("salt").asText(),
                mapper.readTree(accountJson).path("accounts").path(REVIEWER).path("salt").asText());

        Session duplicate = anonymous();
        mvc.perform(post("/api/auth/signup").session(duplicate.cookie()).header("X-CSRF-Token", duplicate.token())
                .contentType(MediaType.APPLICATION_JSON).content(credentials(procurementEmail, PASSWORD)))
                .andExpect(status().isConflict());
        Session invalid = anonymous();
        mvc.perform(post("/api/auth/login").session(invalid.cookie()).header("X-CSRF-Token", invalid.token())
                .contentType(MediaType.APPLICATION_JSON).content(credentials(procurementEmail, "wrong-password")))
                .andExpect(status().isUnauthorized());

        String[] files = {"01_IOCL_Procurement_Catalogue.csv", "02_NTPC_Engineering_Materials.csv",
                "03_OIL_Industrial_Materials_A.csv", "04_OIL_Industrial_Materials_B.csv"};
        String[] orgs = {"IOCL", "NTPC", "OIL", "OIL"};
        int[] sizes = {42, 95, 150, 150};
        for (int i = 0; i < files.length; i++) {
            mvc.perform(upload(files[i], orgs[i], standard)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.validRows").value(sizes[i]))
                    .andExpect(jsonPath("$.recordCount").value(sizes[i]));
        }
        mvc.perform(upload(files[1], orgs[1], standard)).andExpect(status().isConflict());
        mvc.perform(post("/api/workspace/harmonize").session(standard.cookie())).andExpect(status().isForbidden());
        var harmonized = mvc.perform(post("/api/workspace/harmonize").session(standard.cookie()).header("X-CSRF-Token", standard.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.records.length()").value(437))
                .andExpect(jsonPath("$.batches.length()").value(4)).andExpect(jsonPath("$.registry.length()").value(0)).andReturn();
        JsonNode snapshot = json(harmonized);
        var counts = new java.util.TreeMap<String, Integer>();
        String conflict = null;
        var possible = new java.util.ArrayList<JsonNode>();
        for (JsonNode proposal : snapshot.path("proposals")) {
            String state = proposal.path("status").asText();
            counts.merge(state, 1, Integer::sum);
            if (state.equals("POSSIBLE")) possible.add(proposal);
            if (state.equals("CONFLICT") && conflict == null) conflict = proposal.path("id").asText();
        }
        System.out.println("Catalogue verification: " + snapshot.path("run").path("comparisons").asInt()
                + " compared pairs, proposals " + counts);
        assertNotNull(conflict);
        assertTrue(possible.size() >= 2, "Expected at least two safe pairs from actual source rows");
        String first = possible.get(0).path("id").asText();
        mvc.perform(review(first, "APPROVE", standard)).andExpect(status().isForbidden());

        Session reviewer = login(REVIEWER);
        assertEquals(REVIEWER_ID, reviewer.user().path("id").asText());
        assertTrue(reviewer.user().path("reviewer").asBoolean());
        mvc.perform(review(conflict, "APPROVE", reviewer)).andExpect(status().isConflict());
        mvc.perform(review(conflict, "CLARIFY", reviewer)).andExpect(status().isOk());
        mvc.perform(review(conflict, "SEPARATE", reviewer)).andExpect(status().isOk());
        var verified = mvc.perform(review(first, "APPROVE", reviewer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.registry.length()").value(1)).andReturn();
        String code = json(verified).path("registry").get(0).path("group").path("nationalCode").asText();
        String second = possible.stream().filter(p -> p.path("recordA").asInt() != possible.get(0).path("recordA").asInt()
                && p.path("recordA").asInt() != possible.get(0).path("recordB").asInt()
                && p.path("recordB").asInt() != possible.get(0).path("recordA").asInt()
                && p.path("recordB").asInt() != possible.get(0).path("recordB").asInt())
                .findFirst().orElseThrow().path("id").asText();
        mvc.perform(review(second, "APPROVE", reviewer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.registry.length()").value(2));

        var reloaded = new OperationalWorkspace(storage);
        assertEquals(437, reloaded.view().records().size());
        assertEquals(4, reloaded.view().decisions().size());
        assertTrue(reloaded.view().decisions().stream().allMatch(item -> item.reviewerId().equals(REVIEWER_ID)));
        assertEquals(new HashSet<>(reloaded.view().registry().stream().map(i -> i.group().nationalCode()).toList()).size(), 2);
        assertTrue(reloaded.run().registry().stream().anyMatch(i -> i.group().nationalCode().equals(code)));
        String conflictId = conflict;
        assertTrue(reloaded.view().proposals().stream().anyMatch(p -> p.id().equals(conflictId)
                && p.status() == OperationalWorkspace.Status.SEPARATED));
        assertEquals(procurementEmail, new Accounts(storage, "").login(procurementEmail, PASSWORD).email());

        mvc.perform(post("/api/auth/logout").session(standard.cookie()).header("X-CSRF-Token", standard.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user").isEmpty());
        assertTrue(standard.cookie().isInvalid());
        assertNotNull(login(procurementEmail).user().path("id").asText());
    }

    @Test
    void expirationAndAttemptLimitAreEnforced() throws Exception {
        Session signedIn = login(REVIEWER);
        signedIn.cookie().setAttribute(AuthController.STARTED, Instant.now().minusSeconds(8 * 3600));
        mvc.perform(get("/api/workspace").session(signedIn.cookie())).andExpect(status().isUnauthorized());
        assertTrue(signedIn.cookie().isInvalid());
        Session guest = anonymous();
        for (int attempt = 0; attempt <= 10; attempt++) {
            mvc.perform(post("/api/auth/login").session(guest.cookie()).header("X-CSRF-Token", guest.token())
                    .contentType(MediaType.APPLICATION_JSON).content(credentials("throttled@matriq.invalid", "incorrect")))
                    .andExpect(status().is(attempt == 10 ? 429 : 401));
        }
    }

    private Session anonymous() throws Exception {
        return session(mvc.perform(get("/api/auth/session")).andExpect(status().isOk()).andReturn());
    }

    private Session login(String email) throws Exception {
        Session guest = anonymous();
        return session(mvc.perform(post("/api/auth/login").session(guest.cookie()).header("X-CSRF-Token", guest.token())
                .contentType(MediaType.APPLICATION_JSON).content(credentials(email, PASSWORD))).andExpect(status().isOk()).andReturn());
    }

    private Session session(MvcResult result) throws Exception {
        JsonNode json = json(result);
        return new Session((MockHttpSession) result.getRequest().getSession(false), json.path("csrfToken").asText(), json.path("user"));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String credentials(String email, String password) throws Exception {
        return mapper.writeValueAsString(new AuthController.Credentials("Procurement Analyst", email, password));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder upload(String file, String org, Session actor)
            throws Exception {
        var mapping = new CatalogueImport.Mapping("ItemCode", "Description", "MaterialCategory", "MaterialGrade",
                "Dimensions", "Unit", "StandardSpecification", "");
        var options = new CatalogueImport.Options(org, file, "", "", mapping);
        return multipart("/api/workspace/catalogues")
                .file(new MockMultipartFile("file", file, "text/csv", Files.readAllBytes(Path.of("data", "catalogues", file))))
                .file(new MockMultipartFile("options", "", "application/json", mapper.writeValueAsBytes(options)))
                .session(actor.cookie()).header("X-CSRF-Token", actor.token());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder review(String id, String action, Session actor) {
        return post("/api/workspace/reviews/" + id).session(actor.cookie()).header("X-CSRF-Token", actor.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"" + action + "\",\"rationale\":\"Evidence checked in integration test.\"}");
    }
}
