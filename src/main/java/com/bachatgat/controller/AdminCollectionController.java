package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.dto.CollectionGenerateMonthlyRequest;
import com.bachatgat.dto.CollectionPaymentRequest;
import com.bachatgat.model.CollectionRecord;
import com.bachatgat.security.GroupSecurityService;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.CollectionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/groups/{groupId}/collections")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminCollectionController {

    private final CollectionService collectionService;
    private final GroupSecurityService groupSecurityService;

    public AdminCollectionController(CollectionService collectionService, GroupSecurityService groupSecurityService) {
        this.collectionService = collectionService;
        this.groupSecurityService = groupSecurityService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CollectionRecord>>> getGroupCollections(
            @PathVariable String groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        List<CollectionRecord> records = collectionService.getGroupCollections(groupId);
        return ResponseEntity.ok(ApiResponse.ok(records));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> getPendingSavingsMembers(
            @PathVariable String groupId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        int m = (month != null && month > 0) ? month : java.time.LocalDate.now().getMonthValue();
        int y = (year != null && year > 0) ? year : java.time.LocalDate.now().getYear();
        List<java.util.Map<String, Object>> pending = collectionService.getPendingSavingsMembers(groupId, m, y);
        return ResponseEntity.ok(ApiResponse.ok(pending));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CollectionRecord>> recordPayment(
            @PathVariable String groupId,
            @Valid @RequestBody CollectionPaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        CollectionRecord record = collectionService.recordPayment(groupId, request, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Savings payment recorded successfully", record));
    }

    @PostMapping("/monthly")
    public ResponseEntity<ApiResponse<List<CollectionRecord>>> generateMonthlyCollections(
            @PathVariable String groupId,
            @Valid @RequestBody CollectionGenerateMonthlyRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        List<CollectionRecord> generated = collectionService.generateMonthlyCollections(
                groupId, request.getMonth(), request.getYear(), principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Generated " + generated.size() + " monthly collection records", generated));
    }
}

