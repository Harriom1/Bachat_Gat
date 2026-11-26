package com.bachatgat.service;

import com.bachatgat.dto.CollectionPaymentRequest;
import com.bachatgat.dto.LoanRepaymentRequest;
import com.bachatgat.dto.LoanExtraPaymentRequest;
import com.bachatgat.exception.DuplicateRecordException;
import com.bachatgat.exception.InvalidFinancialOperationException;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

    /**
     * The configured late fee is a per-day penalty. The due date itself has
     * no penalty; the first penalty is charged on the following calendar day.
     */
    private BigDecimal calculateAccruedPenalty(BigDecimal dailyPenalty, LocalDate dueDate, LocalDate asOfDate) {
        if (dailyPenalty == null || dailyPenalty.signum() <= 0 || dueDate == null || asOfDate == null
                || !asOfDate.isAfter(dueDate)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long daysLate = java.time.temporal.ChronoUnit.DAYS.between(dueDate, asOfDate);
        return dailyPenalty.multiply(BigDecimal.valueOf(daysLate)).setScale(2, RoundingMode.HALF_UP);
    }

    private Group group(String groupId) {
        return dataService.findGroupById(groupId).orElse(null);
    }

    private GroupMasterData rules(String groupId, YearMonth period, LocalDate today) {
        LocalDate date = period.equals(YearMonth.from(today)) ? today : period.atDay(1);
        return dataService.getMasterDataForDate(groupId, date).orElse(null);
    }

    private BigDecimal lateFee(Group group, GroupMasterData rules) {
        return rules != null && rules.getLatePaymentFee() != null ? rules.getLatePaymentFee()
                : group != null && group.getLateFeeAmount() != null ? group.getLateFeeAmount() : BigDecimal.ZERO;
    }

    private BigDecimal remaining(CollectionRecord record) {
        return zero(record.getExpectedAmount()).subtract(zero(record.getPaidAmount())).max(BigDecimal.ZERO);
    }

    private BigDecimal zero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private LocalDate dueDate(CollectionRecord record, Group group, Member member, GroupMasterData rules, LocalDate today) {
        YearMonth period = YearMonth.of(record.getYear(), record.getMonth());
        return record.getDueDate() != null && period.isBefore(YearMonth.from(today))
                ? record.getDueDate() : MonthlyBachatCalendar.dueDate(group, member, rules, period);
    }

    public List<CollectionRecord> getGroupCollections(String groupId) {
        return getGroupCollections(groupId, MonthlyBachatCalendar.today());
    }

    private List<CollectionRecord> getGroupCollections(String groupId, LocalDate today) {
        return getGroupCollections(groupId, today, record -> true);
    }

    List<CollectionRecord> getGroupCollectionsForPeriod(String groupId, LocalDate today, YearMonth period) {
        return getGroupCollections(groupId, today, record -> record.getMonth() == period.getMonthValue()
                && record.getYear() == period.getYear());
    }

    private List<CollectionRecord> getGroupCollections(String groupId, LocalDate today,
                                                       Predicate<CollectionRecord> includes) {
        if (groupId == null) return List.of();
        Group group = group(groupId);
        var members = dataService.getMembersByGroupIdIncludingArchived(groupId).stream().collect(
                java.util.stream.Collectors.toMap(Member::getMemberId, member -> member, (first, second) -> first));
        return dataService.getCollectionsByGroupId(groupId).stream()
                .filter(includes)
                .map(record -> collectionForRead(record, group, members.get(record.getMemberId()), today))
                .filter(java.util.Objects::nonNull).toList();
    }

    public List<CollectionRecord> getMemberHistory(String memberId) {
        Member member = dataService.findMemberByMemberIdIncludingArchived(memberId).orElse(null);
        Group group = member == null ? null : group(member.getGroupId());
        LocalDate today = MonthlyBachatCalendar.today();
        return dataService.getCollectionsByMemberId(memberId).stream()
                .map(record -> member != null && record.getGroupId().equals(member.getGroupId())
                        ? collectionForRead(record, group, member, today)
                        : collectionForRead(record, group(record.getGroupId()), null, today))
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparingInt(CollectionRecord::getYear)
                        .thenComparingInt(CollectionRecord::getMonth).reversed()).toList();
    }

    public List<CollectionRecord> getMemberCollections(String memberId) {
        Member member = dataService.findMemberByMemberId(memberId).orElse(null);
        if (member != null && member.getStatus() == MemberStatus.ACTIVE) {
            LocalDate today = MonthlyBachatCalendar.today();
            YearMonth current = YearMonth.from(today);
            Group group = group(member.getGroupId());
            if (MonthlyBachatCalendar.eligible(group, member, current)) {
                ensureMonthlyRecord(group, member, current, today, null);
                ensureMonthlyRecord(group, member, current.plusMonths(1), today, null);
            }
        }
        return getMemberHistory(memberId);
    }

    private CollectionRecord collectionForRead(CollectionRecord stored, Group group, Member member, LocalDate today) {
        if (stored.getStatus() == CollectionStatus.VOID_PRE_START) return null;
        YearMonth period = YearMonth.of(stored.getYear(), stored.getMonth());
        if (!MonthlyBachatCalendar.eligible(group, member, period)) {
            if (stored.canVoidPreStart() && !hasHistoricalPayment(stored, period)) {
                if (dataService.voidPreStartCollection(stored)) {
                    auditService.log(stored.getGroupId(), "SYSTEM", "SYSTEM", "PRE_START_COLLECTION_VOIDED",
                            "COLLECTION", stored.getBusinessKey(), null, "Unpaid collection before Bachat start", "127.0.0.1");
                }
            }
            if (zero(stored.getPaidAmount()).signum() <= 0 && stored.getPaidLateFee().signum() <= 0) return null;
            CollectionRecord historical = copyCollection(stored);
            historical.setExpectedAmount(zero(stored.getPaidAmount()));
            historical.setPendingAmount(BigDecimal.ZERO);
            historical.setLateFeeAmount(stored.getPaidLateFee());
            return historical;
        }

        if (remaining(stored).signum() == 0 && zero(stored.getPaidAmount()).signum() > 0) {
            CollectionRecord historical = copyCollection(stored);
            historical.setPendingAmount(BigDecimal.ZERO);
            if (historical.getStatus() != CollectionStatus.LATE) historical.setStatus(CollectionStatus.PAID);
            return historical;
        }

        CollectionRecord view = copyCollection(stored);
        GroupMasterData rules = rules(stored.getGroupId(), period, today);
        alignOpenRecordToGroupRule(view, group, member, rules, today);
        view.setPendingAmount(remaining(view));
        if (period.isAfter(YearMonth.from(today))) {
            view.setStatus(CollectionStatus.UPCOMING);
            view.setDaysLate(0);
            view.setLateFeeAmount(BigDecimal.ZERO);
        } else {
            int days = MonthlyBachatCalendar.daysLate(view.getDueDate(), today);
            view.setDaysLate(days);
            view.setLateFeeAmount(calculateAccruedPenalty(lateFee(group, rules), view.getDueDate(), today));
            view.setStatus(days > 0 ? CollectionStatus.OVERDUE
                    : zero(view.getPaidAmount()).signum() > 0 ? CollectionStatus.PARTIAL : CollectionStatus.PENDING);
        }
        return view;
    }

    private CollectionRecord copyCollection(CollectionRecord source) {
        CollectionRecord copy = new CollectionRecord();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }

    private boolean hasHistoricalPayment(CollectionRecord record, YearMonth period) {
        return dataService.getTransactionsByGroupId(record.getGroupId()).stream()
                .filter(t -> record.getMemberId().equalsIgnoreCase(t.getMemberId()))
                .filter(t -> t.getType() == TransactionType.SHARE_CONTRIBUTION
                        || t.getType() == TransactionType.SHARE_COLLECTION || t.getType() == TransactionType.MEMBER_SAVING
                        || t.getType() == TransactionType.PENALTY_INCOME || t.getType() == TransactionType.SHARE_LATE_FEE)
                .filter(t -> !"INIT-CONTRIB".equalsIgnoreCase(t.getReference()))
                .anyMatch(t -> storedReferenceMatches(record, t.getReference())
                        || (t.getDate() != null && period.equals(YearMonth.from(t.getDate())))
                        || (t.getDescription() != null
                        && t.getDescription().contains(" for " + period.getMonthValue() + "/" + period.getYear())));
    }

    private boolean storedReferenceMatches(CollectionRecord record, String reference) {
        return reference != null && (reference.equalsIgnoreCase(record.getId())
                || reference.equalsIgnoreCase(record.getBusinessKey()));
    }

    private void alignOpenRecordToGroupRule(CollectionRecord record, Group group, Member member,
                                            GroupMasterData rules, LocalDate today) {
        YearMonth period = YearMonth.of(record.getYear(), record.getMonth());
        record.setDueDate(dueDate(record, group, member, rules, today));
        if (!period.isBefore(YearMonth.from(today)) && zero(record.getPaidAmount()).signum() == 0) {
            BigDecimal configured = resolveMonthlyBachat(record.getGroupId(), period.atDay(1), rules);
            if (configured.signum() > 0) record.setExpectedAmount(configured);
        }
    }

    public BigDecimal getCurrentPenalty(Member member) {
        LocalDate today = MonthlyBachatCalendar.today();
        return getCurrentPenalty(member, today, null);
    }

    BigDecimal getCurrentPenalty(Member member, LocalDate today, List<CollectionRecord> history) {
        YearMonth month = YearMonth.from(today);
        Group group = group(member.getGroupId());
        if (member.getStatus() != MemberStatus.ACTIVE || !MonthlyBachatCalendar.eligible(group, member, month)) {
            return BigDecimal.ZERO;
        }
        CollectionRecord current = (history == null ? getMemberHistory(member.getMemberId()) : history).stream()
                .filter(record -> record.getGroupId().equals(member.getGroupId())
                        && record.getMonth() == month.getMonthValue() && record.getYear() == month.getYear())
                .findFirst().orElse(null);
        if (current != null) {
            return remaining(current).signum() > 0
                    ? current.getLateFeeAmount().subtract(current.getPaidLateFee()).max(BigDecimal.ZERO) : BigDecimal.ZERO;
        }
        GroupMasterData rules = rules(member.getGroupId(), month, today);
        return resolveMonthlyBachat(member.getGroupId(), month.atDay(1), rules).signum() > 0
                ? calculateAccruedPenalty(lateFee(group, rules), MonthlyBachatCalendar.dueDate(group, member, rules, month), today)
                : BigDecimal.ZERO;
    }

    LocalDate getNextBachatDueDate(Group group, Member member, List<CollectionRecord> records, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        YearMonth next = MonthlyBachatCalendar.nextEligibleMonth(group, member, current);
        if (next.equals(current) && records.stream().anyMatch(record ->
                group.getId().equals(record.getGroupId())
                        && record.getMonth() == current.getMonthValue() && record.getYear() == current.getYear()
                        && (record.getStatus() == CollectionStatus.PAID || record.getStatus() == CollectionStatus.LATE))) {
            next = next.plusMonths(1);
        }
        return MonthlyBachatCalendar.dueDate(group, member, rules(group.getId(), next, today), next);
    }

    public List<java.util.Map<String, Object>> getPendingSavingsMembers(String groupId, int month, int year) {
        return getPendingSavingsMembers(groupId, YearMonth.of(year, month), MonthlyBachatCalendar.today());
    }

    List<java.util.Map<String, Object>> getPendingSavingsMembers(String groupId, YearMonth period, LocalDate today) {
        Group group = group(groupId);
        // Normalize invalid legacy records for this period even when no dues
        // can be displayed before the group's start date.
        List<CollectionRecord> records = getGroupCollectionsForPeriod(groupId, today, period);
        if (!MonthlyBachatCalendar.eligible(group, null, period) || period.isAfter(YearMonth.from(today))) return List.of();
        return buildPendingSavings(groupId, group, period, today, dataService.getMembersByGroupId(groupId), records);
    }

    public List<Map<String, Object>> getPendingSavingsForYear(String groupId, int year) {
        if (year < 1 || year > 9999) throw new IllegalArgumentException("Year must be between 1 and 9999");
        LocalDate today = MonthlyBachatCalendar.today();
        if (year > today.getYear()) return List.of();
        Group group = group(groupId);
        List<Member> members = dataService.getMembersByGroupId(groupId);
        int lastMonth = year == today.getYear() ? today.getMonthValue() : 12;
        Map<Integer, List<CollectionRecord>> recordsByMonth = getGroupCollections(groupId, today,
                record -> record.getYear() == year && record.getMonth() >= 1 && record.getMonth() <= lastMonth)
                .stream().collect(Collectors.groupingBy(CollectionRecord::getMonth));
        List<Map<String, Object>> pending = new ArrayList<>();
        for (int month = 1; month <= lastMonth; month++) {
            YearMonth period = YearMonth.of(year, month);
            if (!MonthlyBachatCalendar.eligible(group, null, period)) continue;
            for (Map<String, Object> row : buildPendingSavings(groupId, group, period, today, members,
                    recordsByMonth.getOrDefault(month, List.of()))) {
                row.put("month", month);
                row.put("year", year);
                pending.add(row);
            }
        }
        return pending;
    }

    private List<Map<String, Object>> buildPendingSavings(String groupId, Group group, YearMonth period, LocalDate today,
            List<Member> members, List<CollectionRecord> records) {
        GroupMasterData rules = rules(groupId, period, today);
        BigDecimal monthlyBachat = resolveMonthlyBachat(groupId, period.atDay(1), rules);
        Map<String, CollectionRecord> byMember = records.stream().filter(record -> record.getMemberId() != null)
                .collect(Collectors.toMap(record -> record.getMemberId().toLowerCase(Locale.ROOT),
                        record -> record, (first, duplicate) -> first));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Member member : members) {
            if (member.getStatus() != MemberStatus.ACTIVE || !MonthlyBachatCalendar.eligible(group, member, period)) continue;
            CollectionRecord record = byMember.get(member.getMemberId().toLowerCase(Locale.ROOT));
            BigDecimal expected = record != null ? zero(record.getExpectedAmount()) : monthlyBachat;
            BigDecimal paid = record != null ? zero(record.getPaidAmount()) : BigDecimal.ZERO;
            BigDecimal remaining = expected.subtract(paid).max(BigDecimal.ZERO);
            if (remaining.signum() == 0 || (record != null
                    && (record.getStatus() == CollectionStatus.PAID || record.getStatus() == CollectionStatus.LATE))) continue;

            LocalDate due = record != null ? dueDate(record, group, member, rules, today)
                    : MonthlyBachatCalendar.dueDate(group, member, rules, period);
            int days = MonthlyBachatCalendar.daysLate(due, today);
            BigDecimal alreadyPaid = record != null ? record.getPaidLateFee() : BigDecimal.ZERO;
            BigDecimal penalty = calculateAccruedPenalty(lateFee(group, rules), due, today)
                    .subtract(alreadyPaid).max(BigDecimal.ZERO);
            var row = new LinkedHashMap<String, Object>();
            row.put("memberId", member.getMemberId());
            row.put("memberName", member.getFullName());
            row.put("monthlyBachatAmount", expected);
            row.put("paidAmount", paid);
            row.put("amountPaid", paid);
            row.put("remainingDue", remaining);
            row.put("remainingAmount", remaining);
            row.put("penalty", penalty);
            row.put("currentPenalty", penalty);
            row.put("totalPayable", remaining.add(penalty));
            row.put("dueDate", due.toString());
            row.put("daysLate", days);
            row.put("daysOverdue", days);
            row.put("status", days > 0 ? "OVERDUE" : paid.signum() > 0 ? "PARTIAL" : "PENDING");
            result.add(row);
        }
        return result;
    }

    private CollectionRecord ensureMonthlyRecord(Group group, Member member, YearMonth period, LocalDate today, String actor) {
        if (!MonthlyBachatCalendar.eligible(group, member, period)
                || !MonthlyBachatCalendar.eligible(group, member, YearMonth.from(today))) return null;
        String groupId = member.getGroupId();
        GroupMasterData rules = rules(groupId, period, today);
        BigDecimal amount = resolveMonthlyBachat(groupId, period.atDay(1), rules);
        if (amount.signum() <= 0) return null;
        CollectionRecord record = new CollectionRecord(groupId, member.getMemberId(), member.getFullName(),
                period.getMonthValue(), period.getYear(), amount);
        record.setDueDate(MonthlyBachatCalendar.dueDate(group, member, rules, period));
        if (period.isAfter(YearMonth.from(today))) record.setStatus(CollectionStatus.UPCOMING);
        record.setRecordedBy(actor);
        return dataService.createMonthlyCollection(record) ? record : null;
    }

    void requireEligibleMonthlyPayment(String groupId, Member member, YearMonth period, BigDecimal share, BigDecimal fee) {
        if ((zero(share).signum() > 0 || zero(fee).signum() > 0)
                && !MonthlyBachatCalendar.eligible(group(groupId), member, period)) {
            throw new InvalidFinancialOperationException("This month is before the Bachat start date.");
        }
    }

    private BigDecimal requestedShare(CollectionPaymentRequest request) {
        if (request.getShareAmount() != null) return request.getShareAmount();
        boolean otherAllocation = List.of(zero(request.getLoanPrincipalAmount()), zero(request.getLoanInterestAmount()),
                zero(request.getExtraLoanPaymentAmount()), zero(request.getOtherAmount()),
                zero(request.getLateFeeAmount()), zero(request.getWaivedLateFee())).stream().anyMatch(amount -> amount.signum() > 0);
        return otherAllocation ? BigDecimal.ZERO : request.getAmount();
    }

    public List<CollectionRecord> generateMonthlyCollections(String groupId, int month, int year, String recordedBy) {
        return generateMonthlyCollections(groupId, YearMonth.of(year, month), MonthlyBachatCalendar.today(), recordedBy);
    }

    List<CollectionRecord> generateMonthlyCollections(String groupId, YearMonth period, LocalDate today, String recordedBy) {
        Group group = group(groupId);
        // Reconcile records left by older generators even if the requested period is ineligible.
        getGroupCollections(groupId, today);
        if (!MonthlyBachatCalendar.eligible(group, null, period)) return List.of();
        List<CollectionRecord> generated = new ArrayList<>();
        for (Member member : dataService.getMembersByGroupId(groupId)) {
            if (member.getStatus() != MemberStatus.ACTIVE) continue;
            CollectionRecord record = ensureMonthlyRecord(group, member, period, today, recordedBy);
            if (record != null) generated.add(record);
        }
        if (!generated.isEmpty() && !period.isAfter(YearMonth.from(today))) {
            String label = period.getMonthValue() + "/" + period.getYear();
            notificationService.sendNotification(groupId, null, "USER",
                    "Monthly Bachat Due for " + label,
                    "Expected monthly bachat collection has been generated. Please deposit your share on time.",
                    "COLLECTION_DUE");
            auditService.log(groupId, recordedBy, recordedBy, "MONTHLY_COLLECTION_GENERATED", "COLLECTION",
                    label, null, "Generated " + generated.size() + " expected collection records", "127.0.0.1");
        }
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
        validateMoney("amount", request.getAmount(), true);
        validateMoney("monthlyBachatAmount", request.getMonthlyBachatAmount(), false);
        validateMoney("loanPrincipalAmount", request.getLoanPrincipalAmount(), false);
        validateMoney("loanInterestAmount", request.getLoanInterestAmount(), false);
        validateMoney("extraLoanPaymentAmount", request.getExtraLoanPaymentAmount(), false);
        validateMoney("otherAmount", request.getOtherAmount(), false);
        validateMoney("lateFeeAmount", request.getLateFeeAmount(), false);
        validateMoney("waivedLateFee", request.getWaivedLateFee(), false);

        Member member = dataService.findMemberByMemberId(request.getMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with Member ID: " + request.getMemberId()));
        if (!groupId.equals(member.getGroupId())) {
            throw new com.bachatgat.exception.ForbiddenException("Member does not belong to this group.");
        }

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

        LocalDate today = MonthlyBachatCalendar.today();
        int month = request.getMonth() > 0 ? request.getMonth() : today.getMonthValue();
        int year = request.getYear() > 0 ? request.getYear() : today.getYear();
        YearMonth period = YearMonth.of(year, month);
        Group collectionGroup = group(groupId);
        boolean eligible = MonthlyBachatCalendar.eligible(collectionGroup, member, period);
        requireEligibleMonthlyPayment(groupId, member, period, requestedShare(request),
                zero(request.getLateFeeAmount()).add(zero(request.getWaivedLateFee())));
        GroupMasterData masterData = rules(groupId, period, today);
        BigDecimal configuredMonthlyBachat = resolveMonthlyBachat(groupId, period.atDay(1), masterData);
        LocalDate calculatedDueDate = MonthlyBachatCalendar.dueDate(collectionGroup, member, masterData, period);

        String businessKey = groupId + "_" + member.getMemberId() + "_" + month + "_" + year;
        CollectionRecord record = dataService.findCollectionByBusinessKey(businessKey).map(this::copyCollection)
                .orElseGet(() -> {
                    BigDecimal bachatAmt = configuredMonthlyBachat;
                    CollectionRecord cr = new CollectionRecord(groupId, member.getMemberId(), member.getFullName(), month, year, bachatAmt);
                    cr.setDueDate(calculatedDueDate);
                    return cr;
                });
        if (eligible && remaining(record).signum() > 0) {
            alignOpenRecordToGroupRule(record, collectionGroup, member, masterData, today);
        }

        LocalDate payDate = request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now();

        // 2. PAYMENT / BANKING SUCCESS VERIFICATION (Section 4)
        if ("FAILED".equalsIgnoreCase(request.getPaymentStatus()) || "CANCELLED".equalsIgnoreCase(request.getPaymentStatus())) {
            record.setStatus(CollectionStatus.FAILED);
            record.setNotes("Payment attempt failed at gateway or banking provider: " + (request.getNotes() != null ? request.getNotes() : ""));
            if (eligible) dataService.saveCollection(record);
            // Log audit
            auditService.log(groupId, recordedBy, recordedBy, "PAYMENT_FAILED", "COLLECTION",
                    record.getId(), null, "Payment failed for ₹" + request.getAmount() + " - no balances updated", "127.0.0.1");
            return record;
        }

        if ("PENDING".equalsIgnoreCase(request.getPaymentStatus())) {
            record.setStatus(CollectionStatus.PENDING);
            record.setNotes("Payment pending at gateway or banking provider: " + (request.getNotes() != null ? request.getNotes() : ""));
            if (eligible) dataService.saveCollection(record);
            auditService.log(groupId, recordedBy, recordedBy, "PAYMENT_PENDING", "COLLECTION",
                    record.getId(), null, "Payment pending for ₹" + request.getAmount() + " - no balances updated", "127.0.0.1");
            return record;
        }

        // 3. THREE-CATEGORY MONEY DISTRIBUTION CALCULATION (Section 3 & 67)
        BigDecimal totalReceived = request.getAmount();

        // Component 1: Share / Savings
        BigDecimal sharePart = requestedShare(request);
        // Component 2: Loan Principal & Interest
        BigDecimal loanPrincPart = request.getLoanPrincipalAmount() != null ? request.getLoanPrincipalAmount() : BigDecimal.ZERO;
        BigDecimal loanIntPart = request.getLoanInterestAmount() != null ? request.getLoanInterestAmount() : BigDecimal.ZERO;
        BigDecimal extraLoanPart = request.getExtraLoanPaymentAmount() != null ? request.getExtraLoanPaymentAmount() : BigDecimal.ZERO;
        // Component 3: Other
        BigDecimal otherPart = request.getOtherAmount() != null ? request.getOtherAmount() : BigDecimal.ZERO;
        // Late fee
        BigDecimal configuredLateFee = lateFee(collectionGroup, masterData);
        BigDecimal lateFee = request.getLateFeeAmount() != null ? request.getLateFeeAmount() : BigDecimal.ZERO;
        BigDecimal waivedFee = request.getWaivedLateFee() != null ? request.getWaivedLateFee() : BigDecimal.ZERO;
        if (waivedFee.compareTo(lateFee) > 0) {
            throw new InvalidFinancialOperationException("Waived late fee cannot exceed the late fee amount.");
        }
        if (waivedFee.signum() > 0 && waivedFee.compareTo(lateFee) < 0) {
            throw new InvalidFinancialOperationException(
                    "The full penalty must be waived; partial penalty waivers are not allowed."
            );
        }
        LocalDate dueDate = record.getDueDate() != null ? record.getDueDate() : calculatedDueDate;
        BigDecimal expectedShare = record.getExpectedAmount() != null
                ? record.getExpectedAmount() : configuredMonthlyBachat;
        BigDecimal alreadyPaidShare = record.getPaidAmount() != null
                ? record.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal remainingShare = expectedShare.subtract(alreadyPaidShare).max(BigDecimal.ZERO);
        BigDecimal accruedPenalty = eligible && remainingShare.signum() > 0
                ? calculateAccruedPenalty(configuredLateFee, dueDate, payDate).subtract(record.getPaidLateFee()).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        if (accruedPenalty.signum() == 0 && (lateFee.signum() > 0 || waivedFee.signum() > 0)) {
            throw new InvalidFinancialOperationException("No monthly Bachat penalty is due for this payment.");
        }

        // The server owns the penalty calculation. A browser cannot submit an
        // older/smaller penalty to bypass the daily increase.
        if ((sharePart.signum() > 0 || lateFee.signum() > 0 || waivedFee.signum() > 0) && accruedPenalty.signum() > 0) {
            if (waivedFee.signum() > 0) {
                if (waivedFee.compareTo(accruedPenalty) != 0) {
                    throw new InvalidFinancialOperationException(
                            "Only the full accrued penalty of ₹" + accruedPenalty + " can be waived."
                    );
                }
                lateFee = accruedPenalty;
            } else {
                if (lateFee.compareTo(accruedPenalty) != 0) {
                    throw new InvalidFinancialOperationException(
                            "The current penalty is ₹" + accruedPenalty + " for "
                                    + java.time.temporal.ChronoUnit.DAYS.between(dueDate, payDate)
                                    + " overdue day(s). The full amount is required."
                    );
                }
                lateFee = accruedPenalty;
            }
        }
        BigDecimal netLateFee = lateFee.subtract(waivedFee).max(BigDecimal.ZERO);

        if (netLateFee.signum() > 0 && totalReceived.compareTo(netLateFee) < 0) {
            throw new InvalidFinancialOperationException(
                    "Payment amount cannot be less than the penalty of ₹" + netLateFee + ". " +
                    "Pay at least the full penalty amount or waive it before saving the payment."
            );
        }

        // Validate the payment's accounting equation before period-specific
        // rules so callers receive the precise error when allocations do not
        // reconcile, even if the savings component would also exceed the
        // remaining monthly amount.
        BigDecimal totalAllocated = sharePart.add(loanPrincPart).add(loanIntPart).add(extraLoanPart).add(otherPart).add(netLateFee);
        if (totalAllocated.compareTo(totalReceived) != 0) {
            throw new InvalidFinancialOperationException(
                    "Total allocated amount (₹" + totalAllocated + ") does not equal amount received (₹" + totalReceived + "). " +
                    "Every rupee must be explicitly allocated to Share, Loan EMI, Extra Principal, Late Fee, or Other."
            );
        }

        if (sharePart.signum() > 0 && remainingShare.signum() > 0 && sharePart.compareTo(remainingShare) != 0) {
            throw new InvalidFinancialOperationException(
                    "Partial savings payments are not allowed. Pay the full remaining monthly bachat of ₹"
                            + remainingShare + "."
            );
        }
        if (netLateFee.signum() > 0 && remainingShare.signum() > 0) {
            BigDecimal minimumPayment = remainingShare.add(netLateFee);
            if (totalReceived.compareTo(minimumPayment) < 0) {
                throw new InvalidFinancialOperationException(
                        "Monthly bachat of ₹" + remainingShare + " plus the full penalty of ₹" + netLateFee
                                + " is required. Minimum payment is ₹" + minimumPayment + "."
                );
            }
        }
        if (sharePart.signum() > 0 && sharePart.compareTo(remainingShare) > 0) {
            throw new InvalidFinancialOperationException(
                    "Monthly bachat payment cannot exceed the remaining configured amount of ₹" + remainingShare +
                    ". Extra payment must be allocated to a loan, late fee, or other category."
            );
        }

        BigDecimal totalLoanPayment = loanPrincPart.add(loanIntPart);
        String paymentLoanId = null;
        if (totalLoanPayment.signum() > 0 || extraLoanPart.signum() > 0) {
            String selectedId = request.getLoanId();
            if (selectedId == null || selectedId.isBlank()) selectedId = member.getActiveLoanId();
            Loan selectedLoan = (selectedId == null || selectedId.isBlank()
                    ? dataService.findActiveLoanByMemberId(member.getMemberId()) : dataService.findLoanById(selectedId))
                    .filter(loan -> groupId.equals(loan.getGroupId()) && member.getMemberId().equalsIgnoreCase(loan.getMemberId()))
                    .orElseThrow(() -> new InvalidFinancialOperationException("Choose a loan for this member in the current group."));
            paymentLoanId = selectedLoan.getId();
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

            record.setPaymentDate(payDate);
            record.setPaymentMethod(request.getPaymentMethod());
            record.setReferenceNumber(request.getReferenceNumber());
            record.setNotes(request.getNotes());
            record.setRecordedBy(recordedBy);

            // Determine status
            record.setDueDate(dueDate);
            if (payDate.isAfter(dueDate)) {
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
                BigDecimal oldShare = member.overallSavingsBalance();
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
                        TransactionType.PENALTY_INCOME, netLateFee, BigDecimal.ZERO, BigDecimal.ZERO,
                        payDate, request.getReferenceNumber(),
                        "Late Payment Charge for " + month + "/" + year + " (Days late: " + record.getDaysLate() + ")",
                        request.getPaymentMethod(), recordedBy
                );
                lateFeeTxn.setCategory("LATE_FEE");
                lateFeeTxn.setIdempotencyKey(request.getIdempotencyKey());
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
        if (paymentLoanId != null) {
            if (totalLoanPayment.signum() > 0) {
                LoanRepaymentRequest loanReq = new LoanRepaymentRequest(
                        totalLoanPayment, payDate, request.getPaymentMethod(), request.getReferenceNumber());
                loanReq.setNotes("Multi-category Collection Payment [Principal: ₹" + loanPrincPart + ", Interest: ₹" + loanIntPart + "]");
                // Collection components here are earmarked for principal and interest.
                loanReq.setLoanPenaltyAmount(BigDecimal.ZERO);
                loanService.recordRepayment(paymentLoanId, loanReq, recordedBy);
            }
            if (extraLoanPart.signum() > 0) {
                LoanExtraPaymentRequest extraReq = new LoanExtraPaymentRequest();
                extraReq.setAmount(extraLoanPart);
                extraReq.setPaymentDate(payDate);
                extraReq.setPaymentMethod(request.getPaymentMethod());
                extraReq.setReferenceNumber(request.getReferenceNumber());
                extraReq.setNotes("Member extra principal payment");
                loanService.recordExtraPayment(paymentLoanId, extraReq, recordedBy);
            }
        }

        // 6. PROCESS OPTION 3: OTHER
        if (otherPart.compareTo(BigDecimal.ZERO) > 0) {
            String otherCat = request.getOtherCategory() != null ? request.getOtherCategory() : "MISC";
            GroupIncome inc = new GroupIncome(
                    groupId, otherCat,
                    "Collection payment other allocation from " + member.getFullName() + (request.getNotes() != null ? " - " + request.getNotes() : ""),
                    otherPart, payDate, request.getPaymentMethod(), request.getReferenceNumber(),
                    "GROUP_LEVEL_ONLY", recordedBy
            );
            inc.setCategory(otherCat);
            dataService.saveIncome(inc);

            Transaction otherTxn = new Transaction(
                    "TXN-" + System.currentTimeMillis() + "-OTH",
                    groupId, member.getMemberId(), member.getFullName(), null,
                    TransactionType.OTHER_INCOME, otherPart, BigDecimal.ZERO, BigDecimal.ZERO,
                    payDate, request.getReferenceNumber(),
                    "Other Payment Component (" + otherCat + ") from " + member.getFullName(),
                    request.getPaymentMethod(), recordedBy
            );
            otherTxn.setCategory("OTHER");
            otherTxn.setIdempotencyKey(request.getIdempotencyKey());
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

    private void validateMoney(String field, BigDecimal amount, boolean required) {
        if (amount == null) {
            if (required) {
                throw new InvalidFinancialOperationException(field + " is required.");
            }
            return;
        }
        if (amount.signum() < 0) {
            throw new InvalidFinancialOperationException(field + " cannot be negative.");
        }
        if (amount.scale() > 2) {
            throw new InvalidFinancialOperationException(field + " cannot have more than 2 decimal places.");
        }
    }

}

