package com.bachatgat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAlias;

public class CreateAdminRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Temporary password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String temporaryPassword;

    @NotBlank(message = "Full Name is required")
    private String fullName;
    private String fullNameMr;
    private String fullNameHi;

    private String mobileNumber;
    private String email;

    private String groupId;
    private String designation = "PRESIDENT";
    private boolean createNewGroup = false;
    private String groupName;
    private String groupNameMr;
    private String groupNameHi;
    @JsonAlias("monthlyShareAmount")
    private java.math.BigDecimal monthlyBachatAmount;
    private int collectionDueDay = 10;

    public CreateAdminRequest() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getTemporaryPassword() { return temporaryPassword; }
    public void setTemporaryPassword(String temporaryPassword) { this.temporaryPassword = temporaryPassword; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getFullNameMr() { return fullNameMr; }
    public void setFullNameMr(String fullNameMr) { this.fullNameMr = fullNameMr; }
    public String getFullNameHi() { return fullNameHi; }
    public void setFullNameHi(String fullNameHi) { this.fullNameHi = fullNameHi; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public boolean isCreateNewGroup() { return createNewGroup; }
    public void setCreateNewGroup(boolean createNewGroup) { this.createNewGroup = createNewGroup; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getGroupNameMr() { return groupNameMr; }
    public void setGroupNameMr(String groupNameMr) { this.groupNameMr = groupNameMr; }
    public String getGroupNameHi() { return groupNameHi; }
    public void setGroupNameHi(String groupNameHi) { this.groupNameHi = groupNameHi; }
    public java.math.BigDecimal getMonthlyBachatAmount() { return monthlyBachatAmount; }
    public void setMonthlyBachatAmount(java.math.BigDecimal monthlyBachatAmount) { this.monthlyBachatAmount = monthlyBachatAmount; }
    public java.math.BigDecimal getMonthlyShareAmount() { return monthlyBachatAmount; }
    public void setMonthlyShareAmount(java.math.BigDecimal monthlyShareAmount) { this.monthlyBachatAmount = monthlyShareAmount; }
    public int getCollectionDueDay() { return collectionDueDay; }
    public void setCollectionDueDay(int collectionDueDay) { this.collectionDueDay = collectionDueDay; }
}
