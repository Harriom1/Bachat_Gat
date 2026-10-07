package com.bachatgat.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class LoanApplicationRequest {
    @NotNull(message = "Requested loan amount is required")
    @Positive(message = "Loan amount must be greater than zero")
    private BigDecimal requestedAmount;

    @NotBlank(message = "Loan purpose is required")
    private String purpose;

    @NotNull(message = "Preferred duration is required")
    @Min(value = 1, message = "Duration must be at least 1 month")
    private Integer preferredDurationMonths;

    private String optionalMessage;
    private String supportingDocPath;

    public LoanApplicationRequest() {}

    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public Integer getPreferredDurationMonths() { return preferredDurationMonths; }
    public void setPreferredDurationMonths(Integer preferredDurationMonths) { this.preferredDurationMonths = preferredDurationMonths; }
    public Integer getRepaymentPeriodMonths() { return preferredDurationMonths; }
    public void setRepaymentPeriodMonths(Integer repaymentPeriodMonths) { this.preferredDurationMonths = repaymentPeriodMonths; }
    public String getOptionalMessage() { return optionalMessage; }
    public void setOptionalMessage(String optionalMessage) { this.optionalMessage = optionalMessage; }
    public String getNotes() { return optionalMessage; }
    public void setNotes(String notes) { this.optionalMessage = notes; }
    public String getSupportingDocPath() { return supportingDocPath; }
    public void setSupportingDocPath(String supportingDocPath) { this.supportingDocPath = supportingDocPath; }
}
