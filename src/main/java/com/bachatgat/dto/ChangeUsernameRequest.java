package com.bachatgat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ChangeUsernameRequest {
    @NotBlank(message = "New username is required")
    @Size(min = 3, max = 50, message = "Username must be 3 to 50 characters")
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Username may contain letters, numbers, dot, underscore and hyphen only")
    private String newUsername;

    @NotBlank(message = "Confirm username is required")
    private String confirmUsername;

    public String getNewUsername() { return newUsername; }
    public void setNewUsername(String newUsername) { this.newUsername = newUsername; }
    public String getConfirmUsername() { return confirmUsername; }
    public void setConfirmUsername(String confirmUsername) { this.confirmUsername = confirmUsername; }
}
