package com.matriq.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Core domain model — represents a single material record as imported from a company CSV.
 *
 * Every record retains its original company identity and original material code permanently.
 * The normalized fields are computed by the NormalizationService and must never overwrite
 * the originals.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MaterialRecord {

    /** Unique internal identifier assigned on import. */
    private String id;

    // ── Company identity (always preserved) ──────────────────────────────────
    private String companyName;
    private String originalMaterialCode;
    private String originalDescription;

    // ── Normalized fields (computed, never replace originals) ─────────────────
    private String normalizedDescription;
    private String materialCategory;   // e.g. PIPE, VALVE, FITTING, PUMP …
    private String materialType;       // e.g. SEAMLESS, WELDED, FLANGED …
    private String dimensions;         // normalized dimension string
    private String unit;               // normalized unit (MM, INCH, …)
    private String grade;              // e.g. A106 Gr.B, SS316, IS1239 …
    private String technicalSpecification; // primary spec standard e.g. ASTM A106, API 5L

    // ── Optional fields ───────────────────────────────────────────────────────
    private String manufacturer;
    private String importBatchId;

    // ── Status / metadata ─────────────────────────────────────────────────────
    private String nationalMaterialCode; // set after harmonization approval
    private String groupId;              // DSU group representative ID
    private ImportStatus status;
    private Instant importedAt;

    /** Raw extra columns from the CSV that did not map to known fields. */
    private Map<String, String> extraAttributes = new HashMap<>();

    public enum ImportStatus {
        IMPORTED, NORMALIZED, CANDIDATE, GROUPED, APPROVED, REJECTED
    }

    // ── Constructors ──────────────────────────────────────────────────────────
    public MaterialRecord() {}

    public MaterialRecord(String id, String companyName, String originalMaterialCode, String originalDescription) {
        this.id = id;
        this.companyName = companyName;
        this.originalMaterialCode = originalMaterialCode;
        this.originalDescription = originalDescription;
        this.status = ImportStatus.IMPORTED;
        this.importedAt = Instant.now();
    }

    // ── Getters and Setters ───────────────────────────────────────────────────
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getOriginalMaterialCode() { return originalMaterialCode; }
    public void setOriginalMaterialCode(String originalMaterialCode) { this.originalMaterialCode = originalMaterialCode; }

    public String getOriginalDescription() { return originalDescription; }
    public void setOriginalDescription(String originalDescription) { this.originalDescription = originalDescription; }

    public String getNormalizedDescription() { return normalizedDescription; }
    public void setNormalizedDescription(String normalizedDescription) { this.normalizedDescription = normalizedDescription; }

    public String getMaterialCategory() { return materialCategory; }
    public void setMaterialCategory(String materialCategory) { this.materialCategory = materialCategory; }

    public String getMaterialType() { return materialType; }
    public void setMaterialType(String materialType) { this.materialType = materialType; }

    public String getDimensions() { return dimensions; }
    public void setDimensions(String dimensions) { this.dimensions = dimensions; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getTechnicalSpecification() { return technicalSpecification; }
    public void setTechnicalSpecification(String technicalSpecification) { this.technicalSpecification = technicalSpecification; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getImportBatchId() { return importBatchId; }
    public void setImportBatchId(String importBatchId) { this.importBatchId = importBatchId; }

    public String getNationalMaterialCode() { return nationalMaterialCode; }
    public void setNationalMaterialCode(String nationalMaterialCode) { this.nationalMaterialCode = nationalMaterialCode; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public ImportStatus getStatus() { return status; }
    public void setStatus(ImportStatus status) { this.status = status; }

    public Instant getImportedAt() { return importedAt; }
    public void setImportedAt(Instant importedAt) { this.importedAt = importedAt; }

    public Map<String, String> getExtraAttributes() { return extraAttributes; }
    public void setExtraAttributes(Map<String, String> extraAttributes) { this.extraAttributes = extraAttributes; }

    @Override
    public String toString() {
        return "MaterialRecord{id='" + id + "', company='" + companyName +
               "', code='" + originalMaterialCode + "', desc='" + originalDescription + "'}";
    }
}
