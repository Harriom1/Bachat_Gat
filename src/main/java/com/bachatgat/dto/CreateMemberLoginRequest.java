package com.bachatgat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateMemberLoginRequest {
    @NotBlank(message = "Username / mobile is required")
    private String username;

    @NotBlank(message = "Temporary password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String temporaryPassword;

    public CreateMemberLoginRequest() {}

    public CreateMemberLoginRequest(String username, String temporaryPassword) {
        this.username = username;
        this.temporaryPassword = temporaryPassword;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getTemporaryPassword() { return temporaryPassword; }
    public void setTemporaryPassword(String temporaryPassword) { this.temporaryPassword = temporaryPassword; }
}
