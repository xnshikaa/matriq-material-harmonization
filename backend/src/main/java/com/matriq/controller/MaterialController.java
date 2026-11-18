package com.matriq.controller;

import com.matriq.model.ImportBatch;
import com.matriq.model.MaterialRecord;
import com.matriq.repository.HarmonizationRepository;
import com.matriq.repository.MaterialRepository;
import com.matriq.service.CsvImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * REST controller for material import and retrieval.
 */
@org.springframework.context.annotation.Profile("legacy-tooling")
@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    private final CsvImportService csvImportService;
    private final MaterialRepository materialRepository;
    private final HarmonizationRepository harmonizationRepository;

    @Autowired
    public MaterialController(CsvImportService csvImportService,
                               MaterialRepository materialRepository,
                               HarmonizationRepository harmonizationRepository) {
        this.csvImportService = csvImportService;
        this.materialRepository = materialRepository;
        this.harmonizationRepository = harmonizationRepository;
    }

    /**
     * POST /api/materials/upload
     * Upload a CSV file for a specific company.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("companyName") String companyName) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file provided."));
        }
        if (companyName == null || companyName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "companyName is required."));
        }
        try {
            ImportBatch batch = csvImportService.importCsv(file, companyName);
            return ResponseEntity.ok(batch);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "File processing failed: " + e.getMessage()));
        }
    }

    /** GET /api/materials — list all imported material records */
    @GetMapping
    public ResponseEntity<List<MaterialRecord>> getAll(
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String status) {
        List<MaterialRecord> records;
        if (company != null) {
            records = materialRepository.findByCompany(company);
        } else if (status != null) {
            records = materialRepository.findByStatus(MaterialRecord.ImportStatus.valueOf(status));
        } else {
            records = materialRepository.findAll();
        }
        return ResponseEntity.ok(records);
    }

    /** GET /api/materials/{id} — get a single record */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        return materialRepository.findById(id)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/imports — list all import batches */
    @GetMapping("/imports")
    public ResponseEntity<List<ImportBatch>> getImports() {
        return ResponseEntity.ok(harmonizationRepository.findAllBatches());
    }

    /** GET /api/imports/{id}/validation — get validation details for a batch */
    @GetMapping("/imports/{id}/validation")
    public ResponseEntity<?> getImportValidation(@PathVariable String id) {
        return harmonizationRepository.findBatchById(id)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/materials/stats — summary statistics */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        List<MaterialRecord> all = materialRepository.findAll();
        Map<String, Long> byCompany = new HashMap<>();
        Map<String, Long> byCategory = new HashMap<>();

        for (MaterialRecord r : all) {
            byCompany.merge(r.getCompanyName(), 1L, Long::sum);
            byCategory.merge(
                r.getMaterialCategory() != null ? r.getMaterialCategory() : "UNCATEGORIZED",
                1L, Long::sum);
        }

        return ResponseEntity.ok(Map.of(
            "total", all.size(),
            "byCompany", byCompany,
            "byCategory", byCategory
        ));
    }
}
