package com.bachatgat.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class GroupMasterData {
    @DocumentId
    private String id;
    private String groupId;
    private int version = 1;
    private LocalDate effectiveFrom = LocalDate.now();
    private LocalDate effectiveTo;
    private boolean active = true;

    // Financial Rules
    /** Canonical recurring contribution configuration. Effective dates protect historical collection records. */
    private BigDecimal monthlyBachatAmount = BigDecimal.valueOf(5000);
    private BigDecimal defaultLoanEmiAmount = BigDecimal.valueOf(4100);
    private BigDecimal loanInterestRate = BigDecimal.valueOf(12.0); // Annual %
    private String loanInterestType = "REDUCING_BALANCE"; // REDUCING_BALANCE or FLAT
    private int defaultLoanDurationMonths = 12;
    private BigDecimal maxLoanAmount = BigDecimal.valueOf(100000);
    private BigDecimal latePaymentFee = BigDecimal.valueOf(100);
    private int collectionDueDay = 10; // 10th of every month
    private int gracePeriodDays = 5;

    // Multi-loan configuration per group (Requirements 26, 27, 28)
    private boolean allowMultipleLoans = false;
    private int maxActiveLoans = 1;
    private BigDecimal maxOutstandingLoanAmount = BigDecimal.valueOf(150000);
    private BigDecimal maxLoanAmountPerMember = BigDecimal.valueOf(100000);

    private BigDecimal membershipFee = BigDecimal.valueOf(200);
    private String financialYear = "2025-2026";
    private String currency = "INR";
    private String defaultLanguage = "mr"; // mr, en, hi
    private String updateScope = "FUTURE_ONLY"; // FUTURE_ONLY, EXISTING_ONLY, BOTH

    // Notification and payment categories
    private boolean sendSmsNotifications = true;
    private boolean sendEmailNotifications = true;
    private boolean sendWhatsAppNotifications = false;

    private String notes;
    private String createdBy;
    private LocalDateTime createdAt = LocalDateTime.now();

    public GroupMasterData() {}

    public GroupMasterData(String groupId, int version, BigDecimal monthlyBachatAmount, BigDecimal defaultLoanEmiAmount,
                           BigDecimal loanInterestRate, BigDecimal maxLoanAmount, BigDecimal latePaymentFee,
                           int collectionDueDay, int gracePeriodDays, String createdBy) {
        this.id = groupId + "_v" + version;
        this.groupId = groupId;
        this.version = version;
        this.monthlyBachatAmount = monthlyBachatAmount != null ? monthlyBachatAmount : BigDecimal.valueOf(5000);
        this.defaultLoanEmiAmount = defaultLoanEmiAmount != null ? defaultLoanEmiAmount : BigDecimal.valueOf(4100);
        this.loanInterestRate = loanInterestRate != null ? loanInterestRate : BigDecimal.valueOf(12.0);
        this.maxLoanAmount = maxLoanAmount != null ? maxLoanAmount : BigDecimal.valueOf(100000);
        this.latePaymentFee = latePaymentFee != null ? latePaymentFee : BigDecimal.valueOf(100);
        this.collectionDueDay = collectionDueDay > 0 ? collectionDueDay : 10;
        this.gracePeriodDays = gracePeriodDays >= 0 ? gracePeriodDays : 5;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
        this.effectiveFrom = LocalDate.now();
        this.active = true;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public BigDecimal getMonthlyBachatAmount() { return monthlyBachatAmount; }
    public void setMonthlyBachatAmount(BigDecimal monthlyBachatAmount) { this.monthlyBachatAmount = monthlyBachatAmount; }
    @JsonIgnore
    public BigDecimal getMonthlyShareAmount() { return monthlyBachatAmount; }
    @JsonIgnore
    public void setMonthlyShareAmount(BigDecimal monthlyShareAmount) { this.monthlyBachatAmount = monthlyShareAmount; }
    public BigDecimal getDefaultLoanEmiAmount() { return defaultLoanEmiAmount; }
    public void setDefaultLoanEmiAmount(BigDecimal defaultLoanEmiAmount) { this.defaultLoanEmiAmount = defaultLoanEmiAmount; }
    public BigDecimal getLoanInterestRate() { return loanInterestRate; }
    public void setLoanInterestRate(BigDecimal loanInterestRate) { this.loanInterestRate = loanInterestRate; }
    public String getLoanInterestType() { return loanInterestType; }
    public void setLoanInterestType(String loanInterestType) { this.loanInterestType = loanInterestType; }
    public int getDefaultLoanDurationMonths() { return defaultLoanDurationMonths; }
    public void setDefaultLoanDurationMonths(int defaultLoanDurationMonths) { this.defaultLoanDurationMonths = defaultLoanDurationMonths; }
    public BigDecimal getMaxLoanAmount() { return maxLoanAmount; }
    public void setMaxLoanAmount(BigDecimal maxLoanAmount) { this.maxLoanAmount = maxLoanAmount; }
    public BigDecimal getLatePaymentFee() { return latePaymentFee; }
    public void setLatePaymentFee(BigDecimal latePaymentFee) { this.latePaymentFee = latePaymentFee; }
    public int getCollectionDueDay() { return collectionDueDay; }
    public void setCollectionDueDay(int collectionDueDay) { this.collectionDueDay = collectionDueDay; }
    public int getGracePeriodDays() { return gracePeriodDays; }
    public void setGracePeriodDays(int gracePeriodDays) { this.gracePeriodDays = gracePeriodDays; }
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
    public boolean isSendSmsNotifications() { return sendSmsNotifications; }
    public void setSendSmsNotifications(boolean sendSmsNotifications) { this.sendSmsNotifications = sendSmsNotifications; }
    public boolean isSendEmailNotifications() { return sendEmailNotifications; }
    public void setSendEmailNotifications(boolean sendEmailNotifications) { this.sendEmailNotifications = sendEmailNotifications; }
    public boolean isSendWhatsAppNotifications() { return sendWhatsAppNotifications; }
    public void setSendWhatsAppNotifications(boolean sendWhatsAppNotifications) { this.sendWhatsAppNotifications = sendWhatsAppNotifications; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public boolean isAllowMultipleLoans() { return allowMultipleLoans; }
    public void setAllowMultipleLoans(boolean allowMultipleLoans) { this.allowMultipleLoans = allowMultipleLoans; }
    public int getMaxActiveLoans() { return maxActiveLoans > 0 ? maxActiveLoans : 1; }
    public void setMaxActiveLoans(int maxActiveLoans) { this.maxActiveLoans = maxActiveLoans > 0 ? maxActiveLoans : 1; }
    public BigDecimal getMaxOutstandingLoanAmount() { return maxOutstandingLoanAmount; }
    public void setMaxOutstandingLoanAmount(BigDecimal maxOutstandingLoanAmount) { this.maxOutstandingLoanAmount = maxOutstandingLoanAmount; }
    public BigDecimal getMaxLoanAmountPerMember() { return maxLoanAmountPerMember; }
    public void setMaxLoanAmountPerMember(BigDecimal maxLoanAmountPerMember) { this.maxLoanAmountPerMember = maxLoanAmountPerMember; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
