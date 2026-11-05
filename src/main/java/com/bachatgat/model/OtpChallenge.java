package com.bachatgat.model;

import java.time.Instant;

/** Durable, non-sensitive state for a verification challenge. */
public class OtpChallenge {
    private String id;
    private String sessionId;
    private String channel;
    private String targetHash;
    private String otpHash;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant resendAllowedAt;
    private int attempts;
    private int sendCount;
    private boolean verified;
    private boolean consumed;
    private boolean usedForCreation;

    public OtpChallenge() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getTargetHash() { return targetHash; }
    public void setTargetHash(String targetHash) { this.targetHash = targetHash; }
    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getResendAllowedAt() { return resendAllowedAt; }
    public void setResendAllowedAt(Instant resendAllowedAt) { this.resendAllowedAt = resendAllowedAt; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public int getSendCount() { return sendCount; }
    public void setSendCount(int sendCount) { this.sendCount = sendCount; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public boolean isConsumed() { return consumed; }
    public void setConsumed(boolean consumed) { this.consumed = consumed; }
    public boolean isUsedForCreation() { return usedForCreation; }
    public void setUsedForCreation(boolean usedForCreation) { this.usedForCreation = usedForCreation; }
}
