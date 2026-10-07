package com.bachatgat.dto;

import com.bachatgat.model.Role;

public class LoginResponse {
    private String token;
    private String type = "Bearer";
    private String userId;
    private String username;
    private String fullName;
    private String fullNameMr;
    private String fullNameHi;
    private Role role;
    private String groupId;
    private String groupCode;
    private String groupName;
    private String groupNameMr;
    private String groupNameHi;
    private String memberId;
    private String preferredLanguage;
    private String designation;
    private boolean firstLogin = false;

    public LoginResponse() {}

    public LoginResponse(String token, String userId, String username, String fullName, Role role, String groupId, String groupName, String memberId, String preferredLanguage) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.groupId = groupId;
        this.groupName = groupName;
        this.memberId = memberId;
        this.preferredLanguage = preferredLanguage;
    }

    // Getters and Setters
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getFullNameMr() { return fullNameMr; }
    public void setFullNameMr(String fullNameMr) { this.fullNameMr = fullNameMr; }
    public String getFullNameHi() { return fullNameHi; }
    public void setFullNameHi(String fullNameHi) { this.fullNameHi = fullNameHi; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getGroupNameMr() { return groupNameMr; }
    public void setGroupNameMr(String groupNameMr) { this.groupNameMr = groupNameMr; }
    public String getGroupNameHi() { return groupNameHi; }
    public void setGroupNameHi(String groupNameHi) { this.groupNameHi = groupNameHi; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    public boolean isFirstLogin() { return firstLogin; }
    public void setFirstLogin(boolean firstLogin) { this.firstLogin = firstLogin; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
}
