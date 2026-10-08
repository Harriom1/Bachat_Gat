package com.bachatgat.repository;

import com.bachatgat.model.*;
import com.bachatgat.util.FinancialCalculator;
import com.google.cloud.firestore.Firestore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class FirestoreDataService {

    private static final Logger log = LoggerFactory.getLogger(FirestoreDataService.class);

    private final Firestore firestore;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    // In-memory synchronized stores ensuring zero downtime and offline/local compatibility
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, Group> groups = new ConcurrentHashMap<>();
    private final Map<String, Member> members = new ConcurrentHashMap<>();
    private final Map<String, CollectionRecord> collections = new ConcurrentHashMap<>();
    private final Map<String, LoanApplication> loanApplications = new ConcurrentHashMap<>();
    private final Map<String, Loan> loans = new ConcurrentHashMap<>();
    private final Map<String, List<LoanRepaymentSchedule>> loanSchedules = new ConcurrentHashMap<>();
    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
    private final Map<String, AuditLog> auditLogs = new ConcurrentHashMap<>();
    private final Map<String, SystemNotification> notifications = new ConcurrentHashMap<>();
    private final Map<String, DocumentRecord> documents = new ConcurrentHashMap<>();
    private final Map<String, GroupExpense> expenses = new ConcurrentHashMap<>();
    private final Map<String, MeetingRecord> meetings = new ConcurrentHashMap<>();
    private final Map<String, GroupIncome> incomes = new ConcurrentHashMap<>();
    private final Map<String, MemberAdjustment> adjustments = new ConcurrentHashMap<>();
    private final Map<String, GroupMasterData> masterDataMap = new ConcurrentHashMap<>();
    private final Map<String, PaymentOrder> paymentOrders = new ConcurrentHashMap<>();

    @Autowired

    public FirestoreDataService(@Autowired(required = false) Firestore firestore,
                                PasswordEncoder passwordEncoder,
                                ObjectMapper objectMapper) {
        this.firestore = firestore;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing Bachat Gat repository and seed data...");
        seedInitialDemoData();
        normalizeLegacyRegistrationIds();
        log.info("Repository initialized successfully. Total groups: {}, Total members: {}, Total users: {}",
                groups.size(), members.size(), users.size());
    }

    /** One-time-safe migration: make legacy registration numbers group-local. */
    private void normalizeLegacyRegistrationIds() {
        int year = LocalDate.now().getYear();
        for (String groupId : groups.keySet()) {
            List<Member> groupMembers = getMembersByGroupId(groupId);
            groupMembers.sort(Comparator.comparing(Member::getMemberId, Comparator.nullsLast(String::compareTo)));
            int sequence = 1;
            for (Member member : groupMembers) {
                String expected = String.format("MEM-%d-%06d", year, sequence++);
                if (!expected.equals(member.getRegistrationId())) {
                    member.setRegistrationId(expected);
                    members.put(member.getId(), member);
                    persistToFirestoreAsync("members", member.getId(), member);
                }
            }
        }
    }

    // ==========================================
    // USER OPERATIONS
    // ==========================================
    public Optional<User> findUserByUsername(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        String cleanId = identifier.trim();
        String digitsOnly = cleanId.replaceAll("[^0-9]", "");
        return users.values().stream()
                .filter(u -> (u.getUsername() != null && u.getUsername().equalsIgnoreCase(cleanId))
                        || (u.getEmail() != null && u.getEmail().equalsIgnoreCase(cleanId))
                        || (u.getMobileNumber() != null && (u.getMobileNumber().equalsIgnoreCase(cleanId)
                                || (!digitsOnly.isEmpty() && digitsOnly.length() >= 10 && u.getMobileNumber().replaceAll("[^0-9]", "").endsWith(digitsOnly))))
                        || (u.getMemberId() != null && u.getMemberId().equalsIgnoreCase(cleanId)))
                .findFirst();
    }

    public Optional<User> findUserByIdentifier(String identifier) {
        return findUserByUsername(identifier);
    }

    public List<User> findUsersByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return java.util.Collections.emptyList();
        String cleanId = identifier.trim();
        String digitsOnly = cleanId.replaceAll("[^0-9]", "");
        return users.values().stream()
                .filter(u -> (u.getUsername() != null && u.getUsername().equalsIgnoreCase(cleanId))
                        || (u.getEmail() != null && u.getEmail().equalsIgnoreCase(cleanId))
                        || (u.getMobileNumber() != null && (u.getMobileNumber().equalsIgnoreCase(cleanId)
                                || (!digitsOnly.isEmpty() && digitsOnly.length() >= 10 && u.getMobileNumber().replaceAll("[^0-9]", "").endsWith(digitsOnly))))
                        || (u.getMemberId() != null && u.getMemberId().equalsIgnoreCase(cleanId)))
                .collect(Collectors.toList());
    }

    public Optional<User> findUserById(String id) {
        return Optional.ofNullable(users.get(id));
    }

    public Optional<User> findUserByMemberId(String memberId) {
        return users.values().stream()
                .filter(u -> memberId != null && memberId.equalsIgnoreCase(u.getMemberId()))
                .findFirst();
    }

    public User saveUser(User user) {
        if (user.getId() == null) {
            user.setId(UUID.randomUUID().toString());
        }
        user.setUpdatedAt(LocalDateTime.now());
        users.put(user.getId(), user);
        persistToFirestoreAsync("users", user.getId(), user);
        return user;
    }

    // ==========================================
    // GROUP OPERATIONS
    // ==========================================
    public List<Group> getAllGroups() {
        return groups.values().stream()
                .sorted(Comparator.comparing(Group::getId))
                .collect(Collectors.toList());
    }

    public Optional<Group> findGroupById(String id) {
        return Optional.ofNullable(groups.get(id));
    }

    public Optional<Group> findGroupByRegistrationId(String regId) {
        return groups.values().stream()
                .filter(g -> g.getRegistrationId() != null && g.getRegistrationId().equalsIgnoreCase(regId))
                .findFirst();
    }

    public Group saveGroup(Group group) {
        if (group.getId() == null) {
            group.setId("group-" + UUID.randomUUID().toString().substring(0, 8));
        }
        group.setUpdatedAt(LocalDateTime.now());
        groups.put(group.getId(), group);
        persistToFirestoreAsync("groups", group.getId(), group);
        return group;
    }

    public Optional<Group> findGroupByGroupCode(String code) {
        if (code == null) return Optional.empty();
        return groups.values().stream()
                .filter(g -> g.getGroupCode() != null && g.getGroupCode().equalsIgnoreCase(code))
                .findFirst();
    }

    public List<User> findUsersByRole(Role role) {
        return users.values().stream()
                .filter(u -> u.getRole() == role)
                .sorted(Comparator.comparing(User::getUsername))
                .collect(Collectors.toList());
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    public synchronized String generateNextGroupCode() {
        int year = LocalDate.now().getYear();
        int maxSeq = 0;
        for (Group g : groups.values()) {
            if (g.getGroupCode() != null && g.getGroupCode().startsWith("BG-")) {
                String[] parts = g.getGroupCode().split("-");
                if (parts.length >= 3) {
                    try {
                        int seq = Integer.parseInt(parts[2]);
                        if (seq > maxSeq) maxSeq = seq;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return String.format("BG-%d-%06d", year, maxSeq + 1);
    }

    public synchronized String generateNextGroupRegistrationId() {
        int year = LocalDate.now().getYear();
        int maxSeq = 0;
        for (Group g : groups.values()) {
            if (g.getRegistrationId() != null && g.getRegistrationId().startsWith("GR-")) {
                String[] parts = g.getRegistrationId().split("-");
                if (parts.length >= 3) {
                    try {
                        int seq = Integer.parseInt(parts[2]);
                        if (seq > maxSeq) maxSeq = seq;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return String.format("GR-%d-%06d", year, maxSeq + 1);
    }

    public synchronized String generateNextMemberRegistrationId() {
        int year = LocalDate.now().getYear();
        int maxSeq = 0;
        for (Member m : members.values()) {
            if (m.getRegistrationId() != null && m.getRegistrationId().startsWith("MEM-")) {
                String[] parts = m.getRegistrationId().split("-");
                if (parts.length >= 3) {
                    try {
                        int seq = Integer.parseInt(parts[2]);
                        if (seq > maxSeq) maxSeq = seq;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return String.format("MEM-%d-%06d", year, maxSeq + 1);
    }

    /**
     * Registration numbers are human-facing numbers and are scoped to a
     * Bachat Gat. A member in every group can therefore start at 000001.
     */
    public synchronized String generateNextMemberRegistrationId(String groupId) {
        int year = LocalDate.now().getYear();
        int maxSeq = 0;
        for (Member m : getMembersByGroupId(groupId)) {
            String registrationId = m.getRegistrationId();
            if (registrationId == null || !registrationId.startsWith("MEM-")) continue;
            String[] parts = registrationId.split("-");
            if (parts.length >= 3) {
                try {
                    maxSeq = Math.max(maxSeq, Integer.parseInt(parts[2]));
                } catch (NumberFormatException ignored) {
                    // Ignore legacy/non-numeric registration IDs.
                }
            }
        }
        return String.format("MEM-%d-%06d", year, maxSeq + 1);
    }

    // ==========================================
    // MEMBER OPERATIONS
    // ==========================================
    public List<Member> getMembersByGroupId(String groupId) {
        return members.values().stream()
                .filter(m -> groupId == null || m.getGroupId().equals(groupId))
                .sorted(Comparator.comparing(Member::getMemberId))
                .collect(Collectors.toList());
    }

    public Optional<Member> findMemberById(String id) {
        return Optional.ofNullable(members.get(id));
    }

    public Member getMemberById(String idOrMemberId) {
        if (idOrMemberId == null) return null;
        Member m = members.get(idOrMemberId);
        if (m != null) return m;
        return findMemberByMemberId(idOrMemberId).orElse(null);
    }

    public List<Member> getAllMembers() {
        return members.values().stream()
                .sorted(Comparator.comparing(Member::getFullName))
                .collect(Collectors.toList());
    }

    public boolean deleteMember(String id) {
        Member m = members.remove(id);
        if (m != null) {
            Group group = groups.get(m.getGroupId());
            if (group != null) {
                long count = members.values().stream()
                        .filter(mem -> mem.getGroupId().equals(group.getId()) && mem.getStatus() == MemberStatus.ACTIVE)
                        .count();
                group.setActiveMembersCount((int) count);
                saveGroup(group);
            }
            return true;
        }
        return false;
    }

    public Optional<Member> findMemberByMemberId(String memberId) {
        return members.values().stream()
                .filter(m -> m.getMemberId().equalsIgnoreCase(memberId))
                .findFirst();
    }

    public Member saveMember(Member member) {
        if (member.getId() == null) {
            member.setId("mem-" + UUID.randomUUID().toString().substring(0, 8));
        }
        member.setUpdatedAt(LocalDateTime.now());
        members.put(member.getId(), member);
        persistToFirestoreAsync("members", member.getId(), member);

        // Update group active member count
        Group group = groups.get(member.getGroupId());
        if (group != null) {
            long count = members.values().stream()
                    .filter(m -> m.getGroupId().equals(group.getId()) && m.getStatus() == MemberStatus.ACTIVE)
                    .count();
            group.setActiveMembersCount((int) count);
            saveGroup(group);
        }
        return member;
    }

    // ==========================================
    // COLLECTION OPERATIONS
    // ==========================================
    public List<CollectionRecord> getCollectionsByGroupId(String groupId) {
        return collections.values().stream()
                .filter(c -> groupId == null || c.getGroupId().equals(groupId))
                .sorted(Comparator.comparing(CollectionRecord::getYear).reversed()
                        .thenComparing(CollectionRecord::getMonth).reversed())
                .collect(Collectors.toList());
    }

    public List<CollectionRecord> getCollectionsByMemberId(String memberId) {
        return collections.values().stream()
                .filter(c -> c.getMemberId().equalsIgnoreCase(memberId))
                .sorted(Comparator.comparing(CollectionRecord::getYear).reversed()
                        .thenComparing(CollectionRecord::getMonth).reversed())
                .collect(Collectors.toList());
    }

    public Optional<CollectionRecord> findCollectionByBusinessKey(String businessKey) {
        return Optional.ofNullable(collections.get(businessKey));
    }

    public CollectionRecord saveCollection(CollectionRecord record) {
        if (record.getId() == null) {
            record.setId(record.getBusinessKey());
        }
        record.setUpdatedAt(LocalDateTime.now());
        collections.put(record.getId(), record);
        persistToFirestoreAsync("collections", record.getId(), record);
        return record;
    }

    // ==========================================
    // LOAN APPLICATION OPERATIONS
    // ==========================================
    public List<LoanApplication> getLoanApplicationsByGroupId(String groupId) {
        return loanApplications.values().stream()
                .filter(la -> groupId == null || la.getGroupId().equals(groupId))
                .sorted(Comparator.comparing(LoanApplication::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<LoanApplication> getLoanApplicationsByMemberId(String memberId) {
        return loanApplications.values().stream()
                .filter(la -> la.getMemberId().equalsIgnoreCase(memberId))
                .sorted(Comparator.comparing(LoanApplication::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public Optional<LoanApplication> findLoanApplicationById(String id) {
        return Optional.ofNullable(loanApplications.get(id));
    }

    public LoanApplication saveLoanApplication(LoanApplication app) {
        if (app.getId() == null) {
            app.setId("la-" + UUID.randomUUID().toString().substring(0, 8));
        }
        app.setUpdatedAt(LocalDateTime.now());
        loanApplications.put(app.getId(), app);
        persistToFirestoreAsync("loanApplications", app.getId(), app);
        return app;
    }

    // ==========================================
    // LOAN OPERATIONS
    // ==========================================
    public List<Loan> getLoansByGroupId(String groupId) {
        return loans.values().stream()
                .filter(l -> groupId == null || l.getGroupId().equals(groupId))
                .sorted(Comparator.comparing(Loan::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public Optional<Loan> findLoanById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        Loan l = loans.get(id);
        if (l != null) return Optional.of(l);
        return loans.values().stream()
                .filter(loan -> id.equalsIgnoreCase(loan.getId()) || id.equalsIgnoreCase(loan.getLoanId()))
                .findFirst();
    }

    public Optional<Loan> findLoanByLoanId(String loanId) {
        return findLoanById(loanId);
    }

    public Optional<Loan> findActiveLoanByMemberId(String memberId) {
        return loans.values().stream()
                .filter(l -> l.getMemberId().equalsIgnoreCase(memberId) && 
                        (l.getStatus() == LoanStatus.ACTIVE || l.getStatus() == LoanStatus.PARTIALLY_PAID || l.getStatus() == LoanStatus.OVERDUE))
                .findFirst();
    }

    public List<Loan> getLoansByMemberId(String memberId) {
        return loans.values().stream()
                .filter(l -> l.getMemberId().equalsIgnoreCase(memberId))
                .sorted(Comparator.comparing(Loan::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public Loan saveLoan(Loan loan) {
        if (loan.getId() == null) {
            loan.setId("loan-" + UUID.randomUUID().toString().substring(0, 8));
        }
        loan.setUpdatedAt(LocalDateTime.now());
        loans.put(loan.getId(), loan);
        if (loan.getLoanId() != null) {
            loans.put(loan.getLoanId(), loan);
        }
        persistToFirestoreAsync("loans", loan.getId(), loan);
        return loan;
    }

    public List<LoanRepaymentSchedule> getLoanSchedule(String loanId) {
        if (loanId == null) return new ArrayList<>();
        List<LoanRepaymentSchedule> s = loanSchedules.get(loanId);
        if (s != null && !s.isEmpty()) return s;
        Optional<Loan> loanOpt = findLoanById(loanId);
        if (loanOpt.isPresent()) {
            Loan l = loanOpt.get();
            if (loanSchedules.containsKey(l.getId()) && !loanSchedules.get(l.getId()).isEmpty()) {
                return loanSchedules.get(l.getId());
            }
            if (l.getLoanId() != null && loanSchedules.containsKey(l.getLoanId()) && !loanSchedules.get(l.getLoanId()).isEmpty()) {
                return loanSchedules.get(l.getLoanId());
            }

            // Auto-generate reducing balance amortization schedule on the fly
            BigDecimal principal = l.getPrincipalAmount() != null ? l.getPrincipalAmount() : BigDecimal.ZERO;
            BigDecimal rate = l.getInterestRate() != null ? l.getInterestRate() : BigDecimal.valueOf(12.0);
            int duration = l.getDurationMonths() > 0 ? l.getDurationMonths() : 12;
            LocalDate startDate = l.getDisbursementDate() != null ? l.getDisbursementDate() : LocalDate.now();
            List<LoanRepaymentSchedule> generated = com.bachatgat.util.FinancialCalculator.generateBachatGatReducingSchedule(principal, rate, duration, startDate);

            // Reconcile status and reducing balances strictly based on actual payments
            BigDecimal runningPrincipal = principal.setScale(0, java.math.RoundingMode.HALF_UP);
            BigDecimal remainingPaid = l.getTotalPaid() != null ? l.getTotalPaid() : BigDecimal.ZERO;
            boolean isMonthlyRate = rate.compareTo(BigDecimal.valueOf(5.0)) <= 0;
            BigDecimal monthlyRateFactor = isMonthlyRate
                    ? rate.divide(BigDecimal.valueOf(100), 8, java.math.RoundingMode.HALF_UP)
                    : rate.divide(BigDecimal.valueOf(1200), 8, java.math.RoundingMode.HALF_UP);
            BigDecimal defaultMonthlyPrincipal = duration > 0 ? principal.divide(BigDecimal.valueOf(duration), 0, java.math.RoundingMode.DOWN) : BigDecimal.ZERO;

            for (int i = 0; i < generated.size(); i++) {
                LoanRepaymentSchedule item = generated.get(i);
                item.setOpeningPrincipal(runningPrincipal);
                BigDecimal interest = runningPrincipal.multiply(monthlyRateFactor).setScale(0, java.math.RoundingMode.HALF_UP);
                item.setInterestAmount(interest);

                BigDecimal targetPrincipal = (i == generated.size() - 1)
                        ? runningPrincipal
                        : defaultMonthlyPrincipal.min(runningPrincipal);
                item.setPrincipalAmount(targetPrincipal);
                BigDecimal emi = targetPrincipal.add(interest);
                item.setEmiAmount(emi);
                item.setTotalDue(emi);

                if (remainingPaid.compareTo(BigDecimal.ZERO) > 0) {
                    if (remainingPaid.compareTo(emi) >= 0) {
                        item.setPaidAmount(emi);
                        item.setStatus(com.bachatgat.model.RepaymentStatus.PAID);
                        item.setOutstandingAmount(BigDecimal.ZERO);
                        remainingPaid = remainingPaid.subtract(emi);
                        runningPrincipal = runningPrincipal.subtract(targetPrincipal).max(BigDecimal.ZERO);
                        item.setClosingPrincipal(runningPrincipal);
                    } else {
                        // Partial payment on this installment
                        BigDecimal paid = remainingPaid;
                        item.setPaidAmount(paid);
                        item.setOutstandingAmount(emi.subtract(paid));
                        item.setStatus(com.bachatgat.model.RepaymentStatus.PARTIAL);
                        remainingPaid = BigDecimal.ZERO;

                        // Paid amount covers interest first, remaining portion covers principal
                        BigDecimal interestPaid = paid.min(interest);
                        BigDecimal principalPaid = paid.subtract(interestPaid).max(BigDecimal.ZERO);
                        runningPrincipal = runningPrincipal.subtract(principalPaid).max(BigDecimal.ZERO);
                        item.setClosingPrincipal(runningPrincipal);
                    }
                } else {
                    // Future upcoming installment
                    item.setPaidAmount(BigDecimal.ZERO);
                    item.setOutstandingAmount(emi);
                    item.setStatus(com.bachatgat.model.RepaymentStatus.UPCOMING);
                    runningPrincipal = runningPrincipal.subtract(targetPrincipal).max(BigDecimal.ZERO);
                    item.setClosingPrincipal(runningPrincipal);
                }
            }

            loanSchedules.put(l.getId(), generated);
            if (l.getLoanId() != null) {
                loanSchedules.put(l.getLoanId(), generated);
            }
            return generated;
        }
        return new ArrayList<>();
    }

    public void saveLoanSchedule(String loanId, List<LoanRepaymentSchedule> schedule) {
        loanSchedules.put(loanId, schedule);
        findLoanById(loanId).ifPresent(l -> {
            loanSchedules.put(l.getId(), schedule);
            if (l.getLoanId() != null) loanSchedules.put(l.getLoanId(), schedule);
        });
    }

    // ==========================================
    // TRANSACTIONS LEDGER
    // ==========================================
    public List<Transaction> getTransactionsByGroupId(String groupId) {
        return transactions.values().stream()
                .filter(t -> groupId == null || t.getGroupId().equals(groupId))
                .sorted(Comparator.comparing(Transaction::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<Transaction> getTransactionsByMemberId(String memberId) {
        return transactions.values().stream()
                .filter(t -> t.getMemberId() != null && t.getMemberId().equalsIgnoreCase(memberId))
                .sorted(Comparator.comparing(Transaction::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public Transaction saveTransaction(Transaction txn) {
        if (txn.getId() == null) {
            txn.setId("TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4));
        }
        transactions.put(txn.getId(), txn);
        persistToFirestoreAsync("transactions", txn.getId(), txn);
        return txn;
    }

    public Optional<Transaction> findTransactionByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return Optional.empty();
        return transactions.values().stream()
                .filter(t -> idempotencyKey.equalsIgnoreCase(t.getIdempotencyKey()))
                .findFirst();
    }

    public Optional<Transaction> findTransactionByReference(String reference) {
        if (reference == null || reference.isBlank()) return Optional.empty();
        return transactions.values().stream()
                .filter(t -> reference.equalsIgnoreCase(t.getReference()))
                .findFirst();
    }


    // ==========================================
    // AUDIT LOGS
    // ==========================================
    public List<AuditLog> getAuditLogsByGroupId(String groupId) {
        return auditLogs.values().stream()
                .filter(a -> groupId == null || a.getGroupId() == null || a.getGroupId().equals(groupId))
                .sorted(Comparator.comparing(AuditLog::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    public void saveAuditLog(AuditLog logRecord) {
        if (logRecord.getId() == null) {
            logRecord.setId(UUID.randomUUID().toString());
        }
        auditLogs.put(logRecord.getId(), logRecord);
        persistToFirestoreAsync("auditLogs", logRecord.getId(), logRecord);
    }

    // ==========================================
    // NOTIFICATIONS
    // ==========================================
    public List<SystemNotification> getNotificationsForUser(String userId, String role, String groupId) {
        return notifications.values().stream()
                .filter(n -> (n.getTargetUserId() != null && n.getTargetUserId().equals(userId)) ||
                             (n.getTargetRole() != null && n.getTargetRole().equals(role) && (n.getGroupId() == null || n.getGroupId().equals(groupId))))
                .sorted(Comparator.comparing(SystemNotification::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public SystemNotification saveNotification(SystemNotification notification) {
        if (notification.getId() == null) {
            notification.setId(UUID.randomUUID().toString());
        }
        notifications.put(notification.getId(), notification);
        persistToFirestoreAsync("notifications", notification.getId(), notification);
        return notification;
    }

    // ==========================================
    // DOCUMENTS
    // ==========================================
    public List<DocumentRecord> getDocumentsByMemberId(String memberId) {
        return documents.values().stream()
                .filter(d -> d.getMemberId() != null && d.getMemberId().equalsIgnoreCase(memberId))
                .collect(Collectors.toList());
    }

    public List<DocumentRecord> getDocumentsByGroupId(String groupId) {
        return documents.values().stream()
                .filter(d -> groupId == null || d.getGroupId().equals(groupId))
                .collect(Collectors.toList());
    }

    public Optional<DocumentRecord> findDocumentById(String id) {
        return Optional.ofNullable(documents.get(id));
    }

    public DocumentRecord saveDocument(DocumentRecord doc) {
        if (doc.getId() == null) {
            doc.setId("DOC-" + UUID.randomUUID().toString().substring(0, 8));
        }
        documents.put(doc.getId(), doc);
        persistToFirestoreAsync("documents", doc.getId(), doc);
        return doc;
    }

    // ==========================================
    // EXPENSES & MEETINGS
    // ==========================================
    public List<GroupExpense> getExpensesByGroupId(String groupId) {
        return expenses.values().stream()
                .filter(e -> groupId == null || e.getGroupId().equals(groupId))
                .sorted(Comparator.comparing(GroupExpense::getExpenseDate).reversed())
                .collect(Collectors.toList());
    }

    public GroupExpense saveExpense(GroupExpense expense) {
        if (expense.getId() == null) {
            expense.setId("EXP-" + UUID.randomUUID().toString().substring(0, 8));
        }
        expenses.put(expense.getId(), expense);
        persistToFirestoreAsync("expenses", expense.getId(), expense);
        return expense;
    }

    public List<MeetingRecord> getMeetingsByGroupId(String groupId) {
        return meetings.values().stream()
                .filter(m -> groupId == null || m.getGroupId().equals(groupId))
                .sorted(Comparator.comparing(MeetingRecord::getMeetingDate).reversed())
                .collect(Collectors.toList());
    }

    public MeetingRecord saveMeeting(MeetingRecord meeting) {
        if (meeting.getId() == null) {
            meeting.setId("MTG-" + UUID.randomUUID().toString().substring(0, 8));
        }
        meetings.put(meeting.getId(), meeting);
        persistToFirestoreAsync("meetings", meeting.getId(), meeting);
        return meeting;
    }

    // ==========================================
    // OTHER INCOME OPERATIONS (Section 14 & 16)
    // ==========================================
    public List<GroupIncome> getIncomesByGroupId(String groupId) {
        return incomes.values().stream()
                .filter(i -> groupId == null || i.getGroupId().equals(groupId))
                .sorted(Comparator.comparing(GroupIncome::getIncomeDate).reversed())
                .collect(Collectors.toList());
    }

    public Optional<GroupIncome> findIncomeById(String id) {
        return Optional.ofNullable(incomes.get(id));
    }

    public GroupIncome saveIncome(GroupIncome income) {
        if (income.getId() == null) {
            income.setId("INC-" + UUID.randomUUID().toString().substring(0, 8));
        }
        incomes.put(income.getId(), income);
        persistToFirestoreAsync("incomes", income.getId(), income);
        return income;
    }

    // ==========================================
    // MEMBER ADJUSTMENT OPERATIONS (Section 16 & 17)
    // ==========================================
    public List<MemberAdjustment> getAdjustmentsByGroupId(String groupId) {
        return adjustments.values().stream()
                .filter(a -> groupId == null || a.getGroupId().equals(groupId))
                .sorted(Comparator.comparing(MemberAdjustment::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<MemberAdjustment> getAdjustmentsByMemberId(String memberId) {
        return adjustments.values().stream()
                .filter(a -> a.getMemberId() != null && a.getMemberId().equalsIgnoreCase(memberId))
                .sorted(Comparator.comparing(MemberAdjustment::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public MemberAdjustment saveAdjustment(MemberAdjustment adjustment) {
        if (adjustment.getId() == null) {
            adjustment.setId("ADJ-" + UUID.randomUUID().toString().substring(0, 8));
        }
        adjustments.put(adjustment.getId(), adjustment);
        persistToFirestoreAsync("adjustments", adjustment.getId(), adjustment);
        return adjustment;
    }

    // ==========================================
    // MASTER DATA OPERATIONS (Section 7, 8, 9, 58)
    // ==========================================
    public Optional<GroupMasterData> getLatestMasterDataByGroupId(String groupId) {
        return masterDataMap.values().stream()
                .filter(m -> m.getGroupId().equals(groupId) && m.isActive())
                .max(Comparator.comparingInt(GroupMasterData::getVersion));
    }

    /** Selects the rule version that applied to a collection month. */
    public Optional<GroupMasterData> getMasterDataForDate(String groupId, LocalDate collectionDate) {
        LocalDate date = collectionDate != null ? collectionDate : LocalDate.now();
        return masterDataMap.values().stream()
                .filter(m -> m.getGroupId().equals(groupId))
                .filter(m -> m.getEffectiveFrom() == null || !m.getEffectiveFrom().isAfter(date))
                .filter(m -> m.getEffectiveTo() == null || !m.getEffectiveTo().isBefore(date))
                .max(Comparator.comparingInt(GroupMasterData::getVersion));
    }

    public List<GroupMasterData> getAllMasterDataVersions(String groupId) {
        return masterDataMap.values().stream()
                .filter(m -> m.getGroupId().equals(groupId))
                .sorted(Comparator.comparingInt(GroupMasterData::getVersion).reversed())
                .collect(Collectors.toList());
    }

    public GroupMasterData saveMasterData(GroupMasterData md) {
        if (md.getId() == null) {
            md.setId(md.getGroupId() + "_v" + md.getVersion());
        }
        masterDataMap.put(md.getId(), md);
        persistToFirestoreAsync("groupMasterData", md.getId(), md);
        return md;
    }

    // ==========================================
    // PAYMENT ORDER OPERATIONS
    // ==========================================
    public Optional<PaymentOrder> findPaymentOrderById(String orderId) {
        if (orderId == null) return Optional.empty();
        return Optional.ofNullable(paymentOrders.get(orderId));
    }

    public Optional<PaymentOrder> findPaymentOrderByReceiptNumber(String receiptNumber) {
        if (receiptNumber == null) return Optional.empty();
        return paymentOrders.values().stream()
                .filter(o -> receiptNumber.equalsIgnoreCase(o.getReceiptNumber()))
                .findFirst();
    }

    public List<PaymentOrder> getPaymentOrdersByGroupId(String groupId) {
        return paymentOrders.values().stream()
                .filter(o -> groupId == null || groupId.equals(o.getGroupId()))
                .sorted(Comparator.comparing(PaymentOrder::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<PaymentOrder> getPaymentOrdersByMemberId(String memberId) {
        return paymentOrders.values().stream()
                .filter(o -> memberId != null && memberId.equalsIgnoreCase(o.getMemberId()))
                .sorted(Comparator.comparing(PaymentOrder::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public PaymentOrder savePaymentOrder(PaymentOrder order) {
        if (order.getOrderId() == null) {
            order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        order.setUpdatedAt(LocalDateTime.now());
        paymentOrders.put(order.getOrderId(), order);
        persistToFirestoreAsync("paymentOrders", order.getOrderId(), order);
        return order;
    }


    // ==========================================
    // FIRESTORE ASYNC WRITER
    // ==========================================
    private void persistToFirestoreAsync(String collectionName, String documentId, Object object) {
        if (firestore != null) {
            try {
                Map<String, Object> firestoreData = objectMapper.convertValue(
                        object, new TypeReference<Map<String, Object>>() {});
                firestore.collection(collectionName).document(documentId).set(firestoreData);
            } catch (Exception ex) {
                log.debug("Async write to Firestore skipped/failed: {}", ex.getMessage());
            }
        }
    }

    // ==========================================
    // DEMO DATA SEEDING (Section 64)
    // ==========================================
    public synchronized void seedInitialDemoData() {
        users.clear();
        groups.clear();
        members.clear();
        collections.clear();
        loanApplications.clear();
        loans.clear();
        loanSchedules.clear();
        transactions.clear();
        auditLogs.clear();
        notifications.clear();
        documents.clear();
        expenses.clear();
        meetings.clear();
        incomes.clear();
        adjustments.clear();
        masterDataMap.clear();

        // 1. Create Default Group
        Group group = new Group();
        group.setId("bg-001");
        group.setGroupName("Mahila Pragati Bachat Gat");
        group.setGroupNameMr("महिला प्रगती बचत गट");
        group.setGroupNameHi("महिला प्रगति बचत समूह");
        group.setRegistrationId("GR-2026-000001");
        group.setBachatGatRegNumber("MH/SHG/12345");
        group.setGroupCode("BG-2026-000001");
        group.setOrganizationId("MAVIM-PUNE-88");
        group.setFormationDate(LocalDate.of(2023, 1, 15));
        group.setRegistrationDate(LocalDate.of(2023, 3, 10));
        group.setVillage("Shirur");
        group.setTaluka("Shirur");
        group.setDistrict("Pune");
        group.setState("Maharashtra");
        group.setPinCode("412210");
        group.setMeetingDay("Every 1st Sunday");
        group.setMeetingTime("10:30 AM");
        group.setMeetingLocation("Gram Panchayat Hall, Shirur");
        group.setMonthlyShareAmount(BigDecimal.valueOf(5000));
        group.setDefaultInterestRate(BigDecimal.valueOf(12.0));
        group.setMaxLoanAmount(BigDecimal.valueOf(100000));
        group.setMaxLoanAmountPerMember(BigDecimal.valueOf(100000));
        group.setMaxOutstandingLoanAmount(BigDecimal.valueOf(150000));
        group.setGracePeriodDays(5);
        group.setLateFeeAmount(BigDecimal.valueOf(100));
        group.setCollectionDueDay(10);
        group.setPresident("Shalini Deshmukh");
        group.setPresidentNameMr("शालिनी देशमुख");
        group.setPresidentNameHi("शालिनी देशमुख");
        group.setSecretary("Rekha Shinde");
        group.setTreasurer("Anita More");
        group.setContactNumber("9823012345");
        group.setEmail("mahila.pragati@bachatgat.org");
        group.setAssignedAdminId("usr-groupadmin-01");
        group.setAssignedAdminName("Shalini Deshmukh");
        group.setBankName("Bank of Maharashtra");
        group.setBranch("Shirur Main");
        group.setAccountNumber("60123456789");
        group.setIfsc("MAHB0000123");
        group.setUpiDetails("mahilapragati@mahb");
        group.setStatus("ACTIVE");
        group.setActiveMembersCount(20);
        // Reconciled from the seeded member contributions below.
        group.setTotalSavingsBalance(BigDecimal.ZERO);
        group.setTotalLoanOutstanding(BigDecimal.valueOf(65000));
        groups.put(group.getId(), group);

        // 2. Create System Super Admin and Group Admin Users
        User superAdmin = new User("superadmin", passwordEncoder.encode("admin123"), "superadmin@bachatgat.org", "System Super Admin", Role.SUPER_ADMIN, null, null);
        superAdmin.setId("usr-superadmin-01");
        superAdmin.setFullNameMr("सिस्टम सुपर ॲडमिन");
        superAdmin.setFullNameHi("सिस्टम सुपर एडमिन");
        superAdmin.setDesignation("SUPER_ADMIN");
        users.put(superAdmin.getId(), superAdmin);

        // Legacy system admin user (authorized for group 1 maintenance & automated test suites)
        User legacyAdmin = new User("admin", passwordEncoder.encode("admin123"), "admin@bachatgat.org", "System Administrator", Role.ADMIN, group.getId(), null);
        legacyAdmin.setId("usr-admin-01");
        legacyAdmin.setFullNameMr("सिस्टम प्रशासक");
        legacyAdmin.setFullNameHi("सिस्टम प्रशासक");
        legacyAdmin.setDesignation("ADMIN");
        users.put(legacyAdmin.getId(), legacyAdmin);

        // Client / Group Admin / President dedicated for Group 1 (bg-001)
        User groupAdmin = new User("groupadmin", passwordEncoder.encode("admin123"), "groupadmin@bachatgat.org", "Shalini Deshmukh", Role.PRESIDENT, group.getId(), null);
        groupAdmin.setId("usr-groupadmin-01");
        groupAdmin.setFullNameMr("शालिनी देशमुख");
        groupAdmin.setFullNameHi("शालिनी देशमुख");
        groupAdmin.setMobileNumber("9823012345");
        groupAdmin.setDesignation("PRESIDENT");
        groupAdmin.setFirstLogin(false);
        users.put(groupAdmin.getId(), groupAdmin);

        // Dedicated President Login (Requirements 1, 2, 3, 4)
        User presidentUser = new User("president", passwordEncoder.encode("admin123"), "president@bachatgat.org", "Shalini Deshmukh", Role.PRESIDENT, group.getId(), null);
        presidentUser.setId("usr-president-01");
        presidentUser.setFullNameMr("शालिनी देशमुख");
        presidentUser.setFullNameHi("शालिनी देशमुख");
        presidentUser.setMobileNumber("9823012345");
        presidentUser.setDesignation("PRESIDENT");
        presidentUser.setFirstLogin(false);
        users.put(presidentUser.getId(), presidentUser);

        // Dedicated Secretary Login (Requirements 1, 2, 3, 4)
        User secretaryUser = new User("secretary", passwordEncoder.encode("admin123"), "secretary@bachatgat.org", "Rekha Sanjay Shinde", Role.SECRETARY, group.getId(), "MPBG-M002");
        secretaryUser.setId("usr-secretary-01");
        secretaryUser.setFullNameMr("रेखा संजय शिंदे");
        secretaryUser.setFullNameHi("रेखा संजय शिंदे");
        secretaryUser.setMobileNumber("9823023456");
        secretaryUser.setDesignation("SECRETARY");
        secretaryUser.setFirstLogin(false);
        users.put(secretaryUser.getId(), secretaryUser);

        // Dedicated Treasurer Login (Requirements 1, 2, 3, 4)
        User treasurerUser = new User("treasurer", passwordEncoder.encode("admin123"), "treasurer@bachatgat.org", "Anita Vijay More", Role.TREASURER, group.getId(), "MPBG-M003");
        treasurerUser.setId("usr-treasurer-01");
        treasurerUser.setFullNameMr("अनिता विजय मोरे");
        treasurerUser.setFullNameHi("अनीता विजय मोरे");
        treasurerUser.setMobileNumber("9823034567");
        treasurerUser.setDesignation("TREASURER");
        treasurerUser.setFirstLogin(false);
        users.put(treasurerUser.getId(), treasurerUser);

        // 3. Seed 20 Members with English, Marathi, Hindi Names and Registration IDs
        String[] memberNames = {
            "Sunita Ramesh Patil", "Rekha Sanjay Shinde", "Anita Vijay More", "Savita Ashok Deshmukh",
            "Mangal Prakash Kadam", "Sujata Dilip Jadhav", "Vandana Ganesh Pawar", "Meena Suresh Bhosale",
            "Sharda Vilas Chavan", "Usha Mohan Gaikwad", "Lata Datta Salunkhe", "Kavita Raju Jagtap",
            "Surekha Balu Thorat", "Geeta Arun Shirole", "Pratibha Sandeep Tambe", "Pooja Santosh Lande",
            "Archana Nitin Bhandari", "Chhaya Sunil Sonawane", "Swati Sachin Bankar", "Vaishali Dipak Gawade"
        };

        String[] memberNamesMr = {
            "सुनिता रमेश पाटील", "रेखा संजय शिंदे", "अनिता विजय मोरे", "सविता अशोक देशमुख",
            "मंगल प्रकाश कदम", "सुजाता दिलीप जाधव", "वंदना गणेश पवार", "मीना सुरेश भोसले",
            "शारदा विलास चव्हाण", "उषा मोहन गायकवाड", "लता दत्ता साळुंखे", "कविता राजू जगताप",
            "सुरेखा बाळू थोरात", "गीता अरुण शिरोळे", "प्रतिभा संदीप तांबे", "पूजा संतोष लांडे",
            "अर्चना नितीन भंडारी", "छाया सुनील सोनावणे", "स्वाती सचिन बनकर", "वैशाली दीपक गावडे"
        };

        String[] memberNamesHi = {
            "सुनीता रमेश पाटिल", "रेखा संजय शिंदे", "अनीता विजय मोरे", "सविता अशोक देशमुख",
            "मंगल प्रकाश कदम", "सुजाता दिलीप जाधव", "वंदना गणेश पवार", "मीना सुरेश भोसले",
            "शारदा विलास चव्हाण", "उषा मोहन गायकवाड़", "लता दत्ता सालुंखे", "कविता राजू जगताप",
            "सुरेखा बालू थोरात", "गीता अरुण शिरोले", "प्रतिभा संदीप तांबे", "पूजा संतोष लांडे",
            "अर्चना नितिन भंडारी", "छाया सुनील सोनवणे", "स्वाति सचिन बनकर", "वैशाली दीपक गावड़े"
        };

        String[] phoneNumbers = {
            "9823012345", "9823023456", "9823034567", "9823045678", "9823056789",
            "9823067890", "9823078901", "9823089012", "9823090123", "9823001234",
            "9823112345", "9823123456", "9823134567", "9823145678", "9823156789",
            "9823167890", "9823178901", "9823189012", "9823190123", "9823201234"
        };

        for (int i = 0; i < 20; i++) {
            String mId = String.format("MPBG-M%03d", (i + 1));
            String memberKey = "mem-" + (i + 1);
            Member m = new Member();
            m.setId(memberKey);
            m.setMemberId(mId);
            m.setRegistrationId(String.format("MEM-2026-%06d", (i + 1)));
            m.setGroupId(group.getId());
            m.setGroupName(group.getGroupName());
            m.setFullName(memberNames[i]);
            m.setFullNameMr(memberNamesMr[i]);
            m.setFullNameHi(memberNamesHi[i]);
            m.setGuardianName(memberNames[i].split(" ")[1] + " " + memberNames[i].split(" ")[2]);
            m.setGender("FEMALE");
            m.setDob(LocalDate.of(1985 + (i % 10), (i % 12) + 1, (i % 25) + 1));
            m.setMobileNumber(phoneNumbers[i]);
            m.setAlternateMobile("97650" + (10000 + i));
            m.setEmail("member." + (i + 1) + "@bachatgat.org");
            m.setAddress("House No. " + (10 + i) + ", Ward No. 2, Shirur");
            m.setVillage("Shirur");
            m.setTaluka("Shirur");
            m.setDistrict("Pune");
            m.setState("Maharashtra");
            m.setPinCode("412210");
            m.setOccupation((i % 2 == 0) ? "Dairy Farming" : "Agri-business / Tailoring");
            m.setJoiningDate(LocalDate.of(2023, 1, 15));
            m.setStatus(MemberStatus.ACTIVE);
            m.setMonthlyShareAmount(BigDecimal.valueOf(5000));
            m.setInitialShareAmount(BigDecimal.valueOf(2000));
            m.setCurrentShareBalance(m.getInitialShareAmount());
            m.setMembershipFee(BigDecimal.valueOf(200));
            m.setTotalSavingsBalance(m.getInitialShareAmount());
            m.setPendingSavings(i % 5 == 0 ? BigDecimal.valueOf(5000) : BigDecimal.ZERO);
            m.setPanNumber("ABCDE" + (1000 + i) + "F");
            m.setAadhaarReference("XXXXXXXX" + (1000 + i));
            m.setNomineeName(m.getGuardianName());
            m.setNomineeRelation("Spouse");
            m.setNomineeMobile(m.getAlternateMobile());
            m.setNomineeAddress(m.getAddress());
            m.setBankName("Bank of Maharashtra");
            m.setAccountNumber("6098765" + (1000 + i));
            m.setIfsc("MAHB0000123");
            m.setBranch("Shirur Main");
            m.setCreatedBy("admin");

            // Create Member User Account for the first member: user/user123, member/user123 and sunita/user123
            if (i == 0) {
                m.setHasLoginAccount(true);
                m.setLoginUsername("user");
                User memberUser = new User("user", passwordEncoder.encode("user123"), m.getEmail(), m.getFullName(), Role.MEMBER, group.getId(), m.getMemberId());
                memberUser.setId("usr-member-01");
                memberUser.setFullNameMr(m.getFullNameMr());
                memberUser.setFullNameHi(m.getFullNameHi());
                memberUser.setMobileNumber(m.getMobileNumber());
                memberUser.setDesignation("MEMBER");
                users.put(memberUser.getId(), memberUser);

                User directMemberUser = new User("member", passwordEncoder.encode("user123"), m.getEmail(), m.getFullName(), Role.MEMBER, group.getId(), m.getMemberId());
                directMemberUser.setId("usr-member-direct-01");
                directMemberUser.setFullNameMr(m.getFullNameMr());
                directMemberUser.setFullNameHi(m.getFullNameHi());
                directMemberUser.setMobileNumber(m.getMobileNumber());
                directMemberUser.setDesignation("MEMBER");
                users.put(directMemberUser.getId(), directMemberUser);

                User sunitaUser = new User("sunita", passwordEncoder.encode("user123"), m.getEmail(), m.getFullName(), Role.MEMBER, group.getId(), m.getMemberId());
                sunitaUser.setId("usr-sunita-01");
                sunitaUser.setFullNameMr(m.getFullNameMr());
                sunitaUser.setFullNameHi(m.getFullNameHi());
                sunitaUser.setMobileNumber(m.getMobileNumber());
                sunitaUser.setDesignation("MEMBER");
                users.put(sunitaUser.getId(), sunitaUser);
            }

            // Also seed demo user with firstLogin=true to test requirement 14 & 17 force password change
            if (i == 1) {
                m.setHasLoginAccount(true);
                m.setLoginUsername("newmember");
                User newMemberUser = new User("newmember", passwordEncoder.encode("tempPass123"), m.getEmail(), m.getFullName(), Role.USER, group.getId(), m.getMemberId());
                newMemberUser.setId("usr-newmember-02");
                newMemberUser.setFullNameMr(m.getFullNameMr());
                newMemberUser.setFullNameHi(m.getFullNameHi());
                newMemberUser.setMobileNumber(m.getMobileNumber());
                newMemberUser.setFirstLogin(true); // REQUIRED FIRST LOGIN FLAG
                users.put(newMemberUser.getId(), newMemberUser);
            } else if (i >= 2) {
                m.setHasLoginAccount(true);
                m.setLoginUsername(m.getMobileNumber());
                User genericMemberUser = new User(m.getMobileNumber(), passwordEncoder.encode("tempPass123"), m.getEmail(), m.getFullName(), Role.USER, group.getId(), m.getMemberId());
                genericMemberUser.setId("usr-member-" + (i + 1));
                genericMemberUser.setFullNameMr(m.getFullNameMr());
                genericMemberUser.setFullNameHi(m.getFullNameHi());
                genericMemberUser.setMobileNumber(m.getMobileNumber());
                genericMemberUser.setFirstLogin(true); // Mandatory first-login password change
                users.put(genericMemberUser.getId(), genericMemberUser);
            }

            members.put(m.getId(), m);
        }

        // 4. Seed Monthly Collection Records (Current Year 2026, Months: 1 through 10 - January to October)
        int currentYear = 2026;
        for (int month = 1; month <= 10; month++) {
            for (Member m : members.values()) {
                String bKey = group.getId() + "_" + m.getMemberId() + "_" + month + "_" + currentYear;
                CollectionRecord col = new CollectionRecord(group.getId(), m.getMemberId(), m.getFullName(), month, currentYear, BigDecimal.valueOf(5000));
                col.setId(bKey);
                col.setBusinessKey(bKey);
                col.setDueDate(LocalDate.of(currentYear, month, 10));

                if (month <= 6) {
                    // Keep the March record for M001 open so the manual
                    // payment flow has an unambiguous historical example.
                    if (month == 3 && m.getMemberId().endsWith("001")) {
                        col.setPaidAmount(BigDecimal.ZERO);
                        col.setPendingAmount(BigDecimal.valueOf(5000));
                        col.setStatus(CollectionStatus.PENDING);
                    } else {
                        col.setPaidAmount(BigDecimal.valueOf(5000));
                        col.setPendingAmount(BigDecimal.ZERO);
                        col.setStatus(CollectionStatus.PAID);
                        col.setPaymentDate(LocalDate.of(currentYear, month, 5));
                        col.setPaymentMethod(month % 2 == 0 ? "UPI" : "CASH");
                        col.setRecordedBy("admin");
                    }
                } else if (month == 7 || month == 8 || month == 9) {
                    // Leave M001 and M003 open in their exercised periods so
                    // payment and idempotency flows start from a clean state.
                    if (m.getMemberId().endsWith("001") || (month == 9 && m.getMemberId().endsWith("003"))) {
                        col.setPaidAmount(BigDecimal.ZERO);
                        col.setPendingAmount(BigDecimal.valueOf(5000));
                        col.setStatus(CollectionStatus.PENDING);
                    } else {
                        col.setPaidAmount(BigDecimal.valueOf(5000));
                        col.setPendingAmount(BigDecimal.ZERO);
                        col.setStatus(CollectionStatus.PAID);
                        col.setPaymentDate(LocalDate.of(currentYear, month, 6));
                        col.setPaymentMethod("UPI");
                        col.setRecordedBy("admin");
                    }
                } else {
                    // Month 10 (Current Month: October 2026)
                    if (m.getMemberId().endsWith("002")) {
                        col.setPaidAmount(BigDecimal.valueOf(5000));
                        col.setPendingAmount(BigDecimal.ZERO);
                        col.setStatus(CollectionStatus.PAID);
                        col.setPaymentDate(LocalDate.of(currentYear, month, 4));
                        col.setPaymentMethod("UPI");
                        col.setRecordedBy("admin");
                    } else if (m.getMemberId().endsWith("003")) {
                        col.setPaidAmount(BigDecimal.valueOf(3000));
                        col.setPendingAmount(BigDecimal.valueOf(2000));
                        col.setStatus(CollectionStatus.PARTIAL);
                        col.setPaymentDate(LocalDate.of(currentYear, month, 5));
                        col.setPaymentMethod("CASH");
                        col.setRecordedBy("admin");
                    } else {
                        col.setPaidAmount(BigDecimal.ZERO);
                        col.setPendingAmount(BigDecimal.valueOf(5000));
                        col.setStatus(CollectionStatus.PENDING);
                    }
                }
                collections.put(col.getId(), col);
            }
        }

        // 5. Seed Loan 1: Active Loan for Sunita Patil (MPBG-M001)
        Member sunita = members.get("mem-1");
        Loan loan1 = new Loan();
        loan1.setId("loan-001");
        loan1.setLoanId("LN-2026-001");
        loan1.setApplicationId("LA-2026-001");
        loan1.setGroupId(group.getId());
        loan1.setGroupName(group.getGroupName());
        loan1.setMemberId(sunita.getMemberId());
        loan1.setMemberName(sunita.getFullName());
        loan1.setPrincipalAmount(BigDecimal.valueOf(40000));
        loan1.setInterestRate(BigDecimal.valueOf(12.0));
        loan1.setDurationMonths(10);
        loan1.setInterestType("FLAT");
        
        // Calculations:
        // Flat interest: 40000 * 0.12 * (10/12) = 4000
        BigDecimal totalInterest1 = FinancialCalculator.calculateFlatInterest(BigDecimal.valueOf(40000), BigDecimal.valueOf(12.0), 10);
        loan1.setTotalInterest(totalInterest1);
        loan1.setTotalPayable(BigDecimal.valueOf(44000));
        loan1.setMonthlyInstallment(BigDecimal.valueOf(4400));
        loan1.setPrincipalPaid(BigDecimal.valueOf(16000));
        loan1.setInterestPaid(BigDecimal.valueOf(1600));
        loan1.setTotalPaid(BigDecimal.valueOf(17600));
        loan1.setOutstandingPrincipal(BigDecimal.valueOf(24000));
        loan1.setOutstandingInterest(BigDecimal.valueOf(2400));
        loan1.setTotalOutstanding(BigDecimal.valueOf(26400));
        loan1.setDisbursementDate(LocalDate.of(2025, 11, 10));
        loan1.setNextDueDate(LocalDate.of(2026, 4, 10));
        loan1.setStatus(LoanStatus.ACTIVE);
        loan1.setPurpose("Dairy Buffalo Purchase / दुग्ध व्यवसाय");
        loan1.setCreatedBy("admin");
        loans.put(loan1.getId(), loan1);

        sunita.setActiveLoanId(loan1.getLoanId());
        sunita.setCurrentLoanOutstanding(loan1.getTotalOutstanding());
        sunita.setTotalLoanInterestPaid(loan1.getInterestPaid());

        // Repayment schedule for Loan 1
        List<LoanRepaymentSchedule> schedule1 = new ArrayList<>();
        for (int inst = 1; inst <= 10; inst++) {
            LoanRepaymentSchedule s = new LoanRepaymentSchedule();
            s.setId("sch-" + loan1.getId() + "-" + inst);
            s.setLoanId(loan1.getId());
            s.setGroupId(group.getId());
            s.setMemberId(sunita.getMemberId());
            s.setInstallmentNo(inst);
            s.setDueDate(loan1.getDisbursementDate().plusMonths(inst));
            s.setPrincipalAmount(BigDecimal.valueOf(4000));
            s.setInterestAmount(BigDecimal.valueOf(400));
            s.setTotalDue(BigDecimal.valueOf(4400));

            if (inst <= 4) {
                s.setPaidAmount(BigDecimal.valueOf(4400));
                s.setOutstandingAmount(BigDecimal.ZERO);
                s.setStatus(RepaymentStatus.PAID);
                s.setPaidDate(s.getDueDate().minusDays(2));
            } else if (inst == 5) {
                s.setPaidAmount(BigDecimal.ZERO);
                s.setOutstandingAmount(BigDecimal.valueOf(4400));
                s.setStatus(RepaymentStatus.DUE);
            } else {
                s.setPaidAmount(BigDecimal.ZERO);
                s.setOutstandingAmount(BigDecimal.valueOf(4400));
                s.setStatus(RepaymentStatus.UPCOMING);
            }
            schedule1.add(s);
        }
        loanSchedules.put(loan1.getId(), schedule1);

        // Seed Loan 2 for Group 1: Rekha Sanjay Shinde (MPBG-M002)
        Member rekha = members.get("mem-2");
        Loan loan2 = new Loan();
        loan2.setId("loan-002");
        loan2.setLoanId("LN-2026-002");
        loan2.setApplicationId("LA-2026-003");
        loan2.setGroupId(group.getId());
        loan2.setGroupName(group.getGroupName());
        loan2.setMemberId(rekha.getMemberId());
        loan2.setMemberName(rekha.getFullName());
        loan2.setPrincipalAmount(BigDecimal.valueOf(60000));
        loan2.setInterestRate(BigDecimal.valueOf(12.0));
        loan2.setDurationMonths(12);
        loan2.setInterestType("FLAT");
        loan2.setTotalInterest(BigDecimal.valueOf(7200));
        loan2.setTotalPayable(BigDecimal.valueOf(67200));
        loan2.setMonthlyInstallment(BigDecimal.valueOf(5600));
        loan2.setPrincipalPaid(BigDecimal.valueOf(20000));
        loan2.setInterestPaid(BigDecimal.valueOf(2400));
        loan2.setTotalPaid(BigDecimal.valueOf(22400));
        loan2.setOutstandingPrincipal(BigDecimal.valueOf(40000));
        loan2.setOutstandingInterest(BigDecimal.valueOf(4800));
        loan2.setTotalOutstanding(BigDecimal.valueOf(44800));
        loan2.setDisbursementDate(LocalDate.of(2025, 12, 10));
        loan2.setNextDueDate(LocalDate.of(2026, 4, 10));
        loan2.setStatus(LoanStatus.ACTIVE);
        loan2.setPurpose("Agriculture Drip Irrigation / ठिबक सिंचन खरेदी");
        loan2.setCreatedBy("admin");
        loans.put(loan2.getId(), loan2);

        List<LoanRepaymentSchedule> schedule2 = com.bachatgat.util.FinancialCalculator.generateBachatGatReducingSchedule(
                loan2.getPrincipalAmount(),
                loan2.getInterestRate(),
                loan2.getDurationMonths(),
                loan2.getDisbursementDate()
        );
        BigDecimal remPaid2 = loan2.getTotalPaid();
        for (LoanRepaymentSchedule item : schedule2) {
            if (remPaid2.compareTo(BigDecimal.ZERO) > 0) {
                if (remPaid2.compareTo(item.getTotalDue()) >= 0) {
                    item.setPaidAmount(item.getTotalDue());
                    item.setStatus(RepaymentStatus.PAID);
                    item.setOutstandingAmount(BigDecimal.ZERO);
                    remPaid2 = remPaid2.subtract(item.getTotalDue());
                } else {
                    item.setPaidAmount(remPaid2);
                    item.setOutstandingAmount(item.getTotalDue().subtract(remPaid2));
                    item.setStatus(RepaymentStatus.PARTIAL);
                    remPaid2 = BigDecimal.ZERO;
                }
            }
        }
        loanSchedules.put(loan2.getId(), schedule2);
        loanSchedules.put(loan2.getLoanId(), schedule2);

        rekha.setActiveLoanId(loan2.getLoanId());
        rekha.setCurrentLoanOutstanding(loan2.getTotalOutstanding());
        rekha.setTotalLoanInterestPaid(loan2.getInterestPaid());

        // Seed Loan 3 for Group 1: Vandana Pawar (MPBG-M007) - CLOSED Loan
        Member vandana = members.get("mem-7");
        Loan loan3 = new Loan();
        loan3.setId("loan-003");
        loan3.setLoanId("LN-2026-003");
        loan3.setApplicationId("LA-2025-099");
        loan3.setGroupId(group.getId());
        loan3.setGroupName(group.getGroupName());
        loan3.setMemberId(vandana.getMemberId());
        loan3.setMemberName(vandana.getFullName());
        loan3.setPrincipalAmount(BigDecimal.valueOf(25000));
        loan3.setInterestRate(BigDecimal.valueOf(12.0));
        loan3.setDurationMonths(6);
        loan3.setInterestType("FLAT");
        loan3.setTotalInterest(BigDecimal.valueOf(1500));
        loan3.setTotalPayable(BigDecimal.valueOf(26500));
        loan3.setMonthlyInstallment(BigDecimal.valueOf(4417));
        loan3.setPrincipalPaid(BigDecimal.valueOf(25000));
        loan3.setInterestPaid(BigDecimal.valueOf(1500));
        loan3.setTotalPaid(BigDecimal.valueOf(26500));
        loan3.setOutstandingPrincipal(BigDecimal.ZERO);
        loan3.setOutstandingInterest(BigDecimal.ZERO);
        loan3.setTotalOutstanding(BigDecimal.ZERO);
        loan3.setDisbursementDate(LocalDate.of(2025, 6, 10));
        loan3.setNextDueDate(null);
        loan3.setStatus(LoanStatus.CLOSED);
        loan3.setPurpose("Sewing & Tailoring Business / शिलाई व्यवसाय");
        loan3.setCreatedBy("admin");
        loans.put(loan3.getId(), loan3);

        // 6. Seed Loan Application (Pending Approval from Member Anita More)
        Member anita = members.get("mem-3");
        LoanApplication app1 = new LoanApplication();
        app1.setId("la-002");
        app1.setApplicationId("LA-2026-002");
        app1.setGroupId(group.getId());
        app1.setGroupName(group.getGroupName());
        app1.setMemberId(anita.getMemberId());
        app1.setMemberName(anita.getFullName());
        app1.setRequestedAmount(BigDecimal.valueOf(50000));
        app1.setPurpose("Higher Education for Daughter (Engineering Fees)");
        app1.setPreferredDurationMonths(12);
        app1.setOptionalMessage("Kindly sanction as early as possible. College fee submission deadline is next month.");
        app1.setStatus(LoanApplicationStatus.PENDING);
        app1.setCreatedAt(LocalDateTime.now().minusDays(3));
        loanApplications.put(app1.getId(), app1);

        // 7. Seed Transactions Ledger for Group 1
        Transaction t1 = new Transaction("TXN-2026-0001", group.getId(), sunita.getMemberId(), sunita.getFullName(),
                null, TransactionType.MEMBER_SAVING, BigDecimal.valueOf(5000), BigDecimal.valueOf(5000), BigDecimal.ZERO,
                LocalDate.of(2026, 3, 4), "CASH-REC-01", "March 2026 Monthly Regular Share Collection", "CASH", "admin");
        transactions.put(t1.getId(), t1);

        Transaction t2 = new Transaction("TXN-2026-0002", group.getId(), sunita.getMemberId(), sunita.getFullName(),
                loan1.getLoanId(), TransactionType.LOAN_REPAYMENT, BigDecimal.valueOf(4400), BigDecimal.valueOf(4000), BigDecimal.valueOf(400),
                LocalDate.of(2026, 3, 8), "UPI-UTR-98765432", "Installment #4 for Loan LN-2026-001", "UPI", "admin");
        transactions.put(t2.getId(), t2);

        Transaction t3 = new Transaction("TXN-2026-0003", group.getId(), null, null,
                null, TransactionType.EXPENSE, BigDecimal.valueOf(850), BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.of(2026, 3, 1), "REC-STN-99", "Group meeting stationery and registers purchase", "CASH", "admin");
        transactions.put(t3.getId(), t3);

        // =========================================================================
        // GROUP 2: Savitribai Phule Mahila Bachat Gat (Baramati, Pune)
        // =========================================================================
        Group group2 = new Group();
        group2.setId("bg-002");
        group2.setGroupName("Savitribai Phule Bachat Gat");
        group2.setGroupNameMr("सावित्रीबाई फुले बचत गट");
        group2.setGroupNameHi("सावित्रीबाई फुले बचत समूह");
        group2.setRegistrationId("GR-2026-000002");
        group2.setBachatGatRegNumber("MH/SHG/45892");
        group2.setGroupCode("BG-2026-000002");
        group2.setOrganizationId("MAVIM-BARAMATI-12");
        group2.setFormationDate(LocalDate.of(2023, 6, 10));
        group2.setRegistrationDate(LocalDate.of(2023, 8, 15));
        group2.setVillage("Baramati");
        group2.setTaluka("Baramati");
        group2.setDistrict("Pune");
        group2.setState("Maharashtra");
        group2.setPinCode("413102");
        group2.setMeetingDay("Every 2nd Saturday");
        group2.setMeetingTime("11:00 AM");
        group2.setMeetingLocation("Mahila Vikas Bhavan, Baramati");
        group2.setMonthlyShareAmount(BigDecimal.valueOf(3000));
        group2.setDefaultInterestRate(BigDecimal.valueOf(12.0));
        group2.setMaxLoanAmount(BigDecimal.valueOf(75000));
        group2.setMaxLoanAmountPerMember(BigDecimal.valueOf(75000));
        group2.setMaxOutstandingLoanAmount(BigDecimal.valueOf(120000));
        group2.setGracePeriodDays(5);
        group2.setLateFeeAmount(BigDecimal.valueOf(50));
        group2.setCollectionDueDay(5);
        group2.setPresident("Meenakshi Shinde");
        group2.setPresidentNameMr("मीनाक्षी शिंदे");
        group2.setPresidentNameHi("मीनाक्षी शिंदे");
        group2.setSecretary("Priyanka Suresh Gholap");
        group2.setTreasurer("Jayashree Prakash Jagdale");
        group2.setContactNumber("9823311111");
        group2.setEmail("savitribai.shg@bachatgat.org");
        group2.setBankName("State Bank of India");
        group2.setBranch("Baramati Main");
        group2.setAccountNumber("38992014567");
        group2.setIfsc("SBIN0000321");
        group2.setUpiDetails("savitribaiphule@sbi");
        group2.setStatus("ACTIVE");
        group2.setActiveMembersCount(10);
        group2.setTotalSavingsBalance(BigDecimal.ZERO);
        group2.setTotalLoanOutstanding(BigDecimal.valueOf(61500));

        // Group 2 Dedicated Admin
        User group2Admin = new User("groupadmin2", passwordEncoder.encode("admin123"), "group2admin@bachatgat.org", "Meenakshi Shinde", Role.ADMIN, group2.getId(), null);
        group2Admin.setId("usr-groupadmin-02");
        group2Admin.setFullNameMr("मीनाक्षी शिंदे");
        group2Admin.setFullNameHi("मीनाक्षी शिंदे");
        group2Admin.setMobileNumber("9823311111");
        group2Admin.setDesignation("PRESIDENT");
        group2Admin.setFirstLogin(false);
        users.put(group2Admin.getId(), group2Admin);
        group2.setAssignedAdminId(group2Admin.getId());
        group2.setAssignedAdminName("Meenakshi Shinde");

        groups.put(group2.getId(), group2);

        // Group 2 Master Data
        GroupMasterData md2 = new GroupMasterData(group2.getId(), 1,
                BigDecimal.valueOf(3000), BigDecimal.valueOf(3000), BigDecimal.valueOf(12.0),
                BigDecimal.valueOf(75000), BigDecimal.valueOf(50), 10, 5, "SYSTEM_INIT");
        md2.setFinancialYear("2025-2026");
        md2.setDefaultLanguage("mr");
        masterDataMap.put(md2.getId(), md2);

        // Seed 10 Members for Group 2
        String[] g2Names = {
            "Meenakshi Anand Shinde", "Priyanka Suresh Gholap", "Jayashree Prakash Jagdale", "Sangita Deepak Kate",
            "Rupali Sandip Mane", "Rohini Vijay Gaikwad", "Manisha Rajendra Thorat", "Archana Sanjay Salunke",
            "Shobha Balasaheb Kokare", "Ashwini Ganesh Chavan"
        };
        String[] g2Phones = {
            "9823311111", "9823322222", "9823333333", "9823344444", "9823355555",
            "9823366666", "9823377777", "9823388888", "9823399999", "9823300000"
        };

        for (int i = 0; i < 10; i++) {
            String mId = String.format("SPBG-M%03d", (i + 1));
            String memberKey = "mem-g2-" + (i + 1);
            Member m = new Member();
            m.setId(memberKey);
            m.setMemberId(mId);
            m.setGroupId(group2.getId());
            m.setGroupName(group2.getGroupName());
            m.setFullName(g2Names[i]);
            m.setGuardianName(g2Names[i].split(" ")[1] + " " + g2Names[i].split(" ")[2]);
            m.setGender("FEMALE");
            m.setDob(LocalDate.of(1987 + (i % 8), (i % 12) + 1, (i % 26) + 1));
            m.setMobileNumber(g2Phones[i]);
            m.setEmail("member.g2." + (i + 1) + "@bachatgat.org");
            m.setAddress("Gat No. " + (20 + i) + ", Baramati Rural");
            m.setVillage("Baramati");
            m.setTaluka("Baramati");
            m.setDistrict("Pune");
            m.setState("Maharashtra");
            m.setPinCode("413102");
            m.setOccupation((i % 2 == 0) ? "Goat / Dairy Farming" : "Small Scale Agribusiness");
            m.setJoiningDate(LocalDate.of(2023, 6, 10));
            m.setStatus(MemberStatus.ACTIVE);
            m.setMonthlyShareAmount(BigDecimal.valueOf(3000));
            m.setInitialShareAmount(BigDecimal.valueOf(1500));
            m.setCurrentShareBalance(m.getInitialShareAmount());
            m.setMembershipFee(BigDecimal.valueOf(200));
            m.setTotalSavingsBalance(m.getInitialShareAmount());
            m.setPendingSavings(BigDecimal.ZERO);
            m.setPanNumber("SPBGM" + (2000 + i) + "K");
            m.setAadhaarReference("XXXXXXXX" + (2000 + i));
            m.setNomineeName(m.getGuardianName());
            m.setNomineeRelation("Spouse");
            m.setBankName("State Bank of India");
            m.setAccountNumber("389920" + (1000 + i));
            m.setIfsc("SBIN0000321");
            m.setBranch("Baramati Main");
            m.setCreatedBy("admin");
            members.put(m.getId(), m);

            // User account for Meenakshi (Group 2 President)
            if (i == 0) {
                User g2User = new User("meenakshi", passwordEncoder.encode("user123"), m.getEmail(), m.getFullName(), Role.USER, group2.getId(), m.getMemberId());
                g2User.setId("usr-meenakshi-01");
                g2User.setMobileNumber(m.getMobileNumber());
                users.put(g2User.getId(), g2User);
            }

            // Group 2 Monthly Collections (Months 1 through 10)
            for (int month = 1; month <= 10; month++) {
                String bKey = group2.getId() + "_" + m.getMemberId() + "_" + month + "_" + currentYear;
                CollectionRecord col = new CollectionRecord(group2.getId(), m.getMemberId(), m.getFullName(), month, currentYear, BigDecimal.valueOf(3000));
                col.setId(bKey);
                col.setBusinessKey(bKey);
                if (month < 10 || m.getMemberId().endsWith("001")) {
                    col.setPaidAmount(BigDecimal.valueOf(3000));
                    col.setPendingAmount(BigDecimal.ZERO);
                    col.setStatus(CollectionStatus.PAID);
                    col.setPaymentDate(LocalDate.of(currentYear, month, 4));
                    col.setPaymentMethod("UPI");
                    col.setRecordedBy("admin");
                } else {
                    col.setPaidAmount(BigDecimal.ZERO);
                    col.setPendingAmount(BigDecimal.valueOf(3000));
                    col.setStatus(CollectionStatus.PENDING);
                }
                collections.put(col.getId(), col);
            }
        }

        // Group 2 Loans:
        // Loan 4: Meenakshi Shinde (SPBG-M001)
        Member meenakshi = members.get("mem-g2-1");
        Loan loan4 = new Loan();
        loan4.setId("loan-011");
        loan4.setLoanId("LN-2026-011");
        loan4.setApplicationId("LA-2026-011");
        loan4.setGroupId(group2.getId());
        loan4.setGroupName(group2.getGroupName());
        loan4.setMemberId(meenakshi.getMemberId());
        loan4.setMemberName(meenakshi.getFullName());
        loan4.setPrincipalAmount(BigDecimal.valueOf(50000));
        loan4.setInterestRate(BigDecimal.valueOf(12.0));
        loan4.setDurationMonths(10);
        loan4.setInterestType("FLAT");
        loan4.setTotalInterest(BigDecimal.valueOf(5000));
        loan4.setTotalPayable(BigDecimal.valueOf(55000));
        loan4.setMonthlyInstallment(BigDecimal.valueOf(5500));
        loan4.setPrincipalPaid(BigDecimal.valueOf(20000));
        loan4.setInterestPaid(BigDecimal.valueOf(2000));
        loan4.setTotalPaid(BigDecimal.valueOf(22000));
        loan4.setOutstandingPrincipal(BigDecimal.valueOf(30000));
        loan4.setOutstandingInterest(BigDecimal.valueOf(3000));
        loan4.setTotalOutstanding(BigDecimal.valueOf(33000));
        loan4.setDisbursementDate(LocalDate.of(2025, 12, 10));
        loan4.setNextDueDate(LocalDate.of(2026, 4, 10));
        loan4.setStatus(LoanStatus.ACTIVE);
        loan4.setPurpose("Goat Farming Setup / शेळीपालन व्यवसाय");
        loan4.setCreatedBy("admin");
        loans.put(loan4.getId(), loan4);

        meenakshi.setActiveLoanId(loan4.getLoanId());
        meenakshi.setCurrentLoanOutstanding(loan4.getTotalOutstanding());
        meenakshi.setTotalLoanInterestPaid(loan4.getInterestPaid());

        // Loan 5: Priyanka Gholap (SPBG-M002)
        Member priyanka = members.get("mem-g2-2");
        Loan loan5 = new Loan();
        loan5.setId("loan-012");
        loan5.setLoanId("LN-2026-012");
        loan5.setApplicationId("LA-2026-012");
        loan5.setGroupId(group2.getId());
        loan5.setGroupName(group2.getGroupName());
        loan5.setMemberId(priyanka.getMemberId());
        loan5.setMemberName(priyanka.getFullName());
        loan5.setPrincipalAmount(BigDecimal.valueOf(30000));
        loan5.setInterestRate(BigDecimal.valueOf(12.0));
        loan5.setDurationMonths(6);
        loan5.setInterestType("FLAT");
        loan5.setTotalInterest(BigDecimal.valueOf(1800));
        loan5.setTotalPayable(BigDecimal.valueOf(31800));
        loan5.setMonthlyInstallment(BigDecimal.valueOf(5300));
        loan5.setPrincipalPaid(BigDecimal.valueOf(15000));
        loan5.setInterestPaid(BigDecimal.valueOf(900));
        loan5.setTotalPaid(BigDecimal.valueOf(15900));
        loan5.setOutstandingPrincipal(BigDecimal.valueOf(15000));
        loan5.setOutstandingInterest(BigDecimal.valueOf(900));
        loan5.setTotalOutstanding(BigDecimal.valueOf(15900));
        loan5.setDisbursementDate(LocalDate.of(2026, 1, 10));
        loan5.setNextDueDate(LocalDate.of(2026, 4, 10));
        loan5.setStatus(LoanStatus.ACTIVE);
        loan5.setPurpose("Flour Mill Machinery / घरगुती पिठाची गिरणी");
        loan5.setCreatedBy("admin");
        loans.put(loan5.getId(), loan5);

        priyanka.setActiveLoanId(loan5.getLoanId());
        priyanka.setCurrentLoanOutstanding(loan5.getTotalOutstanding());
        priyanka.setTotalLoanInterestPaid(loan5.getInterestPaid());

        // Loan 6: Rupali Mane (SPBG-M005)
        Member rupali = members.get("mem-g2-5");
        Loan loan6 = new Loan();
        loan6.setId("loan-013");
        loan6.setLoanId("LN-2026-013");
        loan6.setApplicationId("LA-2026-013");
        loan6.setGroupId(group2.getId());
        loan6.setGroupName(group2.getGroupName());
        loan6.setMemberId(rupali.getMemberId());
        loan6.setMemberName(rupali.getFullName());
        loan6.setPrincipalAmount(BigDecimal.valueOf(20000));
        loan6.setInterestRate(BigDecimal.valueOf(12.0));
        loan6.setDurationMonths(5);
        loan6.setInterestType("FLAT");
        loan6.setTotalInterest(BigDecimal.valueOf(1000));
        loan6.setTotalPayable(BigDecimal.valueOf(21000));
        loan6.setMonthlyInstallment(BigDecimal.valueOf(4200));
        loan6.setPrincipalPaid(BigDecimal.valueOf(8000));
        loan6.setInterestPaid(BigDecimal.valueOf(400));
        loan6.setTotalPaid(BigDecimal.valueOf(8400));
        loan6.setOutstandingPrincipal(BigDecimal.valueOf(12000));
        loan6.setOutstandingInterest(BigDecimal.valueOf(600));
        loan6.setTotalOutstanding(BigDecimal.valueOf(12600));
        loan6.setDisbursementDate(LocalDate.of(2026, 1, 15));
        loan6.setNextDueDate(LocalDate.of(2026, 4, 10));
        loan6.setStatus(LoanStatus.ACTIVE);
        loan6.setPurpose("Children Education & School Fees / मुलांचे शैक्षणिक शुल्क");
        loan6.setCreatedBy("admin");
        loans.put(loan6.getId(), loan6);

        rupali.setActiveLoanId(loan6.getLoanId());
        rupali.setCurrentLoanOutstanding(loan6.getTotalOutstanding());
        rupali.setTotalLoanInterestPaid(loan6.getInterestPaid());

        // =========================================================================
        // GROUP 3: Laxmi Samruddhi Bachat Gat (Haveli / Hadapsar, Pune)
        // =========================================================================
        Group group3 = new Group();
        group3.setId("bg-003");
        group3.setGroupName("Laxmi Samruddhi Bachat Gat");
        group3.setGroupNameMr("लक्ष्मी समृद्धी बचत गट");
        group3.setGroupNameHi("लक्ष्मी समृद्धि बचत समूह");
        group3.setRegistrationId("GR-2026-000003");
        group3.setBachatGatRegNumber("MH/SHG/78914");
        group3.setGroupCode("BG-2026-000003");
        group3.setOrganizationId("MAVIM-HAVELI-45");
        group3.setFormationDate(LocalDate.of(2024, 1, 20));
        group3.setRegistrationDate(LocalDate.of(2024, 3, 5));
        group3.setVillage("Hadapsar");
        group3.setTaluka("Haveli");
        group3.setDistrict("Pune");
        group3.setState("Maharashtra");
        group3.setPinCode("411028");
        group3.setMeetingDay("Every 3rd Sunday");
        group3.setMeetingTime("03:00 PM");
        group3.setMeetingLocation("Samaj Mandir, Hadapsar");
        group3.setMonthlyShareAmount(BigDecimal.valueOf(2000));
        group3.setDefaultInterestRate(BigDecimal.valueOf(12.0));
        group3.setMaxLoanAmount(BigDecimal.valueOf(50000));
        group3.setMaxLoanAmountPerMember(BigDecimal.valueOf(50000));
        group3.setMaxOutstandingLoanAmount(BigDecimal.valueOf(80000));
        group3.setGracePeriodDays(5);
        group3.setLateFeeAmount(BigDecimal.valueOf(50));
        group3.setCollectionDueDay(5);
        group3.setPresident("Suman Gaikwad");
        group3.setPresidentNameMr("सुमन गायकवाड");
        group3.setPresidentNameHi("सुमन गायकवाड");
        group3.setSecretary("Surekha Maruti Jagtap");
        group3.setTreasurer("Vimal Pandurang Kamble");
        group3.setContactNumber("9823411111");
        group3.setEmail("laxmi.samruddhi@bachatgat.org");
        group3.setBankName("Bank of Baroda");
        group3.setBranch("Hadapsar Branch");
        group3.setAccountNumber("451201998877");
        group3.setIfsc("BARB0HADAPS");
        group3.setUpiDetails("laxmisamruddhi@barodampay");
        group3.setStatus("ACTIVE");
        group3.setActiveMembersCount(10);
        group3.setTotalSavingsBalance(BigDecimal.ZERO);
        group3.setTotalLoanOutstanding(BigDecimal.valueOf(36875));

        // Group 3 Dedicated Admin
        User group3Admin = new User("groupadmin3", passwordEncoder.encode("admin123"), "group3admin@bachatgat.org", "Suman Gaikwad", Role.ADMIN, group3.getId(), null);
        group3Admin.setId("usr-groupadmin-03");
        group3Admin.setFullNameMr("सुमन गायकवाड");
        group3Admin.setFullNameHi("सुमन गायकवाड");
        group3Admin.setMobileNumber("9823411111");
        group3Admin.setDesignation("PRESIDENT");
        group3Admin.setFirstLogin(false);
        users.put(group3Admin.getId(), group3Admin);
        group3.setAssignedAdminId(group3Admin.getId());
        group3.setAssignedAdminName("Suman Gaikwad");

        groups.put(group3.getId(), group3);

        // Group 3 Master Data
        GroupMasterData md3 = new GroupMasterData(group3.getId(), 1,
                BigDecimal.valueOf(2000), BigDecimal.valueOf(2000), BigDecimal.valueOf(12.0),
                BigDecimal.valueOf(50000), BigDecimal.valueOf(50), 10, 5, "SYSTEM_INIT");
        md3.setFinancialYear("2025-2026");
        md3.setDefaultLanguage("mr");
        masterDataMap.put(md3.getId(), md3);

        // Seed 10 Members for Group 3
        String[] g3Names = {
            "Suman Dattatray Gaikwad", "Surekha Maruti Jagtap", "Vimal Pandurang Kamble", "Renuka Santosh Nalawade",
            "Pallavi Sunil Shitole", "Sarika Balu Gholap", "Anuradha Anil Kadam", "Deepali Vilas Magar",
            "Shital Mohan Tupe", "Rohini Dnyaneshwar Landge"
        };
        String[] g3Phones = {
            "9823411111", "9823422222", "9823433333", "9823444444", "9823455555",
            "9823466666", "9823477777", "9823488888", "9823499999", "9823400000"
        };

        for (int i = 0; i < 10; i++) {
            String mId = String.format("LSBG-M%03d", (i + 1));
            String memberKey = "mem-g3-" + (i + 1);
            Member m = new Member();
            m.setId(memberKey);
            m.setMemberId(mId);
            m.setGroupId(group3.getId());
            m.setGroupName(group3.getGroupName());
            m.setFullName(g3Names[i]);
            m.setGuardianName(g3Names[i].split(" ")[1] + " " + g3Names[i].split(" ")[2]);
            m.setGender("FEMALE");
            m.setDob(LocalDate.of(1989 + (i % 7), (i % 12) + 1, (i % 27) + 1));
            m.setMobileNumber(g3Phones[i]);
            m.setEmail("member.g3." + (i + 1) + "@bachatgat.org");
            m.setAddress("Galli No. " + (5 + i) + ", Hadapsar, Pune");
            m.setVillage("Hadapsar");
            m.setTaluka("Haveli");
            m.setDistrict("Pune");
            m.setState("Maharashtra");
            m.setPinCode("411028");
            m.setOccupation((i % 2 == 0) ? "Vegetable & Fruit Retail" : "Home-made Food / Papad Masala");
            m.setJoiningDate(LocalDate.of(2024, 1, 20));
            m.setStatus(MemberStatus.ACTIVE);
            m.setMonthlyShareAmount(BigDecimal.valueOf(2000));
            m.setInitialShareAmount(BigDecimal.valueOf(1000));
            m.setCurrentShareBalance(m.getInitialShareAmount());
            m.setMembershipFee(BigDecimal.valueOf(200));
            m.setTotalSavingsBalance(m.getInitialShareAmount());
            m.setPendingSavings(BigDecimal.ZERO);
            m.setPanNumber("LSBGM" + (3000 + i) + "P");
            m.setAadhaarReference("XXXXXXXX" + (3000 + i));
            m.setNomineeName(m.getGuardianName());
            m.setNomineeRelation("Spouse");
            m.setBankName("Bank of Baroda");
            m.setAccountNumber("451201" + (1000 + i));
            m.setIfsc("BARB0HADAPS");
            m.setBranch("Hadapsar Branch");
            m.setCreatedBy("admin");
            members.put(m.getId(), m);

            // User account for Suman (Group 3 President)
            if (i == 0) {
                User g3User = new User("suman", passwordEncoder.encode("user123"), m.getEmail(), m.getFullName(), Role.USER, group3.getId(), m.getMemberId());
                g3User.setId("usr-suman-01");
                g3User.setMobileNumber(m.getMobileNumber());
                users.put(g3User.getId(), g3User);
            }

            // Group 3 Monthly Collections (Months 1 through 10)
            for (int month = 1; month <= 10; month++) {
                String bKey = group3.getId() + "_" + m.getMemberId() + "_" + month + "_" + currentYear;
                CollectionRecord col = new CollectionRecord(group3.getId(), m.getMemberId(), m.getFullName(), month, currentYear, BigDecimal.valueOf(2000));
                col.setId(bKey);
                col.setBusinessKey(bKey);
                if (month < 10 || m.getMemberId().endsWith("001")) {
                    col.setPaidAmount(BigDecimal.valueOf(2000));
                    col.setPendingAmount(BigDecimal.ZERO);
                    col.setStatus(CollectionStatus.PAID);
                    col.setPaymentDate(LocalDate.of(currentYear, month, 2));
                    col.setPaymentMethod("CASH");
                    col.setRecordedBy("admin");
                } else {
                    col.setPaidAmount(BigDecimal.ZERO);
                    col.setPendingAmount(BigDecimal.valueOf(2000));
                    col.setStatus(CollectionStatus.PENDING);
                }
                collections.put(col.getId(), col);
            }
        }

        // Reconcile all demo balances after every seeded collection has been
        // created. Only paid monthly bachat is included; pending amounts are
        // deliberately excluded from accumulated savings.
        reconcileSeededSavingsFromCollections();

        // Group 3 Loans:
        // Loan 7: Suman Gaikwad (LSBG-M001)
        Member suman = members.get("mem-g3-1");
        Loan loan7 = new Loan();
        loan7.setId("loan-021");
        loan7.setLoanId("LN-2026-021");
        loan7.setApplicationId("LA-2026-021");
        loan7.setGroupId(group3.getId());
        loan7.setGroupName(group3.getGroupName());
        loan7.setMemberId(suman.getMemberId());
        loan7.setMemberName(suman.getFullName());
        loan7.setPrincipalAmount(BigDecimal.valueOf(35000));
        loan7.setInterestRate(BigDecimal.valueOf(12.0));
        loan7.setDurationMonths(8);
        loan7.setInterestType("FLAT");
        loan7.setTotalInterest(BigDecimal.valueOf(2800));
        loan7.setTotalPayable(BigDecimal.valueOf(37800));
        loan7.setMonthlyInstallment(BigDecimal.valueOf(4725));
        loan7.setPrincipalPaid(BigDecimal.valueOf(13125));
        loan7.setInterestPaid(BigDecimal.valueOf(1050));
        loan7.setTotalPaid(BigDecimal.valueOf(14175));
        loan7.setOutstandingPrincipal(BigDecimal.valueOf(21875));
        loan7.setOutstandingInterest(BigDecimal.valueOf(1750));
        loan7.setTotalOutstanding(BigDecimal.valueOf(23625));
        loan7.setDisbursementDate(LocalDate.of(2025, 12, 20));
        loan7.setNextDueDate(LocalDate.of(2026, 4, 10));
        loan7.setStatus(LoanStatus.ACTIVE);
        loan7.setPurpose("Vegetable Retail Stall / भाजीपाला व फळे किरकोळ व्यवसाय");
        loan7.setCreatedBy("admin");
        loans.put(loan7.getId(), loan7);

        suman.setActiveLoanId(loan7.getLoanId());
        suman.setCurrentLoanOutstanding(loan7.getTotalOutstanding());
        suman.setTotalLoanInterestPaid(loan7.getInterestPaid());

        // Loan 8: Surekha Jagtap (LSBG-M002)
        Member surekha = members.get("mem-g3-2");
        Loan loan8 = new Loan();
        loan8.setId("loan-022");
        loan8.setLoanId("LN-2026-022");
        loan8.setApplicationId("LA-2026-022");
        loan8.setGroupId(group3.getId());
        loan8.setGroupName(group3.getGroupName());
        loan8.setMemberId(surekha.getMemberId());
        loan8.setMemberName(surekha.getFullName());
        loan8.setPrincipalAmount(BigDecimal.valueOf(25000));
        loan8.setInterestRate(BigDecimal.valueOf(12.0));
        loan8.setDurationMonths(6);
        loan8.setInterestType("FLAT");
        loan8.setTotalInterest(BigDecimal.valueOf(1500));
        loan8.setTotalPayable(BigDecimal.valueOf(26500));
        loan8.setMonthlyInstallment(BigDecimal.valueOf(4417));
        loan8.setPrincipalPaid(BigDecimal.valueOf(12500));
        loan8.setInterestPaid(BigDecimal.valueOf(750));
        loan8.setTotalPaid(BigDecimal.valueOf(13250));
        loan8.setOutstandingPrincipal(BigDecimal.valueOf(12500));
        loan8.setOutstandingInterest(BigDecimal.valueOf(750));
        loan8.setTotalOutstanding(BigDecimal.valueOf(13250));
        loan8.setDisbursementDate(LocalDate.of(2026, 1, 20));
        loan8.setNextDueDate(LocalDate.of(2026, 4, 10));
        loan8.setStatus(LoanStatus.ACTIVE);
        loan8.setPurpose("Home Spices & Papad Processing / गृह उद्योग मसाला व पापड");
        loan8.setCreatedBy("admin");
        loans.put(loan8.getId(), loan8);

        surekha.setActiveLoanId(loan8.getLoanId());
        surekha.setCurrentLoanOutstanding(loan8.getTotalOutstanding());
        surekha.setTotalLoanInterestPaid(loan8.getInterestPaid());

        // 8. Seed Audit Log
        AuditLog al1 = new AuditLog(group.getId(), "usr-admin-01", "admin", "LOAN_APPROVED", "LOAN",
                "LN-2026-001", "PENDING_APPROVAL", "ACTIVE - Rs 40,000 sanctioned", "127.0.0.1");
        auditLogs.put(al1.getId(), al1);

        // 9. Seed System Notifications
        SystemNotification notif1 = new SystemNotification(group.getId(), null, "ADMIN",
                "New Loan Application Received", "Member Anita More has submitted loan application for ₹50,000.", "LOAN_APPLICATION");
        notifications.put(notif1.getId(), notif1);

        SystemNotification notif2 = new SystemNotification(group.getId(), "usr-member-01", "USER",
                "Monthly Bachat Due Reminder", "March monthly bachat collection of ₹5,000 is due on 10th March.", "COLLECTION_DUE");
        notifications.put(notif2.getId(), notif2);

        // 10. Seed Group Expense
        GroupExpense exp1 = new GroupExpense(group.getId(), "STATIONERY", "Register, Passbooks and receipt books purchase",
                BigDecimal.valueOf(850), LocalDate.of(2026, 3, 1), "CASH", "Patil Stationery Mart", "admin");
        expenses.put(exp1.getId(), exp1);

        // 11. Seed Meeting
        MeetingRecord mtg1 = new MeetingRecord();
        mtg1.setId("mtg-001");
        mtg1.setGroupId(group.getId());
        mtg1.setMeetingDate(LocalDate.of(2026, 3, 1));
        mtg1.setMeetingTime("10:30 AM");
        mtg1.setLocation("Gram Panchayat Hall, Shirur");
        mtg1.setAgenda("Monthly share collection, review of education loan application, and annual audit preparation");
        mtg1.setMinutes("All 18 present members deposited their monthly bachat. Loan application of Anita More discussed and scheduled for sanction.");
        mtg1.setConductedBy("Sunita Patil (President)");
        for (int i = 1; i <= 18; i++) {
            mtg1.getAttendeeMemberIds().add(String.format("MPBG-M%03d", i));
        }
        meetings.put(mtg1.getId(), mtg1);

        // 12. Seed Group Master Data (Version 1)
        GroupMasterData md1 = new GroupMasterData(group.getId(), 1,
                BigDecimal.valueOf(5000), BigDecimal.valueOf(4100), BigDecimal.valueOf(12.0),
                BigDecimal.valueOf(100000), BigDecimal.valueOf(100), 10, 5, "SYSTEM_INIT");
        md1.setFinancialYear("2025-2026");
        md1.setDefaultLanguage("mr");
        masterDataMap.put(md1.getId(), md1);

        // 13. Seed Other Group Income
        GroupIncome inc1 = new GroupIncome(group.getId(), "BANK_INTEREST", "Bank savings account quarterly interest credited",
                BigDecimal.valueOf(1850), LocalDate.of(2026, 2, 28), "BANK_TRANSFER", "BANK-INT-Q4-2026", "GROUP_LEVEL_ONLY", "admin");
        incomes.put(inc1.getId(), inc1);
    }

    /**
     * Keeps demo balances consistent with the seeded collection ledger.
     * Monthly bachat is a recurring amount; accumulated savings is the initial
     * contribution plus paid collection amounts, not a hard-coded multiple of
     * the monthly amount.
     */
    private void reconcileSeededSavingsFromCollections() {
        for (Member member : members.values()) {
            BigDecimal initial = member.getInitialShareAmount() != null
                    ? member.getInitialShareAmount() : BigDecimal.ZERO;
            BigDecimal paidBachat = collections.values().stream()
                    .filter(collection -> member.getGroupId().equals(collection.getGroupId())
                            && member.getMemberId().equals(collection.getMemberId()))
                    .map(CollectionRecord::getPaidAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal accumulatedSavings = initial.add(paidBachat);
            member.setCurrentShareBalance(accumulatedSavings);
            member.setTotalSavingsBalance(accumulatedSavings);

            BigDecimal pending = collections.values().stream()
                    .filter(collection -> member.getGroupId().equals(collection.getGroupId())
                            && member.getMemberId().equals(collection.getMemberId()))
                    .map(CollectionRecord::getPendingAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            member.setPendingSavings(pending);
        }

        for (Group group : groups.values()) {
            BigDecimal groupSavings = members.values().stream()
                    .filter(member -> group.getId().equals(member.getGroupId()))
                    .map(Member::getTotalSavingsBalance)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            group.setTotalSavingsBalance(groupSavings);
        }
    }
}

