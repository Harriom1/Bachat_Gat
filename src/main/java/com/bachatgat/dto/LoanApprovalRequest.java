package com.bachatgat.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class LoanApprovalRequest {
    @NotNull(message = "Approved amount is required")
    @Positive(message = "Approved amount must be greater than zero")
    private BigDecimal approvedAmount;

    @NotNull(message = "Interest rate is required")
    @DecimalMin(value = "0.0", message = "Interest rate must be zero or positive")
    private BigDecimal interestRate = BigDecimal.valueOf(12.0);

    @NotNull(message = "Duration in months is required")
    @Min(value = 1, message = "Duration must be at least 1 month")
    private Integer durationMonths = 12;

    private String interestType = "FLAT"; // FLAT or REDUCING
    private Boolean isMonthlyRate;         // true for e.g. 2% per month, false/null for annual
    private Boolean disburseImmediately = true; // if false, stays in APPROVED status until separate disbursement
    private String adminNotes;

    public LoanApprovalRequest() {}

    public Boolean getIsMonthlyRate() { return isMonthlyRate; }
    public void setIsMonthlyRate(Boolean isMonthlyRate) { this.isMonthlyRate = isMonthlyRate; }
    public Boolean getDisburseImmediately() { return disburseImmediately != null ? disburseImmediately : true; }
    public void setDisburseImmediately(Boolean disburseImmediately) { this.disburseImmediately = disburseImmediately; }

    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public Integer getDurationMonths() { return durationMonths; }
    public void setDurationMonths(Integer durationMonths) { this.durationMonths = durationMonths; }
    public String getInterestType() { return interestType; }
    public void setInterestType(String interestType) { this.interestType = interestType; }
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
}
