package com.bachatgat.model;

import com.google.cloud.firestore.annotation.DocumentId;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MeetingRecord {
    @DocumentId
    private String id;
    private String groupId;
    private LocalDate meetingDate = LocalDate.now();
    private String meetingTime = "11:00 AM";
    private String location;
    private String agenda;
    private String minutes;
    private String conductedBy;
    private List<String> attendeeMemberIds = new ArrayList<>();
    private List<String> absentMemberIds = new ArrayList<>();
    private LocalDateTime createdAt = LocalDateTime.now();

    public MeetingRecord() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public LocalDate getMeetingDate() { return meetingDate; }
    public void setMeetingDate(LocalDate meetingDate) { this.meetingDate = meetingDate; }
    public String getMeetingTime() { return meetingTime; }
    public void setMeetingTime(String meetingTime) { this.meetingTime = meetingTime; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getAgenda() { return agenda; }
    public void setAgenda(String agenda) { this.agenda = agenda; }
    public String getMinutes() { return minutes; }
    public void setMinutes(String minutes) { this.minutes = minutes; }
    public String getConductedBy() { return conductedBy; }
    public void setConductedBy(String conductedBy) { this.conductedBy = conductedBy; }
    public List<String> getAttendeeMemberIds() { return attendeeMemberIds; }
    public void setAttendeeMemberIds(List<String> attendeeMemberIds) { this.attendeeMemberIds = attendeeMemberIds; }
    public List<String> getAbsentMemberIds() { return absentMemberIds; }
    public void setAbsentMemberIds(List<String> absentMemberIds) { this.absentMemberIds = absentMemberIds; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
