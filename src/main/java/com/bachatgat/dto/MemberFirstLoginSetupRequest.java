package com.bachatgat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MemberFirstLoginSetupRequest {
    @NotBlank(message = "Group ID is required")
    private String groupId;

    @NotBlank(message = "Member ID, Mobile Number or Username is required")
    private String identifier;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    public MemberFirstLoginSetupRequest() {}

    public MemberFirstLoginSetupRequest(String groupId, String identifier, String password, String confirmPassword) {
        this.groupId = groupId;
        this.identifier = identifier;
        this.password = password;
        this.confirmPassword = confirmPassword;
    }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
