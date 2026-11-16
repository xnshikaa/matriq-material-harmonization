package harmonizer.api;

import harmonizer.io.CsvMaterialParser;
import harmonizer.model.InvalidDatasetException;
import harmonizer.model.MaterialRecord;
import harmonizer.service.DatasetStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Upload and inspect the material records every other stage works on. */
@RestController
@RequestMapping("/api/dataset")
public class DatasetController {

    /** What the UI shows after a successful upload. */
    public record DatasetSummary(int recordsLoaded,
                                 Map<String, Integer> recordsPerCpse,
                                 int rowsSkipped,
                                 List<String> warnings) {
    }

    private final DatasetStore store;
    private final CsvMaterialParser parser = new CsvMaterialParser();

    public DatasetController(DatasetStore store) {
        this.store = store;
    }

    /** POST /api/dataset/upload  (multipart form field "file", CSV: cpse,code,description[,uom]). */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DatasetSummary upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new InvalidDatasetException("No file was uploaded, or the file is empty.");
        }
        CsvMaterialParser.ParseResult parsed;
        try (InputStream in = file.getInputStream()) {
            parsed = parser.parse(in);
        }
        store.load(parsed.records(), parsed.warnings(), parsed.totalWarnings());
        return summarise(parsed.records(), parsed.warnings(), parsed.totalWarnings());
    }

    /** GET /api/dataset/summary */
    @GetMapping("/summary")
    public DatasetSummary summary() {
        DatasetStore.Dataset d = store.require();
        return summarise(d.records(), d.warnings(), d.totalWarnings());
    }

    private static DatasetSummary summarise(List<MaterialRecord> records, List<String> warnings, int totalWarnings) {
        Map<String, Integer> perCpse = new TreeMap<>();
        for (MaterialRecord r : records) {
            perCpse.merge(r.cpse(), 1, Integer::sum);
        }
        return new DatasetSummary(records.size(), perCpse, totalWarnings, warnings);
    }
}
