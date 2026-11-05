package com.bachatgat.dto;

import jakarta.validation.constraints.NotBlank;

public class OtpVerifyRequest {
    @NotBlank private String challengeId;
    @NotBlank private String sessionId;
    @NotBlank private String target;
    @NotBlank private String code;

    public String getChallengeId() { return challengeId; }
    public void setChallengeId(String challengeId) { this.challengeId = challengeId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
