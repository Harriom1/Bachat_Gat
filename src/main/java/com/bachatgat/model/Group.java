package com.bachatgat.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Group {
    @DocumentId
    private String id;
    private String groupName;
    private String groupNameMr;
    private String groupNameHi;
    private String defaultLanguage = "en";
    private String registrationId;       // e.g. GR-2026-000001
    private String bachatGatRegNumber;   // e.g. MH/SHG/12345
    private String groupCode;            // e.g. BG-2026-000001
    private String organizationId;
    private LocalDate formationDate;
    private LocalDate registrationDate;
    private String village;
    private String taluka;
    private String district;
    private String state = "Maharashtra";
    private String pinCode;
    
    private String meetingDay = "1st Sunday";
    private String meetingTime = "11:00 AM";
    private String meetingLocation;
    
    /** Canonical recurring member contribution. Legacy share records are read through the compatibility accessors below. */
    private BigDecimal monthlyBachatAmount = BigDecimal.valueOf(5000);
    private int collectionDueDay = 10; // Configurable due date (e.g. 5th, 10th of every month)
    private BigDecimal defaultInterestRate = BigDecimal.valueOf(12.0); // 12% per annum
    private String loanInterestType = "REDUCING_BALANCE"; // REDUCING_BALANCE or FLAT
    private BigDecimal maxLoanAmount = BigDecimal.valueOf(100000);
    private int gracePeriodDays = 5;
    private BigDecimal lateFeeAmount = BigDecimal.valueOf(100);

    // Multi-loan configuration per group (Requirements 26, 27, 28)
    private boolean allowMultipleLoans = false;
    private int maxActiveLoans = 1;
    private BigDecimal maxOutstandingLoanAmount = BigDecimal.valueOf(150000);
    private BigDecimal maxLoanAmountPerMember = BigDecimal.valueOf(100000);

    // Office Bearers
    private String president;
    private String presidentNameMr;
    private String presidentNameHi;
    private String presidentMobile;
    private String presidentEmail;
    private String presidentUsername;
    private String secretary;
    private String treasurer;
    private String contactNumber;
    private String email;
    private String assignedAdminId;
    private String assignedAdminName;

    public String getPresidentNameMr() { return presidentNameMr; }
    public void setPresidentNameMr(String presidentNameMr) { this.presidentNameMr = presidentNameMr; }
    public String getPresidentNameHi() { return presidentNameHi; }
    public void setPresidentNameHi(String presidentNameHi) { this.presidentNameHi = presidentNameHi; }
    public String getPresidentMobile() { return presidentMobile; }
    public void setPresidentMobile(String presidentMobile) { this.presidentMobile = presidentMobile; }
    public String getPresidentEmail() { return presidentEmail; }
    public void setPresidentEmail(String presidentEmail) { this.presidentEmail = presidentEmail; }
    public String getPresidentUsername() { return presidentUsername; }
    public void setPresidentUsername(String presidentUsername) { this.presidentUsername = presidentUsername; }

    // Banking Details
    private String bankName;
    private String branch;
    private String accountNumber;
    private String ifsc;
    private String upiDetails;
    
    private String status = "ACTIVE";
    private int activeMembersCount = 0;
    private BigDecimal totalSavingsBalance = BigDecimal.ZERO;
    private BigDecimal totalLoanOutstanding = BigDecimal.ZERO;
    
    private String createdBy;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Group() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGroupName() { return groupName; }
    public String getName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getGroupNameMr() { return groupNameMr; }
    public void setGroupNameMr(String groupNameMr) { this.groupNameMr = groupNameMr; }
    public String getGroupNameHi() { return groupNameHi; }
    public void setGroupNameHi(String groupNameHi) { this.groupNameHi = groupNameHi; }
    public String getDefaultLanguage() { return defaultLanguage; }
    public void setDefaultLanguage(String defaultLanguage) { this.defaultLanguage = defaultLanguage; }
    public String getAssignedAdminId() { return assignedAdminId; }
    public void setAssignedAdminId(String assignedAdminId) { this.assignedAdminId = assignedAdminId; }
    public String getAssignedAdminName() { return assignedAdminName; }
    public void setAssignedAdminName(String assignedAdminName) { this.assignedAdminName = assignedAdminName; }
    public String getRegistrationId() { return registrationId; }
    public String getRegistrationNumber() { return registrationId != null ? registrationId : (bachatGatRegNumber != null ? bachatGatRegNumber : id); }
    public void setRegistrationId(String registrationId) { this.registrationId = registrationId; }
    public String getBachatGatRegNumber() { return bachatGatRegNumber; }
    public void setBachatGatRegNumber(String bachatGatRegNumber) { this.bachatGatRegNumber = bachatGatRegNumber; }
    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
    public LocalDate getFormationDate() { return formationDate; }
    public void setFormationDate(LocalDate formationDate) { this.formationDate = formationDate; }
    public LocalDate getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(LocalDate registrationDate) { this.registrationDate = registrationDate; }
    public String getVillage() { return village; }
    public void setVillage(String village) { this.village = village; }
    public String getTaluka() { return taluka; }
    public void setTaluka(String taluka) { this.taluka = taluka; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPinCode() { return pinCode; }
    public void setPinCode(String pinCode) { this.pinCode = pinCode; }
    public String getMeetingDay() { return meetingDay; }
    public void setMeetingDay(String meetingDay) { this.meetingDay = meetingDay; }
    public String getMeetingTime() { return meetingTime; }
    public void setMeetingTime(String meetingTime) { this.meetingTime = meetingTime; }
    public String getMeetingLocation() { return meetingLocation; }
    public void setMeetingLocation(String meetingLocation) { this.meetingLocation = meetingLocation; }
    public BigDecimal getMonthlyBachatAmount() { return monthlyBachatAmount; }
    public void setMonthlyBachatAmount(BigDecimal monthlyBachatAmount) { this.monthlyBachatAmount = monthlyBachatAmount; }
    @JsonIgnore
    public BigDecimal getMonthlyShareAmount() { return monthlyBachatAmount; }
    @JsonIgnore
    public void setMonthlyShareAmount(BigDecimal monthlyShareAmount) { this.monthlyBachatAmount = monthlyShareAmount; }
    public BigDecimal getDefaultInterestRate() { return defaultInterestRate; }
    public void setDefaultInterestRate(BigDecimal defaultInterestRate) { this.defaultInterestRate = defaultInterestRate; }
    public String getLoanInterestType() { return loanInterestType; }
    public void setLoanInterestType(String loanInterestType) { this.loanInterestType = loanInterestType; }
    public BigDecimal getMaxLoanAmount() { return maxLoanAmount; }
    public void setMaxLoanAmount(BigDecimal maxLoanAmount) { this.maxLoanAmount = maxLoanAmount; }
    public int getGracePeriodDays() { return gracePeriodDays; }
    public void setGracePeriodDays(int gracePeriodDays) { this.gracePeriodDays = gracePeriodDays; }
    public BigDecimal getLateFeeAmount() { return lateFeeAmount; }
    public void setLateFeeAmount(BigDecimal lateFeeAmount) { this.lateFeeAmount = lateFeeAmount; }
    public String getPresident() { return president; }
    public void setPresident(String president) { this.president = president; }
    public String getSecretary() { return secretary; }
    public void setSecretary(String secretary) { this.secretary = secretary; }
    public String getTreasurer() { return treasurer; }
    public void setTreasurer(String treasurer) { this.treasurer = treasurer; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getIfsc() { return ifsc; }
    public void setIfsc(String ifsc) { this.ifsc = ifsc; }
    public String getUpiDetails() { return upiDetails; }
    public void setUpiDetails(String upiDetails) { this.upiDetails = upiDetails; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getActiveMembersCount() { return activeMembersCount; }
    public void setActiveMembersCount(int activeMembersCount) { this.activeMembersCount = activeMembersCount; }
    public BigDecimal getTotalSavingsBalance() { return totalSavingsBalance; }
    public void setTotalSavingsBalance(BigDecimal totalSavingsBalance) { this.totalSavingsBalance = totalSavingsBalance; }
    public BigDecimal getTotalSavings() { return totalSavingsBalance != null ? totalSavingsBalance : BigDecimal.ZERO; }
    public void setTotalSavings(BigDecimal totalSavings) { this.totalSavingsBalance = totalSavings; }
    public BigDecimal getTotalLoanOutstanding() { return totalLoanOutstanding; }
    public void setTotalLoanOutstanding(BigDecimal totalLoanOutstanding) { this.totalLoanOutstanding = totalLoanOutstanding; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public int getCollectionDueDay() { return collectionDueDay > 0 ? collectionDueDay : 10; }
    public void setCollectionDueDay(int collectionDueDay) { this.collectionDueDay = collectionDueDay > 0 ? collectionDueDay : 10; }
    public boolean isAllowMultipleLoans() { return allowMultipleLoans; }
    public void setAllowMultipleLoans(boolean allowMultipleLoans) { this.allowMultipleLoans = allowMultipleLoans; }
    public int getMaxActiveLoans() { return maxActiveLoans > 0 ? maxActiveLoans : 1; }
    public void setMaxActiveLoans(int maxActiveLoans) { this.maxActiveLoans = maxActiveLoans > 0 ? maxActiveLoans : 1; }
    public BigDecimal getMaxOutstandingLoanAmount() { return maxOutstandingLoanAmount; }
    public void setMaxOutstandingLoanAmount(BigDecimal maxOutstandingLoanAmount) { this.maxOutstandingLoanAmount = maxOutstandingLoanAmount; }
    public BigDecimal getMaxLoanAmountPerMember() { return maxLoanAmountPerMember; }
    public void setMaxLoanAmountPerMember(BigDecimal maxLoanAmountPerMember) { this.maxLoanAmountPerMember = maxLoanAmountPerMember; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
