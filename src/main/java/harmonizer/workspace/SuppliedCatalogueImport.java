package harmonizer.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** An explicit, offline re-runnable import of the three controlled synthetic demo catalogues. */
@Component
@Profile("catalogue-import")
class SuppliedCatalogueImport implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(SuppliedCatalogueImport.class);
    private static final CatalogueImport.Mapping MAPPING = new CatalogueImport.Mapping("ItemCode", "Description",
            "MaterialCategory", "MaterialGrade", "Dimensions", "Unit", "StandardSpecification", "");
    private record Input(String organization, String fileName) {}
    private static final List<Input> INPUTS = List.of(
            new Input("IOCL", "IOCL_2025.csv"),
            new Input("NTPC", "NTPC_2024.csv"),
            new Input("OIL", "OIL_2023.csv"));

    private final OperationalWorkspace workspace;
    private final ApplicationContext context;

    SuppliedCatalogueImport(OperationalWorkspace workspace, ApplicationContext context) {
        this.workspace = workspace;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (context instanceof WebServerApplicationContext || !args.containsOption("import-supplied-catalogues")) {
            throw new IllegalStateException("Use the explicit offline supplied-catalogue import command.");
        }
        for (Input input : INPUTS) {
            Path file = Path.of("data", "catalogues", input.fileName());
            var options = new CatalogueImport.Options(input.organization(), input.fileName(), "", "", MAPPING);
            try {
                var batch = workspace.importFile(Files.readAllBytes(file), input.fileName(), options, "offline-catalogue-import");
                LOG.info("Imported {}: {} accepted / {} submitted", input.fileName(), batch.validRows(), batch.recordCount());
            } catch (ResponseStatusException failure) {
                if (failure.getStatusCode() != HttpStatus.CONFLICT) throw failure;
                LOG.info("Already imported: {}", input.fileName());
            }
        }
        if (args.containsOption("harmonize")) {
            var view = workspace.run();
            LOG.info("Operational harmonization: {} records, {} comparisons, {} proposals, {} published identities",
                    view.run().records(), view.run().comparisons(), view.proposals().size(), view.registry().size());
        }
    }
}
