package com.bachatgat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OfficeBearerDTO {
    @NotBlank(message = "Name is required")
    private String fullName;
    private String fullNameMr;
    private String fullNameHi;

    @NotBlank(message = "Username is required")
    private String username;

    private String mobileNumber;
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    private String confirmPassword;

    @NotBlank(message = "Designation is required (SECRETARY or TREASURER)")
    private String designation; // "SECRETARY" or "TREASURER"

    private boolean active = true;

    public OfficeBearerDTO() {}

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getFullNameMr() { return fullNameMr; }
    public void setFullNameMr(String fullNameMr) { this.fullNameMr = fullNameMr; }

    public String getFullNameHi() { return fullNameHi; }
    public void setFullNameHi(String fullNameHi) { this.fullNameHi = fullNameHi; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
