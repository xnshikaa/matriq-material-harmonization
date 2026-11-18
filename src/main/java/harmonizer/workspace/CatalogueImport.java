package harmonizer.workspace;

import harmonizer.io.CsvMaterialParser;
import harmonizer.model.InvalidDatasetException;
import harmonizer.model.MaterialRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class CatalogueImport {
    record Mapping(String itemCode, String description, String category, String grade, String dimensions,
                   String unit, String specification, String materialType) {}
    record Options(String organization, String name, String period, String reference, Mapping mapping) {}
    record Source(MaterialRecord material, String batchId, int row, Map<String, String> columns,
                  String category, String grade, String dimensions, String specification, String materialType) {}
    record Batch(String id, String fileName, String organizationCode, Instant uploadDate, int recordCount,
                 int validRows, String name, String period, String reference, long bytes, String digest,
                 List<String> warnings, String actorId) {}
    record Prepared(Batch batch, List<Source> records) {}
    record Validation(int totalRows, int validRows, List<String> warnings, List<Source> preview) {}

    static List<List<String>> parse(byte[] content) {
        var rows = CsvMaterialParser.readRows(new String(content, StandardCharsets.UTF_8));
        if (rows.size() < 2) throw new InvalidDatasetException("The CSV requires headers and at least one data row.");
        if (rows.size() - 1 > CsvMaterialParser.MAX_ROWS) throw new InvalidDatasetException("Too many records in one file.");
        var normalized = new HashSet<String>();
        for (String header : rows.get(0)) {
            if (header.isBlank() || !normalized.add(header.strip().toLowerCase(Locale.ROOT))) {
                throw new InvalidDatasetException("Use distinct, non-empty CSV headers.");
            }
        }
        return rows;
    }

    static Prepared prepare(byte[] content, String fileName, Options options, String actor,
                            List<Source> existing, List<Batch> batches) {
        String organization = options.organization();
        String digest = digest(content);
        if (batches.stream().anyMatch(batch -> batch.digest().equals(digest) && batch.organizationCode().equals(organization))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This file has already been imported for the organisation.");
        }
        var rows = parse(content);
        List<String> headers = rows.get(0).stream().map(String::strip).toList();
        Mapping mapping = options.mapping();
        if (mapping == null || !headers.contains(mapping.itemCode()) || !headers.contains(mapping.description())
                || mapping.itemCode().equals(mapping.description())) {
            throw new InvalidDatasetException("Map distinct item code and description columns.");
        }

        var organisationColumns = new ArrayList<Integer>();
        for (int col = 0; col < headers.size(); col++) {
            String field = headers.get(col).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (Set.of("organization", "organisation", "cpse", "company", "organizationcode",
                    "organisationcode", "companycode", "sourceorganization", "sourceorganisation").contains(field)) {
                organisationColumns.add(col);
            }
        }
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            for (int col : organisationColumns) {
                if (col < row.size() && !row.get(col).isBlank() && !row.get(col).strip().equalsIgnoreCase(organization)) {
                    throw new InvalidDatasetException("CSV record " + (index + 1) + " declares source organisation '"
                            + row.get(col).strip() + "'. Split the CSV into one file per organisation and select its source before importing.");
                }
            }
        }

        String batchId = UUID.randomUUID().toString();
        Set<String> keys = new HashSet<>();
        for (Source source : existing) keys.add(key(source.material().cpse(), source.material().code()));
        List<String> warnings = new ArrayList<>();
        List<Source> accepted = new ArrayList<>();
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            if (row.size() != headers.size()) {
                warn(warnings, index + 1, "Column count differs from the header.");
                continue;
            }
            Map<String, String> columns = new LinkedHashMap<>();
            for (int col = 0; col < headers.size(); col++) columns.put(headers.get(col), row.get(col));
            String code = cell(columns, mapping.itemCode());
            String description = cell(columns, mapping.description());
            if (!organisationColumns.isEmpty() && organisationColumns.stream().allMatch(col -> row.get(col).isBlank())) {
                warn(warnings, index + 1, "Source organisation is blank.");
            } else if (code.isBlank() || code.length() > 200 || code.contains("|") || description.isBlank()
                    || description.length() > 10_000) {
                warn(warnings, index + 1, "Provide a valid item code and a description of up to 10,000 characters.");
            } else if (!keys.add(key(organization, code))) {
                warn(warnings, index + 1, "Item code is already present for this organisation.");
            } else {
                var material = new MaterialRecord(existing.size() + accepted.size() + 1, organization, code,
                        description, cell(columns, mapping.unit()));
                accepted.add(new Source(material, batchId, index + 1, Map.copyOf(columns),
                        cell(columns, mapping.category()), cell(columns, mapping.grade()),
                        cell(columns, mapping.dimensions()), cell(columns, mapping.specification()),
                        cell(columns, mapping.materialType())));
            }
        }
        if (accepted.isEmpty()) throw new InvalidDatasetException(warnings);
        var batch = new Batch(batchId, fileName, organization, Instant.now(), rows.size() - 1, accepted.size(),
                options.name(), options.period(), options.reference(), content.length, digest, warnings, actor);
        return new Prepared(batch, accepted);
    }

    static String key(String org, String code) {
        return (org + "|" + code).toUpperCase(Locale.ROOT);
    }

    private static void warn(List<String> warnings, int row, String message) {
        if (warnings.size() < 50) warnings.add("CSV record " + row + ": " + message);
    }

    static String named(Map<String, String> columns, String... names) {
        for (String name : names) {
            for (var entry : columns.entrySet()) {
                if (name.equalsIgnoreCase(entry.getKey())) return entry.getValue().strip();
            }
        }
        return "";
    }

    private static String cell(Map<String, String> columns, String header) {
        return header == null ? "" : columns.getOrDefault(header, "").strip();
    }

    private static String digest(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
        }
    }
}
