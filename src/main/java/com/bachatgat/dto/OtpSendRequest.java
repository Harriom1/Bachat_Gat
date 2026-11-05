package com.bachatgat.dto;

import jakarta.validation.constraints.NotBlank;

public class OtpSendRequest {
    @NotBlank private String sessionId;
    @NotBlank private String target;
    private String challengeId;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getChallengeId() { return challengeId; }
    public void setChallengeId(String challengeId) { this.challengeId = challengeId; }
}
