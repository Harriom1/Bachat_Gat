package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.model.DocumentRecord;
import com.bachatgat.model.Member;
import com.bachatgat.security.GroupSecurityService;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.DocumentService;
import com.bachatgat.service.MemberService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/documents")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class DocumentController {

    private final DocumentService documentService;
    private final GroupSecurityService groupSecurityService;
    private final MemberService memberService;

    public DocumentController(DocumentService documentService,
                              GroupSecurityService groupSecurityService,
                              MemberService memberService) {
        this.documentService = documentService;
        this.groupSecurityService = groupSecurityService;
        this.memberService = memberService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentRecord>> uploadDocument(
            @RequestParam String groupId,
            @RequestParam(required = false) String memberId,
            @RequestParam(required = false) String loanId,
            @RequestParam String documentType,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) throws IOException {
        groupSecurityService.validateGroupAccess(principal, groupId);
        DocumentRecord doc = documentService.uploadDocument(groupId, memberId, loanId, documentType, file, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Document uploaded successfully", doc));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<ApiResponse<List<DocumentRecord>>> getGroupDocuments(
            @PathVariable String groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        List<DocumentRecord> docs = documentService.getGroupDocuments(groupId);
        return ResponseEntity.ok(ApiResponse.ok(docs));
    }

    @GetMapping("/member/{memberId}")
    public ResponseEntity<ApiResponse<List<DocumentRecord>>> getMemberDocuments(
            @PathVariable String memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Member member = memberService.getMemberByMemberId(memberId);
        groupSecurityService.validateGroupAccess(principal, member.getGroupId());
        List<DocumentRecord> docs = documentService.getMemberDocuments(memberId);
        return ResponseEntity.ok(ApiResponse.ok(docs));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentRecord>> getDocumentById(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        DocumentRecord doc = documentService.getDocumentById(id);
        groupSecurityService.validateGroupAccess(principal, doc.getGroupId());
        return ResponseEntity.ok(ApiResponse.ok(doc));
    }
}
