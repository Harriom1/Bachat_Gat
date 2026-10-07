package com.bachatgat.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class DistributionRequest {
    @NotNull(message = "Distribution rule is required")
    private String distributionRule; // EQUAL_ACTIVE_MEMBERS, PROPORTIONAL_SAVINGS, SELECTED_MEMBERS, MANUAL, GROUP_LEVEL_ONLY

    private List<String> targetMemberIds;
    private Map<String, BigDecimal> manualAmounts; // memberId -> amount
    private String notes;

    public DistributionRequest() {}

    public DistributionRequest(String distributionRule) {
        this.distributionRule = distributionRule;
    }

    // Getters and Setters
    public String getDistributionRule() { return distributionRule; }
    public void setDistributionRule(String distributionRule) { this.distributionRule = distributionRule; }
    public List<String> getTargetMemberIds() { return targetMemberIds; }
    public void setTargetMemberIds(List<String> targetMemberIds) { this.targetMemberIds = targetMemberIds; }
    public Map<String, BigDecimal> getManualAmounts() { return manualAmounts; }
    public void setManualAmounts(Map<String, BigDecimal> manualAmounts) { this.manualAmounts = manualAmounts; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setNote(String note) { this.notes = note; }
}
