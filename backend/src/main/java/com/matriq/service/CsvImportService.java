package com.matriq.service;

import com.matriq.model.ImportBatch;
import com.matriq.model.MaterialRecord;
import com.matriq.normalization.NormalizationService;
import com.matriq.repository.HarmonizationRepository;
import com.matriq.repository.MaterialRepository;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Handles CSV file parsing, validation, and import of material records.
 *
 * Required CSV columns (case-insensitive):
 *   material_code (or code, originalCode)
 *   description   (or desc, originalDescription)
 *
 * Optional columns (auto-mapped if present):
 *   category, type, dimensions, unit, grade, specification, manufacturer
 */
@Service
public class CsvImportService {

    private final MaterialRepository materialRepository;
    private final HarmonizationRepository harmonizationRepository;
    private final NormalizationService normalizationService;

    // Known column name aliases (all lowercase)
    private static final Map<String, String> COLUMN_ALIASES = new HashMap<>();
    static {
        COLUMN_ALIASES.put("code", "material_code");
        COLUMN_ALIASES.put("originalcode", "material_code");
        COLUMN_ALIASES.put("materialcode", "material_code");
        COLUMN_ALIASES.put("material code", "material_code");
        COLUMN_ALIASES.put("item code", "material_code");
        COLUMN_ALIASES.put("itemcode", "material_code");
        COLUMN_ALIASES.put("part_no", "material_code");
        COLUMN_ALIASES.put("partno", "material_code");

        COLUMN_ALIASES.put("desc", "description");
        COLUMN_ALIASES.put("item_description", "description");
        COLUMN_ALIASES.put("item description", "description");
        COLUMN_ALIASES.put("materialdescription", "description");
        COLUMN_ALIASES.put("material description", "description");
        COLUMN_ALIASES.put("originaldescription", "description");

        COLUMN_ALIASES.put("spec", "specification");
        COLUMN_ALIASES.put("technical_spec", "specification");
        COLUMN_ALIASES.put("dim", "dimensions");
        COLUMN_ALIASES.put("mfr", "manufacturer");
        COLUMN_ALIASES.put("vendor", "manufacturer");
        COLUMN_ALIASES.put("make", "manufacturer");
    }

    @Autowired
    public CsvImportService(MaterialRepository materialRepository,
                             HarmonizationRepository harmonizationRepository,
                             NormalizationService normalizationService) {
        this.materialRepository = materialRepository;
        this.harmonizationRepository = harmonizationRepository;
        this.normalizationService = normalizationService;
    }

    /**
     * Processes an uploaded CSV file and imports material records.
     *
     * @param file        the uploaded CSV
     * @param companyName the company this CSV belongs to
     * @return ImportBatch with validation results
     */
    public ImportBatch importCsv(MultipartFile file, String companyName) throws IOException {
        ImportBatch batch = new ImportBatch();
        batch.setId(UUID.randomUUID().toString());
        batch.setCompanyName(companyName);
        batch.setFileName(file.getOriginalFilename());
        harmonizationRepository.saveBatch(batch);

        List<String[]> rows;
        try (CSVReader reader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            rows = reader.readAll();
        } catch (CsvException e) {
            batch.setStatus(ImportBatch.BatchStatus.FAILED);
            batch.getErrors().add(new ImportBatch.ValidationError(0, "FILE", "CSV parse error: " + e.getMessage()));
            harmonizationRepository.saveBatch(batch);
            return batch;
        }

        if (rows.isEmpty()) {
            batch.setStatus(ImportBatch.BatchStatus.FAILED);
            batch.getErrors().add(new ImportBatch.ValidationError(0, "FILE", "CSV file is empty."));
            harmonizationRepository.saveBatch(batch);
            return batch;
        }

        // ── Parse header row ──────────────────────────────────────────────────
        String[] headers = rows.get(0);
        Map<String, Integer> columnIndex = buildColumnIndex(headers);

        // Validate required columns
        if (!columnIndex.containsKey("material_code")) {
            batch.getWarnings().add("No 'material_code' column found. Looked for: code, materialCode, item_code, part_no.");
        }
        if (!columnIndex.containsKey("description")) {
            batch.getWarnings().add("No 'description' column found. Looked for: desc, item_description, materialDescription.");
        }

        // ── Process data rows ─────────────────────────────────────────────────
        int valid = 0, invalid = 0, duplicate = 0;
        List<MaterialRecord> importedRecords = new ArrayList<>();

        for (int rowNum = 1; rowNum < rows.size(); rowNum++) {
            String[] row = rows.get(rowNum);
            if (isBlankRow(row)) continue;

            batch.setTotalRows(batch.getTotalRows() + 1);

            String code = getColumn(row, columnIndex, "material_code", "");
            String desc = getColumn(row, columnIndex, "description", "");

            // Validate required fields
            if (code.isBlank()) {
                batch.getErrors().add(new ImportBatch.ValidationError(rowNum + 1, "material_code", "Missing material code."));
                invalid++;
                continue;
            }
            if (desc.isBlank()) {
                batch.getErrors().add(new ImportBatch.ValidationError(rowNum + 1, "description", "Missing description."));
                invalid++;
                continue;
            }

            // Check for duplicate within same company
            if (materialRepository.existsByCompanyAndCode(companyName, code)) {
                batch.getWarnings().add("Row " + (rowNum + 1) + ": Duplicate code '" + code + "' for " + companyName + ". Skipped.");
                duplicate++;
                continue;
            }

            // Build material record
            MaterialRecord record = new MaterialRecord(
                UUID.randomUUID().toString(),
                companyName,
                code,
                desc
            );
            record.setImportBatchId(batch.getId());

            // Map optional columns
            record.setMaterialCategory(getColumn(row, columnIndex, "category", null));
            record.setMaterialType(getColumn(row, columnIndex, "type", null));
            record.setDimensions(getColumn(row, columnIndex, "dimensions", null));
            record.setUnit(getColumn(row, columnIndex, "unit", null));
            record.setGrade(getColumn(row, columnIndex, "grade", null));
            record.setTechnicalSpecification(getColumn(row, columnIndex, "specification", null));
            record.setManufacturer(getColumn(row, columnIndex, "manufacturer", null));

            // Capture any extra columns as extra attributes
            for (Map.Entry<String, Integer> colEntry : columnIndex.entrySet()) {
                String colName = colEntry.getKey();
                if (!isKnownColumn(colName) && colEntry.getValue() < row.length) {
                    String val = row[colEntry.getValue()].strip();
                    if (!val.isBlank()) {
                        record.getExtraAttributes().put(colName, val);
                    }
                }
            }

            importedRecords.add(record);
            valid++;
        }

        // Save all valid records
        materialRepository.saveAll(importedRecords);

        batch.setValidRows(valid);
        batch.setInvalidRows(invalid);
        batch.setDuplicateRows(duplicate);
        batch.setStatus(ImportBatch.BatchStatus.COMPLETED);
        harmonizationRepository.saveBatch(batch);

        return batch;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Integer> buildColumnIndex(String[] headers) {
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String raw = headers[i].strip().toLowerCase().replaceAll("[^a-z0-9_ ]", "");
            String canonical = COLUMN_ALIASES.getOrDefault(raw, raw);
            idx.put(canonical, i);
            idx.put(raw, i); // also keep the original
        }
        return idx;
    }

    private String getColumn(String[] row, Map<String, Integer> idx, String key, String defaultValue) {
        Integer colIdx = idx.get(key);
        if (colIdx == null || colIdx >= row.length) return defaultValue;
        String val = row[colIdx].strip();
        return val.isEmpty() ? defaultValue : val;
    }

    private boolean isBlankRow(String[] row) {
        for (String cell : row) {
            if (cell != null && !cell.isBlank()) return false;
        }
        return true;
    }

    private boolean isKnownColumn(String col) {
        return Set.of("material_code", "description", "category", "type",
                      "dimensions", "unit", "grade", "specification", "manufacturer").contains(col);
    }
}
