package com.bachatgat.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;

public class MasterDataDTO {
    @JsonAlias("monthlyShareAmount")
    private BigDecimal monthlyBachatAmount;
    private BigDecimal defaultLoanEmiAmount;
    private BigDecimal loanInterestRate;
    private String loanInterestType;
    private Integer defaultLoanDurationMonths;
    private BigDecimal maxLoanAmount; // Total Group Loan Capacity
    private BigDecimal maxLoanAmountPerMember; // Single Member Loan Limit
    private BigDecimal maxOutstandingLoanAmount; // Max Cumulative Outstanding Debt
    private BigDecimal latePaymentFee;
    private Integer collectionDueDay;
    private Integer gracePeriodDays;
    private BigDecimal membershipFee;
    private String financialYear;
    private String currency;
    private String defaultLanguage;
    private String updateScope; // FUTURE_ONLY, EXISTING_ONLY, BOTH
    private String latePaymentType;
    private java.time.LocalDate effectiveFrom;
    private String notes;

    public MasterDataDTO() {}

    // Getters and Setters
    public BigDecimal getMonthlyBachatAmount() { return monthlyBachatAmount; }
    public void setMonthlyBachatAmount(BigDecimal monthlyBachatAmount) { this.monthlyBachatAmount = monthlyBachatAmount; }
    public BigDecimal getMonthlyShareAmount() { return monthlyBachatAmount; }
    public void setMonthlyShareAmount(BigDecimal monthlyShareAmount) { this.monthlyBachatAmount = monthlyShareAmount; }
    public BigDecimal getDefaultLoanEmiAmount() { return defaultLoanEmiAmount; }
    public void setDefaultLoanEmiAmount(BigDecimal defaultLoanEmiAmount) { this.defaultLoanEmiAmount = defaultLoanEmiAmount; }
    public BigDecimal getLoanInterestRate() { return loanInterestRate; }
    public void setLoanInterestRate(BigDecimal loanInterestRate) { this.loanInterestRate = loanInterestRate; }
    public String getLoanInterestType() { return loanInterestType; }
    public void setLoanInterestType(String loanInterestType) { this.loanInterestType = loanInterestType; }
    public Integer getDefaultLoanDurationMonths() { return defaultLoanDurationMonths; }
    public void setDefaultLoanDurationMonths(Integer defaultLoanDurationMonths) { this.defaultLoanDurationMonths = defaultLoanDurationMonths; }
    public BigDecimal getMaxLoanAmount() { return maxLoanAmount; }
    public void setMaxLoanAmount(BigDecimal maxLoanAmount) { this.maxLoanAmount = maxLoanAmount; }
    public BigDecimal getMaxLoanAmountPerMember() { return maxLoanAmountPerMember; }
    public void setMaxLoanAmountPerMember(BigDecimal maxLoanAmountPerMember) { this.maxLoanAmountPerMember = maxLoanAmountPerMember; }
    public BigDecimal getMaxOutstandingLoanAmount() { return maxOutstandingLoanAmount; }
    public void setMaxOutstandingLoanAmount(BigDecimal maxOutstandingLoanAmount) { this.maxOutstandingLoanAmount = maxOutstandingLoanAmount; }
    public BigDecimal getLatePaymentFee() { return latePaymentFee; }
    public void setLatePaymentFee(BigDecimal latePaymentFee) { this.latePaymentFee = latePaymentFee; }
    public Integer getCollectionDueDay() { return collectionDueDay; }
    public void setCollectionDueDay(Integer collectionDueDay) { this.collectionDueDay = collectionDueDay; }
    public Integer getGracePeriodDays() { return gracePeriodDays; }
    public void setGracePeriodDays(Integer gracePeriodDays) { this.gracePeriodDays = gracePeriodDays; }
    public BigDecimal getMembershipFee() { return membershipFee; }
    public void setMembershipFee(BigDecimal membershipFee) { this.membershipFee = membershipFee; }
    public String getFinancialYear() { return financialYear; }
    public void setFinancialYear(String financialYear) { this.financialYear = financialYear; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDefaultLanguage() { return defaultLanguage; }
    public void setDefaultLanguage(String defaultLanguage) { this.defaultLanguage = defaultLanguage; }
    public String getUpdateScope() { return updateScope; }
    public void setUpdateScope(String updateScope) { this.updateScope = updateScope; }
    public String getLatePaymentType() { return latePaymentType; }
    public void setLatePaymentType(String latePaymentType) { this.latePaymentType = latePaymentType; }
    public java.time.LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(java.time.LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public void setDefaultLoanEmi(BigDecimal amt) { this.defaultLoanEmiAmount = amt; }
    public void setLoanMaxAmount(BigDecimal amt) { this.maxLoanAmount = amt; }
    public void setLoanDurationMonths(int months) { this.defaultLoanDurationMonths = months; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
