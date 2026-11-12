package com.matriq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A unique national material code entry published to the National Material Registry.
 * Maps one common identity to multiple company-specific codes.
 *
 * Format: NMC-YYYY-XXXXXXXX  (e.g. NMC-2026-00000001)
 * National codes are deterministic — once assigned to a group they never change.
 */
public class NationalMaterialCode {

    private String code;             // e.g. NMC-2026-00000001
    private String groupId;
    private String canonicalDescription;  // best human-readable description for the group
    private String materialCategory;
    private String materialType;
    private String grade;
    private String technicalSpecification;
    private List<CompanyCodeMapping> companyMappings = new ArrayList<>();
    private Instant issuedAt;

    /**
     * Maps the national code back to an individual company's original code.
     */
    public static class CompanyCodeMapping {
        private String companyName;
        private String originalCode;
        private String originalDescription;

        public CompanyCodeMapping() {}

        public CompanyCodeMapping(String companyName, String originalCode, String originalDescription) {
            this.companyName = companyName;
            this.originalCode = originalCode;
            this.originalDescription = originalDescription;
        }

        public String getCompanyName() { return companyName; }
        public void setCompanyName(String companyName) { this.companyName = companyName; }

        public String getOriginalCode() { return originalCode; }
        public void setOriginalCode(String originalCode) { this.originalCode = originalCode; }

        public String getOriginalDescription() { return originalDescription; }
        public void setOriginalDescription(String originalDescription) { this.originalDescription = originalDescription; }
    }

    public NationalMaterialCode() {
        this.issuedAt = Instant.now();
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getCanonicalDescription() { return canonicalDescription; }
    public void setCanonicalDescription(String canonicalDescription) { this.canonicalDescription = canonicalDescription; }

    public String getMaterialCategory() { return materialCategory; }
    public void setMaterialCategory(String materialCategory) { this.materialCategory = materialCategory; }

    public String getMaterialType() { return materialType; }
    public void setMaterialType(String materialType) { this.materialType = materialType; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getTechnicalSpecification() { return technicalSpecification; }
    public void setTechnicalSpecification(String technicalSpecification) { this.technicalSpecification = technicalSpecification; }

    public List<CompanyCodeMapping> getCompanyMappings() { return companyMappings; }
    public void setCompanyMappings(List<CompanyCodeMapping> companyMappings) { this.companyMappings = companyMappings; }

    public void addCompanyMapping(CompanyCodeMapping mapping) { this.companyMappings.add(mapping); }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
}
