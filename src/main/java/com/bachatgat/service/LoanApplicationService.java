package com.bachatgat.service;

import com.bachatgat.dto.LoanApplicationRequest;
import com.bachatgat.dto.LoanApprovalRequest;
import com.bachatgat.exception.InvalidFinancialOperationException;
import com.bachatgat.exception.InsufficientGroupFundsException;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import com.bachatgat.util.FinancialCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

        BigDecimal availableFund = groupFundService.getAvailableFund(group.getId());
        if (request.getRequestedAmount().compareTo(availableFund) > 0) {
            throw new InsufficientGroupFundsException(availableFund, request.getRequestedAmount());
        }

        // Check if member already has an active or pending loan application under review
        List<LoanApplication> existingPending = dataService.getLoanApplicationsByMemberId(memberId).stream()
                .filter(la -> la.getStatus() == LoanApplicationStatus.PENDING)
                .toList();
        if (!existingPending.isEmpty()) {
            throw new InvalidFinancialOperationException("You already have a pending loan application under review.");
        }

        // Fetch active loans of the member
        List<Loan> activeLoans = dataService.getLoansByMemberId(memberId).stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE && l.getOutstandingPrincipal().compareTo(BigDecimal.ZERO) > 0)
                .toList();

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

        // 3. Max Loan Amount Per Member / Per Loan Check
        GroupMasterData activeRules = dataService.getMasterDataForDate(group.getId(), LocalDate.now()).orElse(null);
        BigDecimal maxPerMember = activeRules != null && activeRules.getMaxLoanAmountPerMember() != null
                ? activeRules.getMaxLoanAmountPerMember()
                : (group.getMaxLoanAmountPerMember() != null ? group.getMaxLoanAmountPerMember() : group.getMaxLoanAmount());
        if (maxPerMember != null && request.getRequestedAmount().compareTo(maxPerMember) > 0) {
            throw new InvalidFinancialOperationException("Requested loan amount of ₹" + request.getRequestedAmount() + " exceeds the maximum allowed amount of ₹" + maxPerMember + " per loan.");
        }

        // 4. Max Total Outstanding Loan Amount Check
        BigDecimal currentOutstanding = activeLoans.stream()
                .map(Loan::getOutstandingPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal maxOutstanding = group.getMaxOutstandingLoanAmount();
        if (maxOutstanding != null && currentOutstanding.add(request.getRequestedAmount()).compareTo(maxOutstanding) > 0) {
            throw new InvalidFinancialOperationException("Total loan outstanding with this application (₹" + currentOutstanding.add(request.getRequestedAmount()) + ") exceeds the group maximum limit of ₹" + maxOutstanding + ".");
        }

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

        Group groupForLimit = dataService.findGroupById(app.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID: " + app.getGroupId()));
        GroupMasterData activeRules = dataService.getMasterDataForDate(app.getGroupId(), LocalDate.now()).orElse(null);
        BigDecimal maxPerMember = activeRules != null && activeRules.getMaxLoanAmountPerMember() != null
                ? activeRules.getMaxLoanAmountPerMember()
                : (groupForLimit.getMaxLoanAmountPerMember() != null
                    ? groupForLimit.getMaxLoanAmountPerMember() : groupForLimit.getMaxLoanAmount());
        if (maxPerMember != null && approvedPrincipal.compareTo(maxPerMember) > 0) {
            throw new InvalidFinancialOperationException("Approved loan amount of ₹" + approvedPrincipal
                    + " exceeds the maximum loan limit per member of ₹" + maxPerMember + ".");
        }

        BigDecimal resolvedRate = request.getInterestRate();
        if (resolvedRate == null) {
            GroupMasterData md = dataService.getLatestMasterDataByGroupId(app.getGroupId()).orElse(null);
            if (md != null && md.getLoanInterestRate() != null) {
                resolvedRate = md.getLoanInterestRate();
            } else {
                Group group = dataService.findGroupById(app.getGroupId()).orElse(null);
                resolvedRate = (group != null && group.getDefaultInterestRate() != null)
                        ? group.getDefaultInterestRate()
                        : BigDecimal.valueOf(12.0);
            }
        }
        final BigDecimal rate = resolvedRate;

        int durationMonths = (request.getDurationMonths() != null && request.getDurationMonths() > 0)
                ? request.getDurationMonths()
                : (app.getPreferredDurationMonths() > 0 ? app.getPreferredDurationMonths() : 12);

        String rawInterestType = request.getInterestType();
        if (rawInterestType == null || rawInterestType.isBlank()) {
            GroupMasterData md = dataService.getLatestMasterDataByGroupId(app.getGroupId()).orElse(null);
            rawInterestType = (md != null && md.getLoanInterestType() != null) ? md.getLoanInterestType() : "REDUCING";
        }
        String interestType = rawInterestType.trim().toUpperCase();

        BigDecimal totalInterest;
        BigDecimal totalPayable;
        BigDecimal monthlyInstallment;
        List<LoanRepaymentSchedule> schedules = new ArrayList<>();

        boolean isMonthlyRate = Boolean.TRUE.equals(request.getIsMonthlyRate()) ||
                (request.getIsMonthlyRate() == null && rate.compareTo(BigDecimal.valueOf(5.0)) <= 0);

        if ("REDUCING".equalsIgnoreCase(interestType) || "REDUCING_BALANCE".equalsIgnoreCase(interestType)) {
            interestType = "REDUCING";
            schedules = FinancialCalculator.generateBachatGatReducingSchedule(approvedPrincipal, rate, isMonthlyRate, durationMonths, LocalDate.now());
            monthlyInstallment = !schedules.isEmpty() ? schedules.get(0).getEmiAmount() : approvedPrincipal.divide(BigDecimal.valueOf(durationMonths), 0, RoundingMode.HALF_UP);
            totalInterest = schedules.stream()
                    .map(LoanRepaymentSchedule::getInterestAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalPayable = approvedPrincipal.add(totalInterest);
        } else {
            interestType = "FLAT";
            totalInterest = FinancialCalculator.calculateFlatInterest(approvedPrincipal, rate, durationMonths);
            totalPayable = approvedPrincipal.add(totalInterest);
            monthlyInstallment = FinancialCalculator.calculateFlatMonthlyPayment(approvedPrincipal, totalInterest, durationMonths);

            BigDecimal installmentPrincipal = approvedPrincipal.divide(BigDecimal.valueOf(durationMonths), 2, FinancialCalculator.ROUNDING);
            BigDecimal installmentInterest = totalInterest.divide(BigDecimal.valueOf(durationMonths), 2, FinancialCalculator.ROUNDING);

            BigDecimal runningPrincipal = approvedPrincipal;
            for (int i = 1; i <= durationMonths; i++) {
                LoanRepaymentSchedule item = new LoanRepaymentSchedule();
                item.setInstallmentNo(i);
                item.setDueDate(LocalDate.now().plusMonths(i));
                item.setOpeningPrincipal(runningPrincipal);
                item.setEmiAmount(monthlyInstallment);
                item.setPrincipalAmount(installmentPrincipal);
                item.setInterestAmount(installmentInterest);
                item.setPenaltyAmount(BigDecimal.ZERO);
                item.setTotalDue(monthlyInstallment);
                item.setPaidAmount(BigDecimal.ZERO);
                item.setOutstandingAmount(monthlyInstallment);
                runningPrincipal = runningPrincipal.subtract(installmentPrincipal).max(BigDecimal.ZERO);
                item.setClosingPrincipal(runningPrincipal);
                item.setStatus(i == 1 ? RepaymentStatus.DUE : RepaymentStatus.UPCOMING);
                schedules.add(item);
            }
        }

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
        loan.setPrincipalAmount(approvedPrincipal);
        loan.setInterestRate(rate);
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

        boolean disburseNow = request.getDisburseImmediately();
        if (disburseNow) {
            validateAvailableFund(app.getGroupId(), approvedPrincipal);
        }
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
        BigDecimal availableFund = groupFundService.getAvailableFund(groupId);
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

        List<Loan> activeLoans = dataService.getLoansByMemberId(memberId).stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE && l.getOutstandingPrincipal().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        int activeLoanCount = activeLoans.size();
        BigDecimal totalOutstanding = activeLoans.stream()
                .map(Loan::getOutstandingPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean allowMultiple = group.isAllowMultipleLoans();
        int maxActive = allowMultiple ? (group.getMaxActiveLoans() > 0 ? group.getMaxActiveLoans() : 2) : 1;
        GroupMasterData activeRules = dataService.getMasterDataForDate(group.getId(), LocalDate.now()).orElse(null);
        BigDecimal maxPerMember = activeRules != null && activeRules.getMaxLoanAmountPerMember() != null
                ? activeRules.getMaxLoanAmountPerMember()
                : (group.getMaxLoanAmountPerMember() != null ? group.getMaxLoanAmountPerMember() : (group.getMaxLoanAmount() != null ? group.getMaxLoanAmount() : BigDecimal.valueOf(50000)));
        BigDecimal maxOutstanding = group.getMaxOutstandingLoanAmount() != null ? group.getMaxOutstandingLoanAmount() : maxPerMember.multiply(BigDecimal.valueOf(maxActive));

        boolean hasOverdue = activeLoans.stream().anyMatch(l -> l.getOverdueInstallments() > 0);
        BigDecimal availableFund = groupFundService.getAvailableFund(group.getId());
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
        res.put("maxLoanAmount", group.getMaxLoanAmount() != null ? group.getMaxLoanAmount() : maxPerMember);
        res.put("maxGroupLoanCapacity", group.getMaxLoanAmount() != null ? group.getMaxLoanAmount() : maxPerMember);
        res.put("maxOutstandingLoanAmount", maxOutstanding);
        res.put("maxOutstandingLimit", maxOutstanding);
        res.put("availableGroupFund", availableFund);
        res.put("defaultInterestRate", group.getDefaultInterestRate() != null ? group.getDefaultInterestRate() : BigDecimal.valueOf(12.0));
        res.put("interestRate", group.getDefaultInterestRate() != null ? group.getDefaultInterestRate() : BigDecimal.valueOf(12.0));
        res.put("loanInterestType", group.getLoanInterestType() != null ? group.getLoanInterestType() : "REDUCING_BALANCE");
        res.put("interestType", group.getLoanInterestType() != null ? group.getLoanInterestType() : "REDUCING_BALANCE");
        return res;
    }
}
