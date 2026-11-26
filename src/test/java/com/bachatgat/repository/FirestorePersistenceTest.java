package com.bachatgat.repository;

import com.bachatgat.dto.LoanApplicationRequest;
import com.bachatgat.dto.LoanApprovalRequest;
import com.bachatgat.model.AuditLog;
import com.bachatgat.model.Group;
import com.bachatgat.model.Loan;
import com.bachatgat.model.LoanStatus;
import com.bachatgat.model.Member;
import com.bachatgat.model.Role;
import com.bachatgat.model.TransactionType;
import com.bachatgat.service.AuditService;
import com.bachatgat.service.GroupFundService;
import com.bachatgat.service.LoanApplicationService;
import com.bachatgat.service.NotificationService;
import com.bachatgat.service.TransactionService;
import com.bachatgat.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a record survives replacement of the repository instance.
 * This test is enabled only when FIRESTORE_EMULATOR_HOST is explicitly set.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class FirestorePersistenceTest {

    @Autowired
    private Firestore firestore;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FirestoreDataService dataService;

    @Test
    void topUpAndCapacityChangesAreVisibleToAnotherRepositoryInstance() {
        Group group = new Group();
        group.setGroupName("Top-up persistence test");
        dataService.saveGroup(group);
        Member member = new Member();
        member.setMemberId("test-member-" + UUID.randomUUID());
        member.setFullName("Test borrower");
        member.setGroupId(group.getId());
        member.setTotalSavingsBalance(BigDecimal.valueOf(1_000_000));
        dataService.saveMember(member);

        FirestoreDataService other = new FirestoreDataService(firestore, passwordEncoder, objectMapper);
        LoanApplicationService originating = loanApplicationService(dataService);
        LoanApplicationService reviewing = loanApplicationService(other);
        LocalDate start = LoanScheduleCalculatorSupport.today();
        var firstRequest = new LoanApplicationRequest();
        firstRequest.setRequestedAmount(BigDecimal.valueOf(270_000));
        firstRequest.setPreferredDurationMonths(24);
        firstRequest.setPurpose("Housing");
        var app = originating.submitApplication(member.getMemberId(), firstRequest);
        Loan original = reviewing.approveApplication(app.getId(), terms(270_000, start), "test-admin");

        BigDecimal capacity = new BigDecimal("400000");
        dataService.updateMemberLoanCapacity(member, capacity, new AuditLog(group.getId(), "test-admin",
                "test-admin", "MEMBER_LOAN_CAPACITY_UPDATED", "MEMBER", member.getMemberId(), null, "400000", null));
        assertEquals(0, capacity.compareTo((BigDecimal) reviewing.getLoanEligibility(member.getMemberId()).get("maxIndividualLoanLimit")));
        var topUp = new LoanApplicationRequest();
        topUp.setRequestedAmount(BigDecimal.valueOf(100_000));
        topUp.setPreferredDurationMonths(12);
        topUp.setPurpose("Housing");
        topUp.setTopUpLoanId(original.getId());
        var topUpApp = originating.submitApplication(member.getMemberId(), topUp);
        var approval = terms(100_000, start);
        approval.setExpectedOutstandingPrincipal(BigDecimal.valueOf(270_000));
        Loan replacement = reviewing.approveApplication(topUpApp.getId(), approval, "test-admin");

        FirestoreDataService reopened = new FirestoreDataService(firestore, passwordEncoder, objectMapper);
        assertEquals(LoanStatus.CLOSED, reopened.findLoanById(original.getId()).orElseThrow().getStatus());
        assertEquals(0, BigDecimal.valueOf(370_000).compareTo(reopened.findLoanById(replacement.getId()).orElseThrow().getPrincipalAmount()));
        assertEquals(12, reopened.getLoanSchedule(replacement.getId()).size());
        assertEquals(2, reopened.getTransactionsByGroupId(group.getId()).stream()
                .filter(entry -> entry.getType() == TransactionType.LOAN_DISBURSEMENT).count());
        assertEquals(1, reopened.getTransactionsByGroupId(group.getId()).stream()
                .filter(entry -> entry.getType() == TransactionType.ADJUSTMENT).count());
    }

    private LoanApplicationService loanApplicationService(FirestoreDataService repository) {
        return new LoanApplicationService(repository, new TransactionService(repository), new NotificationService(repository),
                new AuditService(repository), new GroupFundService(repository));
    }

    private LoanApprovalRequest terms(long amount, LocalDate start) {
        var request = new LoanApprovalRequest();
        request.setApprovedAmount(BigDecimal.valueOf(amount));
        request.setDurationMonths(12);
        request.setStartDate(start);
        request.setFirstEmiDueDate(start.plusMonths(1));
        request.setEmiLatePenalty(BigDecimal.valueOf(100));
        return request;
    }

    @Test
    void savedUserSurvivesRepositoryRestart() throws Exception {
        String id = "test-restart-" + UUID.randomUUID();
        User saved = new User("restart-user-" + UUID.randomUUID(),
                passwordEncoder.encode("test-password"),
                "restart@example.test", "Restart Test User", Role.MEMBER, "test-group", "test-member");
        saved.setId(id);

        dataService.saveUser(saved);

        FirestoreDataService restarted = new FirestoreDataService(
                firestore, passwordEncoder, objectMapper, false);
        restarted.init();

        assertTrue(restarted.findUsersByIdentifier(saved.getUsername()).stream()
                .anyMatch(user -> id.equals(user.getId())));
        assertTrue(restarted.findUserById(id).isPresent());
        assertEquals(saved.getUsername(), restarted.findUserById(id).orElseThrow().getUsername());

        firestore.collection("users").document(id).delete().get();
    }
}
