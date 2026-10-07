package com.bachatgat.service;

import com.bachatgat.dto.CollectionPaymentRequest;
import com.bachatgat.dto.LoanRepaymentRequest;
import com.bachatgat.exception.DuplicateRecordException;
import com.bachatgat.exception.InvalidFinancialOperationException;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class CollectionService {

    private final FirestoreDataService dataService;
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final LoanService loanService;
    private final DistributionService distributionService;

    public CollectionService(FirestoreDataService dataService, TransactionService transactionService,
                             NotificationService notificationService, AuditService auditService,
                             @Lazy LoanService loanService, @Lazy DistributionService distributionService) {
        this.dataService = dataService;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.loanService = loanService;
        this.distributionService = distributionService;
    }

    /**
     * Resolves the one authoritative monthly bachat amount for a group and
     * period. Historical collection records keep their own expected amount;
     * new records use the applicable master rule, then the group setting.
     */
    private BigDecimal resolveMonthlyBachat(String groupId, LocalDate period, GroupMasterData rules) {
        if (rules != null && rules.getMonthlyBachatAmount() != null) {
            return rules.getMonthlyBachatAmount();
        }
        return dataService.findGroupById(groupId)
                .map(Group::getMonthlyBachatAmount)
                .orElse(BigDecimal.ZERO);
    }

    public List<CollectionRecord> getGroupCollections(String groupId) {
        List<CollectionRecord> records = dataService.getCollectionsByGroupId(groupId);
        Group group = dataService.findGroupById(groupId).orElse(null);
        int dueDay = (group != null && group.getCollectionDueDay() > 0) ? group.getCollectionDueDay() : 10;
        BigDecimal lateFeeConfig = (group != null && group.getLateFeeAmount() != null) ? group.getLateFeeAmount() : BigDecimal.valueOf(50);
        int graceDays = (group != null && group.getGracePeriodDays() > 0) ? group.getGracePeriodDays() : 5;
        LocalDate today = LocalDate.now();

        for (CollectionRecord cr : records) {
            alignOpenRecordToGroupRule(cr, groupId, today);
            enrichAndNormalizeCollection(cr, dueDay, lateFeeConfig, graceDays, today);
        }
        return records;
    }

    public List<CollectionRecord> getMemberCollections(String memberId) {
        Member member = dataService.findMemberByMemberId(memberId).orElse(null);
        if (member == null) {
            return dataService.getCollectionsByMemberId(memberId);
        }

        String groupId = member.getGroupId();
        Group group = dataService.findGroupById(groupId).orElse(null);
        int dueDay = (group != null && group.getCollectionDueDay() > 0) ? group.getCollectionDueDay() : 10;
        BigDecimal lateFeeConfig = (group != null && group.getLateFeeAmount() != null) ? group.getLateFeeAmount() : BigDecimal.valueOf(50);
        int graceDays = (group != null && group.getGracePeriodDays() > 0) ? group.getGracePeriodDays() : 5;
        LocalDate today = LocalDate.now();
        GroupMasterData currentRules = dataService.getMasterDataForDate(groupId, today).orElse(null);
        BigDecimal monthlyBachat = resolveMonthlyBachat(groupId, today, currentRules);

        // Ensure Current Month record exists
        int curMonth = today.getMonthValue();
        int curYear = today.getYear();
        String curKey = groupId + "_" + member.getMemberId() + "_" + curMonth + "_" + curYear;
        if (dataService.findCollectionByBusinessKey(curKey).isEmpty()) {
            CollectionRecord curCr = new CollectionRecord(groupId, member.getMemberId(), member.getFullName(), curMonth, curYear, monthlyBachat);
            int maxDays = java.time.YearMonth.of(curYear, curMonth).lengthOfMonth();
            curCr.setDueDate(LocalDate.of(curYear, curMonth, Math.min(dueDay, maxDays)));
            dataService.saveCollection(curCr);
        }

        // Ensure Upcoming Month record exists
        LocalDate upcomingDate = today.plusMonths(1);
        int upMonth = upcomingDate.getMonthValue();
        int upYear = upcomingDate.getYear();
        String upKey = groupId + "_" + member.getMemberId() + "_" + upMonth + "_" + upYear;
        if (dataService.findCollectionByBusinessKey(upKey).isEmpty()) {
            BigDecimal nextBachat = dataService.getMasterDataForDate(groupId, upcomingDate).map(GroupMasterData::getMonthlyBachatAmount).orElse(monthlyBachat);
            CollectionRecord upCr = new CollectionRecord(groupId, member.getMemberId(), member.getFullName(), upMonth, upYear, nextBachat);
            int maxDays = java.time.YearMonth.of(upYear, upMonth).lengthOfMonth();
            upCr.setDueDate(LocalDate.of(upYear, upMonth, Math.min(dueDay, maxDays)));
            upCr.setStatus(CollectionStatus.UPCOMING);
            dataService.saveCollection(upCr);
        }

        List<CollectionRecord> records = dataService.getCollectionsByMemberId(memberId);
        for (CollectionRecord cr : records) {
            alignOpenRecordToGroupRule(cr, groupId, today);
            enrichAndNormalizeCollection(cr, dueDay, lateFeeConfig, graceDays, today);
        }

        // Sort records by year desc, month desc
        records.sort((a, b) -> {
            if (b.getYear() != a.getYear()) return Integer.compare(b.getYear(), a.getYear());
            return Integer.compare(b.getMonth(), a.getMonth());
        });

        return records;
    }

    /** Keep current/upcoming unpaid records aligned with the President's group rule. */
    private void alignOpenRecordToGroupRule(CollectionRecord record, String groupId, LocalDate today) {
        LocalDate period = LocalDate.of(record.getYear(), record.getMonth(), 1);
        BigDecimal paid = record.getPaidAmount() != null ? record.getPaidAmount() : BigDecimal.ZERO;
        if (!period.isBefore(today.withDayOfMonth(1)) && paid.signum() == 0) {
            GroupMasterData rules = dataService.getMasterDataForDate(groupId, period).orElse(null);
            BigDecimal configured = resolveMonthlyBachat(groupId, period, rules);
            if (configured != null && configured.signum() > 0
                    && (record.getExpectedAmount() == null || record.getExpectedAmount().compareTo(configured) != 0)) {
                record.setExpectedAmount(configured);
                record.setPendingAmount(configured);
                dataService.saveCollection(record);
            }
        }
    }

    private void enrichAndNormalizeCollection(CollectionRecord cr, int dueDay, BigDecimal lateFeeConfig, int graceDays, LocalDate today) {
        if (cr.getDueDate() == null) {
            int maxDays = java.time.YearMonth.of(cr.getYear(), cr.getMonth()).lengthOfMonth();
            cr.setDueDate(LocalDate.of(cr.getYear(), cr.getMonth(), Math.min(dueDay, maxDays)));
        }

        // Check if fully paid
        if (cr.getPaidAmount() != null && cr.getExpectedAmount() != null && cr.getPaidAmount().compareTo(cr.getExpectedAmount()) >= 0) {
            cr.setStatus(CollectionStatus.PAID);
            return;
        }

        LocalDate dueDate = cr.getDueDate();
        LocalDate graceCutoff = dueDate.plusDays(graceDays);

        // Check if upcoming month
        if (today.getYear() < cr.getYear() || (today.getYear() == cr.getYear() && today.getMonthValue() < cr.getMonth())) {
            cr.setStatus(CollectionStatus.UPCOMING);
            cr.setDaysLate(0);
        } else if (today.isAfter(graceCutoff)) {
            cr.setStatus(CollectionStatus.OVERDUE);
            int daysLate = (int) java.time.temporal.ChronoUnit.DAYS.between(dueDate, today);
            cr.setDaysLate(daysLate);
            if (cr.getLateFeeAmount() == null || cr.getLateFeeAmount().compareTo(BigDecimal.ZERO) == 0) {
                cr.setLateFeeAmount(lateFeeConfig);
            }
        } else {
            cr.setStatus(CollectionStatus.PENDING);
            cr.setDaysLate(0);
        }
    }

    /**
     * Monthly Savings Pending Members
     * Lists active members who have not yet cleared their monthly savings share for the month/year.
     */
    public List<java.util.Map<String, Object>> getPendingSavingsMembers(String groupId, int month, int year) {
        List<Member> activeMembers = dataService.getMembersByGroupId(groupId).stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .toList();

        List<CollectionRecord> collections = dataService.getCollectionsByGroupId(groupId);
        GroupMasterData masterData = dataService.getMasterDataForDate(groupId, LocalDate.of(year, month, 1)).orElse(null);
        int dueDay = masterData != null ? masterData.getCollectionDueDay() : 10;
        int graceDays = masterData != null ? masterData.getGracePeriodDays() : 5;
        BigDecimal lateFeeConfig = masterData != null && masterData.getLatePaymentFee() != null 
                ? masterData.getLatePaymentFee() 
                : BigDecimal.valueOf(100);

        int maxDaysInMonth = java.time.YearMonth.of(year, month).lengthOfMonth();
        LocalDate dueDate = LocalDate.of(year, month, Math.min(dueDay, maxDaysInMonth));
        LocalDate graceCutoff = dueDate.plusDays(graceDays);
        LocalDate today = LocalDate.now();
        int daysOverdue = today.isAfter(graceCutoff) ? (int) java.time.temporal.ChronoUnit.DAYS.between(dueDate, today) : 0;
        BigDecimal calculatedLateFee = daysOverdue > 0 ? lateFeeConfig : BigDecimal.ZERO;

        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (Member m : activeMembers) {
            String bKey = groupId + "_" + m.getMemberId() + "_" + month + "_" + year;
            CollectionRecord cr = collections.stream()
                    .filter(c -> bKey.equalsIgnoreCase(c.getBusinessKey()))
                    .findFirst()
                    .orElse(null);

            BigDecimal bachatAmt = (cr != null && cr.getExpectedAmount() != null)
                    ? cr.getExpectedAmount()
                    : resolveMonthlyBachat(groupId, LocalDate.of(year, month, 1), masterData);
            BigDecimal paid = (cr != null && cr.getPaidAmount() != null) ? cr.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal remainingDue = bachatAmt.subtract(paid).max(BigDecimal.ZERO);
            BigDecimal penalty = (cr != null && cr.getLateFeeAmount() != null && cr.getLateFeeAmount().compareTo(BigDecimal.ZERO) > 0)
                    ? cr.getLateFeeAmount() : calculatedLateFee;
            BigDecimal totalPayable = remainingDue.add(penalty);

            String status = (cr != null) ? cr.getStatus().name() : (daysOverdue > 0 ? "OVERDUE" : "PENDING");
            if (cr != null && cr.getStatus() == CollectionStatus.PAID) {
                continue; // Only pending / partial / overdue
            }

            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("memberId", m.getMemberId());
            map.put("memberName", m.getFullName());
            map.put("monthlyBachatAmount", bachatAmt);
            map.put("paidAmount", paid);
            map.put("amountPaid", paid);
            map.put("remainingDue", remainingDue);
            map.put("remainingAmount", remainingDue);
            map.put("penalty", penalty);
            map.put("currentPenalty", penalty);
            map.put("totalPayable", totalPayable);
            map.put("dueDate", (cr != null && cr.getDueDate() != null) ? cr.getDueDate().toString() : dueDate.toString());
            map.put("daysLate", daysOverdue);
            map.put("daysOverdue", daysOverdue);
            map.put("status", status);
            result.add(map);
        }

        return result;
    }

    /**
     * Generates expected monthly collections for all active group members.
     * Guaranteed idempotent: skips any record with existing business key.
     */
    public List<CollectionRecord> generateMonthlyCollections(String groupId, int month, int year, String recordedBy) {
        List<Member> activeMembers = dataService.getMembersByGroupId(groupId).stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .toList();

        GroupMasterData masterData = dataService.getMasterDataForDate(groupId, LocalDate.of(year, month, 1)).orElse(null);
        int dueDay = masterData != null && masterData.getCollectionDueDay() > 0 ? masterData.getCollectionDueDay() : 10;
        int maxDaysInMonth = java.time.YearMonth.of(year, month).lengthOfMonth();
        LocalDate calculatedDueDate = LocalDate.of(year, month, Math.min(dueDay, maxDaysInMonth));

        List<CollectionRecord> generated = new ArrayList<>();

        for (Member m : activeMembers) {
            String businessKey = groupId + "_" + m.getMemberId() + "_" + month + "_" + year;
            if (dataService.findCollectionByBusinessKey(businessKey).isEmpty()) {
                BigDecimal bachatAmt = resolveMonthlyBachat(groupId, LocalDate.of(year, month, 1), masterData);
                CollectionRecord record = new CollectionRecord(groupId, m.getMemberId(), m.getFullName(), month, year, bachatAmt);
                record.setDueDate(calculatedDueDate);
                record.setRecordedBy(recordedBy);
                CollectionRecord saved = dataService.saveCollection(record);
                generated.add(saved);

                // Update member pending savings tracking
                BigDecimal currentPending = m.getPendingSavings() != null ? m.getPendingSavings() : BigDecimal.ZERO;
                m.setPendingSavings(currentPending.add(bachatAmt));
                dataService.saveMember(m);
            }
        }

        // Notify group members
        notificationService.sendNotification(groupId, null, "USER",
                "Monthly Bachat Due for " + month + "/" + year,
                "Expected monthly bachat collection has been generated. Please deposit your share on time.",
                "COLLECTION_DUE");

        auditService.log(groupId, recordedBy, recordedBy, "MONTHLY_COLLECTION_GENERATED", "COLLECTION",
                month + "/" + year, null, "Generated " + generated.size() + " expected collection records", "127.0.0.1");

        return generated;
    }

    /**
     * Records member collection payment with the core 3-category accounting principle:
     * OPTION 1: Monthly Bachat / Savings
     * OPTION 2: Loan EMI / Loan Payment (Principal & Interest)
     * OPTION 3: Other Income / Fees
     *
     * Validates:
     * 1. Payment Idempotency (duplicate transaction keys rejected)
     * 2. Banking verification (FAILED payments do not update balances)
     * 3. Distribution rule: Sum of components MUST strictly equal total amount received.
     */
    public CollectionRecord recordPayment(String groupId, CollectionPaymentRequest request, String recordedBy) {
        Member member = dataService.findMemberByMemberId(request.getMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with Member ID: " + request.getMemberId()));

        // 1. PAYMENT IDEMPOTENCY (Section 5)
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            if (dataService.findTransactionByIdempotencyKey(request.getIdempotencyKey()).isPresent()) {
                throw new DuplicateRecordException("Payment with idempotency key '" + request.getIdempotencyKey() + "' was already processed.");
            }
        }
        if (request.getReferenceNumber() != null && !request.getReferenceNumber().isBlank()) {
            if (dataService.findTransactionByReference(request.getReferenceNumber()).isPresent()) {
                throw new DuplicateRecordException("Payment with reference '" + request.getReferenceNumber() + "' was already processed.");
            }
        }

        int month = request.getMonth() > 0 ? request.getMonth() : LocalDate.now().getMonthValue();
        int year = request.getYear() > 0 ? request.getYear() : LocalDate.now().getYear();

        GroupMasterData masterData = dataService.getMasterDataForDate(groupId, LocalDate.of(year, month, 1)).orElse(null);
        BigDecimal configuredMonthlyBachat = resolveMonthlyBachat(groupId, LocalDate.of(year, month, 1), masterData);
        int dueDay = masterData != null && masterData.getCollectionDueDay() > 0 ? masterData.getCollectionDueDay() : 10;
        int maxDaysInMonth = java.time.YearMonth.of(year, month).lengthOfMonth();
        LocalDate calculatedDueDate = LocalDate.of(year, month, Math.min(dueDay, maxDaysInMonth));

        String businessKey = groupId + "_" + member.getMemberId() + "_" + month + "_" + year;
        CollectionRecord record = dataService.findCollectionByBusinessKey(businessKey)
                .orElseGet(() -> {
                    BigDecimal bachatAmt = configuredMonthlyBachat;
                    CollectionRecord cr = new CollectionRecord(groupId, member.getMemberId(), member.getFullName(), month, year, bachatAmt);
                    cr.setDueDate(calculatedDueDate);
                    return cr;
                });
        alignOpenRecordToGroupRule(record, groupId, LocalDate.now());

        // 2. PAYMENT / BANKING SUCCESS VERIFICATION (Section 4)
        if ("FAILED".equalsIgnoreCase(request.getPaymentStatus()) || "CANCELLED".equalsIgnoreCase(request.getPaymentStatus())) {
            record.setStatus(CollectionStatus.FAILED);
            record.setNotes("Payment attempt failed at gateway or banking provider: " + (request.getNotes() != null ? request.getNotes() : ""));
            dataService.saveCollection(record);
            // Log audit
            auditService.log(groupId, recordedBy, recordedBy, "PAYMENT_FAILED", "COLLECTION",
                    record.getId(), null, "Payment failed for ₹" + request.getAmount() + " - no balances updated", "127.0.0.1");
            return record;
        }

        if ("PENDING".equalsIgnoreCase(request.getPaymentStatus())) {
            record.setStatus(CollectionStatus.PENDING);
            record.setNotes("Payment pending at gateway or banking provider: " + (request.getNotes() != null ? request.getNotes() : ""));
            dataService.saveCollection(record);
            auditService.log(groupId, recordedBy, recordedBy, "PAYMENT_PENDING", "COLLECTION",
                    record.getId(), null, "Payment pending for ₹" + request.getAmount() + " - no balances updated", "127.0.0.1");
            return record;
        }

        // 3. THREE-CATEGORY MONEY DISTRIBUTION CALCULATION (Section 3 & 67)
        BigDecimal totalReceived = request.getAmount();

        // Component 1: Share / Savings
        BigDecimal sharePart = request.getShareAmount();
        // Component 2: Loan Principal & Interest
        BigDecimal loanPrincPart = request.getLoanPrincipalAmount() != null ? request.getLoanPrincipalAmount() : BigDecimal.ZERO;
        BigDecimal loanIntPart = request.getLoanInterestAmount() != null ? request.getLoanInterestAmount() : BigDecimal.ZERO;
        // Component 3: Other
        BigDecimal otherPart = request.getOtherAmount() != null ? request.getOtherAmount() : BigDecimal.ZERO;
        // Late fee
        BigDecimal lateFee = request.getLateFeeAmount() != null ? request.getLateFeeAmount() : BigDecimal.ZERO;
        BigDecimal waivedFee = request.getWaivedLateFee() != null ? request.getWaivedLateFee() : BigDecimal.ZERO;
        BigDecimal netLateFee = lateFee.subtract(waivedFee).max(BigDecimal.ZERO);

        // Fallback for single-field legacy payment (applies directly to share)
        if (sharePart == null && loanPrincPart.compareTo(BigDecimal.ZERO) == 0 &&
                loanIntPart.compareTo(BigDecimal.ZERO) == 0 && otherPart.compareTo(BigDecimal.ZERO) == 0) {
            sharePart = totalReceived;
        } else if (sharePart == null) {
            sharePart = BigDecimal.ZERO;
        }

        BigDecimal expectedShare = record.getExpectedAmount() != null
                ? record.getExpectedAmount()
                : configuredMonthlyBachat;
        BigDecimal alreadyPaidShare = record.getPaidAmount() != null
                ? record.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal remainingShare = expectedShare.subtract(alreadyPaidShare).max(BigDecimal.ZERO);
        if (sharePart.compareTo(remainingShare) > 0) {
            throw new InvalidFinancialOperationException(
                    "Monthly bachat payment cannot exceed the remaining configured amount of ₹" + remainingShare +
                    ". Extra payment must be allocated to a loan, late fee, or other category."
            );
        }

        // VALIDATION: Total allocated MUST equal payment amount
        BigDecimal totalAllocated = sharePart.add(loanPrincPart).add(loanIntPart).add(otherPart).add(netLateFee);
        if (totalAllocated.compareTo(totalReceived) != 0) {
            throw new InvalidFinancialOperationException(
                    "Total allocated amount (₹" + totalAllocated + ") does not equal amount received (₹" + totalReceived + "). " +
                    "Every rupee must be explicitly allocated to Share, Loan, Late Fee, or Other."
            );
        }

        // 4. PROCESS OPTION 1: SHARE / SAVINGS & LATE FEE
        if (sharePart.compareTo(BigDecimal.ZERO) > 0 || netLateFee.compareTo(BigDecimal.ZERO) > 0 || waivedFee.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentPaid = record.getPaidAmount() != null ? record.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal newPaid = currentPaid.add(sharePart);
            record.setPaidAmount(newPaid);

            BigDecimal expected = record.getExpectedAmount() != null ? record.getExpectedAmount()
                    : masterData != null ? masterData.getMonthlyBachatAmount() : BigDecimal.ZERO;
            BigDecimal pending = expected.subtract(newPaid);
            record.setPendingAmount(pending.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : pending);

            // Late fee tracking
            record.setLateFeeAmount(lateFee);
            record.setWaivedLateFee(waivedFee);
            record.setPaidLateFee((record.getPaidLateFee() != null ? record.getPaidLateFee() : BigDecimal.ZERO).add(netLateFee));

            LocalDate payDate = request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now();
            record.setPaymentDate(payDate);
            record.setPaymentMethod(request.getPaymentMethod());
            record.setReferenceNumber(request.getReferenceNumber());
            record.setNotes(request.getNotes());
            record.setRecordedBy(recordedBy);

            // Determine status
            int graceDays = masterData != null ? masterData.getGracePeriodDays() : 5;
            LocalDate dueDate = record.getDueDate() != null ? record.getDueDate() : calculatedDueDate;
            record.setDueDate(dueDate);
            if (payDate.isAfter(dueDate.plusDays(graceDays))) {
                record.setDaysLate((int) java.time.temporal.ChronoUnit.DAYS.between(dueDate, payDate));
            }

            if (newPaid.compareTo(expected) >= 0) {
                record.setStatus(record.getDaysLate() > 0 ? CollectionStatus.LATE : CollectionStatus.PAID);
            } else if (newPaid.compareTo(BigDecimal.ZERO) > 0) {
                record.setStatus(CollectionStatus.PARTIAL);
            }

            dataService.saveCollection(record);

            // Update Member total savings balance and current share balance
            if (sharePart.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal oldSavings = member.getTotalSavingsBalance() != null ? member.getTotalSavingsBalance() : BigDecimal.ZERO;
                BigDecimal oldShare = member.getCurrentShareBalance() != null ? member.getCurrentShareBalance() : BigDecimal.ZERO;
                member.setTotalSavingsBalance(oldSavings.add(sharePart));
                member.setCurrentShareBalance(oldShare.add(sharePart));

                // Reduce pending savings
                BigDecimal memberPending = member.getPendingSavings() != null ? member.getPendingSavings() : BigDecimal.ZERO;
                BigDecimal updatedMemberPending = memberPending.subtract(sharePart);
                member.setPendingSavings(updatedMemberPending.compareTo(BigDecimal.ZERO) > 0 ? updatedMemberPending : BigDecimal.ZERO);
                dataService.saveMember(member);

                // Update Group aggregate savings
                Group group = dataService.findGroupById(groupId).orElse(null);
                if (group != null) {
                    BigDecimal grpSavings = group.getTotalSavingsBalance() != null ? group.getTotalSavingsBalance() : BigDecimal.ZERO;
                    group.setTotalSavingsBalance(grpSavings.add(sharePart));
                    dataService.saveGroup(group);
                }

                // Record transaction in ledger
                Transaction shareTxn = new Transaction(
                        "TXN-" + System.currentTimeMillis() + "-SHR",
                        groupId, member.getMemberId(), member.getFullName(), null,
                        TransactionType.SHARE_CONTRIBUTION, sharePart, sharePart, BigDecimal.ZERO,
                        payDate, request.getReferenceNumber(),
                        "Monthly Bachat Contribution for " + month + "/" + year, request.getPaymentMethod(), recordedBy
                );
                shareTxn.setCategory("SHARE");
                shareTxn.setIdempotencyKey(request.getIdempotencyKey());
                dataService.saveTransaction(shareTxn);
            }

            // Record Late Fee Transaction, Group Income, and Distribute to Members
            if (netLateFee.compareTo(BigDecimal.ZERO) > 0) {
                Transaction lateFeeTxn = new Transaction(
                        "TXN-" + System.currentTimeMillis() + "-LF",
                        groupId, member.getMemberId(), member.getFullName(), null,
                        TransactionType.SHARE_LATE_FEE, netLateFee, BigDecimal.ZERO, BigDecimal.ZERO,
                        payDate, request.getReferenceNumber(),
                        "Late Payment Charge for " + month + "/" + year + " (Days late: " + record.getDaysLate() + ")",
                        request.getPaymentMethod(), recordedBy
                );
                lateFeeTxn.setCategory("LATE_FEE");
                dataService.saveTransaction(lateFeeTxn);

                // Collect penalty amount into Group Income
                GroupIncome penaltyIncome = new GroupIncome(
                        groupId, "PENALTY",
                        "Late Penalty fee collected from " + member.getFullName() + " (" + member.getMemberId() + ") for " + month + "/" + year,
                        netLateFee, payDate, request.getPaymentMethod(), request.getReferenceNumber(),
                        "EQUAL_ACTIVE_MEMBERS", recordedBy
                );
                penaltyIncome.setCategory("PENALTY");
                dataService.saveIncome(penaltyIncome);

                // Distribute penalty income dividend among all active members
                try {
                    distributionService.distributeIncome(groupId, penaltyIncome.getId(),
                            new com.bachatgat.dto.DistributionRequest("EQUAL_ACTIVE_MEMBERS"), recordedBy);
                } catch (Exception ex) {
                    System.err.println("Note: Penalty auto-distribution deferred: " + ex.getMessage());
                }
            }
        }

        // 5. PROCESS OPTION 2: LOAN PAYMENT (PRINCIPAL & INTEREST)
        if (loanPrincPart.compareTo(BigDecimal.ZERO) > 0 || loanIntPart.compareTo(BigDecimal.ZERO) > 0) {
            String loanId = request.getLoanId();
            if (loanId == null || loanId.isBlank()) {
                loanId = member.getActiveLoanId();
            }
            if (loanId == null || loanId.isBlank()) {
                loanId = dataService.findActiveLoanByMemberId(member.getMemberId())
                        .map(Loan::getId)
                        .orElse(null);
            }
            if (loanId != null && !loanId.isBlank()) {
                BigDecimal totalLoanPayment = loanPrincPart.add(loanIntPart);
                LoanRepaymentRequest loanReq = new LoanRepaymentRequest(
                        totalLoanPayment,
                        request.getPaymentDate(),
                        request.getPaymentMethod(),
                        request.getReferenceNumber()
                );
                loanReq.setNotes("Multi-category Collection Payment [Principal: ₹" + loanPrincPart + ", Interest: ₹" + loanIntPart + "]");
                try {
                    loanService.recordRepayment(loanId, loanReq, recordedBy);
                } catch (Exception ex) {
                    throw new InvalidFinancialOperationException("Failed to credit loan payment component: " + ex.getMessage());
                }
            }
        }


        // 6. PROCESS OPTION 3: OTHER
        if (otherPart.compareTo(BigDecimal.ZERO) > 0) {
            String otherCat = request.getOtherCategory() != null ? request.getOtherCategory() : "MISC";
            GroupIncome inc = new GroupIncome(
                    groupId, otherCat,
                    "Collection payment other allocation from " + member.getFullName() + (request.getNotes() != null ? " - " + request.getNotes() : ""),
                    otherPart, request.getPaymentDate(), request.getPaymentMethod(), request.getReferenceNumber(),
                    "GROUP_LEVEL_ONLY", recordedBy
            );
            dataService.saveIncome(inc);

            Transaction otherTxn = new Transaction(
                    "TXN-" + System.currentTimeMillis() + "-OTH",
                    groupId, member.getMemberId(), member.getFullName(), null,
                    TransactionType.OTHER_INCOME, otherPart, BigDecimal.ZERO, BigDecimal.ZERO,
                    request.getPaymentDate(), request.getReferenceNumber(),
                    "Other Payment Component (" + otherCat + ") from " + member.getFullName(),
                    request.getPaymentMethod(), recordedBy
            );
            otherTxn.setCategory("OTHER");
            dataService.saveTransaction(otherTxn);
        }

        // 7. NOTIFICATIONS & AUDIT LOG
        final BigDecimal finalSharePart = sharePart;
        dataService.findUserByMemberId(member.getMemberId()).ifPresent(u ->
                notificationService.sendNotification(groupId, u.getId(), "USER",
                        "Payment Confirmation",
                        "Payment of ₹" + totalReceived + " recorded. (Share: ₹" + finalSharePart +
                                ", Loan: ₹" + (loanPrincPart.add(loanIntPart)) + ", Other: ₹" + otherPart + ")",
                        "PAYMENT_CONFIRMATION")
        );

        auditService.log(groupId, recordedBy, recordedBy, "PAYMENT_RECORDED", "COLLECTION",
                record.getId(), null,
                "Payment recorded ₹" + totalReceived + " for " + member.getFullName() +
                        " [Share: ₹" + finalSharePart + ", Loan Princ: ₹" + loanPrincPart + ", Loan Int: ₹" + loanIntPart +
                        ", Late Fee: ₹" + netLateFee + ", Other: ₹" + otherPart + "]", "127.0.0.1");

        return record;
    }

}

