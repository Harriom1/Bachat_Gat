package com.bachatgat.dto;

import jakarta.validation.constraints.NotBlank;

public class FirebaseMobileVerifyRequest {
    @NotBlank private String sessionId;
    @NotBlank private String phoneNumber;
    @NotBlank private String idToken;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }
}
