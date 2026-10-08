package com.bachatgat.service;

import com.bachatgat.dto.LoanExtraPaymentRequest;
import com.bachatgat.dto.LoanRepaymentRequest;
import com.bachatgat.exception.InvalidFinancialOperationException;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import com.bachatgat.util.FinancialCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

@Service
public class LoanService {

    private final FirestoreDataService dataService;
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final DistributionService distributionService;

    public LoanService(FirestoreDataService dataService, TransactionService transactionService,
                       NotificationService notificationService, AuditService auditService,
                       @org.springframework.context.annotation.Lazy DistributionService distributionService) {
        this.dataService = dataService;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.distributionService = distributionService;
    }

    public List<Loan> getLoansByGroupId(String groupId) {
        return dataService.getLoansByGroupId(groupId);
    }

    public List<Loan> getLoansByMemberId(String memberId) {
        return dataService.getLoansByMemberId(memberId);
    }

    public Loan getLoanById(String id) {
        return dataService.findLoanById(id)
                .or(() -> dataService.findLoanByLoanId(id))
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found with ID: " + id));
    }

    public List<LoanRepaymentSchedule> getLoanSchedule(String loanId) {
        return dataService.getLoanSchedule(loanId);
    }

    /**
     * Executes separate disbursement step for an approved loan.
     * Reduces available group cash and increases member loan principal outstanding.
     */
    public Loan disburseLoan(String loanId, LocalDate disbursementDate, String paymentMethod, String referenceNumber, String adminUsername) {
        Loan loan = getLoanById(loanId);

        if (loan.getStatus() != LoanStatus.APPROVED && loan.getStatus() != LoanStatus.PENDING) {
            throw new InvalidFinancialOperationException("Loan is currently in status: " + loan.getStatus() + ". Only APPROVED loans can be disbursed.");
        }

        LocalDate disbDate = disbursementDate != null ? disbursementDate : LocalDate.now();
        String method = (paymentMethod != null && !paymentMethod.isBlank()) ? paymentMethod : "BANK_TRANSFER";
        String ref = (referenceNumber != null && !referenceNumber.isBlank()) ? referenceNumber : ("DISB-" + loan.getLoanId());

        loan.setStatus(LoanStatus.ACTIVE);
        loan.setDisbursementDate(disbDate);
        loan.setNextDueDate(disbDate.plusMonths(1));
        Loan savedLoan = dataService.saveLoan(loan);

        // Update Member profile
        Member member = dataService.findMemberByMemberId(loan.getMemberId()).orElse(null);
        if (member != null) {
            member.setActiveLoanId(savedLoan.getLoanId());
            member.setCurrentLoanOutstanding(savedLoan.getTotalOutstanding());
            dataService.saveMember(member);
        }

        // Update Group aggregate outstanding
        Group group = dataService.findGroupById(loan.getGroupId()).orElse(null);
        if (group != null) {
            BigDecimal grpLoan = group.getTotalLoanOutstanding() != null ? group.getTotalLoanOutstanding() : BigDecimal.ZERO;
            group.setTotalLoanOutstanding(grpLoan.add(savedLoan.getTotalOutstanding()));
            dataService.saveGroup(group);
        }

        // Record Disbursement Transaction in Ledger
        transactionService.recordTransaction(
                loan.getGroupId(), loan.getMemberId(), loan.getMemberName(), savedLoan.getLoanId(),
                TransactionType.LOAN_DISBURSEMENT, savedLoan.getPrincipalAmount(), savedLoan.getPrincipalAmount(), BigDecimal.ZERO,
                disbDate, ref,
                "Disbursement for sanctioned loan " + savedLoan.getLoanId() + " via " + method,
                method, adminUsername
        );

        // Notify member
        dataService.findUserByMemberId(loan.getMemberId()).ifPresent(u ->
                notificationService.sendNotification(loan.getGroupId(), u.getId(), "USER",
                        "Loan Disbursed!",
                        "Your sanctioned loan of ₹" + savedLoan.getPrincipalAmount() + " (ID: " + savedLoan.getLoanId() + 
                                ") has been successfully disbursed.",
                        "LOAN_DISBURSED")
        );

        auditService.log(loan.getGroupId(), adminUsername, adminUsername, "LOAN_DISBURSED", "LOAN",
                savedLoan.getLoanId(), "APPROVED", "Disbursed ₹" + savedLoan.getPrincipalAmount() + " via " + method, "127.0.0.1");

        return savedLoan;
    }

    /**
     * Records a standard regular loan repayment installment.
     * Separates interest and principal accounting, updates schedules, and verifies totals.
     * Triggers interest income allocation to all eligible group members (including borrower).
     */
    public Loan recordRepayment(String loanId, LoanRepaymentRequest request, String adminUsername) {
        Loan loan = getLoanById(loanId);

        if (loan.getStatus() == LoanStatus.COMPLETED) {
            throw new InvalidFinancialOperationException("Loan " + loan.getLoanId() + " is already fully repaid.");
        }

        BigDecimal paymentAmount = FinancialCalculator.round(request.getAmount());

        if (paymentAmount.compareTo(loan.getTotalOutstanding()) > 0) {
            throw new InvalidFinancialOperationException("Payment amount ₹" + paymentAmount + 
                    " exceeds total outstanding balance of ₹" + loan.getTotalOutstanding());
        }

        // Allocate between scheduled installment interest and principal based on schedule
        List<LoanRepaymentSchedule> schedule = dataService.getLoanSchedule(loan.getId());
        LoanRepaymentSchedule currentInst = schedule.stream()
                .filter(s -> s.getStatus() != RepaymentStatus.PAID)
                .findFirst()
                .orElse(null);

        BigDecimal scheduledInterestDue;
        if (currentInst != null && currentInst.getInterestAmount() != null && currentInst.getInterestAmount().compareTo(BigDecimal.ZERO) > 0) {
            // A partially paid installment may already have paid some of its interest.
            // Charge only the unpaid interest for this installment; using the full
            // scheduled interest here makes the loan summary disagree with the schedule.
            BigDecimal alreadyPaid = currentInst.getPaidAmount() != null
                    ? currentInst.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal interestAlreadyPaid = alreadyPaid.min(currentInst.getInterestAmount());
            scheduledInterestDue = currentInst.getInterestAmount()
                    .subtract(interestAlreadyPaid)
                    .max(BigDecimal.ZERO)
                    .min(loan.getOutstandingInterest());
        } else {
            scheduledInterestDue = loan.getTotalInterest()
                    .divide(BigDecimal.valueOf(Math.max(1, loan.getDurationMonths())), 2, FinancialCalculator.ROUNDING)
                    .min(loan.getOutstandingInterest());
        }

        BigDecimal interestComponent = paymentAmount.min(scheduledInterestDue);
        BigDecimal principalComponent = paymentAmount.subtract(interestComponent).min(loan.getOutstandingPrincipal());

        // Update Loan Balances
        loan.setPrincipalPaid(loan.getPrincipalPaid().add(principalComponent));
        loan.setInterestPaid(loan.getInterestPaid().add(interestComponent));
        loan.setTotalPaid(loan.getTotalPaid().add(paymentAmount));
        
        loan.setOutstandingPrincipal(loan.getOutstandingPrincipal().subtract(principalComponent));
        loan.setOutstandingInterest(loan.getOutstandingInterest().subtract(interestComponent));
        loan.setTotalOutstanding(loan.getTotalOutstanding().subtract(paymentAmount));

        // Update Repayment Schedules
        BigDecimal remainingForSchedule = paymentAmount;

        for (LoanRepaymentSchedule item : schedule) {
            if (item.getStatus() != RepaymentStatus.PAID && remainingForSchedule.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal needed = item.getOutstandingAmount() != null
                        ? item.getOutstandingAmount() : BigDecimal.ZERO;
                if (remainingForSchedule.compareTo(needed) >= 0) {
                    item.setPaidAmount(item.getPaidAmount().add(needed));
                    item.setOutstandingAmount(BigDecimal.ZERO);
                    item.setStatus(RepaymentStatus.PAID);
                    item.setPaidDate(request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now());
                    remainingForSchedule = remainingForSchedule.subtract(needed);
                } else {
                    item.setPaidAmount(item.getPaidAmount().add(remainingForSchedule));
                    item.setOutstandingAmount(needed.subtract(remainingForSchedule));
                    item.setStatus(RepaymentStatus.PARTIAL);
                    item.setPaidDate(request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now());
                    remainingForSchedule = BigDecimal.ZERO;
                }
            }
        }
        dataService.saveLoanSchedule(loan.getId(), schedule);

        // Check if fully settled
        if (loan.getTotalOutstanding().compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(LoanStatus.COMPLETED);
            loan.setTotalOutstanding(BigDecimal.ZERO);
            loan.setOutstandingPrincipal(BigDecimal.ZERO);
            loan.setOutstandingInterest(BigDecimal.ZERO);
        } else {
            loan.setStatus(LoanStatus.PARTIALLY_PAID);
            // Advance next due date to next unpaid installment
            schedule.stream()
                    .filter(s -> s.getStatus() != RepaymentStatus.PAID)
                    .findFirst()
                    .ifPresent(nextInst -> loan.setNextDueDate(nextInst.getDueDate()));
        }

        Loan savedLoan = dataService.saveLoan(loan);

        // Update Member profile
        Member member = dataService.findMemberByMemberId(loan.getMemberId()).orElse(null);
        if (member != null) {
            member.setCurrentLoanOutstanding(savedLoan.getTotalOutstanding());
            member.setTotalLoanInterestPaid(savedLoan.getInterestPaid());
            if (savedLoan.getStatus() == LoanStatus.COMPLETED) {
                member.setActiveLoanId(null);
            }
            dataService.saveMember(member);
        }

        // Update Group aggregates
        Group group = dataService.findGroupById(loan.getGroupId()).orElse(null);
        if (group != null) {
            BigDecimal grpLoan = group.getTotalLoanOutstanding() != null ? group.getTotalLoanOutstanding() : BigDecimal.ZERO;
            BigDecimal updatedGrpLoan = grpLoan.subtract(paymentAmount);
            group.setTotalLoanOutstanding(updatedGrpLoan.compareTo(BigDecimal.ZERO) > 0 ? updatedGrpLoan : BigDecimal.ZERO);
            dataService.saveGroup(group);
        }

        // Record Transaction Ledger Entry
        transactionService.recordTransaction(
                loan.getGroupId(), loan.getMemberId(), loan.getMemberName(), loan.getLoanId(),
                TransactionType.LOAN_REPAYMENT, paymentAmount, principalComponent, interestComponent,
                request.getPaymentDate(), request.getReferenceNumber(),
                "Loan installment repayment (Principal: ₹" + principalComponent + ", Interest: ₹" + interestComponent + ")",
                request.getPaymentMethod(), adminUsername
        );

        // Requirements 6, 7, 8, 9 & 50: Distribute interest income among all active members including borrower!
        if (interestComponent.compareTo(BigDecimal.ZERO) > 0 && distributionService != null) {
            distributionService.distributeLoanInterest(
                    loan.getGroupId(), loan.getLoanId(), loan.getMemberId(),
                    interestComponent, request.getPaymentDate(), adminUsername
            );
        }

        // Notify Member
        dataService.findUserByMemberId(loan.getMemberId()).ifPresent(u ->
                notificationService.sendNotification(loan.getGroupId(), u.getId(), "USER",
                        "Loan Repayment Received",
                        "Repayment of ₹" + paymentAmount + " received for Loan " + loan.getLoanId() + 
                                ". Outstanding balance is now ₹" + savedLoan.getTotalOutstanding() + ".",
                        "LOAN_REPAYMENT")
        );

        auditService.log(loan.getGroupId(), adminUsername, adminUsername, "LOAN_REPAYMENT_RECORDED", "LOAN",
                loan.getLoanId(), null, "Recorded ₹" + paymentAmount + " (Prin: " + principalComponent + ", Int: " + interestComponent + ")", "127.0.0.1");

        return savedLoan;
    }

    /**
     * Records an extra prepayment towards principal outstanding.
     * Applies directly to reducing principal and outstanding schedule.
     */
    public Loan recordExtraPayment(String loanId, LoanExtraPaymentRequest request, String adminUsername) {
        Loan loan = getLoanById(loanId);

        if (loan.getStatus() == LoanStatus.COMPLETED) {
            throw new InvalidFinancialOperationException("Loan " + loan.getLoanId() + " is already fully repaid.");
        }

        BigDecimal extraAmount = FinancialCalculator.round(request.getAmount());
        BigDecimal previousTotalOutstanding = loan.getTotalOutstanding();

        if (extraAmount.compareTo(loan.getTotalOutstanding()) > 0) {
            throw new InvalidFinancialOperationException("Extra payment of ₹" + extraAmount + 
                    " exceeds remaining outstanding balance of ₹" + loan.getTotalOutstanding());
        }

        // Extra payment directly reduces outstanding principal
        loan.setPrincipalPaid(loan.getPrincipalPaid().add(extraAmount));
        loan.setTotalPaid(loan.getTotalPaid().add(extraAmount));
        loan.setOutstandingPrincipal(loan.getOutstandingPrincipal().subtract(extraAmount));
        loan.setTotalOutstanding(loan.getTotalOutstanding().subtract(extraAmount));

        // Rebuild only the unpaid reducing-balance portion. This makes the
        // next month's interest use the principal after the extra payment,
        // while preserving the already-paid ledger installments.
        if ("REDUCING".equalsIgnoreCase(loan.getInterestType())) {
            recalculateUnpaidScheduleAfterExtraPayment(loan);
        }

        if (loan.getTotalOutstanding().compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(LoanStatus.COMPLETED);
            loan.setTotalOutstanding(BigDecimal.ZERO);
            loan.setOutstandingPrincipal(BigDecimal.ZERO);
            loan.setOutstandingInterest(BigDecimal.ZERO);
        }

        Loan savedLoan = dataService.saveLoan(loan);

        Group group = dataService.findGroupById(loan.getGroupId()).orElse(null);
        if (group != null) {
            BigDecimal groupOutstanding = group.getTotalLoanOutstanding() != null
                    ? group.getTotalLoanOutstanding() : BigDecimal.ZERO;
            BigDecimal adjusted = groupOutstanding.subtract(previousTotalOutstanding)
                    .add(savedLoan.getTotalOutstanding());
            group.setTotalLoanOutstanding(adjusted.max(BigDecimal.ZERO));
            dataService.saveGroup(group);
        }

        // Update Member profile
        Member member = dataService.findMemberByMemberId(loan.getMemberId()).orElse(null);
        if (member != null) {
            member.setCurrentLoanOutstanding(savedLoan.getTotalOutstanding());
            if (savedLoan.getStatus() == LoanStatus.COMPLETED) {
                member.setActiveLoanId(null);
            }
            dataService.saveMember(member);
        }

        // Record Transaction Ledger Entry
        transactionService.recordTransaction(
                loan.getGroupId(), loan.getMemberId(), loan.getMemberName(), loan.getLoanId(),
                TransactionType.EXTRA_LOAN_PAYMENT, extraAmount, extraAmount, BigDecimal.ZERO,
                request.getPaymentDate(), request.getReferenceNumber(),
                "Extra loan prepayment directly applied to principal reduction",
                request.getPaymentMethod(), adminUsername
        );

        // Notify member
        dataService.findUserByMemberId(loan.getMemberId()).ifPresent(u ->
                notificationService.sendNotification(loan.getGroupId(), u.getId(), "USER",
                        "Extra Loan Prepayment Credited",
                        "Extra prepayment of ₹" + extraAmount + " applied to Loan " + loan.getLoanId() + 
                                ". New outstanding balance: ₹" + savedLoan.getTotalOutstanding(),
                        "EXTRA_PAYMENT")
        );

        auditService.log(loan.getGroupId(), adminUsername, adminUsername, "EXTRA_LOAN_PAYMENT", "LOAN",
                loan.getLoanId(), null, "Extra prepayment of ₹" + extraAmount + " recorded", "127.0.0.1");

        return savedLoan;
    }

    private void recalculateUnpaidScheduleAfterExtraPayment(Loan loan) {
        List<LoanRepaymentSchedule> existing = dataService.getLoanSchedule(loan.getId());
        List<LoanRepaymentSchedule> paid = existing.stream()
                .filter(s -> s.getStatus() == RepaymentStatus.PAID)
                .toList();
        int remainingMonths = Math.max(1, loan.getDurationMonths() - paid.size());
        LocalDate startDate = paid.isEmpty()
                ? (loan.getDisbursementDate() != null ? loan.getDisbursementDate() : LocalDate.now())
                : paid.get(paid.size() - 1).getDueDate();
        boolean monthlyRate = "REDUCING".equalsIgnoreCase(loan.getInterestType())
                && loan.getInterestRate() != null
                && loan.getInterestRate().compareTo(BigDecimal.valueOf(5)) <= 0;
        List<LoanRepaymentSchedule> rebuilt = FinancialCalculator.generateBachatGatReducingSchedule(
                loan.getOutstandingPrincipal(), loan.getInterestRate(), monthlyRate, remainingMonths, startDate);
        int nextInstallment = paid.size() + 1;
        BigDecimal futureInterest = BigDecimal.ZERO;
        for (LoanRepaymentSchedule item : rebuilt) {
            item.setId(loan.getId() + "-" + (nextInstallment + item.getInstallmentNo() - 1));
            item.setLoanId(loan.getId());
            item.setGroupId(loan.getGroupId());
            item.setMemberId(loan.getMemberId());
            item.setInstallmentNo(nextInstallment + item.getInstallmentNo() - 1);
            futureInterest = futureInterest.add(item.getInterestAmount());
        }
        List<LoanRepaymentSchedule> combined = new ArrayList<>(paid);
        combined.addAll(rebuilt);
        dataService.saveLoanSchedule(loan.getId(), combined);
        loan.setOutstandingInterest(futureInterest);
        loan.setTotalInterest(loan.getInterestPaid().add(futureInterest));
        loan.setTotalOutstanding(loan.getOutstandingPrincipal().add(futureInterest));
        loan.setTotalPayable(loan.getTotalPaid().add(loan.getTotalOutstanding()));
        rebuilt.stream().findFirst().ifPresent(next -> {
            loan.setMonthlyInstallment(next.getEmiAmount());
            loan.setNextDueDate(next.getDueDate());
        });
    }
}
