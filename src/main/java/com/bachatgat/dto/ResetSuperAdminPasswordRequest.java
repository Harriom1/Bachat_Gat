package com.bachatgat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request used only by the temporary, token-protected recovery endpoint. */
public class ResetSuperAdminPasswordRequest {
    @NotBlank
    @Size(min = 12, max = 128)
    private String newPassword;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
