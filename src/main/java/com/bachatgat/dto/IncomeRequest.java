package com.bachatgat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public class IncomeRequest {
    @NotNull(message = "Source is required")
    private String source; // INTEREST_INCOME, DONATION, GRANT, MISC, BANK_INTEREST, OTHER
    
    private String description;
    
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;
    
    private LocalDate incomeDate = LocalDate.now();
    private String paymentMethod = "CASH";
    private String reference;
    private String distributionRule = "GROUP_LEVEL_ONLY"; // EQUAL_ACTIVE_MEMBERS, PROPORTIONAL_SAVINGS, SELECTED_MEMBERS, MANUAL, GROUP_LEVEL_ONLY

    public IncomeRequest() {}

    // Getters and Setters
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getIncomeDate() { return incomeDate; }
    public void setIncomeDate(LocalDate incomeDate) { this.incomeDate = incomeDate; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getDistributionRule() { return distributionRule; }
    public void setDistributionRule(String distributionRule) { this.distributionRule = distributionRule; }
}
