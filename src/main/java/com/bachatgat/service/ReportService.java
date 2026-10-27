package com.bachatgat.service;

import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;

@Service
public class ReportService {

    private final FirestoreDataService dataService;
    private final GroupFundService groupFundService;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;

    @Value("${spring.mail.host:}")
    private String smtpHost;
    @Value("${spring.mail.username:}")
    private String smtpUsername;
    @Value("${spring.mail.password:}")
    private String smtpPassword;
    @Value("${report.delivery.email.from:}")
    private String reportEmailFrom;
    @Value("${report.delivery.whatsapp.enabled:false}")
    private boolean whatsappReportsEnabled;
    @Value("${report.delivery.whatsapp.graph-version:v22.0}")
    private String whatsappGraphVersion;
    @Value("${report.delivery.whatsapp.phone-number-id:}")
    private String whatsappPhoneNumberId;
    @Value("${report.delivery.whatsapp.access-token:}")
    private String whatsappAccessToken;
    @Value("${report.delivery.whatsapp.default-country-code:91}")
    private String whatsappDefaultCountryCode;

    public ReportService(FirestoreDataService dataService, GroupFundService groupFundService,
                         ObjectProvider<JavaMailSender> mailSenderProvider,
                         ObjectMapper objectMapper) {
        this.dataService = dataService;
        this.groupFundService = groupFundService;
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getDeliveryStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("emailConfigured", isEmailConfigured());
        status.put("whatsappConfigured", isWhatsAppConfigured());
        return status;
    }

    public Map<String, Object> getMonthlyReport(String groupId, int month, int year) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12.");
        }
        Map<String, Object> report = new HashMap<>();
        Group group = dataService.findGroupById(groupId).orElse(null);
        report.put("group", group);
        report.put("month", month);
        report.put("year", year);

        YearMonth selected = YearMonth.of(year, month);
        LocalDate start = selected.atDay(1);
        LocalDate endExclusive = selected.plusMonths(1).atDay(1);

        List<CollectionRecord> monthCollections = dataService.getCollectionsByGroupId(groupId).stream()
                .filter(c -> c.getMonth() == month && c.getYear() == year)
                .toList();
        report.put("collections", monthCollections);
        List<Member> groupMembers = dataService.getMembersByGroupId(groupId).stream()
                .filter(m -> m.getStatus() == null || m.getStatus() != MemberStatus.EXITED)
                .toList();
        report.put("members", groupMembers);
        report.put("loans", dataService.getLoansByGroupId(groupId).stream()
                .filter(l -> l.getStatus() != LoanStatus.CANCELLED).toList());

        BigDecimal expected = monthCollections.stream().map(c -> nz(c.getExpectedAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collected = monthCollections.stream().map(c -> nz(c.getPaidAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pending = monthCollections.stream().map(c -> nz(c.getPendingAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthlyBachatPenalty = monthCollections.stream().map(c -> nz(c.getLateFeeAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        GroupMasterData applicableRules = dataService.getMasterDataForDate(groupId, start).orElse(null);

        List<Transaction> monthTransactions = dataService.getTransactionsByGroupId(groupId).stream()
                .filter(t -> t.getDate() != null && !t.getDate().isBefore(start) && t.getDate().isBefore(endExclusive))
                .filter(t -> t.getPaymentStatus() == null || "SUCCESS".equalsIgnoreCase(t.getPaymentStatus()))
                .toList();
        List<GroupIncome> monthIncomes = dataService.getIncomesByGroupId(groupId).stream()
                .filter(i -> i.getIncomeDate() != null && !i.getIncomeDate().isBefore(start) && i.getIncomeDate().isBefore(endExclusive))
                .toList();
        List<GroupExpense> monthExpenses = dataService.getExpensesByGroupId(groupId).stream()
                .filter(e -> e.getExpenseDate() != null && !e.getExpenseDate().isBefore(start) && e.getExpenseDate().isBefore(endExclusive))
                .toList();

        BigDecimal principalRecovered = monthTransactions.stream()
                .filter(t -> isLoanPayment(t))
                .map(t -> nz(t.getPrincipalAmount()).signum() > 0 ? nz(t.getPrincipalAmount()) : nz(t.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal interestIncome = monthTransactions.stream()
                .filter(t -> isLoanPayment(t))
                .map(t -> nz(t.getInterestAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal penaltyIncome = sumTransactions(monthTransactions, TransactionType.LOAN_PENALTY_INCOME, TransactionType.SHARE_LATE_FEE, TransactionType.PENALTY_INCOME);
        BigDecimal otherIncome = sumTransactions(monthTransactions, TransactionType.OTHER_INCOME);
        BigDecimal loanDisbursement = sumTransactions(monthTransactions, TransactionType.LOAN_DISBURSEMENT);
        BigDecimal expenses = monthExpenses.stream().map(e -> nz(e.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Loan> loansGiven = dataService.getLoansByGroupId(groupId).stream()
                .filter(l -> l.getDisbursementDate() != null && !l.getDisbursementDate().isBefore(start) && l.getDisbursementDate().isBefore(endExclusive))
                .toList();
        List<Loan> activeLoans = dataService.getLoansByGroupId(groupId).stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE || l.getStatus() == LoanStatus.PARTIALLY_PAID || l.getStatus() == LoanStatus.OVERDUE)
                .toList();
        BigDecimal outstandingLoan = activeLoans.stream().map(l -> nz(l.getOutstandingPrincipal())).reduce(BigDecimal.ZERO, BigDecimal::add);

        report.put("openingFund", groupFundService.getAvailableFundBefore(groupId, start));
        report.put("monthlyCollection", collected);
        report.put("loanPrincipalRecovered", principalRecovered);
        report.put("loanInterestIncome", interestIncome);
        report.put("loanPenaltyIncome", penaltyIncome);
        report.put("otherIncome", otherIncome);
        report.put("loanDisbursement", loanDisbursement);
        report.put("monthlyExpenses", expenses);
        // Keep this identical to the dashboard's current available-fund value.
        report.put("remainingFund", groupFundService.getAvailableFund(groupId));
        report.put("outstandingLoanPrincipal", outstandingLoan);
        report.put("transactions", monthTransactions);
        report.put("incomes", monthIncomes);
        report.put("expenses", monthExpenses);
        report.put("loanPayments", monthTransactions.stream().filter(t -> t.getType() != null && (t.getType().name().contains("LOAN_"))).toList());
        report.put("loansGiven", loansGiven);
        report.put("loansGivenThisMonth", !loansGiven.isEmpty());
        report.put("savingsPaidMembers", monthCollections.stream().filter(c -> c.getStatus() == CollectionStatus.PAID || c.getStatus() == CollectionStatus.LATE).map(CollectionRecord::getMemberId).toList());
        report.put("pendingShareMembers", monthCollections.stream().filter(c -> c.getStatus() != CollectionStatus.PAID && c.getStatus() != CollectionStatus.LATE).map(CollectionRecord::getMemberId).toList());

        Map<String, CollectionRecord> monthlyCollectionsByMember = monthCollections.stream()
                .filter(c -> c.getMemberId() != null)
                .collect(java.util.stream.Collectors.toMap(CollectionRecord::getMemberId, c -> c, (first, duplicate) -> first));
        List<Map<String, Object>> paidMonthlyBachat = new ArrayList<>();
        List<Map<String, Object>> unpaidMonthlyBachat = new ArrayList<>();
        for (Member member : groupMembers) {
            CollectionRecord collection = monthlyCollectionsByMember.get(member.getMemberId());
            boolean paid = collection != null
                    && (collection.getStatus() == CollectionStatus.PAID || collection.getStatus() == CollectionStatus.LATE);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("memberName", member.getFullName());
            row.put("monthlyBachat", collection != null
                    ? nz(collection.getExpectedAmount())
                    : nz(member.getMonthlyBachatAmount()));
            row.put("status", paid ? "PAID" : "NOT PAID");
            (paid ? paidMonthlyBachat : unpaidMonthlyBachat).add(row);
        }
        report.put("paidMonthlyBachat", paidMonthlyBachat);
        report.put("unpaidMonthlyBachat", unpaidMonthlyBachat);
        report.put("memberLoanLedger", groupMembers.stream()
                .map(m -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    List<CollectionRecord> memberCollections = dataService.getCollectionsByMemberId(m.getMemberId());
                    CollectionRecord selectedCollection = memberCollections.stream()
                            .filter(c -> c.getMonth() == month && c.getYear() == year)
                            .findFirst()
                            .orElse(null);
                    BigDecimal monthToDateSavings = memberCollections.stream()
                            .filter(c -> c.getYear() < year
                                    || (c.getYear() == year && c.getMonth() <= month))
                            .map(c -> nz(c.getPaidAmount()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    row.put("memberId", m.getMemberId());
                    row.put("memberName", m.getFullName());
                    row.put("monthlyBachat", selectedCollection != null
                            ? nz(selectedCollection.getExpectedAmount())
                            : nz(m.getMonthlyBachatAmount()));
                    row.put("totalSavings", monthToDateSavings);
                    row.put("loanOutstanding", m.getCurrentLoanOutstanding());
                    row.put("loanId", m.getActiveLoanId());
                    return row;
                }).toList());

        // Complete member-wise report payload used by both member and admin report pages.
        // Keep historical members visible unless they explicitly exited the group.
        List<Map<String, Object>> memberReports = groupMembers.stream().map(member -> {
            Map<String, Object> memberReport = new java.util.LinkedHashMap<>();
            List<CollectionRecord> memberCollections = dataService.getCollectionsByMemberId(member.getMemberId());
            List<Loan> memberLoans = dataService.getLoansByMemberId(member.getMemberId()).stream()
                    .filter(l -> l.getStatus() != LoanStatus.CANCELLED).toList();
            List<Map<String, Object>> loanReports = memberLoans.stream().map(loan -> {
                Map<String, Object> loanReport = new java.util.LinkedHashMap<>();
                loanReport.put("loan", loan);
                loanReport.put("schedule", dataService.getLoanSchedule(loan.getId()));
                return loanReport;
            }).toList();
            List<Transaction> memberTransactions = dataService.getTransactionsByMemberId(member.getMemberId());
            memberReport.put("member", member);
            memberReport.put("collections", memberCollections);
            memberReport.put("loans", memberLoans);
            memberReport.put("loanReports", loanReports);
            memberReport.put("transactions", memberTransactions);
            memberReport.put("adjustments", dataService.getAdjustmentsByMemberId(member.getMemberId()));
            memberReport.put("totalSavings", nz(member.getTotalSavingsBalance()));
            memberReport.put("loanOutstanding", nz(member.getCurrentLoanOutstanding()));
            memberReport.put("monthlyBachat", nz(member.getMonthlyBachatAmount()));
            return memberReport;
        }).toList();
        report.put("memberReports", memberReports);

        report.put("totalExpected", expected);
        report.put("totalCollected", collected);
        report.put("totalPending", pending);
        report.put("monthlyBachatAmount", applicableRules != null ? applicableRules.getMonthlyBachatAmount() : BigDecimal.ZERO);
        report.put("totalExpectedMonthlyBachat", expected);
        report.put("totalMonthlyBachatCollected", collected);
        report.put("totalMonthlyBachatPending", pending);
        report.put("totalMonthlyBachatPenalty", monthlyBachatPenalty);

        return report;
    }

    private BigDecimal sumTransactions(List<Transaction> transactions, TransactionType... types) {
        java.util.Set<TransactionType> accepted = java.util.EnumSet.noneOf(TransactionType.class);
        java.util.Collections.addAll(accepted, types);
        return transactions.stream().filter(t -> accepted.contains(t.getType())).map(t -> nz(t.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isLoanPayment(Transaction transaction) {
        if (transaction == null || transaction.getType() == null) return false;
        return switch (transaction.getType()) {
            case LOAN_REPAYMENT, LOAN_PRINCIPAL_PAYMENT, LOAN_PRINCIPAL_REPAYMENT,
                    LOAN_INTEREST_PAYMENT, LOAN_INTEREST_INCOME, LOAN_INTEREST,
                    LOAN_PENALTY_INCOME, LOAN_EXTRA_PAYMENT, EXTRA_LOAN_PAYMENT -> true;
            default -> false;
        };
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public Map<String, Object> getGroupFinancialSummary(String groupId) {
        Map<String, Object> summary = new HashMap<>();
        Group group = dataService.findGroupById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID: " + groupId));

        summary.put("group", group);
        List<Member> members = dataService.getMembersByGroupId(groupId);
        List<Loan> loans = dataService.getLoansByGroupId(groupId);
        List<GroupExpense> expenses = dataService.getExpensesByGroupId(groupId);

        BigDecimal totalSavings = members.stream().filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .map(Member::getTotalSavingsBalance).map(this::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalShareContributions = members.stream().filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .map(Member::getCurrentShareBalance).map(this::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLoansDisbursed = loans.stream().map(Loan::getPrincipalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPrincipalRecovered = loans.stream().map(Loan::getPrincipalPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInterestCollected = loans.stream().map(Loan::getInterestPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpenses = expenses.stream().map(GroupExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstandingPrincipal = loans.stream().map(Loan::getOutstandingPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstandingInterest = loans.stream().map(Loan::getOutstandingInterest).reduce(BigDecimal.ZERO, BigDecimal::add);

        summary.put("totalSavings", totalSavings);
        summary.put("totalShareContributions", totalShareContributions);
        summary.put("totalLoansDisbursed", totalLoansDisbursed);
        summary.put("totalPrincipalRecovered", totalPrincipalRecovered);
        summary.put("totalInterestCollected", totalInterestCollected);
        summary.put("totalExpenses", totalExpenses);
        summary.put("outstandingPrincipal", outstandingPrincipal);
        summary.put("outstandingInterest", outstandingInterest);
        summary.put("totalOutstanding", outstandingPrincipal.add(outstandingInterest));

        return summary;
    }

    public Map<String, Object> getMemberStatement(String memberId) {
        Map<String, Object> statement = new HashMap<>();
        Member member = dataService.findMemberByMemberId(memberId)
                .or(() -> dataService.findMemberById(memberId))
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with ID: " + memberId));

        Group group = dataService.findGroupById(member.getGroupId()).orElse(null);
        statement.put("group", group);
        statement.put("member", member);

        List<CollectionRecord> collections = dataService.getCollectionsByMemberId(member.getMemberId());
        statement.put("collections", collections);

        List<Loan> loans = dataService.getLoansByMemberId(member.getMemberId());
        statement.put("loans", loans);

        List<Transaction> transactions = dataService.getTransactionsByMemberId(member.getMemberId());
        statement.put("transactions", transactions);

        List<MemberAdjustment> adjustments = dataService.getAdjustmentsByMemberId(member.getMemberId());
        statement.put("adjustments", adjustments);

        return statement;
    }

    public Map<String, Object> getYearlyReport(String groupId, int year) {
        Map<String, Object> report = new HashMap<>();
        Group group = dataService.findGroupById(groupId).orElse(null);
        report.put("group", group);
        report.put("year", year);

        List<CollectionRecord> yearCollections = dataService.getCollectionsByGroupId(groupId).stream()
                .filter(c -> c.getYear() == year)
                .toList();

        BigDecimal totalExpected = yearCollections.stream().map(CollectionRecord::getExpectedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCollected = yearCollections.stream().map(CollectionRecord::getPaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPending = yearCollections.stream().map(CollectionRecord::getPendingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLateFee = yearCollections.stream().map(CollectionRecord::getPaidLateFee).reduce(BigDecimal.ZERO, BigDecimal::add);

        report.put("totalExpected", totalExpected);
        report.put("totalCollected", totalCollected);
        report.put("totalPending", totalPending);
        report.put("totalLateFee", totalLateFee);

        // Group Incomes & Expenses for that year
        List<GroupIncome> yearIncomes = dataService.getIncomesByGroupId(groupId).stream()
                .filter(i -> i.getIncomeDate() != null && i.getIncomeDate().getYear() == year)
                .toList();
        BigDecimal totalIncome = yearIncomes.stream().map(GroupIncome::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        report.put("totalOtherIncome", totalIncome);

        List<GroupExpense> yearExpenses = dataService.getExpensesByGroupId(groupId).stream()
                .filter(e -> e.getExpenseDate() != null && e.getExpenseDate().getYear() == year)
                .toList();
        BigDecimal totalExpense = yearExpenses.stream().map(GroupExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        report.put("totalOtherExpense", totalExpense);

        List<Transaction> yearTransactions = dataService.getTransactionsByGroupId(groupId).stream()
                .filter(t -> t.getDate() != null && t.getDate().getYear() == year)
                .filter(t -> t.getPaymentStatus() == null || "SUCCESS".equalsIgnoreCase(t.getPaymentStatus()))
                .toList();
        BigDecimal principalRecovered = sumTransactions(yearTransactions,
                TransactionType.LOAN_PRINCIPAL_PAYMENT, TransactionType.LOAN_PRINCIPAL_REPAYMENT,
                TransactionType.LOAN_REPAYMENT, TransactionType.LOAN_EXTRA_PAYMENT,
                TransactionType.EXTRA_LOAN_PAYMENT);
        BigDecimal interestCollected = sumTransactions(yearTransactions,
                TransactionType.LOAN_INTEREST_PAYMENT, TransactionType.LOAN_INTEREST_INCOME,
                TransactionType.LOAN_INTEREST);
        BigDecimal loansDisbursed = sumTransactions(yearTransactions, TransactionType.LOAN_DISBURSEMENT);
        BigDecimal sharesCollected = totalCollected;
        BigDecimal totalInflow = sharesCollected.add(principalRecovered).add(interestCollected).add(totalIncome);
        BigDecimal totalOutflow = loansDisbursed.add(totalExpense);

        // Names consumed by the existing report page.
        report.put("sharesCollected", sharesCollected);
        report.put("principalRecovered", principalRecovered);
        report.put("interestCollected", interestCollected);
        report.put("loansDisbursed", loansDisbursed);
        report.put("expensesTotal", totalExpense);
        report.put("otherIncomeTotal", totalIncome);
        report.put("totalInflow", totalInflow);
        report.put("totalOutflow", totalOutflow);
        report.put("netSurplus", totalInflow.subtract(totalOutflow));

        // Monthly Breakdown
        Map<Integer, Map<String, BigDecimal>> monthlyBreakdown = new HashMap<>();
        for (int m = 1; m <= 12; m++) {
            final int monthVal = m;
            BigDecimal mCollected = yearCollections.stream()
                    .filter(c -> c.getMonth() == monthVal)
                    .map(CollectionRecord::getPaidAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal mExpected = yearCollections.stream()
                    .filter(c -> c.getMonth() == monthVal)
                    .map(CollectionRecord::getExpectedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, BigDecimal> data = new HashMap<>();
            data.put("collected", mCollected);
            data.put("expected", mExpected);
            monthlyBreakdown.put(m, data);
        }
        report.put("monthlyBreakdown", monthlyBreakdown);

        return report;
    }

    public Map<String, Object> getOutstandingReport(String groupId) {
        Map<String, Object> report = new HashMap<>();
        Group group = dataService.findGroupById(groupId).orElse(null);
        report.put("group", group);

        List<Loan> activeLoans = dataService.getLoansByGroupId(groupId).stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE || l.getStatus() == LoanStatus.PARTIALLY_PAID || l.getStatus() == LoanStatus.OVERDUE)
                .toList();

        BigDecimal totalPrincipalOutstanding = activeLoans.stream()
                .map(Loan::getOutstandingPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalInterestOutstanding = activeLoans.stream()
                .map(Loan::getOutstandingInterest)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        report.put("activeLoansCount", activeLoans.size());
        report.put("loans", activeLoans);
        report.put("totalPrincipalOutstanding", totalPrincipalOutstanding);
        report.put("totalInterestOutstanding", totalInterestOutstanding);
        report.put("totalOutstanding", totalPrincipalOutstanding.add(totalInterestOutstanding));
        BigDecimal totalRecovered = activeLoans.stream()
                .map(l -> nz(l.getPrincipalPaid()).add(nz(l.getInterestPaid())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Backward-compatible aliases used by the existing browser page.
        report.put("totalOutstandingPrincipal", totalPrincipalOutstanding);
        report.put("totalRecovered", totalRecovered);

        return report;
    }

    public Map<String, Object> shareReportWhatsApp(String groupId, com.bachatgat.dto.ReportShareRequest request, String adminUsername) {
        requireChannel(request, "WHATSAPP");
        requireWhatsAppConfiguration();
        byte[] pdf = generateReportPdf(groupId, request);
        String mediaId = uploadWhatsAppDocument(pdf, request);
        String recipient = normalizeWhatsAppNumber(request.getRecipient());
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messaging_product", "whatsapp");
        message.put("to", recipient);
        message.put("type", "document");
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", mediaId);
        document.put("filename", reportFileName(request));
        if (request.getCustomMessage() != null && !request.getCustomMessage().isBlank()) {
            document.put("caption", request.getCustomMessage().trim());
        }
        message.put("document", document);
        HttpResponse<String> response = sendWhatsAppRequest("messages", objectMapper.valueToTree(message).toString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("WhatsApp delivery failed: " + safeProviderError(response.body()));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("reportId", "RPT-" + UUID.randomUUID().toString().substring(0, 8));
        res.put("channel", "WHATSAPP");
        res.put("recipient", recipient);
        res.put("status", "SENT");
        res.put("attachment", reportFileName(request));
        res.put("message", "PDF report sent to the WhatsApp number ending in " + recipient.substring(Math.max(0, recipient.length() - 4)) + ".");
        return res;
    }

    public Map<String, Object> sendReportEmail(String groupId, com.bachatgat.dto.ReportShareRequest request, String adminUsername) {
        requireChannel(request, "EMAIL");
        if (!request.getRecipient().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Recipient must be a valid email address.");
        }
        if (!isEmailConfigured()) {
            throw new IllegalArgumentException("Email delivery is not configured. Set SMTP_HOST, SMTP_USERNAME, SMTP_PASSWORD, and REPORT_EMAIL_FROM.");
        }
        byte[] pdf = generateReportPdf(groupId, request);
        String subject = extractSubject(request.getCustomMessage());
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(reportEmailFrom);
            helper.setTo(request.getRecipient().trim());
            helper.setSubject(subject);
            helper.setText(emailBody(groupId, request), false);
            helper.addAttachment(reportFileName(request), new org.springframework.core.io.ByteArrayResource(pdf), "application/pdf");
            mailSender.send(message);
        } catch (MessagingException | RuntimeException ex) {
            throw new IllegalArgumentException("Email delivery failed: " + ex.getMessage(), ex);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("reportId", "RPT-" + UUID.randomUUID().toString().substring(0, 8));
        res.put("channel", "EMAIL");
        res.put("recipient", request.getRecipient().trim());
        res.put("status", "SENT");
        res.put("attachment", reportFileName(request));
        res.put("message", "PDF report sent successfully to the entered email address.");
        return res;
    }

    private void requireWhatsAppConfiguration() {
        if (!isWhatsAppConfigured()) {
            throw new IllegalArgumentException("WhatsApp PDF delivery is not configured. Set WHATSAPP_REPORTS_ENABLED=true, WHATSAPP_PHONE_NUMBER_ID, and WHATSAPP_ACCESS_TOKEN.");
        }
    }

    private boolean isWhatsAppConfigured() {
        return whatsappReportsEnabled
                && whatsappPhoneNumberId != null && !whatsappPhoneNumberId.isBlank()
                && whatsappAccessToken != null && !whatsappAccessToken.isBlank();
    }

    private boolean isEmailConfigured() {
        return mailSender != null
                && smtpHost != null && !smtpHost.isBlank()
                && smtpUsername != null && !smtpUsername.isBlank()
                && smtpPassword != null && !smtpPassword.isBlank()
                && reportEmailFrom != null && !reportEmailFrom.isBlank();
    }

    private void requireChannel(com.bachatgat.dto.ReportShareRequest request, String expected) {
        if (request.getChannel() == null || !expected.equalsIgnoreCase(request.getChannel())) {
            throw new IllegalArgumentException("This endpoint only accepts channel " + expected + ".");
        }
    }

    private String normalizeWhatsAppNumber(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.length() == 10) digits = whatsappDefaultCountryCode + digits;
        if (digits.length() < 11 || digits.length() > 15) {
            throw new IllegalArgumentException("WhatsApp number must include a valid country code and contain 11 to 15 digits.");
        }
        return digits;
    }

    private String uploadWhatsAppDocument(byte[] pdf, com.bachatgat.dto.ReportShareRequest request) {
        String boundary = "----BachatGat" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writePart(body, boundary, "messaging_product", "whatsapp");
        writePart(body, boundary, "type", "application/pdf");
        String fileName = reportFileName(request);
        writeFilePart(body, boundary, "file", fileName, "application/pdf", pdf);
        writeAscii(body, "--" + boundary + "--\\r\\n");
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://graph.facebook.com/" + whatsappGraphVersion + "/" + whatsappPhoneNumberId + "/media"))
                .header("Authorization", "Bearer " + whatsappAccessToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException("WhatsApp PDF upload failed: " + safeProviderError(response.body()));
            }
            JsonNode mediaId = objectMapper.readTree(response.body()).get("id");
            if (mediaId == null || mediaId.asText().isBlank()) throw new IllegalArgumentException("WhatsApp provider returned no media ID.");
            return mediaId.asText();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("WhatsApp PDF upload was interrupted.", ex);
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private HttpResponse<String> sendWhatsAppRequest(String resource, String json) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://graph.facebook.com/" + whatsappGraphVersion + "/" + whatsappPhoneNumberId + "/" + resource))
                .header("Authorization", "Bearer " + whatsappAccessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        try {
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("WhatsApp delivery was interrupted.", ex);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not connect to WhatsApp: " + ex.getMessage(), ex);
        }
    }

    private void writePart(ByteArrayOutputStream out, String boundary, String name, String value) {
        writeAscii(out, "--" + boundary + "\\r\\nContent-Disposition: form-data; name=\\\"" + name + "\\\"\\r\\n\\r\\n" + value + "\\r\\n");
    }

    private void writeFilePart(ByteArrayOutputStream out, String boundary, String name, String fileName, String contentType, byte[] bytes) {
        writeAscii(out, "--" + boundary + "\\r\\nContent-Disposition: form-data; name=\\\"" + name + "\\\"; filename=\\\"" + fileName + "\\\"\\r\\nContent-Type: " + contentType + "\\r\\n\\r\\n");
        out.writeBytes(bytes);
        writeAscii(out, "\\r\\n");
    }

    private void writeAscii(ByteArrayOutputStream out, String value) { out.writeBytes(value.getBytes(StandardCharsets.US_ASCII)); }

    private String safeProviderError(String body) {
        try {
            JsonNode error = objectMapper.readTree(body).path("error").path("message");
            return error.asText(body == null || body.isBlank() ? "unknown provider error" : body);
        } catch (Exception ignored) { return body == null || body.isBlank() ? "unknown provider error" : body; }
    }

    private String reportFileName(com.bachatgat.dto.ReportShareRequest request) {
        String type = request.getReportType() == null ? "financial-report" : request.getReportType().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        return "bachat-gat-" + type + "-" + LocalDate.now() + ".pdf";
    }

    private String extractSubject(String customMessage) {
        if (customMessage == null || customMessage.isBlank()) return "Bachat Gat financial report";
        String firstLine = customMessage.lines().findFirst().orElse("").trim();
        return firstLine.isBlank() ? "Bachat Gat financial report" : firstLine;
    }

    private String emailBody(String groupId, com.bachatgat.dto.ReportShareRequest request) {
        String note = request.getCustomMessage() == null ? "" : request.getCustomMessage();
        return "Attached is the requested Bachat Gat financial report for group " + groupId + ".\\n\\n" + note;
    }

    private byte[] generateReportPdf(String groupId, com.bachatgat.dto.ReportShareRequest request) {
        Map<String, Object> report;
        String type = request.getReportType() == null ? "MONTHLY" : request.getReportType().toUpperCase();
        switch (type) {
            case "FINANCIAL_SUMMARY" -> report = getGroupFinancialSummary(groupId);
            case "MONTHLY", "MONTHLY_AUDIT" -> report = getMonthlyReport(groupId, request.getMonth() == null ? LocalDate.now().getMonthValue() : request.getMonth(), request.getYear() == null ? LocalDate.now().getYear() : request.getYear());
            case "YEARLY", "YEARLY_STATEMENT" -> report = getYearlyReport(groupId, request.getYear() == null ? LocalDate.now().getYear() : request.getYear());
            case "OUTSTANDING", "OUTSTANDING_LOANS" -> report = getOutstandingReport(groupId);
            case "MEMBER_STATEMENT" -> {
                if (request.getMemberId() == null || request.getMemberId().isBlank()) throw new IllegalArgumentException("Member ID is required for a member statement.");
                report = getMemberStatement(request.getMemberId());
            }
            default -> throw new IllegalArgumentException("Unsupported report type: " + request.getReportType());
        }
        Group group = dataService.findGroupById(groupId).orElse(null);
        String title = "Bachat Gat Financial Report - " + type;
        StringBuilder text = new StringBuilder(title).append("\\nGroup: ").append(group != null ? group.getGroupName() : groupId).append("\\nGenerated: ").append(java.time.LocalDateTime.now()).append("\\n\\n");
        report.forEach((key, value) -> {
            if (value == null || value instanceof List<?> || value instanceof Group || value instanceof Member) return;
            text.append(key).append(": ").append(value).append("\\n");
        });
        return simplePdf(text.toString());
    }

    private byte[] simplePdf(String content) {
        String[] lines = content.replaceAll("[^\\x20-\\x7E\\n]", "?").split("\\n");
        StringBuilder stream = new StringBuilder("BT\\n/F1 10 Tf\\n50 760 Td\\n");
        for (String line : lines) {
            String safe = escapePdf(line.length() > 100 ? line.substring(0, 100) : line);
            stream.append("(").append(safe).append(") Tj\\n0 -14 Td\\n");
        }
        stream.append("ET\\n");
        byte[] bytes = stream.toString().getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.writeBytes("%PDF-1.4\\n".getBytes(StandardCharsets.US_ASCII));
            long[] offsets = new long[6];
            offsets[1] = out.size(); out.writeBytes("1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\\n".getBytes(StandardCharsets.US_ASCII));
            offsets[2] = out.size(); out.writeBytes("2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj\\n".getBytes(StandardCharsets.US_ASCII));
            offsets[3] = out.size(); out.writeBytes("3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>endobj\\n".getBytes(StandardCharsets.US_ASCII));
            offsets[4] = out.size(); out.writeBytes("4 0 obj<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>endobj\\n".getBytes(StandardCharsets.US_ASCII));
            offsets[5] = out.size(); out.writeBytes(("5 0 obj<< /Length " + bytes.length + " >>stream\\n").getBytes(StandardCharsets.US_ASCII)); out.writeBytes(bytes); out.writeBytes("endstream endobj\\n".getBytes(StandardCharsets.US_ASCII));
            long xref = out.size(); out.writeBytes("xref\\n0 6\\n0000000000 65535 f \\n".getBytes(StandardCharsets.US_ASCII));
            for (int i = 1; i <= 5; i++) out.writeBytes(String.format("%010d 00000 n \\n", offsets[i]).getBytes(StandardCharsets.US_ASCII));
            out.writeBytes(("trailer<< /Size 6 /Root 1 0 R >>\\nstartxref\\n" + xref + "\\n%%EOF").getBytes(StandardCharsets.US_ASCII));
            return out.toByteArray();
        } catch (Exception ex) { throw new IllegalArgumentException("Could not generate report PDF.", ex); }
    }

    private String escapePdf(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}

