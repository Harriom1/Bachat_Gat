package com.bachatgat.model;

import com.google.cloud.firestore.annotation.DocumentId;
import java.time.LocalDateTime;

public class User {
    @DocumentId
    private String id;
    private String username;
    private String password;
    private String email;
    private String fullName;
    private String mobileNumber;
    private Role role;
    private String groupId;
    private String memberId;
    private boolean active = true;
    private boolean firstLogin = false;
    private String fullNameMr;
    private String fullNameHi;
    private String preferredLanguage = "en";
    private String designation;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public User() {}

    public User(String username, String password, String email, String fullName, Role role, String groupId, String memberId) {
        this.id = java.util.UUID.randomUUID().toString();
        this.username = username;
        this.password = password;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.groupId = groupId;
        this.memberId = memberId;
        this.active = true;
        this.firstLogin = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getFullNameMr() { return fullNameMr; }
    public void setFullNameMr(String fullNameMr) { this.fullNameMr = fullNameMr; }
    public String getFullNameHi() { return fullNameHi; }
    public void setFullNameHi(String fullNameHi) { this.fullNameHi = fullNameHi; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isFirstLogin() { return firstLogin; }
    public void setFirstLogin(boolean firstLogin) { this.firstLogin = firstLogin; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
