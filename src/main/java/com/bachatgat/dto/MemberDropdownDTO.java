package com.bachatgat.dto;

public class MemberDropdownDTO {
    private String id;
    private String memberId;
    private String registrationId;
    private String fullName;
    private String fullNameMr;
    private String fullNameHi;
    private String mobileNumber;
    private boolean hasLoginAccount;
    private String role;
    private String designation;
    private String username;

    public MemberDropdownDTO() {}

    public MemberDropdownDTO(String id, String memberId, String registrationId, String fullName, 
                             String fullNameMr, String fullNameHi, String mobileNumber, boolean hasLoginAccount) {
        this.id = id;
        this.memberId = memberId;
        this.registrationId = registrationId;
        this.fullName = fullName;
        this.fullNameMr = fullNameMr;
        this.fullNameHi = fullNameHi;
        this.mobileNumber = mobileNumber;
        this.hasLoginAccount = hasLoginAccount;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public String getRegistrationId() { return registrationId; }
    public void setRegistrationId(String registrationId) { this.registrationId = registrationId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getFullNameMr() { return fullNameMr; }
    public void setFullNameMr(String fullNameMr) { this.fullNameMr = fullNameMr; }
    public String getFullNameHi() { return fullNameHi; }
    public void setFullNameHi(String fullNameHi) { this.fullNameHi = fullNameHi; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public boolean isHasLoginAccount() { return hasLoginAccount; }
    public void setHasLoginAccount(boolean hasLoginAccount) { this.hasLoginAccount = hasLoginAccount; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
