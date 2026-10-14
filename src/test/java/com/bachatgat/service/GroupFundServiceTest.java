package com.bachatgat.service;

import com.bachatgat.model.Member;
import com.bachatgat.model.MemberStatus;
import com.bachatgat.model.Transaction;
import com.bachatgat.model.TransactionType;
import com.bachatgat.repository.FirestoreDataService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupFundServiceTest {

    @Test
    void keepsLegacySavingsWhenExitAddsOnlyRefundLedgerEntry() {
        FirestoreDataService dataService = new FirestoreDataService(null, null, null);
        String groupId = "group-legacy-mixed";

        Member active = new Member();
        active.setId("active");
        active.setMemberId("M-ACTIVE");
        active.setGroupId(groupId);
        active.setStatus(MemberStatus.ACTIVE);
        active.setTotalSavingsBalance(new BigDecimal("10000"));

        Member exited = new Member();
        exited.setId("exited");
        exited.setMemberId("M-EXITED");
        exited.setGroupId(groupId);
        exited.setStatus(MemberStatus.EXITED);
        exited.setTotalSavingsBalance(new BigDecimal("5000"));

        Transaction refund = new Transaction();
        refund.setGroupId(groupId);
        refund.setType(TransactionType.REFUND);
        refund.setAmount(new BigDecimal("5000"));

        dataService.saveMember(active);
        dataService.saveMember(exited);
        dataService.saveTransaction(refund);

        GroupFundService service = new GroupFundService(dataService);

        assertEquals(new BigDecimal("10000"), service.getFundBreakdown(groupId).availableFund());
        assertEquals(new BigDecimal("10000"), service.getFundBreakdown(groupId).savings());
    }

    @Test
    void memberExitClearsMemberBalanceDeductsGroupSavingsAndReservesRefund() {
        FirestoreDataService dataService = new FirestoreDataService(null, null, null);
        String groupId = "group-exit-flow";

        var group = new com.bachatgat.model.Group();
        group.setId(groupId);
        group.setTotalSavingsBalance(new BigDecimal("15000"));
        dataService.saveGroup(group);

        Member member = new Member();
        member.setId("member-exit-flow");
        member.setMemberId("M-EXIT-FLOW");
        member.setGroupId(groupId);
        member.setFullName("Exit Test Member");
        member.setStatus(MemberStatus.ACTIVE);
        member.setTotalSavingsBalance(new BigDecimal("5000"));
        member.setCurrentShareBalance(new BigDecimal("5000"));
        dataService.saveMember(member);

        Member remainingMember = new Member();
        remainingMember.setId("member-remaining");
        remainingMember.setMemberId("M-REMAINING");
        remainingMember.setGroupId(groupId);
        remainingMember.setStatus(MemberStatus.ACTIVE);
        remainingMember.setTotalSavingsBalance(new BigDecimal("10000"));
        remainingMember.setCurrentShareBalance(new BigDecimal("10000"));
        dataService.saveMember(remainingMember);

        GroupFundService fundService = new GroupFundService(dataService);
        MemberExitService exitService = new MemberExitService(
                dataService, new TransactionService(dataService), new AuditService(dataService), fundService);

        var refund = exitService.exitMember(member.getId(), "admin", "Test exit");

        assertEquals(new BigDecimal("5000"), refund.getAmount());
        assertEquals("PENDING", refund.getStatus());
        assertEquals(MemberStatus.EXITED, dataService.getMemberById(member.getId()).getStatus());
        assertEquals(BigDecimal.ZERO, dataService.getMemberById(member.getId()).getTotalSavingsBalance());
        assertEquals(BigDecimal.ZERO, dataService.getMemberById(member.getId()).getCurrentShareBalance());
        assertEquals(new BigDecimal("10000"), dataService.findGroupById(groupId).orElseThrow().getTotalSavingsBalance());
        assertEquals(new BigDecimal("10000"), fundService.getAvailableFund(groupId));
    }

    @Test
    void preservesSignedFundBalanceInsteadOfHidingShortfallAsZero() {
        FirestoreDataService dataService = new FirestoreDataService(null, null, null);
        String groupId = "group-signed-fund";

        Transaction savings = new Transaction();
        savings.setGroupId(groupId);
        savings.setType(TransactionType.SHARE_CONTRIBUTION);
        savings.setAmount(new BigDecimal("2000"));

        Transaction disbursement = new Transaction();
        disbursement.setGroupId(groupId);
        disbursement.setType(TransactionType.LOAN_DISBURSEMENT);
        disbursement.setAmount(new BigDecimal("5000"));

        dataService.saveTransaction(savings);
        dataService.saveTransaction(disbursement);

        GroupFundService service = new GroupFundService(dataService);

        assertEquals(new BigDecimal("-3000"), service.getAvailableFund(groupId));
    }

    @Test
    void keepsHistoricalSavingsWhenNewLedgerPaymentIsAdded() {
        FirestoreDataService dataService = new FirestoreDataService(null, null, null);
        String groupId = "group-historical-savings";

        Member member = new Member();
        member.setId("historical-member");
        member.setMemberId("M-HISTORICAL");
        member.setGroupId(groupId);
        member.setStatus(MemberStatus.ACTIVE);
        member.setTotalSavingsBalance(new BigDecimal("192000"));
        dataService.saveMember(member);

        Transaction newPayment = new Transaction();
        newPayment.setGroupId(groupId);
        newPayment.setType(TransactionType.SHARE_CONTRIBUTION);
        newPayment.setAmount(new BigDecimal("2000"));
        dataService.saveTransaction(newPayment);

        GroupFundService service = new GroupFundService(dataService);

        assertEquals(new BigDecimal("192000"), service.getFundBreakdown(groupId).savings());
    }

    @Test
    void keepsVerifiedLedgerSavingsWhenMemberBalanceLagsBehind() {
        FirestoreDataService dataService = new FirestoreDataService(null, null, null);
        String groupId = "group-current-payment";

        Member member = new Member();
        member.setId("current-member");
        member.setMemberId("M-CURRENT");
        member.setGroupId(groupId);
        member.setStatus(MemberStatus.ACTIVE);
        member.setTotalSavingsBalance(new BigDecimal("5600"));
        dataService.saveMember(member);

        Transaction firstPayment = new Transaction();
        firstPayment.setGroupId(groupId);
        firstPayment.setType(TransactionType.SHARE_CONTRIBUTION);
        firstPayment.setAmount(new BigDecimal("3300"));
        dataService.saveTransaction(firstPayment);

        Transaction secondPayment = new Transaction();
        secondPayment.setGroupId(groupId);
        secondPayment.setType(TransactionType.SHARE_CONTRIBUTION);
        secondPayment.setAmount(new BigDecimal("3300"));
        dataService.saveTransaction(secondPayment);

        GroupFundService service = new GroupFundService(dataService);

        assertEquals(new BigDecimal("6600"), service.getFundBreakdown(groupId).savings());
    }
}
