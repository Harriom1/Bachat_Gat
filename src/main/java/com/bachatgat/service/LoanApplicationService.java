package com.bachatgat.service;

import com.bachatgat.dto.LoanApplicationRequest;
import com.bachatgat.dto.LoanApprovalRequest;
import com.bachatgat.dto.LoanRepaymentPreview;
import com.bachatgat.exception.InvalidFinancialOperationException;
import com.bachatgat.exception.InsufficientGroupFundsException;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import com.bachatgat.util.FinancialCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LoanApplicationService {

    private final FirestoreDataService dataService;
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final GroupFundService groupFundService;

    @org.springframework.beans.factory.annotation.Autowired
    public LoanApplicationService(FirestoreDataService dataService, TransactionService transactionService,
                                  NotificationService notificationService, AuditService auditService,
                                  GroupFundService groupFundService) {
        this.dataService = dataService;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.groupFundService = groupFundService;
    }

    public List<LoanApplication> getApplicationsByGroupId(String groupId) {
        return dataService.getLoanApplicationsByGroupId(groupId);
    }

    public List<LoanApplication> getApplicationsByMemberId(String memberId) {
        return dataService.getLoanApplicationsByMemberId(memberId);
    }

    public LoanApplication getApplicationById(String id) {
        return dataService.findLoanApplicationById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found with ID: " + id));
    }

    public LoanRepaymentPreview previewRepayment(String memberId, BigDecimal amount, int durationMonths) {
        Member member = dataService.findMemberByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));
        Group group = dataService.findGroupById(member.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID: " + member.getGroupId()));
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidFinancialOperationException("Loan amount must be greater than zero.");
        }
        validateDuration(durationMonths);
        LocalDate today = LocalDate.now();
        GroupMasterData rules = dataService.getCurrentLoanRules(group.getId()).orElse(null);
        LoanLimits limits = loanLimits(group, rules);
        validateLoanAmount(amount, limits, outstandingPrincipal(activeLoans(memberId)));
        BigDecimal principal = FinancialCalculator.round(amount);
        if (principal.signum() <= 0) {
            throw new InvalidFinancialOperationException("Loan amount must be greater than zero.");
        }
        RepaymentPlan plan = calculateRepayment(principal, defaultRate(group, rules),
                false, durationMonths, normalizeInterestType(defaultInterestType(group, rules)), today);
        return plan.preview();
    }

    /**
     * Submits a new loan application by the logged-in Member/User.
     */
    public LoanApplication submitApplication(String memberId, LoanApplicationRequest request) {
        Member member = dataService.findMemberByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found for: " + memberId));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new InvalidFinancialOperationException("Only active members can submit loan applications.");
        }

        Group group = dataService.findGroupById(member.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID: " + member.getGroupId()));

        if (request.getRequestedAmount() == null || request.getRequestedAmount().signum() <= 0) {
            throw new InvalidFinancialOperationException("Requested loan amount must be greater than zero.");
        }
        validateDuration(request.getPreferredDurationMonths());

        // Check if member already has an active or pending loan application under review
        List<LoanApplication> existingPending = dataService.getLoanApplicationsByMemberId(memberId).stream()
                .filter(la -> la.getStatus() == LoanApplicationStatus.PENDING)
                .toList();
        if (!existingPending.isEmpty()) {
            throw new InvalidFinancialOperationException("You already have a pending loan application under review.");
        }

        // Fetch active loans of the member
        List<Loan> activeLoans = activeLoans(memberId);

        // 1. Multiple Active Loans Check
        if (!group.isAllowMultipleLoans()) {
            if (!activeLoans.isEmpty() || (member.getActiveLoanId() != null && member.getCurrentLoanOutstanding() != null && member.getCurrentLoanOutstanding().compareTo(BigDecimal.ZERO) > 0)) {
                throw new InvalidFinancialOperationException("Your group does not allow multiple active loans. Please clear your current loan first.");
            }
        } else {
            int maxActive = group.getMaxActiveLoans() > 0 ? group.getMaxActiveLoans() : 2;
            if (activeLoans.size() >= maxActive) {
                throw new InvalidFinancialOperationException("You already have " + activeLoans.size() + " active loan(s), which reaches the group maximum limit of " + maxActive + " active loans.");
            }
        }

        // 2. Check for Overdue EMI on existing loans
        boolean hasOverdue = activeLoans.stream().anyMatch(l -> l.getOverdueInstallments() > 0);
        if (hasOverdue) {
            throw new InvalidFinancialOperationException("You have overdue loan installments on an active loan. Please clear all overdue installments before applying for a new loan.");
        }

        GroupMasterData activeRules = dataService.getCurrentLoanRules(group.getId()).orElse(null);
        validateLoanAmount(request.getRequestedAmount(), loanLimits(group, activeRules), outstandingPrincipal(activeLoans));
        validateAvailableFund(group.getId(), request.getRequestedAmount());

        String appId = "LA-" + LocalDate.now().getYear() + "-" + String.format("%03d", (dataService.getLoanApplicationsByGroupId(member.getGroupId()).size() + 1));
        LoanApplication app = new LoanApplication();
        app.setApplicationId(appId);
        app.setGroupId(member.getGroupId());
        app.setGroupName(member.getGroupName());
        app.setMemberId(member.getMemberId());
        app.setMemberName(member.getFullName());
        app.setRequestedAmount(FinancialCalculator.round(request.getRequestedAmount()));
        app.setPurpose(request.getPurpose());
        app.setPreferredDurationMonths(request.getPreferredDurationMonths());
        app.setOptionalMessage(request.getOptionalMessage());
        app.setSupportingDocPath(request.getSupportingDocPath());
        app.setStatus(LoanApplicationStatus.PENDING);

        LoanApplication saved = dataService.saveLoanApplication(app);

        // Notify Admin
        notificationService.sendNotification(member.getGroupId(), null, "ADMIN",
                "New Loan Application: " + member.getFullName(),
                member.getFullName() + " has applied for a loan of ₹" + request.getRequestedAmount() + " for: " + request.getPurpose(),
                "LOAN_APPLICATION");

        auditService.log(member.getGroupId(), member.getMemberId(), member.getFullName(),
                "LOAN_APPLICATION_SUBMITTED", "LOAN_APPLICATION", saved.getApplicationId(),
                null, "Requested ₹" + request.getRequestedAmount(), "127.0.0.1");

        return saved;
    }

    /**
     * Admin approves a loan application, determining the final approved amount,
     * interest rate, duration, and automatically creating the Loan, Repayment Schedule,
     * Disbursement Transaction, and Member balance.
     */
    public Loan approveApplication(String applicationId, LoanApprovalRequest request, String adminUsername) {
        LoanApplication app = getApplicationById(applicationId);

        if (app.getStatus() != LoanApplicationStatus.PENDING) {
            throw new InvalidFinancialOperationException("Application is already " + app.getStatus());
        }

        BigDecimal approvedPrincipal = request.getApprovedAmount() != null
                ? FinancialCalculator.round(request.getApprovedAmount())
                : FinancialCalculator.round(app.getRequestedAmount());

        if (approvedPrincipal.signum() <= 0) {
            throw new InvalidFinancialOperationException("Approved loan amount must be greater than zero.");
        }

        Group groupForLimit = dataService.findGroupById(app.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID: " + app.getGroupId()));
        LocalDate today = LocalDate.now();
        GroupMasterData activeRules = dataService.getCurrentLoanRules(app.getGroupId()).orElse(null);
        validateLoanAmount(approvedPrincipal, loanLimits(groupForLimit, activeRules),
                outstandingPrincipal(activeLoans(app.getMemberId())));
        boolean disburseNow = request.getDisburseImmediately();
        if (disburseNow) validateAvailableFund(app.getGroupId(), approvedPrincipal);

        BigDecimal rate = request.getInterestRate() != null ? request.getInterestRate() : defaultRate(groupForLimit, activeRules);

        if (rate == null || rate.signum() < 0 || rate.scale() > 2) {
            throw new InvalidFinancialOperationException("Interest rate must be non-negative with at most two decimals.");
        }

        int durationMonths = (request.getDurationMonths() != null && request.getDurationMonths() > 0)
                ? request.getDurationMonths()
                : (app.getPreferredDurationMonths() > 0 ? app.getPreferredDurationMonths() : 12);

        validateDuration(durationMonths);

        String rawInterestType = request.getInterestType();
        if (rawInterestType == null || rawInterestType.isBlank()) {
            rawInterestType = defaultInterestType(groupForLimit, activeRules);
        }
        String interestType = normalizeInterestType(rawInterestType);
        boolean isMonthlyRate = Boolean.TRUE.equals(request.getIsMonthlyRate());
        RepaymentPlan plan = calculateRepayment(approvedPrincipal, rate, isMonthlyRate, durationMonths, interestType, today);
        BigDecimal totalInterest = plan.preview().totalInterest();
        BigDecimal totalPayable = plan.preview().totalRepayment();
        BigDecimal monthlyInstallment = plan.preview().monthlyEmi();
        List<LoanRepaymentSchedule> schedules = plan.schedules();

        app.setApprovedAmount(approvedPrincipal);
        app.setApprovedInterestRate(rate);
        app.setApprovedDurationMonths(durationMonths);
        app.setInterestType(interestType);
        app.setAdminNotes(request.getAdminNotes());
        app.setReviewedBy(adminUsername);
        app.setReviewedAt(LocalDateTime.now());
        app.setStatus(LoanApplicationStatus.APPROVED);

        // 2. Create the Loan Record
        String loanId = "LN-" + LocalDate.now().getYear() + "-" + String.format("%03d", (dataService.getLoansByGroupId(app.getGroupId()).size() + 1));
        Loan loan = new Loan();
        loan.setLoanId(loanId);
        loan.setApplicationId(app.getApplicationId());
        loan.setGroupId(app.getGroupId());
        loan.setGroupName(app.getGroupName());
        loan.setMemberId(app.getMemberId());
        loan.setMemberName(app.getMemberName());
        loan.setPurpose(app.getPurpose() != null ? app.getPurpose() : "Member Personal / Household Need");
        loan.setChequeNumber(normalizeChequeNumber(request.getChequeNumber()));
        loan.setPrincipalAmount(approvedPrincipal);
        loan.setInterestRate(rate);
        loan.setInterestRateBasis(isMonthlyRate ? "MONTHLY" : "ANNUAL");
        loan.setDurationMonths(durationMonths);
        loan.setInterestType(interestType);
        loan.setTotalInterest(totalInterest);
        loan.setTotalPayable(totalPayable);
        loan.setPrincipalPaid(BigDecimal.ZERO);
        loan.setInterestPaid(BigDecimal.ZERO);
        loan.setTotalPaid(BigDecimal.ZERO);
        loan.setOutstandingPrincipal(approvedPrincipal);
        loan.setOutstandingInterest(totalInterest);
        loan.setTotalOutstanding(totalPayable);
        loan.setMonthlyInstallment(monthlyInstallment);
        loan.setNextDueDate(LocalDate.now().plusMonths(1));
        loan.setCreatedBy(adminUsername);

        if (disburseNow) {
            loan.setDisbursementDate(LocalDate.now());
            loan.setStatus(LoanStatus.ACTIVE);
        } else {
            loan.setStatus(LoanStatus.APPROVED);
        }

        Loan savedLoan = dataService.saveLoan(loan);

        // Link back to application
        app.setGeneratedLoanId(savedLoan.getLoanId());
        dataService.saveLoanApplication(app);

        // 3. Save Repayment Schedule with generated IDs
        for (int i = 0; i < schedules.size(); i++) {
            LoanRepaymentSchedule item = schedules.get(i);
            item.setId("sch-" + savedLoan.getId() + "-" + (i + 1));
            item.setLoanId(savedLoan.getId());
            item.setGroupId(savedLoan.getGroupId());
            item.setMemberId(savedLoan.getMemberId());
        }
        dataService.saveLoanSchedule(savedLoan.getId(), schedules);

        if (disburseNow) {
            // Update Member active loan and outstanding
            Member member = dataService.findMemberByMemberId(app.getMemberId()).orElse(null);
            if (member != null) {
                member.setActiveLoanId(savedLoan.getLoanId());
                member.setCurrentLoanOutstanding(savedLoan.getTotalOutstanding());
                dataService.saveMember(member);
            }

            // Update Group aggregate outstanding
            Group group = dataService.findGroupById(app.getGroupId()).orElse(null);
            if (group != null) {
                BigDecimal grpLoan = group.getTotalLoanOutstanding() != null ? group.getTotalLoanOutstanding() : BigDecimal.ZERO;
                group.setTotalLoanOutstanding(grpLoan.add(savedLoan.getTotalOutstanding()));
                dataService.saveGroup(group);
            }

            // Record Disbursement Transaction in Ledger
            transactionService.recordTransaction(
                    app.getGroupId(), app.getMemberId(), app.getMemberName(), savedLoan.getLoanId(),
                    TransactionType.LOAN_DISBURSEMENT, approvedPrincipal, approvedPrincipal, BigDecimal.ZERO,
                    LocalDate.now(), "DISB-" + savedLoan.getLoanId(),
                    "Disbursement for sanctioned loan " + savedLoan.getLoanId(), "BANK_TRANSFER", adminUsername
            );
        }

        // Notify Member
        dataService.findUserByMemberId(app.getMemberId()).ifPresent(u ->
                notificationService.sendNotification(app.getGroupId(), u.getId(), "USER",
                        disburseNow ? "Loan Sanctioned and Disbursed!" : "Loan Sanctioned - Awaiting Disbursement",
                        "Congratulations! Your loan of ₹" + approvedPrincipal + " (ID: " + savedLoan.getLoanId() + 
                                ") has been sanctioned at " + rate + "% interest.",
                        "LOAN_APPROVED")
        );

        // Audit Log
        auditService.log(app.getGroupId(), adminUsername, adminUsername,
                disburseNow ? "LOAN_APPROVED_AND_DISBURSED" : "LOAN_APPROVED", "LOAN",
                savedLoan.getLoanId(), "REQUESTED: " + app.getRequestedAmount(),
                "SANCTIONED: " + approvedPrincipal + " @ " + rate + "% (disbursed: " + disburseNow + ")", "127.0.0.1");

        return savedLoan;
    }

    private String normalizeChequeNumber(String chequeNumber) {
        if (chequeNumber == null) return null;
        String normalized = chequeNumber.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private static void validateDuration(Integer months) {
        if (months == null || months < 1 || months > LoanRepaymentPreview.MAX_DURATION_MONTHS) {
            throw new InvalidFinancialOperationException(
                    "Loan duration must be between 1 and " + LoanRepaymentPreview.MAX_DURATION_MONTHS + " months.");
        }
    }

    private static BigDecimal defaultRate(Group group, GroupMasterData rules) {
        if (rules != null && rules.getLoanInterestRate() != null) return rules.getLoanInterestRate();
        return group.getDefaultInterestRate() != null ? group.getDefaultInterestRate() : BigDecimal.valueOf(12);
    }

    private static String defaultInterestType(Group group, GroupMasterData rules) {
        if (rules != null && rules.getLoanInterestType() != null) return rules.getLoanInterestType();
        return group.getLoanInterestType() != null ? group.getLoanInterestType() : "REDUCING_BALANCE";
    }

    private static String normalizeInterestType(String type) {
        return "FLAT".equalsIgnoreCase(type.trim()) ? "FLAT" : "REDUCING";
    }

    private record RepaymentPlan(LoanRepaymentPreview preview, List<LoanRepaymentSchedule> schedules) {}

    private static RepaymentPlan calculateRepayment(BigDecimal principal, BigDecimal rate, boolean monthlyRate,
                                                    int months, String interestType, LocalDate startDate) {
        List<LoanRepaymentSchedule> schedule = "FLAT".equals(interestType)
                ? flatSchedule(principal, monthlyRate ? rate.multiply(BigDecimal.valueOf(12)) : rate, months, startDate)
                : FinancialCalculator.generateAmortizationSchedule(principal, rate, monthlyRate, months, startDate);
        BigDecimal interest = schedule.stream().map(LoanRepaymentSchedule::getInterestAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = schedule.stream().map(LoanRepaymentSchedule::getTotalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RepaymentPlan(new LoanRepaymentPreview(schedule.getFirst().getEmiAmount(),
                schedule.getLast().getEmiAmount(), interest, total), schedule);
    }

    private static List<LoanRepaymentSchedule> flatSchedule(BigDecimal principal, BigDecimal annualRate,
                                                            int months, LocalDate startDate) {
        BigDecimal totalInterest = FinancialCalculator.calculateFlatInterest(principal, annualRate, months);
        BigDecimal emi = FinancialCalculator.calculateFlatMonthlyPayment(principal, totalInterest, months);
        BigDecimal regularPrincipal = principal.divide(BigDecimal.valueOf(months), 2, FinancialCalculator.ROUNDING);
        BigDecimal remainingPrincipal = principal;
        BigDecimal remainingInterest = totalInterest;
        List<LoanRepaymentSchedule> schedule = new ArrayList<>(months);

        for (int i = 1; i <= months; i++) {
            BigDecimal principalDue = i == months ? remainingPrincipal : regularPrincipal.min(remainingPrincipal);
            BigDecimal interestDue = i == months ? remainingInterest : emi.subtract(principalDue).min(remainingInterest);
            BigDecimal due = principalDue.add(interestDue);
            LoanRepaymentSchedule item = new LoanRepaymentSchedule();
            item.setInstallmentNo(i);
            item.setDueDate(startDate.plusMonths(i));
            item.setOpeningPrincipal(remainingPrincipal);
            item.setEmiAmount(due);
            item.setPrincipalAmount(principalDue);
            item.setInterestAmount(interestDue);
            item.setPenaltyAmount(BigDecimal.ZERO);
            item.setTotalDue(due);
            item.setPaidAmount(BigDecimal.ZERO);
            item.setOutstandingAmount(due);
            remainingPrincipal = remainingPrincipal.subtract(principalDue);
            remainingInterest = remainingInterest.subtract(interestDue);
            item.setClosingPrincipal(remainingPrincipal);
            item.setStatus(i == 1 ? RepaymentStatus.DUE : RepaymentStatus.UPCOMING);
            schedule.add(item);
        }
        return schedule;
    }

    private record LoanLimits(BigDecimal groupCapacity, BigDecimal perLoan, BigDecimal outstanding) {}

    private static LoanLimits loanLimits(Group group, GroupMasterData rules) {
        BigDecimal perLoan = rules == null ? null : rules.getMaxLoanAmountPerMember();
        if (perLoan == null) perLoan = group.getMaxLoanAmountPerMember();
        if (perLoan == null) perLoan = Group.DEFAULT_LOAN_LIMIT;
        BigDecimal groupCapacity = rules == null ? null : rules.getMaxLoanAmount();
        if (groupCapacity == null) groupCapacity = group.getMaxLoanAmount();
        if (groupCapacity == null) groupCapacity = Group.DEFAULT_LOAN_LIMIT;
        BigDecimal outstanding = rules == null ? null : rules.getMaxOutstandingLoanAmount();
        if (outstanding == null) outstanding = group.getMaxOutstandingLoanAmount();
        if (outstanding == null) outstanding = Group.DEFAULT_LOAN_LIMIT;
        return new LoanLimits(groupCapacity, perLoan, outstanding);
    }

    private static void validateLoanAmount(BigDecimal amount, LoanLimits limits, BigDecimal outstanding) {
        if (amount.compareTo(limits.perLoan()) > 0) {
            throw new InvalidFinancialOperationException("Loan amount of ₹" + amount
                    + " exceeds the maximum allowed amount of ₹" + limits.perLoan() + " per loan.");
        }
        if (outstanding.add(amount).compareTo(limits.outstanding()) > 0) {
            throw new InvalidFinancialOperationException("Total outstanding principal would exceed the limit of ₹"
                    + limits.outstanding() + ".");
        }
    }

    private List<Loan> activeLoans(String memberId) {
        return dataService.getLoansByMemberId(memberId).stream()
                .filter(loan -> loan.getStatus() == LoanStatus.ACTIVE && loan.getOutstandingPrincipal().signum() > 0)
                .toList();
    }

    private static BigDecimal outstandingPrincipal(List<Loan> loans) {
        return loans.stream().map(Loan::getOutstandingPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Executes separate disbursement step for an already approved loan.
     * Section 26: Loan Disbursement
     * Flow: Loan Request -> Approval -> Approved -> Disbursement -> Loan Active.
     * Reduces group available cash fund and increases member loan principal outstanding.
     */
    public Loan disburseLoan(String loanId, LocalDate disbursementDate, String paymentMethod, String referenceNumber, String adminUsername) {
        Loan loan = dataService.findLoanById(loanId)
                .or(() -> dataService.findLoanByLoanId(loanId))
                .orElseThrow(() -> new ResourceNotFoundException("Loan record not found with ID: " + loanId));

        if (loan.getStatus() != LoanStatus.APPROVED && loan.getStatus() != LoanStatus.PENDING) {
            throw new InvalidFinancialOperationException("Loan is already in status: " + loan.getStatus() + ". Only APPROVED loans can be disbursed.");
        }

        LocalDate disbDate = disbursementDate != null ? disbursementDate : LocalDate.now();
        validateAvailableFund(loan.getGroupId(), loan.getPrincipalAmount());
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

        // Audit log
        auditService.log(loan.getGroupId(), adminUsername, adminUsername, "LOAN_DISBURSED", "LOAN",
                savedLoan.getLoanId(), "APPROVED", "DISBURSED ₹" + savedLoan.getPrincipalAmount() + " via " + method, "127.0.0.1");

        return savedLoan;
    }

    private void validateAvailableFund(String groupId, BigDecimal requestedAmount) {
        BigDecimal availableFund = groupFundService.getLendingCapacity(groupId);
        if (requestedAmount.compareTo(availableFund) > 0) {
            throw new InsufficientGroupFundsException(availableFund, requestedAmount);
        }
    }

    public LoanApplication rejectApplication(String applicationId, String rejectionReason, String adminUsername) {
        LoanApplication app = getApplicationById(applicationId);

        if (app.getStatus() != LoanApplicationStatus.PENDING) {
            throw new InvalidFinancialOperationException("Application is already " + app.getStatus());
        }

        app.setStatus(LoanApplicationStatus.REJECTED);
        app.setRejectionReason(rejectionReason);
        app.setReviewedBy(adminUsername);
        app.setReviewedAt(LocalDateTime.now());

        LoanApplication saved = dataService.saveLoanApplication(app);

        // Notify member
        dataService.findUserByMemberId(app.getMemberId()).ifPresent(u ->
                notificationService.sendNotification(app.getGroupId(), u.getId(), "USER",
                        "Loan Application Update",
                        "Your loan application has been rejected. Reason: " + rejectionReason,
                        "LOAN_REJECTED")
        );

        auditService.log(app.getGroupId(), adminUsername, adminUsername, "LOAN_REJECTED", "LOAN_APPLICATION",
                app.getApplicationId(), "PENDING", "REJECTED: " + rejectionReason, "127.0.0.1");

        return saved;
    }

    public java.util.Map<String, Object> getLoanEligibility(String memberId) {
        Member member = dataService.findMemberByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));
        Group group = dataService.findGroupById(member.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + member.getGroupId()));

        List<Loan> activeLoans = activeLoans(memberId);

        int activeLoanCount = activeLoans.size();
        BigDecimal totalOutstanding = outstandingPrincipal(activeLoans);

        boolean allowMultiple = group.isAllowMultipleLoans();
        int maxActive = allowMultiple ? (group.getMaxActiveLoans() > 0 ? group.getMaxActiveLoans() : 2) : 1;
        GroupMasterData activeRules = dataService.getCurrentLoanRules(group.getId()).orElse(null);
        LoanLimits limits = loanLimits(group, activeRules);
        BigDecimal maxPerMember = limits.perLoan();
        BigDecimal maxOutstanding = limits.outstanding();

        boolean hasOverdue = activeLoans.stream().anyMatch(l -> l.getOverdueInstallments() > 0);
        BigDecimal availableFund = groupFundService.getLendingCapacity(group.getId());
        boolean eligible = true;
        String reason = "Eligible to apply for a loan.";

        if (member.getStatus() != MemberStatus.ACTIVE) {
            eligible = false;
            reason = "Member profile is not active.";
        } else if (hasOverdue) {
            eligible = false;
            reason = "Overdue installments on an active loan must be cleared first.";
        } else if (!allowMultiple && activeLoanCount > 0) {
            eligible = false;
            reason = "Your group does not allow multiple active loans.";
        } else if (activeLoanCount >= maxActive) {
            eligible = false;
            reason = "Reached maximum limit of " + maxActive + " active loans.";
        } else if (totalOutstanding.compareTo(maxOutstanding) >= 0) {
            eligible = false;
            reason = "Current total outstanding loan balance has reached the group limit.";
        }

        java.util.Map<String, Object> res = new java.util.HashMap<>();
        res.put("eligible", eligible);
        res.put("reason", reason);
        res.put("activeLoanCount", activeLoanCount);
        res.put("activeLoans", activeLoans);
        res.put("totalOutstanding", totalOutstanding);
        res.put("allowMultipleLoans", allowMultiple);
        res.put("maxActiveLoans", maxActive);
        res.put("maxPerMember", maxPerMember);
        res.put("maxIndividualLoanLimit", maxPerMember);
        res.put("maxLoanAmountPerMember", maxPerMember);
        res.put("maxLoanAmount", limits.groupCapacity());
        res.put("maxGroupLoanCapacity", limits.groupCapacity());
        res.put("maxOutstandingLoanAmount", maxOutstanding);
        res.put("maxOutstandingLimit", maxOutstanding);
        res.put("availableGroupFund", availableFund);
        BigDecimal rate = defaultRate(group, activeRules);
        String interestType = defaultInterestType(group, activeRules);
        res.put("defaultInterestRate", rate);
        res.put("interestRate", rate);
        res.put("loanInterestType", interestType);
        res.put("interestType", interestType);
        return res;
    }
}
