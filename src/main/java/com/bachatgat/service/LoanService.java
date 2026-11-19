package com.bachatgat.service;

import com.bachatgat.dto.LoanExtraPaymentRequest;
import com.bachatgat.dto.LoanRepaymentRequest;
import com.bachatgat.dto.LoanPartialPaymentRequest;
import com.bachatgat.dto.CollectionPaymentRequest;
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
        Loan loan = getLoanById(loanId);
        BigDecimal penalty = money(request.getPenaltyAmount());
        BigDecimal bachat = money(request.getMonthlyBachatAmount());
        BigDecimal total = money(request.getAmount());
        BigDecimal principal = BigDecimal.ZERO.setScale(2);
        BigDecimal interest = BigDecimal.ZERO.setScale(2);

        if (request.isPayBachatOnly() && (penalty.signum() > 0 || principal.signum() > 0 || interest.signum() > 0)) {
            throw new InvalidFinancialOperationException("Bachat-only payment cannot include penalty or loan components.");
        }
        if (isDuplicateLoanReference(loan, request.getReferenceNumber())) {
            throw new InvalidFinancialOperationException("A payment with this reference already exists.");
        }

        LocalDate paymentDate = request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now();
        if (penalty.add(bachat).signum() > 0) {
            CollectionPaymentRequest collection = new CollectionPaymentRequest();
            collection.setMemberId(loan.getMemberId());
            collection.setMonth(paymentDate.getMonthValue());
            collection.setYear(paymentDate.getYear());
            collection.setAmount(penalty.add(bachat));
            collection.setShareAmount(bachat);
            collection.setLateFeeAmount(penalty);
            collection.setWaivedLateFee(BigDecimal.ZERO);
            collection.setPaymentDate(paymentDate);
            collection.setPaymentMethod(request.getPaymentMethod());
            collection.setReferenceNumber(request.getReferenceNumber());
            collection.setIdempotencyKey("PARTIAL-" + loan.getLoanId() + "-" + (request.getReferenceNumber() != null ? request.getReferenceNumber() : System.nanoTime()));
            collection.setPaymentStatus("SUCCESS");
            collection.setNotes(request.getNotes());
            collectionService.recordPayment(loan.getGroupId(), collection, adminUsername);
        }

        BigDecimal loanPayment = total.subtract(penalty).subtract(bachat).max(BigDecimal.ZERO);
        if (loanPayment.signum() > 0) {
            BigDecimal requestedInterest = money(request.getLoanInterestAmount());
            BigDecimal requestedPrincipal = money(request.getLoanPrincipalAmount());
            boolean hasExplicitAllocation = requestedInterest.signum() > 0 || requestedPrincipal.signum() > 0;
            if (hasExplicitAllocation) {
                // The component checkboxes represent an approved arrangement:
                // principal-only payments must not be silently redirected to
                // interest, and interest-only payments must not reduce
                // principal. Validate the requested split against the amount
                // received and cap it at the actual outstanding balances.
                if (requestedInterest.add(requestedPrincipal).compareTo(loanPayment) != 0) {
                    throw new InvalidFinancialOperationException("Selected loan principal and interest must add up to the amount received.");
                }
                BigDecimal principalDue = loan.getOutstandingPrincipal()
                        .add(loan.getPendingPrincipal() != null ? loan.getPendingPrincipal() : BigDecimal.ZERO);
                if (requestedInterest.compareTo(loan.getOutstandingInterest()) > 0
                        || requestedPrincipal.compareTo(principalDue) > 0) {
                    throw new InvalidFinancialOperationException("Selected loan component exceeds the outstanding balance.");
                }
                interest = requestedInterest;
                principal = requestedPrincipal;
            } else {
                BigDecimal[] allocated = allocateLoanPayment(loan, loanPayment);
                principal = allocated[0];
                interest = allocated[1];
            }
        }
        if (loanPayment.signum() > 0) {
            List<LoanRepaymentSchedule> schedule = dataService.getLoanSchedule(loan.getId());
            applyPartialSchedulePayment(schedule, principal, interest, paymentDate);
            markInterestOnlyRowPartialWhenNeeded(schedule, principal, interest);
            recordArrangement(schedule, request.getArrangementType(), adminUsername, paymentDate);
            dataService.saveLoanSchedule(loan.getId(), schedule);
            // Rebuild all future rows from the updated payment history. This
            // is what makes interest-only payment and no-payment arrears
            // behave differently in the next month.
            schedule = dataService.getLoanSchedule(loan.getId());

            loan.setPrincipalPaid(loan.getPrincipalPaid().add(principal));
            loan.setInterestPaid(loan.getInterestPaid().add(interest));
            loan.setTotalPaid(loan.getTotalPaid().add(loanPayment));
            if (!"FLAT".equalsIgnoreCase(loan.getInterestType())) {
                // Reconcile from the updated schedule so Option B keeps the
                // contractual balance reduction and the separately pending
                // principal in sync after a partial payment.
                com.bachatgat.repository.LoanScheduleCalculatorSupport.reconcileReducingLoanSummary(loan, schedule);
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
            schedule.stream().filter(s -> s.getStatus() != RepaymentStatus.PAID).findFirst()
                    .ifPresent(next -> loan.setNextDueDate(next.getDueDate()));

            Loan savedLoan = dataService.saveLoan(loan);
            dataService.findMemberByMemberId(loan.getMemberId()).ifPresent(member -> {
                member.setCurrentLoanOutstanding(savedLoan.getTotalOutstanding());
                member.setTotalLoanInterestPaid(savedLoan.getInterestPaid());
                if (savedLoan.getStatus() == LoanStatus.COMPLETED) member.setActiveLoanId(null);
                dataService.saveMember(member);
            });
            dataService.findGroupById(loan.getGroupId()).ifPresent(group -> {
                group.setTotalLoanOutstanding((group.getTotalLoanOutstanding() != null ? group.getTotalLoanOutstanding() : BigDecimal.ZERO)
                        .subtract(loanPayment).max(BigDecimal.ZERO));
                dataService.saveGroup(group);
            });
            transactionService.recordTransaction(loan.getGroupId(), loan.getMemberId(), loan.getMemberName(), loan.getLoanId(),
                    TransactionType.LOAN_REPAYMENT, loanPayment, principal, interest, paymentDate,
                    request.getReferenceNumber(), "Partial loan payment (Principal: ₹" + principal + ", Interest: ₹" + interest + ")",
                    request.getPaymentMethod(), adminUsername);
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
            BigDecimal scheduledInterest = item.getTotalInterestDue() != null
                    ? item.getTotalInterestDue() : BigDecimal.ZERO;
            if (scheduledInterest.signum() == 0 && item.getInterestAmount() != null) {
                scheduledInterest = item.getInterestAmount();
            }
            BigDecimal scheduledPrincipal = item.getPrincipalAmount() != null ? item.getPrincipalAmount() : BigDecimal.ZERO;
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
            item.setPaidDate(paymentDate);
            item.setStatus(item.getOutstandingAmount().signum() == 0 ? RepaymentStatus.PAID : RepaymentStatus.PARTIAL);
            remainingInterest = remainingInterest.subtract(interestApplied);
            remainingPrincipal = remainingPrincipal.subtract(principalApplied);
            if (remainingInterest.signum() == 0 && remainingPrincipal.signum() == 0) break;
        }
    }

    private void markInterestOnlyRowPartialWhenNeeded(List<LoanRepaymentSchedule> schedule,
                                                       BigDecimal principal, BigDecimal interest) {
        if (principal.signum() != 0 || interest.signum() == 0
                || schedule.stream().anyMatch(item -> item.getStatus() == RepaymentStatus.PARTIAL)) {
            return;
        }
        schedule.stream()
                .filter(item -> item.getPrincipalAmount() != null
                        && item.getPrincipalAmount().subtract(item.getPaidPrincipalAmount()).signum() > 0)
                .findFirst()
                .ifPresent(item -> item.setStatus(RepaymentStatus.PARTIAL));
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

    private boolean isDuplicateLoanReference(Loan loan, String reference) {
        if (reference == null || reference.isBlank()) return false;
        return dataService.findTransactionByReference(reference)
                .filter(existing -> loan.getLoanId() != null
                        && loan.getLoanId().equalsIgnoreCase(existing.getLoanId())
                        && (existing.getType() == TransactionType.LOAN_REPAYMENT
                        || existing.getType() == TransactionType.LOAN_EXTRA_PAYMENT
                        || existing.getType() == TransactionType.EXTRA_LOAN_PAYMENT))
                .isPresent();
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
        Loan loan = getLoanById(loanId);

        if (loan.getStatus() == LoanStatus.COMPLETED) {
            throw new InvalidFinancialOperationException("Loan " + loan.getLoanId() + " is already fully repaid.");
        }

        BigDecimal paymentAmount = FinancialCalculator.round(request.getAmount());

        if (isDuplicateLoanReference(loan, request.getReferenceNumber())) {
            throw new InvalidFinancialOperationException("A payment with this reference already exists.");
        }

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

        // Partial repayment is allowed. The amount is allocated to the
        // current installment and its status remains PARTIAL until the
        // unpaid balance is received.

        BigDecimal scheduledInterestDue;
        BigDecimal currentInterestDue = currentInst == null ? BigDecimal.ZERO : currentInst.getTotalInterestDue();
        if (currentInst != null && currentInterestDue.signum() == 0 && currentInst.getInterestAmount() != null) {
            currentInterestDue = currentInst.getInterestAmount();
        }
        if (currentInst != null && currentInterestDue != null && currentInterestDue.compareTo(BigDecimal.ZERO) > 0) {
            // A partially paid installment may already have paid some of its interest.
            // Charge only the unpaid interest for this installment; using the full
            // scheduled interest here makes the loan summary disagree with the schedule.
            BigDecimal alreadyPaid = currentInst.getPaidAmount() != null
                    ? currentInst.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal interestAlreadyPaid = alreadyPaid.min(currentInterestDue);
            scheduledInterestDue = currentInterestDue
                    .subtract(interestAlreadyPaid)
                    .max(BigDecimal.ZERO)
                    .min(loan.getOutstandingInterest());
        } else {
            scheduledInterestDue = loan.getTotalInterest()
                    .divide(BigDecimal.valueOf(Math.max(1, loan.getDurationMonths())), 2, FinancialCalculator.ROUNDING)
                    .min(loan.getOutstandingInterest());
        }

        BigDecimal[] allocated = allocateLoanPayment(loan, paymentAmount);
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

    /** Central backend allocation policy: oldest unpaid interest first, then
     * oldest unpaid scheduled principal. Browser-supplied components are never
     * authoritative. */
    private BigDecimal[] allocateLoanPayment(Loan loan, BigDecimal payment) {
        BigDecimal remaining = payment.max(BigDecimal.ZERO);
        BigDecimal interest = BigDecimal.ZERO;
        BigDecimal principal = BigDecimal.ZERO;
        List<LoanRepaymentSchedule> schedule = dataService.getLoanSchedule(loan.getId());
        for (LoanRepaymentSchedule item : schedule) {
            BigDecimal interestDue = item.getTotalInterestDue() == null ? BigDecimal.ZERO : item.getTotalInterestDue();
            if (interestDue.signum() == 0 && item.getInterestAmount() != null) {
                interestDue = item.getInterestAmount();
            }
            BigDecimal principalDue = item.getPrincipalAmount() == null ? BigDecimal.ZERO : item.getPrincipalAmount();
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
    public Loan recordExtraPayment(String loanId, LoanExtraPaymentRequest request, String adminUsername) {
        Loan loan = getLoanById(loanId);

        if (loan.getStatus() == LoanStatus.COMPLETED) {
            throw new InvalidFinancialOperationException("Loan " + loan.getLoanId() + " is already fully repaid.");
        }

        BigDecimal extraAmount = FinancialCalculator.round(request.getAmount());
        BigDecimal previousTotalOutstanding = loan.getTotalOutstanding();

        // An extra prepayment is principal-only. It must never consume the
        // remaining interest balance or drive outstanding principal negative.
        BigDecimal outstandingPrincipal = loan.getOutstandingPrincipal() != null
                ? loan.getOutstandingPrincipal() : BigDecimal.ZERO;
        if (extraAmount.compareTo(outstandingPrincipal) > 0) {
            throw new InvalidFinancialOperationException("Extra payment of ₹" + extraAmount + 
                    " exceeds outstanding principal of ₹" + outstandingPrincipal);
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
                && "MONTHLY".equalsIgnoreCase(loan.getInterestRateBasis());
        List<LoanRepaymentSchedule> rebuilt = FinancialCalculator.generateAmortizationSchedule(
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
