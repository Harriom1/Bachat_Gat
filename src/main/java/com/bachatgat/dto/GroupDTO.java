package com.bachatgat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.time.LocalDate;

public class GroupDTO {
    private String id;
    
    @NotBlank(message = "Group Name is required")
    private String groupName;
    private String groupNameMr;
    private String groupNameHi;
    private String defaultLanguage = "en";
    
    private String registrationId; // auto-generated if blank e.g. GR-2026-000001
    private String bachatGatRegNumber;
    private String groupCode;      // auto-generated if blank e.g. BG-2026-000001
    private String organizationId;
    private LocalDate formationDate;
    private LocalDate registrationDate;
    private String village;
    private String taluka;
    private String district;
    private String state;
    private String pinCode;
    private String meetingDay;
    private String meetingTime;
    private String meetingLocation;
    
    @NotNull(message = "Monthly Bachat is required")
    @Positive(message = "Monthly Bachat must be positive")
    @JsonAlias("monthlyShareAmount")
    private BigDecimal monthlyBachatAmount;
    private Integer collectionDueDay; // Configurable due date (e.g. 5, 10)
    
    private BigDecimal defaultInterestRate;
    private String loanInterestType;
    private BigDecimal maxLoanAmount;
    private int gracePeriodDays;
    private BigDecimal lateFeeAmount;

    // Multi-loan configuration per group (Requirements 26, 27, 28)
    private Boolean allowMultipleLoans;
    private Integer maxActiveLoans;
    private BigDecimal maxOutstandingLoanAmount;
    private BigDecimal maxLoanAmountPerMember;

    private String president;
    private String presidentNameMr;
    private String presidentNameHi;
    private String presidentMobile;
    private String presidentEmail;
    private String presidentUsername;
    private String presidentPassword;
    private String confirmPresidentPassword;
    private String secretary;
    private String treasurer;
    private String contactNumber;
    private String email;
    private String assignedAdminId;
    private String assignedAdminName;
    private String temporaryAdminPassword;

    private String bankName;
    private String branch;
    private String accountNumber;
    private String ifsc;
    private String upiDetails;
    private String status;

    public GroupDTO() {}

    // Getters and Setters
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
    public String getPresidentPassword() { return presidentPassword; }
    public void setPresidentPassword(String presidentPassword) { this.presidentPassword = presidentPassword; }
    public String getConfirmPresidentPassword() { return confirmPresidentPassword; }
    public void setConfirmPresidentPassword(String confirmPresidentPassword) { this.confirmPresidentPassword = confirmPresidentPassword; }
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
    public String getTemporaryAdminPassword() { return temporaryAdminPassword; }
    public void setTemporaryAdminPassword(String temporaryAdminPassword) { this.temporaryAdminPassword = temporaryAdminPassword; }
    public String getRegistrationId() { return registrationId; }
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
    public BigDecimal getMonthlyShareAmount() { return monthlyBachatAmount; }
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

    public Integer getCollectionDueDay() { return collectionDueDay != null && collectionDueDay > 0 ? collectionDueDay : 10; }
    public void setCollectionDueDay(Integer collectionDueDay) { this.collectionDueDay = collectionDueDay; }
    public Boolean getAllowMultipleLoans() { return allowMultipleLoans != null ? allowMultipleLoans : false; }
    public Boolean isAllowMultipleLoans() { return Boolean.TRUE.equals(allowMultipleLoans); }
    public void setAllowMultipleLoans(Boolean allowMultipleLoans) { this.allowMultipleLoans = allowMultipleLoans; }
    public Integer getMaxActiveLoans() { return maxActiveLoans != null && maxActiveLoans > 0 ? maxActiveLoans : 1; }
    public void setMaxActiveLoans(Integer maxActiveLoans) { this.maxActiveLoans = maxActiveLoans; }
    public BigDecimal getMaxOutstandingLoanAmount() { return maxOutstandingLoanAmount; }
    public void setMaxOutstandingLoanAmount(BigDecimal maxOutstandingLoanAmount) { this.maxOutstandingLoanAmount = maxOutstandingLoanAmount; }
    public BigDecimal getMaxLoanAmountPerMember() { return maxLoanAmountPerMember; }
    public void setMaxLoanAmountPerMember(BigDecimal maxLoanAmountPerMember) { this.maxLoanAmountPerMember = maxLoanAmountPerMember; }
}
