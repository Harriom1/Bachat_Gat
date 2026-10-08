package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.dto.DashboardUserDTO;
import com.bachatgat.dto.LoanApplicationRequest;
import com.bachatgat.exception.ForbiddenException;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.*;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/me")
public class CurrentUserController {

    private final MemberService memberService;
    private final DashboardService dashboardService;
    private final CollectionService collectionService;
    private final LoanService loanService;
    private final LoanApplicationService loanApplicationService;
    private final TransactionService transactionService;
    private final DocumentService documentService;
    private final NotificationService notificationService;
    private final ReportService reportService;
    private final GroupService groupService;
    private final MasterDataService masterDataService;

    public CurrentUserController(MemberService memberService, DashboardService dashboardService,
                                 CollectionService collectionService, LoanService loanService,
                                 LoanApplicationService loanApplicationService, TransactionService transactionService,
                                 DocumentService documentService, NotificationService notificationService,
                                 ReportService reportService, GroupService groupService,
                                 MasterDataService masterDataService) {
        this.memberService = memberService;
        this.dashboardService = dashboardService;
        this.collectionService = collectionService;
        this.loanService = loanService;
        this.loanApplicationService = loanApplicationService;
        this.transactionService = transactionService;
        this.documentService = documentService;
        this.notificationService = notificationService;
        this.reportService = reportService;
        this.groupService = groupService;
        this.masterDataService = masterDataService;
    }

    private String requireMemberId(UserPrincipal principal) {
        if (principal == null || principal.getMemberId() == null || principal.getMemberId().isEmpty()) {
            throw new ForbiddenException("Logged-in user is not associated with an active member profile.");
        }
        return principal.getMemberId();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Member>> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        String memberId = requireMemberId(principal);
        Member member = memberService.getMemberByMemberId(memberId);
        return ResponseEntity.ok(ApiResponse.ok(member));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardUserDTO>> getDashboard(@AuthenticationPrincipal UserPrincipal principal) {
        String memberId = requireMemberId(principal);
        DashboardUserDTO dashboard = dashboardService.getUserDashboard(memberId);
        return ResponseEntity.ok(ApiResponse.ok(dashboard));
    }

    @GetMapping("/savings")
    public ResponseEntity<ApiResponse<List<CollectionRecord>>> getMySavings(@AuthenticationPrincipal UserPrincipal principal) {
        String memberId = requireMemberId(principal);
        List<CollectionRecord> collections = collectionService.getMemberCollections(memberId);
        return ResponseEntity.ok(ApiResponse.ok(collections));
    }

    @GetMapping("/collections")
    public ResponseEntity<ApiResponse<List<CollectionRecord>>> getMyCollections(@AuthenticationPrincipal UserPrincipal principal) {
        return getMySavings(principal);
    }

    /** Read-only group collection transparency for every enrolled member. */
    @GetMapping("/group-collections")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGroupCollections(
            @AuthenticationPrincipal UserPrincipal principal) {
        requireMemberId(principal);
        String groupId = principal.getGroupId();
        if (groupId == null || groupId.isBlank()) {
            throw new ResourceNotFoundException("User is not assigned to any Bachat Gat group.");
        }
        int month = java.time.LocalDate.now().getMonthValue();
        int year = java.time.LocalDate.now().getYear();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("month", month);
        result.put("year", year);
        result.put("members", memberService.getMembersByGroupId(groupId).stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE).toList());
        result.put("collections", collectionService.getGroupCollections(groupId));
        result.put("pending", collectionService.getPendingSavingsMembers(groupId, month, year));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/loans")
    public ResponseEntity<ApiResponse<List<Loan>>> getMyLoans(@AuthenticationPrincipal UserPrincipal principal) {
        String memberId = requireMemberId(principal);
        List<Loan> loans = loanService.getLoansByMemberId(memberId);
        return ResponseEntity.ok(ApiResponse.ok(loans));
    }

    /**
     * Community Credit Transparency: Allows all members of the group to see who has taken a loan,
     * the sanctioned amount, and their required monthly EMI / installment (read-only transparency).
     */
    @GetMapping("/group-loans")
    public ResponseEntity<ApiResponse<List<Loan>>> getMyGroupLoans(@AuthenticationPrincipal UserPrincipal principal) {
        String groupId = principal.getGroupId();
        if (groupId == null || groupId.isBlank()) {
            throw new ResourceNotFoundException("User is not assigned to any Bachat Gat group.");
        }
        List<Loan> groupLoans = loanService.getLoansByGroupId(groupId);
        return ResponseEntity.ok(ApiResponse.ok(groupLoans));
    }

    @GetMapping("/loans/{loanId}/schedule")
    public ResponseEntity<ApiResponse<List<LoanRepaymentSchedule>>> getMyLoanSchedule(
            @PathVariable String loanId,
            @AuthenticationPrincipal UserPrincipal principal) {
        String memberId = requireMemberId(principal);
        Loan loan = loanService.getLoanById(loanId);
        if (!loan.getMemberId().equalsIgnoreCase(memberId)) {
            throw new ForbiddenException("You are not authorized to view another member's loan schedule.");
        }
        List<LoanRepaymentSchedule> schedule = loanService.getLoanSchedule(loanId);
        List<LoanRepaymentSchedule> visibleSchedule = new ArrayList<>(2);
        for (LoanRepaymentSchedule installment : schedule) {
            if (installment.getStatus() != RepaymentStatus.PAID) {
                visibleSchedule.add(installment);
                if (visibleSchedule.size() == 2) break;
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(visibleSchedule));
    }

    @PostMapping("/loan-applications")
    public ResponseEntity<ApiResponse<LoanApplication>> submitLoanApplication(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LoanApplicationRequest request) {
        String memberId = requireMemberId(principal);
        LoanApplication app = loanApplicationService.submitApplication(memberId, request);
        return ResponseEntity.ok(ApiResponse.ok("Loan application submitted successfully and sent for admin review", app));
    }

    @GetMapping("/loan-applications")
    public ResponseEntity<ApiResponse<List<LoanApplication>>> getMyLoanApplications(@AuthenticationPrincipal UserPrincipal principal) {
        String memberId = requireMemberId(principal);
        List<LoanApplication> apps = loanApplicationService.getApplicationsByMemberId(memberId);
        return ResponseEntity.ok(ApiResponse.ok(apps));
    }

    @GetMapping("/loan-eligibility")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyLoanEligibility(@AuthenticationPrincipal UserPrincipal principal) {
        String memberId = requireMemberId(principal);
        Map<String, Object> eligibility = loanApplicationService.getLoanEligibility(memberId);
        return ResponseEntity.ok(ApiResponse.ok(eligibility));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<Transaction>>> getMyTransactions(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String type,
            @AuthenticationPrincipal UserPrincipal principal) {
        String memberId = requireMemberId(principal);
        List<Transaction> transactions = transactionService.getFilteredTransactions(principal.getGroupId(), memberId, month, year, type);
        return ResponseEntity.ok(ApiResponse.ok(transactions));
    }

    /**
     * Tab 9: My Group Information (Section 29)
     * Provides members with full transparency into their group's executive committee,
     * meeting schedule, bylaws, bank details, and active rules.
     */
    @GetMapping("/group-info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyGroupInfo(@AuthenticationPrincipal UserPrincipal principal) {
        String groupId = principal.getGroupId();
        if (groupId == null || groupId.isBlank()) {
            throw new ResourceNotFoundException("User is not assigned to any Bachat Gat group.");
        }
        Group group = groupService.getGroupById(groupId);
        GroupMasterData masterData = masterDataService.getLatestMasterData(groupId);
        long activeCount = memberService.getMembersByGroupId(groupId).stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .count();

        Map<String, Object> info = new java.util.LinkedHashMap<>();
        info.put("group", group);
        info.put("activeMembersCount", activeCount);
        info.put("rules", masterData);
        info.put("monthlyBachatAmount", masterData.getMonthlyBachatAmount());
        info.put("loanInterestRate", masterData.getLoanInterestRate());
        info.put("loanInterestType", masterData.getLoanInterestType());
        info.put("maxLoanAmount", masterData.getMaxLoanAmount());
        info.put("maxLoanAmountPerMember", masterData.getMaxLoanAmountPerMember());
        info.put("latePaymentFee", masterData.getLatePaymentFee());
        info.put("collectionDueDay", masterData.getCollectionDueDay());
        int dueDay = (group.getCollectionDueDay() > 0) ? group.getCollectionDueDay() : (masterData != null ? masterData.getCollectionDueDay() : 10);
        info.put("meetingSchedule", "Monthly Bachat Due Date: " + dueDay + "th of each month");

        return ResponseEntity.ok(ApiResponse.ok(info));
    }

    /**
     * Tab 8: Group Rules (Section 28)
     */
    @GetMapping("/group-rules")
    public ResponseEntity<ApiResponse<GroupMasterData>> getMyGroupRules(@AuthenticationPrincipal UserPrincipal principal) {
        String groupId = principal.getGroupId();
        if (groupId == null || groupId.isBlank()) {
            throw new ResourceNotFoundException("User is not assigned to any Bachat Gat group.");
        }
        GroupMasterData masterData = masterDataService.getLatestMasterData(groupId);
        return ResponseEntity.ok(ApiResponse.ok(masterData));
    }

    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<List<DocumentRecord>>> getMyDocuments(@AuthenticationPrincipal UserPrincipal principal) {
        String memberId = requireMemberId(principal);
        List<DocumentRecord> docs = documentService.getMemberDocuments(memberId);
        return ResponseEntity.ok(ApiResponse.ok(docs));
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<SystemNotification>>> getMyNotifications(@AuthenticationPrincipal UserPrincipal principal) {
        List<SystemNotification> notifs = notificationService.getNotifications(principal.getId(), principal.getRole().name(), principal.getGroupId());
        return ResponseEntity.ok(ApiResponse.ok(notifs));
    }

    @GetMapping("/statement")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyStatement(@AuthenticationPrincipal UserPrincipal principal) {
        String memberId = requireMemberId(principal);
        Map<String, Object> statement = reportService.getMemberStatement(memberId);
        return ResponseEntity.ok(ApiResponse.ok(statement));
    }

    @GetMapping("/report/monthly")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyMonthlyReport(
            @RequestParam int month, @RequestParam int year,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireMemberId(principal);
        if (principal.getGroupId() == null || principal.getGroupId().isBlank()) {
            throw new ForbiddenException("Logged-in user is not assigned to a group.");
        }
        return ResponseEntity.ok(ApiResponse.ok(reportService.getMonthlyReport(principal.getGroupId(), month, year)));
    }

    @PostMapping("/pay")
    public ResponseEntity<ApiResponse<CollectionRecord>> payCurrentDue(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody com.bachatgat.dto.CollectionPaymentRequest request) {
        String memberId = requireMemberId(principal);
        String groupId = principal.getGroupId();
        request.setMemberId(memberId);
        if (request.getPaymentMethod() == null || request.getPaymentMethod().isBlank()) {
            request.setPaymentMethod("UPI");
        }
        if (request.getReferenceNumber() == null || request.getReferenceNumber().isBlank()) {
            request.setReferenceNumber("UPI-TXN-" + System.currentTimeMillis());
        }
        // A client-side request is only an attempt. Balances are updated only
        // after the payment gateway verification/webhook succeeds.
        request.setPaymentStatus("PENDING");
        CollectionRecord record = collectionService.recordPayment(groupId, request, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Payment request received. It will be marked PAID after gateway verification.", record));
    }
}
