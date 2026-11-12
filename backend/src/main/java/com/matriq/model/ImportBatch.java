package com.matriq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks a CSV import operation — one batch per uploaded file.
 */
public class ImportBatch {

    private String id;
    private String companyName;
    private String fileName;
    private int totalRows;
    private int validRows;
    private int invalidRows;
    private int duplicateRows;
    private List<String> warnings = new ArrayList<>();
    private List<ValidationError> errors = new ArrayList<>();
    private BatchStatus status;
    private Instant importedAt;

    public enum BatchStatus {
        PROCESSING, COMPLETED, FAILED
    }

    public static class ValidationError {
        private int rowNumber;
        private String field;
        private String message;

        public ValidationError(int rowNumber, String field, String message) {
            this.rowNumber = rowNumber;
            this.field = field;
            this.message = message;
        }

        public int getRowNumber() { return rowNumber; }
        public String getField() { return field; }
        public String getMessage() { return message; }
    }

    public ImportBatch() {
        this.importedAt = Instant.now();
        this.status = BatchStatus.PROCESSING;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }

    public int getValidRows() { return validRows; }
    public void setValidRows(int validRows) { this.validRows = validRows; }

    public int getInvalidRows() { return invalidRows; }
    public void setInvalidRows(int invalidRows) { this.invalidRows = invalidRows; }

    public int getDuplicateRows() { return duplicateRows; }
    public void setDuplicateRows(int duplicateRows) { this.duplicateRows = duplicateRows; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    public List<ValidationError> getErrors() { return errors; }
    public void setErrors(List<ValidationError> errors) { this.errors = errors; }

    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus status) { this.status = status; }

    public Instant getImportedAt() { return importedAt; }
    public void setImportedAt(Instant importedAt) { this.importedAt = importedAt; }
}
