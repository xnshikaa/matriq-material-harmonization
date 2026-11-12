package com.matriq.model;

import java.time.Instant;
import java.util.List;

/**
 * Represents a proposed match between two material records.
 *
 * A match is proposed by the matching engine after Bloom Filter, Trie and KMP screening.
 * It must NOT be used to establish equivalence until a human reviewer approves it,
 * or the attribute-level verification passes all required rules.
 */
public class MaterialMatch {

    private String id;
    private String recordAId;
    private String recordBId;

    /** Why these records were proposed as a match. */
    private String matchReason;

    /** KMP-discovered specification patterns that appear in both descriptions. */
    private List<String> matchedSpecifications;

    /** Attribute-level verification result details. */
    private AttributeVerification attributeVerification;

    /** Overall match confidence score (0.0 – 1.0). NOT a substitute for verification. */
    private double confidenceScore;

    /** Current review status. */
    private MatchStatus status;

    private String reviewedBy;
    private String reviewNote;
    private Instant proposedAt;
    private Instant reviewedAt;

    public enum MatchStatus {
        PENDING,    // Proposed, awaiting review
        APPROVED,   // Reviewer confirmed equivalence — eligible for DSU union
        REJECTED,   // Reviewer rejected — must NOT be passed to DSU
        AUTO_APPROVED // Passed strict attribute verification without manual review
    }

    /**
     * Detailed attribute-level verification summary.
     * Both category, type, grade AND dimensions must be compatible for a match to proceed.
     */
    public static class AttributeVerification {
        private boolean categoryMatch;
        private boolean typeMatch;
        private boolean gradeMatch;
        private boolean dimensionMatch;      // considering unit conversion and tolerance
        private boolean specificationMatch;  // KMP-confirmed specification
        private boolean overallPass;         // ALL required attributes compatible
        private String failureReason;        // populated when overallPass = false

        public boolean isCategoryMatch() { return categoryMatch; }
        public void setCategoryMatch(boolean categoryMatch) { this.categoryMatch = categoryMatch; }

        public boolean isTypeMatch() { return typeMatch; }
        public void setTypeMatch(boolean typeMatch) { this.typeMatch = typeMatch; }

        public boolean isGradeMatch() { return gradeMatch; }
        public void setGradeMatch(boolean gradeMatch) { this.gradeMatch = gradeMatch; }

        public boolean isDimensionMatch() { return dimensionMatch; }
        public void setDimensionMatch(boolean dimensionMatch) { this.dimensionMatch = dimensionMatch; }

        public boolean isSpecificationMatch() { return specificationMatch; }
        public void setSpecificationMatch(boolean specificationMatch) { this.specificationMatch = specificationMatch; }

        public boolean isOverallPass() { return overallPass; }
        public void setOverallPass(boolean overallPass) { this.overallPass = overallPass; }

        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    }

    // ── Getters and Setters ───────────────────────────────────────────────────
    public MaterialMatch() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRecordAId() { return recordAId; }
    public void setRecordAId(String recordAId) { this.recordAId = recordAId; }

    public String getRecordBId() { return recordBId; }
    public void setRecordBId(String recordBId) { this.recordBId = recordBId; }

    public String getMatchReason() { return matchReason; }
    public void setMatchReason(String matchReason) { this.matchReason = matchReason; }

    public List<String> getMatchedSpecifications() { return matchedSpecifications; }
    public void setMatchedSpecifications(List<String> matchedSpecifications) { this.matchedSpecifications = matchedSpecifications; }

    public AttributeVerification getAttributeVerification() { return attributeVerification; }
    public void setAttributeVerification(AttributeVerification attributeVerification) { this.attributeVerification = attributeVerification; }

    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }

    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }

    public Instant getProposedAt() { return proposedAt; }
    public void setProposedAt(Instant proposedAt) { this.proposedAt = proposedAt; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
