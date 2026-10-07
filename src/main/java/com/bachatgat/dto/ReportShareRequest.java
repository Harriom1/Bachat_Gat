package com.bachatgat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ReportShareRequest {
    @NotBlank(message = "Recipient is required")
    private String recipient; // Mobile number for WhatsApp, or Email address

    @NotBlank(message = "Channel is required")
    private String channel; // WHATSAPP or EMAIL

    @NotNull(message = "Report type is required")
    private String reportType; // MONTHLY, YEARLY, OUTSTANDING, MEMBER_STATEMENT

    private String groupId;
    private String memberId;
    private Integer month;
    private Integer year;
    private String customMessage;

    public ReportShareRequest() {}

    // Getters and Setters
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public Integer getMonth() { return month; }
    public void setMonth(Integer month) { this.month = month; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public String getCustomMessage() { return customMessage; }
    public void setCustomMessage(String customMessage) { this.customMessage = customMessage; }
}
