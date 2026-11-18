package harmonizer.workspace;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {
    record Review(OperationalWorkspace.Action action, String rationale) {}

    private final OperationalWorkspace workspace;

    public WorkspaceController(OperationalWorkspace workspace) {
        this.workspace = workspace;
    }

    @GetMapping
    OperationalWorkspace.View snapshot() {
        return workspace.view();
    }

    @PostMapping("/organizations")
    OperationalWorkspace.View register(@RequestBody OperationalWorkspace.Organisation input) throws IOException {
        return workspace.register(input);
    }

    @PostMapping(value = "/catalogues/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    List<List<String>> inspect(@RequestPart MultipartFile file) throws IOException {
        return CatalogueImport.parse(file.getBytes());
    }

    @PostMapping(value = "/catalogues/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    CatalogueImport.Validation validate(@RequestPart MultipartFile file, @RequestPart CatalogueImport.Options options)
            throws IOException {
        return workspace.validateImport(file.getBytes(), file.getOriginalFilename(), options);
    }

    @PostMapping(value = "/catalogues", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    CatalogueImport.Batch importFile(@RequestPart MultipartFile file, @RequestPart CatalogueImport.Options options,
                                     HttpServletRequest request) throws IOException {
        return workspace.importFile(file.getBytes(), file.getOriginalFilename(), options, user(request).id());
    }

    @PostMapping("/harmonize")
    OperationalWorkspace.View harmonize() throws IOException {
        return workspace.run();
    }

    @PostMapping("/reviews/{id}")
    OperationalWorkspace.View review(@PathVariable String id, @RequestBody Review review, HttpServletRequest request)
            throws IOException {
        return workspace.decide(id, review.action(), review.rationale(), user(request));
    }

    private static Accounts.User user(HttpServletRequest request) {
        return (Accounts.User) request.getAttribute(AuthController.USER);
    }
}
