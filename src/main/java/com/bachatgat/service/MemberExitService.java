package com.bachatgat.service;

import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.Member;
import com.bachatgat.model.MemberRefund;
import com.bachatgat.model.MemberStatus;
import com.bachatgat.model.Transaction;
import com.bachatgat.model.TransactionType;
import com.bachatgat.repository.FirestoreDataService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MemberExitService {
    private final FirestoreDataService dataService;
    private final TransactionService transactionService;
    private final AuditService auditService;
    private final GroupFundService groupFundService;

    public MemberExitService(FirestoreDataService dataService, TransactionService transactionService,
                             AuditService auditService, GroupFundService groupFundService) {
        this.dataService = dataService;
        this.transactionService = transactionService;
        this.auditService = auditService;
        this.groupFundService = groupFundService;
    }

    public synchronized MemberRefund exitMember(String memberIdOrDocumentId, String actor, String reason) {
        Member member = dataService.findMemberById(memberIdOrDocumentId)
                .or(() -> dataService.findMemberByMemberId(memberIdOrDocumentId))
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with ID: " + memberIdOrDocumentId));
        if (member.getStatus() == MemberStatus.EXITED) {
            return dataService.findMemberRefundByMemberId(member.getMemberId())
                    .orElseThrow(() -> new IllegalStateException("Member is already exited without a refund record."));
        }
        BigDecimal loanOutstanding = nz(member.getCurrentLoanOutstanding());
        if (loanOutstanding.signum() > 0) {
            throw new IllegalStateException("Member cannot exit while loan outstanding is ₹" + loanOutstanding + ". Settle the loan first.");
        }

        // Savings and share balance are the member-owned refundable balance. Group
        // income/dividends are intentionally excluded from the exit calculation.
        BigDecimal refundable = nz(member.getTotalSavingsBalance());
        if (refundable.signum() == 0) refundable = nz(member.getCurrentShareBalance());
        if (refundable.signum() < 0) refundable = BigDecimal.ZERO;
        BigDecimal available = groupFundService.getLendingCapacity(member.getGroupId());
        if (refundable.compareTo(available) > 0) {
            throw new IllegalStateException("The group does not currently have enough available funds to reserve this refund.");
        }

        Transaction reservation = transactionService.recordTransaction(
                member.getGroupId(), member.getMemberId(), member.getFullName(), null,
                TransactionType.REFUND, refundable, BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.now(), "EXIT-" + member.getMemberId(),
                "Member exit refund reservation" + (reason == null || reason.isBlank() ? "" : ": " + reason),
                "BANK_TRANSFER", actor);

        // Reserve the group cash immediately and clear the member-owned balances.
        // The bank transfer remains pending, but the amount must not remain in
        // active/member savings while it is reserved for refund.
        member.setTotalSavingsBalance(BigDecimal.ZERO);
        member.setCurrentShareBalance(BigDecimal.ZERO);
        member.setPendingSavings(BigDecimal.ZERO);
        member.setStatus(MemberStatus.EXITED);
        dataService.saveMember(member);

        BigDecimal reservedAmount = refundable;
        dataService.findGroupById(member.getGroupId()).ifPresent(group -> {
            BigDecimal groupSavings = nz(group.getTotalSavingsBalance());
            group.setTotalSavingsBalance(groupSavings.subtract(reservedAmount).max(BigDecimal.ZERO));
            dataService.saveGroup(group);
        });

        MemberRefund refund = new MemberRefund();
        refund.setGroupId(member.getGroupId());
        refund.setMemberId(member.getMemberId());
        refund.setMemberName(member.getFullName());
        refund.setAmount(refundable);
        refund.setMaskedBankAccount(maskAccount(member.getAccountNumber()));
        refund.setExpectedCreditDate(addWorkingDays(LocalDate.now(), 5));
        refund.setCreatedBy(actor);
        refund.setBalanceReserved(true);
        refund.setReservationTransactionId(reservation.getTransactionId());
        MemberRefund saved = dataService.saveMemberRefund(refund);

        auditService.log(member.getGroupId(), actor, actor, "MEMBER_EXIT_REFUND_RESERVED", "MEMBER_REFUND",
                saved.getRefundId(), "ACTIVE", "EXITED / PENDING REFUND", "127.0.0.1");
        return saved;
    }

    public List<MemberRefund> getGroupRefunds(String groupId) {
        return dataService.getMemberRefundsByGroupId(groupId);
    }

    public List<MemberRefund> getMemberRefunds(String memberId) {
        return dataService.getMemberRefundsByMemberId(memberId);
    }

    public MemberRefund updateStatus(String refundId, String status, String actor, String failureReason) {
        MemberRefund refund = dataService.getMemberRefundsByGroupId(null).stream()
                .filter(r -> refundId.equalsIgnoreCase(r.getRefundId()) || refundId.equalsIgnoreCase(r.getId()))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));
        String normalized = status == null ? "" : status.toUpperCase();
        if (!List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED").contains(normalized)) {
            throw new IllegalArgumentException("Invalid refund status");
        }
        refund.setStatus(normalized);
        refund.setProcessedBy(actor);
        refund.setFailureReason(failureReason);
        if ("PROCESSING".equals(normalized)) refund.setProcessedAt(LocalDateTime.now());
        if ("COMPLETED".equals(normalized)) refund.setCompletedAt(LocalDateTime.now());
        return dataService.saveMemberRefund(refund);
    }

    private BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    private String maskAccount(String account) {
        if (account == null || account.isBlank()) return null;
        String clean = account.trim();
        return "••••" + clean.substring(Math.max(0, clean.length() - 4));
    }

    private LocalDate addWorkingDays(LocalDate date, int days) {
        LocalDate result = date;
        int added = 0;
        while (added < days) {
            result = result.plusDays(1);
            if (result.getDayOfWeek() != DayOfWeek.SATURDAY && result.getDayOfWeek() != DayOfWeek.SUNDAY) added++;
        }
        return result;
    }
}
