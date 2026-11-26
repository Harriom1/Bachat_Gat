package com.bachatgat.service;

import com.bachatgat.dto.LoanApplicationRequest;
import com.bachatgat.dto.LoanApprovalRequest;
import com.bachatgat.dto.LoanRepaymentPreview;
import com.bachatgat.exception.InvalidFinancialOperationException;
import com.bachatgat.exception.InsufficientGroupFundsException;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import com.bachatgat.repository.LoanScheduleCalculatorSupport;

import java.util.Map;
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
        return previewRepayment(memberId, amount, durationMonths, null);
    }

    public LoanRepaymentPreview previewRepayment(String memberId, BigDecimal amount, int durationMonths, String topUpLoanId) {
        return previewRepayment(memberId, null, amount, durationMonths, topUpLoanId);
    }

    public LoanRepaymentPreview previewRepayment(String memberId, String groupId, BigDecimal amount, int durationMonths, String topUpLoanId) {
        LendingContext context = lendingContext(memberId, groupId);
        Loan source = topUpLoanId == null ? null : topUpSource(context, topUpLoanId);
        BigDecimal additional = positiveAmount(amount);
        validateDuration(durationMonths);
        validateLoanAmount(additional, context, source);
        return calculateRepayment(additional.add(sourcePrincipal(source)), defaultRate(context.group(), context.rules()),
                false, durationMonths, interestType(context, source), LoanScheduleCalculatorSupport.today(), null, null).preview();
    }

    /**
     * Submits a new loan application by the logged-in Member/User.
     */
    public LoanApplication submitApplication(String memberId, LoanApplicationRequest request) {
        return submitApplication(memberId, null, request);
    }

    public synchronized LoanApplication submitApplication(String memberId, String groupId, LoanApplicationRequest request) {
        LendingContext context = lendingContext(memberId, groupId);
        Member member = context.member();
        Group group = context.group();
        BigDecimal additional = positiveAmount(request.getRequestedAmount());
        validateDuration(request.getPreferredDurationMonths());
        Loan source = request.getTopUpLoanId() == null ? null : topUpSource(context, request.getTopUpLoanId());
        validateBorrower(context, source);
        validateLoanAmount(additional, context, source);
        validateAvailableFund(group.getId(), additional);

        if (dataService.getLoanApplicationsByMemberId(memberId).stream()
                .anyMatch(app -> app.getGroupId().equals(group.getId()) && app.getStatus() == LoanApplicationStatus.PENDING)) {
            throw new InvalidFinancialOperationException("You already have a pending loan application under review.");
        }

        String appId = "LA-" + LocalDate.now().getYear() + "-" + String.format("%03d", (dataService.getLoanApplicationsByGroupId(member.getGroupId()).size() + 1));
        LoanApplication app = new LoanApplication();
        app.setApplicationId(appId);
        app.setGroupId(member.getGroupId());
        app.setGroupName(member.getGroupName());
        app.setMemberId(member.getMemberId());
        app.setMemberName(member.getFullName());
        app.setRequestedAmount(additional);
        if (source != null) app.setTopUpLoanId(source.getId());
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
    public synchronized Loan approveApplication(String applicationId, LoanApprovalRequest request, String adminUsername) {
        var approval = dataService.beginLoanApproval(getApplicationById(applicationId));
        LoanApplication app = approval.application();
        if (app.getStatus() != LoanApplicationStatus.PENDING) {
            throw new InvalidFinancialOperationException("Application is already " + app.getStatus());
        }
        LendingContext context = lendingContext(approval.member(), approval.group());
        Loan selected = app.getTopUpLoanId() == null ? null : topUpSource(context, app.getTopUpLoanId());
        var source = selected == null ? null : dataService.beginLoanPayment(selected);
        Loan previous = source == null ? null : source.loan();
        validateBorrower(context, previous);
        LocalDate today = LoanScheduleCalculatorSupport.today();
        List<LoanRepaymentSchedule> previousRows = previous == null ? List.of()
                : dataService.getLoanScheduleForSettlement(source, today);
        if (previous != null) {
            if (!LoanScheduleCalculatorSupport.isServicing(previous) || previous.getOutstandingPrincipal().signum() <= 0) {
                throw new InvalidFinancialOperationException("The loan selected for top-up is no longer active.");
            }
            if (previousRows.stream().anyMatch(row -> !row.getDueDate().isAfter(today)
                    && row.getTotalDue().compareTo(row.getPaidAmount()) > 0)) {
                throw new InvalidFinancialOperationException("Settle all due installments and penalties before approving a top-up.");
            }
            if (request.getExpectedOutstandingPrincipal() == null
                    || request.getExpectedOutstandingPrincipal().compareTo(previous.getOutstandingPrincipal()) != 0) {
                throw new InvalidFinancialOperationException("The outstanding principal changed. Refresh the top-up calculation before approval.");
            }
        }
        ApprovalTerms terms = approvalTerms(context, previous, request);
        LocalDate disbursementDate = terms.start().isAfter(today) ? today : terms.start();
        if (previous != null && dataService.getGroupTransactionsForLending(app.getGroupId()).stream()
                .anyMatch(entry -> previous.getLoanId().equals(entry.getLoanId()) && entry.getDate() != null
                        && entry.getDate().isAfter(disbursementDate))) {
            throw new InvalidFinancialOperationException("Top-up start date cannot precede a recorded loan transaction.");
        }
        boolean disburseNow = request.getDisburseImmediately();
        if (previous != null && !disburseNow) {
            throw new InvalidFinancialOperationException("A top-up must be disbursed when its existing loan is settled.");
        }
        if (disburseNow) validateAvailableFund(app.getGroupId(), terms.additional());

        RepaymentPlan plan = repaymentPlan(terms);
        Loan loan = new Loan();
        loan.setId("loan-" + UUID.randomUUID());
        loan.setLoanId("LN-" + today.getYear() + "-" + String.format("%03d", context.groupLoans().size() + 1));
        loan.setApplicationId(app.getApplicationId());
        loan.setGroupId(app.getGroupId());
        loan.setGroupName(app.getGroupName());
        loan.setMemberId(app.getMemberId());
        loan.setMemberName(app.getMemberName());
        loan.setPurpose(app.getPurpose() != null ? app.getPurpose() : "Member Personal / Household Need");
        loan.setChequeNumber(normalizeChequeNumber(request.getChequeNumber()));
        loan.setPrincipalAmount(terms.principal());
        loan.setInterestRate(terms.rate());
        loan.setInterestRateBasis(terms.monthly() ? "MONTHLY" : "ANNUAL");
        loan.setDurationMonths(terms.months());
        loan.setInterestType(terms.type());
        loan.setTotalInterest(plan.preview().totalInterest());
        loan.setTotalPayable(plan.preview().totalRepayment());
        loan.setOutstandingPrincipal(terms.principal());
        loan.setOutstandingInterest(plan.preview().totalInterest());
        loan.setTotalOutstanding(plan.preview().totalRepayment());
        loan.setMonthlyInstallment(plan.preview().monthlyEmi());
        loan.setStartDate(terms.start());
        loan.setFirstEmiDueDate(terms.firstDue());
        loan.setEmiDueDay(terms.dueDay());
        loan.setNextDueDate(terms.firstDue());
        loan.setEmiLatePenalty(terms.penalty());
        loan.setCreatedBy(adminUsername);
        loan.setStatus(disburseNow ? LoanStatus.ACTIVE : LoanStatus.APPROVED);
        loan.setDisbursementDate(disburseNow ? disbursementDate : null);

        app.setApprovedAmount(terms.additional());
        app.setApprovedInterestRate(terms.rate());
        app.setApprovedDurationMonths(terms.months());
        app.setInterestType(terms.type());
        app.setAdminNotes(request.getAdminNotes());
        app.setReviewedBy(adminUsername);
        app.setReviewedAt(LocalDateTime.now());
        app.setUpdatedAt(app.getReviewedAt());
        app.setStatus(LoanApplicationStatus.APPROVED);
        app.setGeneratedLoanId(loan.getLoanId());

        List<LoanRepaymentSchedule> rows = plan.schedules();
        for (LoanRepaymentSchedule row : rows) {
            row.setId("sch-" + loan.getId() + "-" + row.getInstallmentNo());
            row.setLoanId(loan.getId());
            row.setGroupId(loan.getGroupId());
            row.setMemberId(loan.getMemberId());
        }
        List<Transaction> entries = new ArrayList<>();
        BigDecimal previousOutstanding = source == null ? BigDecimal.ZERO : source.originalOutstanding();
        BigDecimal transferred = sourcePrincipal(previous);
        if (previous != null) {
            entries.add(settleForTopUp(previous, loan, transferred, disbursementDate, adminUsername));
            previousRows.stream().filter(row -> row.getStatus() != RepaymentStatus.PAID).forEach(row -> {
                row.setStatus(RepaymentStatus.SETTLED);
                row.setOutstandingAmount(BigDecimal.ZERO);
                row.setPendingPrincipal(BigDecimal.ZERO);
                row.setPendingInterest(BigDecimal.ZERO);
                row.setRemainingPendingPrincipal(BigDecimal.ZERO);
                row.setRemainingPendingInterest(BigDecimal.ZERO);
            });
        }
        if (disburseNow) {
            entries.add(approvalEntry(loan, TransactionType.LOAN_DISBURSEMENT, terms.additional(),
                    loan.getDisbursementDate(), "DISB-" + loan.getLoanId(),
                    previous == null ? "Loan disbursement" : "Additional cash disbursed for loan top-up",
                    "BANK_TRANSFER", adminUsername));
        }
        BigDecimal groupDelta = loan.getTotalOutstanding().subtract(previousOutstanding);
        BigDecimal memberOutstanding = context.memberLoans().stream()
                .filter(LoanScheduleCalculatorSupport::isServicing)
                .filter(existing -> selected == null || !existing.getId().equals(selected.getId()))
                .map(Loan::getTotalOutstanding).reduce(loan.getTotalOutstanding(), BigDecimal::add);
        String action = previous != null ? "LOAN_TOP_UP_APPROVED"
                : disburseNow ? "LOAN_APPROVED_AND_DISBURSED" : "LOAN_APPROVED";
        AuditLog audit = new AuditLog(app.getGroupId(), adminUsername, adminUsername, action, "LOAN", loan.getLoanId(),
                previous == null ? "Requested " + app.getRequestedAmount()
                        : previous.getLoanId() + " principal transferred: " + transferred,
                "Principal " + terms.principal() + ", additional " + terms.additional() + ", start " + terms.start()
                        + ", first EMI " + terms.firstDue() + ", penalty " + terms.penalty()
                        + ", duration " + terms.months() + ", rate " + terms.rate(), "127.0.0.1");
        dataService.confirmLoanApproval(approval, loan, rows, source, previousRows, entries, audit,
                groupDelta, disburseNow ? memberOutstanding : null);

        dataService.findUserByMemberId(app.getMemberId()).ifPresent(user ->
                notificationService.sendNotification(app.getGroupId(), user.getId(), "USER",
                        previous != null ? "Loan top-up approved" : disburseNow ? "Loan sanctioned and disbursed" : "Loan sanctioned",
                        "Approved amount: ₹" + terms.additional() + ". Loan ID: " + loan.getLoanId(), "LOAN_APPROVED"));
        return loan;
    }

    private Transaction settleForTopUp(Loan previous, Loan replacement, BigDecimal amount, LocalDate date, String admin) {
        replacement.setRefinancedLoanId(previous.getLoanId());
        replacement.setTopUpAmount(replacement.getPrincipalAmount().subtract(amount));
        previous.setReplacementLoanId(replacement.getLoanId());
        previous.setSettlementDate(date);
        previous.setStatus(LoanStatus.CLOSED);
        previous.setOutstandingPrincipal(BigDecimal.ZERO);
        previous.setOutstandingInterest(BigDecimal.ZERO);
        previous.setOutstandingPenalty(BigDecimal.ZERO);
        previous.setPendingPrincipal(BigDecimal.ZERO);
        previous.setPendingInterest(BigDecimal.ZERO);
        previous.setTotalOutstanding(BigDecimal.ZERO);
        previous.setNextDueDate(null);
        previous.setOverdueInstallments(0);
        previous.setUpdatedAt(LocalDateTime.now());
        Transaction entry = approvalEntry(previous, TransactionType.ADJUSTMENT, amount, date,
                "TOPUP-" + replacement.getLoanId(), "Outstanding principal transferred to " + replacement.getLoanId()
                        + "; future interest ended at settlement", "NON_CASH", admin);
        entry.setCategory("LOAN_TOP_UP_SETTLEMENT");
        return entry;
    }

    private Transaction approvalEntry(Loan loan, TransactionType type, BigDecimal amount, LocalDate date,
                                      String reference, String description, String method, String admin) {
        return new Transaction("TXN-" + UUID.randomUUID(), loan.getGroupId(), loan.getMemberId(), loan.getMemberName(),
                loan.getLoanId(), type, amount, amount, BigDecimal.ZERO, date, reference, description, method, admin);
    }

    public Map<String, Object> getApprovalDetails(LoanApplication application) {
        LendingContext context = lendingContext(application.getMemberId(), application.getGroupId());
        Loan source = application.getTopUpLoanId() == null ? null : topUpSource(context, application.getTopUpLoanId());
        Group group = context.group();
        Map<String, Object> details = new java.util.HashMap<>(Map.of(
                "maxAdditional", maximumAdditional(context, source), "outstandingPrincipal", sourcePrincipal(source),
                "interestRate", defaultRate(group, context.rules()), "interestType", interestType(context, source),
                "dueDay", group.getLoanDueDay(), "penalty", group.getLoanLatePenalty(),
                "today", LoanScheduleCalculatorSupport.today().toString()));
        if (group.getDefaultLoanStartDate() != null && group.getDefaultFirstEmiDueDate() != null) {
            details.put("defaultLoanStartDate", group.getDefaultLoanStartDate().toString());
            details.put("defaultFirstEmiDueDate", group.getDefaultFirstEmiDueDate().toString());
        }
        return details;
    }

    public Map<String, Object> previewApproval(LoanApplication application, LoanApprovalRequest request) {
        LendingContext context = lendingContext(application.getMemberId(), application.getGroupId());
        Loan source = application.getTopUpLoanId() == null ? null : topUpSource(context, application.getTopUpLoanId());
        ApprovalTerms terms = approvalTerms(context, source, request);
        RepaymentPlan plan = repaymentPlan(terms);
        return Map.of("principal", terms.principal(), "outstandingPrincipal", sourcePrincipal(source),
                "summary", plan.preview(), "schedule", plan.schedules().stream().map(ApprovalRow::from).toList());
    }

    public record ApprovalRow(int installmentNo, LocalDate dueDate, BigDecimal emiAmount,
                              BigDecimal principalAmount, BigDecimal interestAmount, BigDecimal closingPrincipal) {
        private static ApprovalRow from(LoanRepaymentSchedule row) {
            return new ApprovalRow(row.getInstallmentNo(), row.getDueDate(), row.getEmiAmount(),
                    row.getPrincipalAmount(), row.getInterestAmount(), row.getClosingPrincipal());
        }
    }

    private record ApprovalTerms(BigDecimal additional, BigDecimal principal, BigDecimal rate, boolean monthly,
                                 int months, String type, LocalDate start, LocalDate firstDue, int dueDay, BigDecimal penalty) {}

    private ApprovalTerms approvalTerms(LendingContext context, Loan source, LoanApprovalRequest request) {
        BigDecimal additional = positiveAmount(request.getApprovedAmount());
        validateLoanAmount(additional, context, source);
        validateDuration(request.getDurationMonths());
        BigDecimal rate = request.getInterestRate();
        if (rate == null || rate.signum() < 0 || rate.scale() > 2) {
            throw new InvalidFinancialOperationException("Interest rate must be non-negative with at most two decimals.");
        }
        LocalDate start = request.getStartDate();
        LocalDate firstDue = request.getFirstEmiDueDate();
        if (start == null || firstDue == null || !firstDue.isAfter(start)) {
            throw new InvalidFinancialOperationException("Select a loan start date and a first EMI due date after it.");
        }
        int dueDay = request.getEmiDueDay() == null ? firstDue.getDayOfMonth() : request.getEmiDueDay();
        if (dueDay < 1 || dueDay > 31 || !FinancialCalculator.monthlyDueDate(firstDue, dueDay, 0).equals(firstDue)) {
            throw new InvalidFinancialOperationException("The first EMI date must match the selected recurring due day.");
        }
        LocalDate sourceStart = source == null ? null : source.getStartDate() != null ? source.getStartDate() : source.getDisbursementDate();
        if (sourceStart != null && start.isBefore(sourceStart)) {
            throw new InvalidFinancialOperationException("Top-up start cannot precede the existing loan start.");
        }
        BigDecimal penalty = request.getEmiLatePenalty();
        if (penalty == null || penalty.signum() < 0 || penalty.scale() > 2) {
            throw new InvalidFinancialOperationException("Select an EMI penalty with at most two decimals. Use zero for no penalty.");
        }
        String type = source != null || request.getInterestType() == null || request.getInterestType().isBlank()
                ? interestType(context, source) : normalizeInterestType(request.getInterestType());
        return new ApprovalTerms(additional, additional.add(sourcePrincipal(source)), rate,
                Boolean.TRUE.equals(request.getIsMonthlyRate()), request.getDurationMonths(), type, start, firstDue, dueDay, penalty);
    }

    private RepaymentPlan repaymentPlan(ApprovalTerms terms) {
        return calculateRepayment(terms.principal(), terms.rate(), terms.monthly(), terms.months(), terms.type(),
                terms.start(), terms.firstDue(), terms.dueDay());
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
                                                    int months, String interestType, LocalDate startDate, LocalDate firstDueDate, Integer dueDay) {
        List<LoanRepaymentSchedule> schedule = "FLAT".equals(interestType)
                ? flatSchedule(principal, monthlyRate ? rate.multiply(BigDecimal.valueOf(12)) : rate, months, startDate)
                : FinancialCalculator.generateAmortizationSchedule(principal, rate, monthlyRate, months, startDate);
        if (firstDueDate != null) {
            schedule.forEach(row -> row.setDueDate(FinancialCalculator.monthlyDueDate(firstDueDate, dueDay, row.getInstallmentNo() - 1)));
        }
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

    private record LendingContext(Member member, Group group, GroupMasterData rules, List<Loan> groupLoans,
                                  List<Loan> memberLoans, BigDecimal groupLimit, BigDecimal memberLimit,
                                  BigDecimal outstandingLimit) {}

    private LendingContext lendingContext(String memberId, String groupId) {
        Member member = dataService.getMemberForLending(memberId, groupId);
        return lendingContext(member, dataService.getGroupForLending(member.getGroupId()));
    }

    private LendingContext lendingContext(Member member, Group group) {
        GroupMasterData rules = dataService.getCurrentLoanRules(group.getId()).orElse(null);
        List<Loan> groupLoans = dataService.getGroupLoansForLending(group.getId());
        List<Loan> memberLoans = groupLoans.stream().filter(LoanApplicationService::countsTowardLimits)
                .filter(loan -> member.getMemberId().equals(loan.getMemberId())).toList();
        BigDecimal groupLimit = limit(rules == null ? null : rules.getMaxLoanAmount(), group.getMaxLoanAmount());
        BigDecimal memberLimit = limit(member.getLoanCapacity(),
                limit(rules == null ? null : rules.getMaxLoanAmountPerMember(), group.getMaxLoanAmountPerMember()));
        BigDecimal outstandingLimit = limit(rules == null ? null : rules.getMaxOutstandingLoanAmount(), group.getMaxOutstandingLoanAmount());
        return new LendingContext(member, group, rules, groupLoans, memberLoans, groupLimit, memberLimit, outstandingLimit);
    }

    private static BigDecimal limit(BigDecimal preferred, BigDecimal fallback) {
        return preferred != null ? preferred : fallback != null ? fallback : Group.DEFAULT_LOAN_LIMIT;
    }

    private static boolean countsTowardLimits(Loan loan) {
        return LoanScheduleCalculatorSupport.isServicing(loan) || loan.getStatus() == LoanStatus.APPROVED;
    }

    private static BigDecimal positiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new InvalidFinancialOperationException("Loan amount must be positive with at most two decimals.");
        }
        return FinancialCalculator.round(amount);
    }

    private static BigDecimal sourcePrincipal(Loan source) {
        return source == null ? BigDecimal.ZERO : source.getOutstandingPrincipal();
    }

    private static BigDecimal outstandingPrincipal(List<Loan> loans) {
        return loans.stream().map(Loan::getOutstandingPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal maximumAdditional(LendingContext context, Loan source) {
        BigDecimal memberOpen = outstandingPrincipal(context.memberLoans());
        BigDecimal groupOpen = context.groupLoans().stream().filter(LoanApplicationService::countsTowardLimits)
                .map(Loan::getOutstandingPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal individualExposure = context.member().getLoanCapacity() == null ? sourcePrincipal(source) : memberOpen;
        return context.memberLimit().subtract(individualExposure)
                .min(context.outstandingLimit().subtract(memberOpen))
                .min(context.groupLimit().subtract(groupOpen)).max(BigDecimal.ZERO);
    }

    private void validateLoanAmount(BigDecimal amount, LendingContext context, Loan source) {
        BigDecimal maximum = maximumAdditional(context, source);
        if (amount.compareTo(maximum) > 0) {
            throw new InvalidFinancialOperationException("The requested amount exceeds the current lending limit of ₹" + maximum + ".");
        }
    }

    private Loan topUpSource(LendingContext context, String loanId) {
        return context.memberLoans().stream().filter(LoanScheduleCalculatorSupport::isServicing)
                .filter(loan -> loanId.equals(loan.getId()))
                .filter(loan -> sourcePrincipal(loan).signum() > 0)
                .findFirst().orElseThrow(() -> new InvalidFinancialOperationException("Select one of your active loans for a top-up."));
    }

    private int maxActiveLoans(Group group) {
        return group.isAllowMultipleLoans() ? (group.getMaxActiveLoans() > 0 ? group.getMaxActiveLoans() : 2) : 1;
    }

    private String borrowingBlock(LendingContext context, Loan source) {
        if (context.member().getStatus() != MemberStatus.ACTIVE) return "Member profile is not active.";
        if (context.memberLoans().stream().anyMatch(loan -> loan.getStatus() == LoanStatus.OVERDUE || loan.getOverdueInstallments() > 0)) {
            return "Overdue loan installments must be cleared first.";
        }
        if (source == null && context.memberLoans().size() >= maxActiveLoans(context.group())) {
            return context.group().isAllowMultipleLoans() ? "Reached the group limit for active or approved loans."
                    : "Your group allows one active or approved loan. You can request a top-up on an active loan.";
        }
        return null;
    }

    private void validateBorrower(LendingContext context, Loan source) {
        String blocked = borrowingBlock(context, source);
        if (blocked != null) throw new InvalidFinancialOperationException(blocked);
    }

    private static String interestType(LendingContext context, Loan source) {
        return source != null ? "REDUCING" : normalizeInterestType(defaultInterestType(context.group(), context.rules()));
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
        loan.setNextDueDate(loan.getFirstEmiDueDate() != null ? loan.getFirstEmiDueDate() : disbDate.plusMonths(1));
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
        BigDecimal availableFund = groupFundService.getCurrentLendingCapacity(groupId);
        if (requestedAmount.compareTo(availableFund) > 0) {
            throw new InsufficientGroupFundsException(availableFund, requestedAmount);
        }
    }

    public synchronized LoanApplication rejectApplication(String applicationId, String rejectionReason, String adminUsername) {
        var review = dataService.beginLoanApproval(getApplicationById(applicationId));
        LoanApplication app = review.application();

        if (app.getStatus() != LoanApplicationStatus.PENDING) {
            throw new InvalidFinancialOperationException("Application is already " + app.getStatus());
        }

        app.setStatus(LoanApplicationStatus.REJECTED);
        app.setRejectionReason(rejectionReason);
        app.setReviewedBy(adminUsername);
        app.setReviewedAt(LocalDateTime.now());

        app.setUpdatedAt(app.getReviewedAt());
        AuditLog audit = new AuditLog(app.getGroupId(), adminUsername, adminUsername, "LOAN_REJECTED", "LOAN_APPLICATION",
                app.getApplicationId(), "PENDING", "REJECTED: " + rejectionReason, "127.0.0.1");
        LoanApplication saved = dataService.rejectLoanApplication(review, audit);

        // Notify member
        dataService.findUserByMemberId(app.getMemberId()).ifPresent(u ->
                notificationService.sendNotification(app.getGroupId(), u.getId(), "USER",
                        "Loan Application Update",
                        "Your loan application has been rejected. Reason: " + rejectionReason,
                        "LOAN_REJECTED")
        );

        return saved;
    }

    public Map<String, Object> getLoanEligibility(String memberId) {
        return getLoanEligibility(memberId, null);
    }

    public Map<String, Object> getLoanEligibility(String memberId, String groupId) {
        LendingContext context = lendingContext(memberId, groupId);
        List<Loan> active = context.memberLoans().stream().filter(LoanScheduleCalculatorSupport::isServicing).toList();
        BigDecimal maximum = maximumAdditional(context, null);
        String blocked = borrowingBlock(context, null);
        if (blocked == null && maximum.signum() == 0) blocked = "The available lending limit has been reached.";
        var topUps = active.stream().filter(loan -> sourcePrincipal(loan).signum() > 0)
                .map(loan -> Map.of("id", loan.getId(), "loanId", loan.getLoanId(),
                        "outstandingPrincipal", sourcePrincipal(loan), "maxAdditional", maximumAdditional(context, loan),
                        "eligible", borrowingBlock(context, loan) == null && maximumAdditional(context, loan).signum() > 0))
                .toList();
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("eligible", blocked == null);
        result.put("reason", blocked == null ? "Eligible to apply for a loan." : blocked);
        result.put("activeLoanCount", active.size());
        result.put("activeLoans", active);
        result.put("topUpOptions", topUps);
        result.put("totalOutstanding", outstandingPrincipal(active));
        result.put("allowMultipleLoans", context.group().isAllowMultipleLoans());
        result.put("maxActiveLoans", maxActiveLoans(context.group()));
        result.put("maxPerMember", context.memberLimit());
        result.put("maxIndividualLoanLimit", context.memberLimit());
        result.put("maxLoanAmountPerMember", context.memberLimit());
        result.put("maxAdditional", maximum);
        result.put("maxLoanAmount", context.groupLimit());
        result.put("maxGroupLoanCapacity", context.groupLimit());
        result.put("maxOutstandingLoanAmount", context.outstandingLimit());
        result.put("maxOutstandingLimit", context.outstandingLimit());
        result.put("availableGroupFund", groupFundService.getCurrentLendingCapacity(context.group().getId()));
        BigDecimal rate = defaultRate(context.group(), context.rules());
        String type = interestType(context, null);
        result.put("defaultInterestRate", rate);
        result.put("interestRate", rate);
        result.put("loanInterestType", type);
        result.put("interestType", type);
        return result;
    }
}
