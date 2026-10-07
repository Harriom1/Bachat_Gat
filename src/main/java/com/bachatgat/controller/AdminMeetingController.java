package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.model.MeetingRecord;
import com.bachatgat.repository.FirestoreDataService;
import com.bachatgat.security.GroupSecurityService;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/groups/{groupId}/meetings")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminMeetingController {

    private final FirestoreDataService dataService;
    private final AuditService auditService;
    private final GroupSecurityService groupSecurityService;

    public AdminMeetingController(FirestoreDataService dataService,
                                  AuditService auditService,
                                  GroupSecurityService groupSecurityService) {
        this.dataService = dataService;
        this.auditService = auditService;
        this.groupSecurityService = groupSecurityService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MeetingRecord>>> getMeetings(
            @PathVariable String groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        List<MeetingRecord> meetings = dataService.getMeetingsByGroupId(groupId);
        return ResponseEntity.ok(ApiResponse.ok(meetings));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MeetingRecord>> createMeeting(
            @PathVariable String groupId,
            @RequestBody MeetingRecord meeting,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        meeting.setGroupId(groupId);
        String username = principal != null ? principal.getUsername() : "ADMIN";
        String userId = principal != null ? principal.getId() : "admin-001";
        if (meeting.getConductedBy() == null || meeting.getConductedBy().isEmpty()) {
            meeting.setConductedBy(username);
        }
        if (meeting.getMeetingDate() == null) {
            meeting.setMeetingDate(LocalDate.now());
        }
        MeetingRecord saved = dataService.saveMeeting(meeting);

        auditService.log(
                groupId,
                userId,
                username,
                "MEETING_RECORDED",
                "MeetingRecord",
                saved.getId(),
                null,
                "Date: " + saved.getMeetingDate() + ", Attendees: " + (saved.getAttendeeMemberIds() != null ? saved.getAttendeeMemberIds().size() : 0),
                "127.0.0.1"
        );

        return ResponseEntity.ok(ApiResponse.ok("Meeting and attendance recorded successfully", saved));
    }
}
