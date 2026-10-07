package com.bachatgat.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    private String groupId;
    private String memberId;

    public LoginRequest() {}

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public LoginRequest(String groupId, String memberId, String password) {
        this.groupId = groupId;
        this.memberId = memberId;
        this.password = password;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
}
