package com.matriq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A group of material records confirmed to be physically equivalent.
 * Created by the DSU after approved matches are unioned.
 */
public class MaterialGroup {

    private String id;             // DSU representative record ID
    private String nationalCode;   // assigned after approval
    private List<String> memberIds = new ArrayList<>();
    private GroupStatus status;
    private String description;    // human-readable group description
    private Instant createdAt;
    private Instant approvedAt;

    public enum GroupStatus {
        DRAFT,    // Built from DSU but not yet formally approved
        APPROVED, // National code assigned and published
        REJECTED  // Entire group rejected
    }

    public MaterialGroup() {
        this.createdAt = Instant.now();
        this.status = GroupStatus.DRAFT;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNationalCode() { return nationalCode; }
    public void setNationalCode(String nationalCode) { this.nationalCode = nationalCode; }

    public List<String> getMemberIds() { return memberIds; }
    public void setMemberIds(List<String> memberIds) { this.memberIds = memberIds; }

    public void addMember(String recordId) { this.memberIds.add(recordId); }

    public GroupStatus getStatus() { return status; }
    public void setStatus(GroupStatus status) { this.status = status; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
}
