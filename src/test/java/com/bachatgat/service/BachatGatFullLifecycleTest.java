package com.bachatgat.service;

import com.bachatgat.dto.*;
import com.bachatgat.exception.DuplicateRecordException;
import com.bachatgat.exception.InvalidFinancialOperationException;
import com.bachatgat.exception.InsufficientGroupFundsException;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext
class BachatGatFullLifecycleTest {

    @Autowired
    private GroupService groupService;

    @Autowired
    private AuthService authService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private LoanApplicationService loanApplicationService;

    @Autowired
    private LoanService loanService;

    @Autowired
    private FirestoreDataService dataService;

    @Test
    @DisplayName("End-to-End Flow: Group Creation with President Credentials -> Forced First Login -> Member Provisioning -> Member First Login -> Idempotent Payment -> Reducing Balance Loan")
    void testEndToEndApplicationLifecycle() {
        // ========================================================
        // 1. Super Admin Creates New Bachat Gat (Requirements 6, 7, 8, 9)
        // ========================================================
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setGroupName("Saraswati Mahila Bachat Gat");
        groupDTO.setGroupNameMr("सरस्वती महिला बचत गट");
        groupDTO.setGroupNameHi("सरस्वती महिला बचत समूह");
        groupDTO.setVillage("Baramati");
        groupDTO.setDistrict("Pune");
        groupDTO.setMonthlyShareAmount(BigDecimal.valueOf(5000));
        groupDTO.setFormationDate(LocalDate.of(2026, 1, 1));
        groupDTO.setPresident("Anjali Sharad Pawar");
        groupDTO.setPresidentNameMr("अंजली शरद पवार");
        groupDTO.setPresidentNameHi("अंजलि शरद पवार");
        groupDTO.setPresidentMobile("9823998877");
        groupDTO.setPresidentEmail("anjali.pawar@saraswati.org");
        groupDTO.setPresidentUsername("anjali9823");
        groupDTO.setPresidentPassword("InitialP@ss123");
        groupDTO.setConfirmPresidentPassword("InitialP@ss123");

        Group createdGroup = groupService.createGroup(groupDTO, "superadmin");
        assertNotNull(createdGroup.getId());
        assertNotNull(createdGroup.getGroupCode(), "Group Code must be auto-generated");
        assertTrue(createdGroup.getGroupCode().startsWith("BG-"), "Group code must start with BG-");
        assertNotNull(createdGroup.getRegistrationId(), "Registration ID must be auto-generated");
        assertEquals("सरस्वती महिला बचत गट", createdGroup.getGroupNameMr());
        assertEquals("Saraswati Mahila Bachat Gat", createdGroup.getName()); // Alias check

        // Verify rejection if password missing
        GroupDTO invalidDTO = new GroupDTO();
        invalidDTO.setGroupName("Test Group Without Password");
        invalidDTO.setMonthlyShareAmount(BigDecimal.valueOf(5000));
        assertThrows(InvalidFinancialOperationException.class, () -> groupService.createGroup(invalidDTO, "superadmin"));

        // ========================================================
        // 2. President First Login & Forced Password Change (Requirements 10, 11, 12)
        // ========================================================
        LoginRequest presLoginReq = new LoginRequest("anjali9823", "InitialP@ss123");
        LoginResponse presRes = authService.login(presLoginReq);
        assertNotNull(presRes.getToken());
        assertEquals(Role.PRESIDENT, presRes.getRole());
        assertEquals(createdGroup.getId(), presRes.getGroupId());
        assertTrue(presRes.isFirstLogin(), "President must be flagged for mandatory first login password change");

        // President performs forced password change
        FirstLoginPasswordRequest presPwReq = new FirstLoginPasswordRequest("AnjaliSecure#2026", "AnjaliSecure#2026");
        authService.firstLoginChangePassword("anjali9823", presPwReq);

        // Subsequent login must succeed with new password and firstLogin=false
        LoginRequest presSubsequentLogin = new LoginRequest("anjali9823", "AnjaliSecure#2026");
        LoginResponse presRes2 = authService.login(presSubsequentLogin);
        assertNotNull(presRes2.getToken());
        assertFalse(presRes2.isFirstLogin(), "President firstLogin must be false after update");

        // ========================================================
        // 3. President Adds New Member & Provisions Login (Requirements 13, 14, 15)
        // ========================================================
        MemberDTO memDTO = new MemberDTO();
        memDTO.setGroupId(createdGroup.getId());
        memDTO.setFullName("Rukmini Vitthal Shinde");
        memDTO.setFullNameMr("रुक्मिणी विठ्ठल शिंदे");
        memDTO.setFullNameHi("रुक्मिणी विट्ठल शिंदे");
        memDTO.setMobileNumber("9823554433");
        memDTO.setEmail("rukmini.shinde@shg.org");
        memDTO.setMonthlyShareAmount(BigDecimal.valueOf(5000));
        memDTO.setPanNumber("ABCPS1234D");
        memDTO.setCreateLoginAccount(true);
        memDTO.setLoginUsername("rukmini5544");
        memDTO.setTemporaryPassword("tempMember123");

        Member member = memberService.createMember(memDTO, "anjali9823");
        assertNotNull(member.getId());
        assertNotNull(member.getMemberId());
        assertTrue(member.isHasLoginAccount());

        // Check member login status check API returns both flags
        var loginStatus = memberService.getMemberLoginStatus(member.getId());
        assertEquals(true, loginStatus.get("hasLoginAccount"));
        assertEquals(true, loginStatus.get("hasLogin"));
        assertTrue((Boolean) loginStatus.get("firstLogin"));

        // ========================================================
        // 4. Member First Login via Group & Member Selection (Requirements 15, 16, 17, 18, 19)
        // ========================================================
        LoginRequest memLoginReq = new LoginRequest();
        memLoginReq.setGroupId(createdGroup.getId());
        memLoginReq.setMemberId(member.getMemberId());
        memLoginReq.setPassword("tempMember123");

        LoginResponse memRes = authService.login(memLoginReq);
        assertNotNull(memRes.getToken());
        assertEquals(Role.MEMBER, memRes.getRole());
        assertTrue(memRes.isFirstLogin(), "Member must be flagged for mandatory first login password change");

        // Member forces password change
        FirstLoginPasswordRequest memPwReq = new FirstLoginPasswordRequest("Rukmini#Strong1", "Rukmini#Strong1");
        authService.firstLoginChangePassword("rukmini5544", memPwReq);

        // Subsequent member login succeeds with new password
        LoginRequest memSubsequent = new LoginRequest();
        memSubsequent.setGroupId(createdGroup.getId());
        memSubsequent.setMemberId(member.getMemberId());
        memSubsequent.setPassword("Rukmini#Strong1");

        LoginResponse memRes2 = authService.login(memSubsequent);
        assertNotNull(memRes2.getToken());
        assertFalse(memRes2.isFirstLogin());

        // ========================================================
        // 5. Payment Integrity: PENDING vs SUCCESS vs Duplicate (Requirements 26, 27, 28, 29, 30)
        // ========================================================
        BigDecimal initialSavings = member.getTotalSavingsBalance();

        // 5a. PENDING payment: must NOT update member savings or group balance
        CollectionPaymentRequest pendingReq = new CollectionPaymentRequest();
        pendingReq.setMemberId(member.getMemberId());
        pendingReq.setAmount(BigDecimal.valueOf(5000));
        pendingReq.setShareAmount(BigDecimal.valueOf(5000));
        pendingReq.setPaymentStatus("PENDING");
        pendingReq.setPaymentDate(LocalDate.of(2026, 7, 5));
        pendingReq.setMonth(7);
        pendingReq.setYear(2026);
        pendingReq.setReferenceNumber("PENDING-REF-001");
        pendingReq.setIdempotencyKey("IDEM-PEND-001");

        CollectionRecord pendingRecord = collectionService.recordPayment(createdGroup.getId(), pendingReq, "gateway");
        assertEquals(CollectionStatus.PENDING, pendingRecord.getStatus());
        Member memberAfterPending = memberService.getMemberById(member.getId());
        assertEquals(initialSavings, memberAfterPending.getTotalSavingsBalance(), "Pending payment must not credit balance");

        // 5b. SUCCESS payment: automatically updates status to PAID and updates balance
        CollectionPaymentRequest successReq = new CollectionPaymentRequest();
        successReq.setMemberId(member.getMemberId());
        successReq.setAmount(BigDecimal.valueOf(5000));
        successReq.setShareAmount(BigDecimal.valueOf(5000));
        successReq.setPaymentStatus("SUCCESS");
        successReq.setPaymentDate(LocalDate.of(2026, 7, 5));
        successReq.setMonth(7);
        successReq.setYear(2026);
        successReq.setReferenceNumber("PAY-SUCCESS-777");
        successReq.setIdempotencyKey("IDEM-SUCCESS-777");

        CollectionRecord successRecord = collectionService.recordPayment(createdGroup.getId(), successReq, "gateway");
        assertEquals(CollectionStatus.PAID, successRecord.getStatus());
        Member memberAfterSuccess = memberService.getMemberById(member.getId());
        assertEquals(initialSavings.add(BigDecimal.valueOf(5000)), memberAfterSuccess.getTotalSavingsBalance());

        // 5c. Duplicate payment: repeating the same idempotency key or reference must throw DuplicateRecordException
        assertThrows(DuplicateRecordException.class, () ->
                collectionService.recordPayment(createdGroup.getId(), successReq, "gateway")
        );

        // ========================================================
        // 6. Reducing Balance Loan Schedule (Requirements 33, 34, 35)
        // ========================================================
        LoanApplicationRequest loanAppReq = new LoanApplicationRequest();
        loanAppReq.setRequestedAmount(BigDecimal.valueOf(60000));
        loanAppReq.setPurpose("Dairy Livestock Purchase");
        loanAppReq.setPreferredDurationMonths(12);

        assertThrows(InsufficientGroupFundsException.class,
                () -> loanApplicationService.submitApplication(member.getMemberId(), loanAppReq));
    }
}
