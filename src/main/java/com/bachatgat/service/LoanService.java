package com.bachatgat.service;

import com.bachatgat.dto.LoanExtraPaymentRequest;
import com.bachatgat.dto.LoanRepaymentRequest;
import com.bachatgat.dto.LoanPartialPaymentRequest;
import com.bachatgat.dto.CollectionPaymentRequest;
import com.bachatgat.exception.InvalidFinancialOperationException;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import com.bachatgat.repository.LoanScheduleCalculatorSupport;
import com.bachatgat.util.FinancialCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class LoanService {

    private final FirestoreDataService dataService;
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final DistributionService distributionService;
    private final CollectionService collectionService;

    public LoanService(FirestoreDataService dataService, TransactionService transactionService,
                       NotificationService notificationService, AuditService auditService,
                       @org.springframework.context.annotation.Lazy DistributionService distributionService,
                       @org.springframework.context.annotation.Lazy CollectionService collectionService) {
        this.dataService = dataService;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.distributionService = distributionService;
        this.collectionService = collectionService;
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
     * Records an internal, administrator-approved partial payment. Each
     * component is explicit so paying interest alone never reduces principal.
     */
    public synchronized Loan recordPartialPayment(String loanId, LoanPartialPaymentRequest request, String adminUsername) {
        var state = dataService.beginLoanPayment(getLoanById(loanId));
        Loan loan = state.loan();
        BigDecimal penalty = money(request.getPenaltyAmount());
        BigDecimal bachat = money(request.getMonthlyBachatAmount());
        BigDecimal total = money(request.getAmount());
        BigDecimal principal = BigDecimal.ZERO.setScale(2);
        BigDecimal interest = BigDecimal.ZERO.setScale(2);

        if (isDuplicateLoanReference(loan, request.getReferenceNumber(), TransactionType.LOAN_REPAYMENT)) {
            throw new InvalidFinancialOperationException("A payment with this reference already exists.");
        }

        LocalDate paymentDate = request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now();
        BigDecimal collectionAmount = penalty.add(bachat);
        BigDecimal requestedPrincipal = money(request.getLoanPrincipalAmount());
        BigDecimal requestedInterest = money(request.getLoanInterestAmount());
        BigDecimal instructed = requestedPrincipal.add(requestedInterest);
        BigDecimal loanPayment = total.subtract(collectionAmount);
        if (total.signum() <= 0 || loanPayment.signum() < 0
                || (request.isPayBachatOnly() && (penalty.signum() > 0 || loanPayment.signum() != 0 || instructed.signum() != 0))
                || (instructed.signum() > 0 && instructed.compareTo(loanPayment) != 0)) {
            throw new InvalidFinancialOperationException("Payment components must match the amount received.");
        }
        List<LoanRepaymentSchedule> schedule = dataService.getLoanScheduleForPayment(loan.getId());
        boolean reducing = !"FLAT".equalsIgnoreCase(loan.getInterestType());
        if (reducing) LoanScheduleCalculatorSupport.reconcileReducingLoanSummary(loan, schedule);
        BigDecimal previousOutstanding = loan.getTotalOutstanding();
        if (loanPayment.signum() > 0) {
            validateLoanPayment(loan, paymentDate);
            if (instructed.signum() > 0) {
                if (requestedInterest.compareTo(loan.getOutstandingInterest()) > 0
                        || requestedPrincipal.compareTo(loan.getOutstandingPrincipal()) > 0) {
                    throw new InvalidFinancialOperationException("Selected loan component exceeds the outstanding balance.");
                }
                interest = requestedInterest;
                principal = requestedPrincipal;
            } else {
                BigDecimal[] allocated = allocateLoanPayment(schedule, loanPayment);
                principal = allocated[0];
                interest = allocated[1];
            }
        }
        if (loanPayment.signum() > 0) {
            applyPartialSchedulePayment(schedule, principal, interest, paymentDate);
            recordArrangement(schedule, request.getArrangementType(), adminUsername, paymentDate);
            schedule = rebuildReducingSchedule(loan, schedule);
        }
        if (collectionAmount.signum() > 0) {
            CollectionPaymentRequest collection = new CollectionPaymentRequest();
            collection.setMemberId(loan.getMemberId());
            collection.setMonth(paymentDate.getMonthValue());
            collection.setYear(paymentDate.getYear());
            collection.setAmount(collectionAmount);
            collection.setShareAmount(bachat);
            collection.setLateFeeAmount(penalty);
            collection.setWaivedLateFee(BigDecimal.ZERO);
            collection.setPaymentDate(paymentDate);
            collection.setPaymentMethod(request.getPaymentMethod());
            collection.setReferenceNumber(request.getReferenceNumber());
            collection.setIdempotencyKey("PARTIAL-" + loan.getLoanId() + "-" + (request.getReferenceNumber() != null && !request.getReferenceNumber().isBlank()
                    ? request.getReferenceNumber().trim() : UUID.randomUUID()));
            collection.setPaymentStatus("SUCCESS");
            collection.setNotes(request.getNotes());
            collectionService.recordPayment(loan.getGroupId(), collection, adminUsername);
        }

        if (loanPayment.signum() > 0) {
            loan.setPrincipalPaid(loan.getPrincipalPaid().add(principal));
            loan.setInterestPaid(loan.getInterestPaid().add(interest));
            loan.setTotalPaid(loan.getTotalPaid().add(loanPayment));
            if (reducing) {
                LoanScheduleCalculatorSupport.reconcileReducingLoanSummary(loan, schedule);
            } else {
                BigDecimal originalPrincipal = money(loan.getPrincipalAmount());
                loan.setOutstandingPrincipal(originalPrincipal.subtract(loan.getPrincipalPaid()).max(BigDecimal.ZERO));
                loan.setOutstandingInterest(calculateProjectedInterest(loan, schedule)
                        .subtract(loan.getInterestPaid()).max(BigDecimal.ZERO));
                loan.setPendingInterest(loan.getOutstandingInterest());
                loan.setPendingPrincipal(loan.getOutstandingPrincipal());
                loan.setTotalOutstanding(loan.getOutstandingPrincipal().add(loan.getOutstandingInterest()));
            }
            loan.setStatus(loan.getTotalOutstanding().signum() <= 0 ? LoanStatus.COMPLETED : LoanStatus.PARTIALLY_PAID);
            if (!reducing) {
                schedule.stream().filter(s -> s.getStatus() != RepaymentStatus.PAID).findFirst()
                        .ifPresent(next -> loan.setNextDueDate(next.getDueDate()));
            }

            Transaction entry = loanPaymentEntry(loan, TransactionType.LOAN_REPAYMENT, loanPayment,
                    principal, interest, paymentDate, request.getReferenceNumber(),
                    "Partial loan payment", request.getPaymentMethod(), adminUsername);
            Loan savedLoan = dataService.saveLoanPayment(state, schedule, entry);
            dataService.findMemberByMemberId(loan.getMemberId()).ifPresent(member -> {
                member.setCurrentLoanOutstanding(savedLoan.getTotalOutstanding());
                member.setTotalLoanInterestPaid(savedLoan.getInterestPaid());
                if (savedLoan.getStatus() == LoanStatus.COMPLETED) member.setActiveLoanId(null);
                dataService.saveMember(member);
            });
            dataService.findGroupById(loan.getGroupId()).ifPresent(group -> {
                group.setTotalLoanOutstanding((group.getTotalLoanOutstanding() != null ? group.getTotalLoanOutstanding() : BigDecimal.ZERO)
                        .subtract(previousOutstanding).add(savedLoan.getTotalOutstanding()).max(BigDecimal.ZERO));
                dataService.saveGroup(group);
            });
            if (interest.signum() > 0 && distributionService != null) {
                distributionService.distributeLoanInterest(loan.getGroupId(), loan.getLoanId(), loan.getMemberId(), interest, paymentDate, adminUsername);
            }
            auditService.log(loan.getGroupId(), adminUsername, adminUsername, "PARTIAL_LOAN_PAYMENT", "LOAN", loan.getLoanId(), null,
                    "Partial payment recorded: ₹" + total, "127.0.0.1");
            return savedLoan;
        }

        auditService.log(loan.getGroupId(), adminUsername, adminUsername, "PARTIAL_COLLECTION_PAYMENT", "LOAN", loan.getLoanId(), null,
                "Bachat/penalty-only payment recorded: ₹" + total, "127.0.0.1");
        return loan;
    }

    private void applyPartialSchedulePayment(List<LoanRepaymentSchedule> schedule, BigDecimal principal, BigDecimal interest, LocalDate paymentDate) {
        BigDecimal remainingInterest = interest;
        BigDecimal remainingPrincipal = principal;
        for (LoanRepaymentSchedule item : schedule) {
            BigDecimal scheduledInterest = item.getInterestAmount();
            BigDecimal scheduledPrincipal = item.getScheduledPrincipalAmount();
            BigDecimal alreadyPaidInterest = item.getPaidInterestAmount();
            BigDecimal alreadyPaidPrincipal = item.getPaidPrincipalAmount();
            if (alreadyPaidInterest.signum() == 0 && alreadyPaidPrincipal.signum() == 0 && item.getPaidAmount() != null && item.getPaidAmount().signum() > 0) {
                alreadyPaidInterest = item.getPaidAmount().min(scheduledInterest);
                alreadyPaidPrincipal = item.getPaidAmount().subtract(alreadyPaidInterest).max(BigDecimal.ZERO).min(scheduledPrincipal);
            }
            BigDecimal interestApplied = remainingInterest.min(scheduledInterest.subtract(alreadyPaidInterest).max(BigDecimal.ZERO));
            BigDecimal principalApplied = remainingPrincipal.min(scheduledPrincipal.subtract(alreadyPaidPrincipal).max(BigDecimal.ZERO));
            if (interestApplied.signum() == 0 && principalApplied.signum() == 0) continue;
            item.setPaidInterestAmount(alreadyPaidInterest.add(interestApplied));
            item.setPaidPrincipalAmount(alreadyPaidPrincipal.add(principalApplied));
            item.setPaidAmount(item.getPaidInterestAmount().add(item.getPaidPrincipalAmount()));
            item.setOutstandingAmount(item.getTotalDue().subtract(item.getPaidAmount()).max(BigDecimal.ZERO));
            item.setPendingInterest(scheduledInterest.subtract(item.getPaidInterestAmount()).max(BigDecimal.ZERO));
            item.setPendingPrincipal(scheduledPrincipal.subtract(item.getPaidPrincipalAmount()).max(BigDecimal.ZERO));
            if (principalApplied.signum() > 0 || (alreadyPaidPrincipal.signum() > 0 && item.getPrincipalReceipts() == null)) {
                var receipts = item.getPrincipalReceipts() == null
                        ? new LinkedHashMap<String, BigDecimal>() : new LinkedHashMap<>(item.getPrincipalReceipts());
                if (item.getPrincipalReceipts() == null && alreadyPaidPrincipal.signum() > 0) {
                    LocalDate originalDate = item.getPaidDate() == null ? item.getDueDate() : item.getPaidDate();
                    receipts.put(originalDate.toString(), alreadyPaidPrincipal);
                }
                if (principalApplied.signum() > 0) receipts.merge(paymentDate.toString(), principalApplied, BigDecimal::add);
                item.setPrincipalReceipts(receipts);
            }
            item.setPaidDate(paymentDate);
            item.setStatus(item.getOutstandingAmount().signum() == 0 ? RepaymentStatus.PAID : RepaymentStatus.PARTIAL);
            remainingInterest = remainingInterest.subtract(interestApplied);
            remainingPrincipal = remainingPrincipal.subtract(principalApplied);
            if (remainingInterest.signum() == 0 && remainingPrincipal.signum() == 0) return;
        }
        throw new InvalidFinancialOperationException("Payment exceeds the available principal or interest on the schedule.");
    }

    private void recordArrangement(List<LoanRepaymentSchedule> schedule, String arrangementType,
                                   String approvedBy, LocalDate paymentDate) {
        String type = arrangementType == null || arrangementType.isBlank() ? "NORMAL" : arrangementType.toUpperCase();
        if ("NORMAL".equals(type)) return;
        schedule.stream()
                .filter(item -> item.getStatus() == RepaymentStatus.PARTIAL
                        || (item.getPaidDate() != null && item.getPaidDate().equals(paymentDate)))
                .findFirst()
                .ifPresent(item -> {
                    item.setArrangementType(type);
                    item.setExceptionApprovedBy(approvedBy);
                    item.setExceptionApprovedAt(java.time.LocalDateTime.now());
                });
    }

    private boolean isDuplicateLoanReference(Loan loan, String reference, TransactionType type) {
        if (reference == null || reference.isBlank()) return false;
        return dataService.getTransactionsByGroupId(loan.getGroupId()).stream()
                .anyMatch(existing -> reference.trim().equalsIgnoreCase(existing.getReference())
                        && loan.getLoanId() != null
                        && loan.getLoanId().equalsIgnoreCase(existing.getLoanId())
                        && (existing.getType() == type || (type == TransactionType.EXTRA_LOAN_PAYMENT
                        && existing.getType() == TransactionType.LOAN_EXTRA_PAYMENT)));
    }

    private BigDecimal money(BigDecimal amount) {
        if (amount == null) return BigDecimal.ZERO.setScale(2);
        if (amount.signum() < 0 || amount.scale() > 2) throw new InvalidFinancialOperationException("Payment amounts must be non-negative with at most 2 decimals.");
        return FinancialCalculator.round(amount);
    }

    private BigDecimal calculateProjectedInterest(Loan loan, List<LoanRepaymentSchedule> schedule) {
        if ("FLAT".equalsIgnoreCase(loan.getInterestType())) {
            return money(loan.getTotalInterest());
        }
        // Contractual interest is the standard reducing-balance interest for
        // the original tenure.  Do not sum arrears rows: those rows include
        // carried-forward dues and would count the same interest repeatedly.
        BigDecimal rate = loan.getInterestRate() != null ? loan.getInterestRate() : BigDecimal.ZERO;
        boolean monthlyRate = "MONTHLY".equalsIgnoreCase(loan.getInterestRateBasis());
        return FinancialCalculator.round(FinancialCalculator.generateAmortizationSchedule(
                loan.getPrincipalAmount(), rate, monthlyRate,
                Math.max(1, loan.getDurationMonths()), loan.getDisbursementDate()).stream()
                .map(LoanRepaymentSchedule::getInterestAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
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
    public synchronized Loan recordRepayment(String loanId, LoanRepaymentRequest request, String adminUsername) {
        var state = dataService.beginLoanPayment(getLoanById(loanId));
        Loan loan = state.loan();

        if (loan.getStatus() == LoanStatus.COMPLETED) {
            throw new InvalidFinancialOperationException("Loan " + loan.getLoanId() + " is already fully repaid.");
        }

        LocalDate date = request.getPaymentDate() == null ? LocalDate.now() : request.getPaymentDate();
        validateLoanPayment(loan, date);
        BigDecimal paymentAmount = money(request.getAmount());

        if (isDuplicateLoanReference(loan, request.getReferenceNumber(), TransactionType.LOAN_REPAYMENT)) {
            throw new InvalidFinancialOperationException("A payment with this reference already exists.");
        }

        List<LoanRepaymentSchedule> schedule = dataService.getLoanScheduleForPayment(loan.getId());
        if (!"FLAT".equalsIgnoreCase(loan.getInterestType())) {
            LoanScheduleCalculatorSupport.reconcileReducingLoanSummary(loan, schedule);
        }
        BigDecimal previousOutstanding = loan.getTotalOutstanding();
        if (paymentAmount.signum() <= 0 || paymentAmount.compareTo(previousOutstanding) > 0) {
            throw new InvalidFinancialOperationException("Payment must be positive and within the total outstanding balance.");
        }
        BigDecimal[] allocated = allocateLoanPayment(schedule, paymentAmount);
        BigDecimal principalComponent = allocated[0];
        BigDecimal interestComponent = allocated[1];

        // Update Loan Balances
        loan.setPrincipalPaid(loan.getPrincipalPaid().add(principalComponent));
        loan.setInterestPaid(loan.getInterestPaid().add(interestComponent));
        loan.setTotalPaid(loan.getTotalPaid().add(paymentAmount));
        
        loan.setOutstandingPrincipal(loan.getOutstandingPrincipal().subtract(principalComponent));
        loan.setOutstandingInterest(loan.getOutstandingInterest().subtract(interestComponent));
        loan.setTotalOutstanding(loan.getTotalOutstanding().subtract(paymentAmount));
        loan.setPendingInterest(loan.getOutstandingInterest());
        loan.setPendingPrincipal(loan.getOutstandingPrincipal());

        // Update the component ledger, not only the displayed pending amount.
        // Future installments deliberately expose ₹0 pending until their due
        // date, but a payment can still be recorded against them.  Using the
        // explicit components ensures principal paid is persisted and the
        // recalculated closing principal moves immediately.
        applyPartialSchedulePayment(
                schedule,
                principalComponent,
                interestComponent,
                request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now());
        recordArrangement(schedule, request.getArrangementType(), adminUsername,
                request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now());
        schedule = rebuildReducingSchedule(loan, schedule);
        if (!"FLAT".equalsIgnoreCase(loan.getInterestType())) {
            LoanScheduleCalculatorSupport.reconcileReducingLoanSummary(loan, schedule);
        }

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
                    .filter(s -> s.getStatus() != RepaymentStatus.PAID && s.getTotalDue().compareTo(s.getPaidAmount()) > 0)
                    .findFirst()
                    .ifPresent(nextInst -> loan.setNextDueDate(nextInst.getDueDate()));
        }

        Transaction entry = loanPaymentEntry(loan, TransactionType.LOAN_REPAYMENT, paymentAmount,
                principalComponent, interestComponent, date, request.getReferenceNumber(),
                "Loan installment repayment", request.getPaymentMethod(), adminUsername);
        Loan savedLoan = dataService.saveLoanPayment(state, schedule, entry);

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
            BigDecimal updatedGrpLoan = grpLoan.subtract(previousOutstanding).add(savedLoan.getTotalOutstanding());
            group.setTotalLoanOutstanding(updatedGrpLoan.compareTo(BigDecimal.ZERO) > 0 ? updatedGrpLoan : BigDecimal.ZERO);
            dataService.saveGroup(group);
        }

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

    /** Central backend allocation policy: oldest unpaid interest first, then
     * oldest unpaid scheduled principal. Browser-supplied components are never
     * authoritative. */
    private BigDecimal[] allocateLoanPayment(List<LoanRepaymentSchedule> schedule, BigDecimal payment) {
        BigDecimal remaining = payment.max(BigDecimal.ZERO);
        BigDecimal interest = BigDecimal.ZERO;
        BigDecimal principal = BigDecimal.ZERO;
        for (LoanRepaymentSchedule item : schedule) {
            BigDecimal interestDue = item.getInterestAmount();
            BigDecimal principalDue = item.getScheduledPrincipalAmount();
            BigDecimal paidInterest = item.getPaidInterestAmount();
            BigDecimal paidPrincipal = item.getPaidPrincipalAmount();
            if (paidInterest.signum() == 0 && paidPrincipal.signum() == 0 && item.getPaidAmount() != null
                    && item.getPaidAmount().signum() > 0) {
                paidInterest = item.getPaidAmount().min(interestDue);
                paidPrincipal = item.getPaidAmount().subtract(paidInterest).min(principalDue).max(BigDecimal.ZERO);
            }
            BigDecimal interestOpen = interestDue.subtract(paidInterest).max(BigDecimal.ZERO);
            BigDecimal principalOpen = principalDue.subtract(paidPrincipal).max(BigDecimal.ZERO);
            BigDecimal interestPart = remaining.min(interestOpen);
            remaining = remaining.subtract(interestPart);
            BigDecimal principalPart = remaining.min(principalOpen);
            remaining = remaining.subtract(principalPart);
            interest = interest.add(interestPart);
            principal = principal.add(principalPart);
            if (remaining.signum() == 0) break;
        }
        if (remaining.signum() > 0) {
            throw new InvalidFinancialOperationException("Payment exceeds the currently scheduled loan dues.");
        }
        return new BigDecimal[]{money(principal), money(interest)};
    }

    /**
     * Records an extra prepayment towards principal outstanding.
     * Applies directly to reducing principal and outstanding schedule.
     */
    public synchronized Loan recordExtraPayment(String loanId, LoanExtraPaymentRequest request, String adminUsername) {
        var state = dataService.beginLoanPayment(getLoanById(loanId));
        Loan loan = state.loan();
        if (loan.getStatus() == LoanStatus.COMPLETED) {
            throw new InvalidFinancialOperationException("Loan " + loan.getLoanId() + " is already fully repaid.");
        }
        if (isDuplicateLoanReference(loan, request.getReferenceNumber(), TransactionType.EXTRA_LOAN_PAYMENT)) {
            throw new InvalidFinancialOperationException("A payment with this reference already exists.");
        }
        LocalDate date = request.getPaymentDate() == null ? LocalDate.now() : request.getPaymentDate();
        validateLoanPayment(loan, date);
        BigDecimal extraAmount = money(request.getAmount());
        List<LoanRepaymentSchedule> schedule = dataService.getLoanScheduleForPayment(loan.getId());
        boolean reducing = !"FLAT".equalsIgnoreCase(loan.getInterestType());
        if (reducing) LoanScheduleCalculatorSupport.reconcileReducingLoanSummary(loan, schedule);
        BigDecimal previousTotalOutstanding = loan.getTotalOutstanding();
        if (extraAmount.signum() <= 0 || extraAmount.compareTo(loan.getOutstandingPrincipal()) > 0) {
            throw new InvalidFinancialOperationException("Extra payment must be positive and within outstanding principal.");
        }
        if (reducing) {
            LoanRepaymentSchedule next = schedule.stream()
                    .filter(row -> !row.getDueDate().isBefore(date) && row.getPaidAmount().signum() == 0)
                    .findFirst().orElse(null);
            if (next == null) {
                LoanRepaymentSchedule last = schedule.getLast();
                last.setPostMaturityPrepayment(last.getPostMaturityPrepayment().add(extraAmount));
            } else {
                next.setPrincipalPrepayment(next.getPrincipalPrepayment().add(extraAmount));
            }
            schedule = rebuildReducingSchedule(loan, schedule);
            LoanScheduleCalculatorSupport.reconcileReducingLoanSummary(loan, schedule);
        } else {
            loan.setPrincipalPaid(loan.getPrincipalPaid().add(extraAmount));
            loan.setTotalPaid(loan.getTotalPaid().add(extraAmount));
            loan.setOutstandingPrincipal(loan.getOutstandingPrincipal().subtract(extraAmount));
            loan.setTotalOutstanding(previousTotalOutstanding.subtract(extraAmount));
        }
        loan.setStatus(loan.getTotalOutstanding().signum() == 0 ? LoanStatus.COMPLETED : LoanStatus.PARTIALLY_PAID);
        Transaction entry = loanPaymentEntry(loan, TransactionType.EXTRA_LOAN_PAYMENT, extraAmount, extraAmount,
                BigDecimal.ZERO, date, request.getReferenceNumber(), "Extra loan prepayment applied to principal",
                request.getPaymentMethod(), adminUsername);
        Loan savedLoan = dataService.saveLoanPayment(state, schedule, entry);

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

    private void validateLoanPayment(Loan loan, LocalDate date) {
        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.PARTIALLY_PAID
                && loan.getStatus() != LoanStatus.OVERDUE) {
            throw new InvalidFinancialOperationException("Payments require an active loan.");
        }
        if (date.isAfter(LocalDate.now()) || (loan.getDisbursementDate() != null && date.isBefore(loan.getDisbursementDate()))) {
            throw new InvalidFinancialOperationException("The payment date must be between disbursement and today.");
        }
    }

    private List<LoanRepaymentSchedule> rebuildReducingSchedule(Loan loan, List<LoanRepaymentSchedule> schedule) {
        if ("FLAT".equalsIgnoreCase(loan.getInterestType())) return schedule;
        return FinancialCalculator.generateReducingScheduleWithPayments(
                loan.getPrincipalAmount(), loan.getInterestRate(), "MONTHLY".equalsIgnoreCase(loan.getInterestRateBasis()),
                loan.getDurationMonths() > 0 ? loan.getDurationMonths() : 12,
                loan.getDisbursementDate() == null ? LocalDate.now() : loan.getDisbursementDate(), schedule);
    }

    private Transaction loanPaymentEntry(Loan loan, TransactionType type, BigDecimal amount, BigDecimal principal,
            BigDecimal interest, LocalDate date, String reference, String description, String method, String user) {
        String ref = reference == null ? "" : reference.trim();
        UUID key = ref.isEmpty() ? UUID.randomUUID() : UUID.nameUUIDFromBytes(
                (loan.getId() + ":" + type + ":" + ref.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
        return new Transaction("TXN-" + key, loan.getGroupId(), loan.getMemberId(), loan.getMemberName(),
                loan.getLoanId(), type, amount, principal, interest, date, ref, description, method, user);
    }
}
