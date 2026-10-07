package com.bachatgat.service;

import com.bachatgat.dto.*;
import com.bachatgat.exception.DuplicateRecordException;
import com.bachatgat.exception.ForbiddenException;
import com.bachatgat.exception.InvalidFinancialOperationException;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import com.bachatgat.security.GroupSecurityService;
import com.bachatgat.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String HMAC_SECRET = "BachatGatSecurePaymentKey2026_HMAC_Secret";

    private final FirestoreDataService dataService;
    private final CollectionService collectionService;
    private final MemberService memberService;
    private final GroupService groupService;
    private final LoanService loanService;
    private final MasterDataService masterDataService;
    private final GroupSecurityService groupSecurityService;
    private final AuditService auditService;

    public PaymentService(FirestoreDataService dataService,
                          CollectionService collectionService,
                          MemberService memberService,
                          GroupService groupService,
                          LoanService loanService,
                          MasterDataService masterDataService,
                          GroupSecurityService groupSecurityService,
                          AuditService auditService) {
        this.dataService = dataService;
        this.collectionService = collectionService;
        this.memberService = memberService;
        this.groupService = groupService;
        this.loanService = loanService;
        this.masterDataService = masterDataService;
        this.groupSecurityService = groupSecurityService;
        this.auditService = auditService;
    }

    /**
     * Creates a server-side verified payment order.
     * Enforces group isolation and derives breakdown components if not provided.
     */
    public PaymentOrder createPaymentOrder(PaymentOrderRequest request, UserPrincipal principal) {
        String effectiveGroupId;
        String effectiveMemberId;

        if (principal.getRole() == Role.USER || principal.getRole() == Role.MEMBER) {
            effectiveGroupId = principal.getGroupId();
            effectiveMemberId = principal.getMemberId();
        } else {
            // ADMIN / SUPER_ADMIN creating order for a member
            effectiveGroupId = groupSecurityService.resolveTargetGroupId(principal, request.getGroupId());
            groupSecurityService.validateGroupAccess(principal, effectiveGroupId);
            effectiveMemberId = request.getMemberId();
            if (effectiveMemberId == null || effectiveMemberId.isBlank()) {
                throw new InvalidFinancialOperationException("Member ID is required to initiate payment order.");
            }
        }

        Member member = memberService.getMemberByMemberId(effectiveMemberId);
        if (!member.getGroupId().equalsIgnoreCase(effectiveGroupId)) {
            throw new ForbiddenException("Member does not belong to the authorized Bachat Gat group.");
        }

        int month = request.getMonth() > 0 ? request.getMonth() : LocalDate.now().getMonthValue();
        int year = request.getYear() > 0 ? request.getYear() : LocalDate.now().getYear();

        // Derive component breakdown
        BigDecimal shareAmount = request.getShareAmount();
        BigDecimal loanPrincipal = request.getLoanPrincipalAmount() != null ? request.getLoanPrincipalAmount() : BigDecimal.ZERO;
        BigDecimal loanInterest = request.getLoanInterestAmount() != null ? request.getLoanInterestAmount() : BigDecimal.ZERO;
        BigDecimal lateFee = request.getLateFeeAmount() != null ? request.getLateFeeAmount() : BigDecimal.ZERO;
        BigDecimal otherAmount = request.getOtherAmount() != null ? request.getOtherAmount() : BigDecimal.ZERO;

        String loanId = request.getLoanId();
        if (loanId == null || loanId.isBlank()) {
            List<Loan> activeLoans = loanService.getLoansByMemberId(effectiveMemberId).stream()
                    .filter(l -> l.getStatus() == LoanStatus.ACTIVE)
                    .collect(Collectors.toList());
            if (!activeLoans.isEmpty()) {
                Loan l = activeLoans.get(0);
                loanId = l.getId();
            }
        }

        // Automatic financial breakdown if only lump-sum amount was sent
        BigDecimal totalRequested = request.getAmount();

        // Monthly Bachat is controlled by the group's Master Data rule.
        // Never trust a stale amount sent by an older member-page/browser.
        if ("MONTHLY_SAVINGS".equalsIgnoreCase(request.getPaymentType())) {
            String businessKey = effectiveGroupId + "_" + member.getMemberId() + "_" + month + "_" + year;
            CollectionRecord collection = dataService.findCollectionByBusinessKey(businessKey).orElse(null);
            GroupMasterData rules = masterDataService.getMasterDataForDate(
                    effectiveGroupId, LocalDate.of(year, month, 1));
            Group group = groupService.getGroupById(effectiveGroupId);
            BigDecimal configured = rules != null && rules.getMonthlyBachatAmount() != null
                    ? rules.getMonthlyBachatAmount() : group.getMonthlyBachatAmount();
            BigDecimal expected = collection != null && collection.getExpectedAmount() != null
                    ? collection.getExpectedAmount() : configured;
            BigDecimal alreadyPaid = collection != null && collection.getPaidAmount() != null
                    ? collection.getPaidAmount() : BigDecimal.ZERO;
            LocalDate period = LocalDate.of(year, month, 1);
            if (!period.isBefore(LocalDate.now().withDayOfMonth(1)) && alreadyPaid.signum() == 0
                    && configured != null && configured.signum() > 0) {
                expected = configured;
            }
            BigDecimal remaining = expected.subtract(alreadyPaid).max(BigDecimal.ZERO);
            shareAmount = remaining;
            totalRequested = remaining.add(lateFee);
        }
        if (shareAmount == null) {
            GroupMasterData masterData = masterDataService.getMasterDataForDate(
                    effectiveGroupId, LocalDate.of(year, month, 1));
            Group group = groupService.getGroupById(effectiveGroupId);
            BigDecimal expectedShare = masterData != null && masterData.getMonthlyBachatAmount() != null
                    ? masterData.getMonthlyBachatAmount()
                    : (group.getMonthlyBachatAmount() != null
                        ? group.getMonthlyBachatAmount()
                        : (member.getMonthlyBachatAmount() != null ? member.getMonthlyBachatAmount() : BigDecimal.ZERO));

            if ("LOAN_EMI".equalsIgnoreCase(request.getPaymentType()) && loanId != null) {
                shareAmount = BigDecimal.ZERO;
                Loan loan = loanService.getLoanById(loanId);
                BigDecimal monthlyEmi = loan.getMonthlyInstallment();
                BigDecimal rate = loan.getInterestRate().divide(BigDecimal.valueOf(1200), 6, RoundingMode.HALF_UP);
                loanInterest = loan.getOutstandingPrincipal().multiply(rate).setScale(2, RoundingMode.HALF_UP);
                loanPrincipal = totalRequested.subtract(loanInterest).max(BigDecimal.ZERO);
            } else if ("COMBINED".equalsIgnoreCase(request.getPaymentType()) && loanId != null) {
                shareAmount = expectedShare;
                BigDecimal remaining = totalRequested.subtract(shareAmount).max(BigDecimal.ZERO);
                Loan loan = loanService.getLoanById(loanId);
                BigDecimal rate = loan.getInterestRate().divide(BigDecimal.valueOf(1200), 6, RoundingMode.HALF_UP);
                loanInterest = loan.getOutstandingPrincipal().multiply(rate).setScale(2, RoundingMode.HALF_UP);
                loanPrincipal = remaining.subtract(loanInterest).max(BigDecimal.ZERO);
            } else {
                shareAmount = totalRequested;
            }
        }

        // Validate that breakdown matches total amount requested
        BigDecimal computedTotal = shareAmount.add(loanPrincipal).add(loanInterest).add(lateFee).add(otherAmount);
        if (computedTotal.compareTo(totalRequested) != 0) {
            // Adjust monthly bachat to balance
            shareAmount = totalRequested.subtract(loanPrincipal).subtract(loanInterest).subtract(lateFee).subtract(otherAmount).max(BigDecimal.ZERO);
        }

        String orderId = "ORD-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String signatureToken = generateSignatureToken(orderId, totalRequested, effectiveMemberId);

        PaymentOrder order = new PaymentOrder();
        order.setOrderId(orderId);
        order.setGroupId(effectiveGroupId);
        order.setMemberId(effectiveMemberId);
        order.setMemberName(member.getFullName());
        order.setAmount(totalRequested);
        order.setCurrency("INR");
        order.setPaymentType(request.getPaymentType() != null ? request.getPaymentType() : "MONTHLY_SAVINGS");
        order.setMonth(month);
        order.setYear(year);

        order.setShareAmount(shareAmount);
        order.setLoanId(loanId);
        order.setLoanPrincipalAmount(loanPrincipal);
        order.setLoanInterestAmount(loanInterest);
        order.setLateFeeAmount(lateFee);
        order.setOtherAmount(otherAmount);

        order.setServerVerificationToken(signatureToken);
        order.setIdempotencyKey(request.getIdempotencyKey() != null ? request.getIdempotencyKey() : orderId);
        order.setStatus("CREATED");
        order.setNotes("Payment order created for " + member.getFullName());

        PaymentOrder saved = dataService.savePaymentOrder(order);
        log.info("Created payment order {} for member {} amount ₹{}", saved.getOrderId(), effectiveMemberId, totalRequested);
        return saved;
    }

    /**
     * Server-side payment verification. Never trusts frontend paymentSuccess flag.
     * Cryptographically verifies token and executes idempotency check.
     */
    public PaymentReceiptDTO verifyPayment(PaymentVerificationRequest request, UserPrincipal principal) {
        String orderId = request.getOrderId();
        PaymentOrder order = dataService.findPaymentOrderById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found: " + orderId));

        // Group security check
        if (principal.getRole() == Role.USER || principal.getRole() == Role.MEMBER) {
            if (!order.getMemberId().equalsIgnoreCase(principal.getMemberId())) {
                throw new ForbiddenException("Cannot verify payment order belonging to another member.");
            }
        } else {
            groupSecurityService.validateGroupAccess(principal, order.getGroupId());
        }

        // Idempotency: If already verified and marked SUCCESS, return existing receipt immediately
        if ("SUCCESS".equalsIgnoreCase(order.getStatus())) {
            log.info("Payment order {} already successfully verified. Returning existing receipt.", orderId);
            return buildReceiptDTO(order, "Payment was previously verified and recorded successfully.");
        }

        // Verify cryptographic signature token
        boolean tokenValid = verifySignatureToken(order.getOrderId(), order.getAmount(), order.getMemberId(), request.getGatewaySignatureToken());
        if (!tokenValid && !request.getGatewaySignatureToken().equalsIgnoreCase(order.getServerVerificationToken())) {
            log.warn("Security alert: Invalid gateway signature token for order {}", orderId);
            order.setStatus("FAILED");
            order.setNotes("Cryptographic signature verification failed");
            dataService.savePaymentOrder(order);
            throw new InvalidFinancialOperationException("Payment verification failed: Invalid cryptographic server token.");
        }

        String paymentMethod = request.getPaymentMethod() != null ? request.getPaymentMethod() : "UPI";
        String gatewayPaymentId = request.getGatewayPaymentId() != null ? request.getGatewayPaymentId() : "GATEWAY-TXN-" + System.currentTimeMillis();
        String refNumber = request.getReferenceNumber() != null && !request.getReferenceNumber().isBlank()
                ? request.getReferenceNumber()
                : gatewayPaymentId;

        // Generate formal receipt number
        String receiptNumber = "REC-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        order.setPaymentMethod(paymentMethod);
        order.setGatewayPaymentId(gatewayPaymentId);
        order.setReferenceNumber(refNumber);
        order.setReceiptNumber(receiptNumber);
        order.setStatus("SUCCESS");
        order.setCompletedAt(LocalDateTime.now());
        order.setNotes(request.getNotes() != null ? request.getNotes() : "Online payment successfully verified by gateway");

        // Record the underlying 3-category accounting transaction in Collections ledger
        CollectionPaymentRequest colReq = new CollectionPaymentRequest();
        colReq.setMemberId(order.getMemberId());
        colReq.setMonth(order.getMonth());
        colReq.setYear(order.getYear());
        colReq.setAmount(order.getAmount());
        colReq.setShareAmount(order.getShareAmount());
        colReq.setLoanId(order.getLoanId());
        colReq.setLoanPrincipalAmount(order.getLoanPrincipalAmount());
        colReq.setLoanInterestAmount(order.getLoanInterestAmount());
        colReq.setLateFeeAmount(order.getLateFeeAmount());
        colReq.setOtherAmount(order.getOtherAmount());
        colReq.setPaymentMethod(paymentMethod);
        colReq.setReferenceNumber(refNumber);
        colReq.setIdempotencyKey(order.getOrderId());
        colReq.setPaymentStatus("SUCCESS");
        colReq.setPaymentDate(LocalDate.now());
        colReq.setNotes("Online Gateway Payment - Order ID: " + order.getOrderId() + " | Receipt: " + receiptNumber);

        collectionService.recordPayment(order.getGroupId(), colReq, principal.getUsername());
        dataService.savePaymentOrder(order);

        auditService.log(order.getGroupId(), principal.getUsername(), principal.getUsername(),
                "ONLINE_PAYMENT_VERIFIED", "PAYMENT", order.getOrderId(), null,
                "Online payment of ₹" + order.getAmount() + " successfully verified with receipt " + receiptNumber, "127.0.0.1");

        return buildReceiptDTO(order, "Payment verified successfully and ledger updated.");
    }

    /**
     * Records a manual payment (Cash / Bank deposit) entered by authorized Group President / Admin.
     */
    public PaymentReceiptDTO recordManualPayment(ManualPaymentRequest request, UserPrincipal adminPrincipal) {
        String groupId = groupSecurityService.resolveTargetGroupId(adminPrincipal, request.getGroupId());
        groupSecurityService.validateGroupAccess(adminPrincipal, groupId);

        Member member = memberService.getMemberByMemberId(request.getMemberId());
        if (!member.getGroupId().equalsIgnoreCase(groupId)) {
            throw new ForbiddenException("Member does not belong to your Bachat Gat group.");
        }

        BigDecimal totalAmount = request.getAmount();
        BigDecimal sharePart = request.getShareAmount();
        BigDecimal loanPrinc = request.getLoanPrincipalAmount() != null ? request.getLoanPrincipalAmount() : BigDecimal.ZERO;
        BigDecimal loanInt = request.getLoanInterestAmount() != null ? request.getLoanInterestAmount() : BigDecimal.ZERO;
        BigDecimal lateFee = request.getLateFeeAmount() != null ? request.getLateFeeAmount() : BigDecimal.ZERO;
        BigDecimal otherPart = request.getOtherAmount() != null ? request.getOtherAmount() : BigDecimal.ZERO;

        if (sharePart == null) {
            if ("LOAN".equalsIgnoreCase(request.getPaymentType())) {
                sharePart = BigDecimal.ZERO;
                if (loanPrinc.add(loanInt).compareTo(BigDecimal.ZERO) == 0) {
                    loanPrinc = totalAmount;
                }
            } else {
                sharePart = totalAmount.subtract(loanPrinc).subtract(loanInt).subtract(lateFee).subtract(otherPart).max(BigDecimal.ZERO);
            }
        }

        BigDecimal allocatedSum = sharePart.add(loanPrinc).add(loanInt).add(lateFee).add(otherPart);
        if (allocatedSum.compareTo(totalAmount) != 0) {
            throw new InvalidFinancialOperationException("Total allocated amount (₹" + allocatedSum + ") must equal amount received (₹" + totalAmount + ").");
        }

        String orderId = "MAN-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String receiptNumber = "REC-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String ref = request.getReferenceNumber() != null && !request.getReferenceNumber().isBlank()
                ? request.getReferenceNumber()
                : "MANUAL-" + System.currentTimeMillis();

        PaymentOrder order = new PaymentOrder();
        order.setOrderId(orderId);
        order.setGroupId(groupId);
        order.setMemberId(member.getMemberId());
        order.setMemberName(member.getFullName());
        order.setAmount(totalAmount);
        order.setCurrency("INR");
        order.setPaymentType(request.getPaymentType());
        order.setMonth(request.getMonth());
        order.setYear(request.getYear());
        order.setShareAmount(sharePart);
        order.setLoanId(request.getLoanId());
        order.setLoanPrincipalAmount(loanPrinc);
        order.setLoanInterestAmount(loanInt);
        order.setLateFeeAmount(lateFee);
        order.setOtherAmount(otherPart);
        order.setPaymentMethod(request.getPaymentMethod());
        order.setReferenceNumber(ref);
        order.setIdempotencyKey(orderId);
        order.setStatus("SUCCESS");
        order.setReceiptNumber(receiptNumber);
        order.setCompletedAt(LocalDateTime.now());
        order.setNotes(request.getNotes() != null ? request.getNotes() : "Manual payment recorded by admin " + adminPrincipal.getUsername());

        // Delegate to collection service
        CollectionPaymentRequest colReq = new CollectionPaymentRequest();
        colReq.setMemberId(member.getMemberId());
        colReq.setMonth(request.getMonth());
        colReq.setYear(request.getYear());
        colReq.setAmount(totalAmount);
        colReq.setShareAmount(sharePart);
        colReq.setLoanId(request.getLoanId());
        colReq.setLoanPrincipalAmount(loanPrinc);
        colReq.setLoanInterestAmount(loanInt);
        colReq.setLateFeeAmount(lateFee);
        colReq.setOtherAmount(otherPart);
        colReq.setPaymentMethod(request.getPaymentMethod());
        colReq.setReferenceNumber(ref);
        colReq.setIdempotencyKey(orderId);
        colReq.setPaymentStatus("SUCCESS");
        colReq.setPaymentDate(request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now());
        colReq.setNotes(order.getNotes());

        collectionService.recordPayment(groupId, colReq, adminPrincipal.getUsername());
        dataService.savePaymentOrder(order);

        auditService.log(groupId, adminPrincipal.getUsername(), adminPrincipal.getUsername(),
                "MANUAL_PAYMENT_RECORDED", "PAYMENT", orderId, null,
                "Manual " + request.getPaymentMethod() + " payment of ₹" + totalAmount + " recorded for " + member.getFullName(), "127.0.0.1");

        return buildReceiptDTO(order, "Manual payment recorded successfully.");
    }

    /**
     * Webhook receiver for payment gateways (e.g. Razorpay/Cashfree).
     */
    public PaymentReceiptDTO processWebhook(Map<String, Object> payload, String signatureHeader) {
        log.info("Processing gateway webhook: {}", payload);
        String rawOrderId = (String) payload.get("orderId");
        if (rawOrderId == null && payload.containsKey("order_id")) {
            rawOrderId = (String) payload.get("order_id");
        }
        if (rawOrderId == null) {
            throw new InvalidFinancialOperationException("Webhook payload missing orderId");
        }
        final String orderId = rawOrderId;

        PaymentOrder order = dataService.findPaymentOrderById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found: " + orderId));

        if ("SUCCESS".equalsIgnoreCase(order.getStatus())) {
            log.info("Webhook duplicate received for order {}. Ignoring duplicate.", orderId);
            return buildReceiptDTO(order, "Order already successfully processed.");
        }

        String event = (String) payload.getOrDefault("event", "payment.captured");
        if ("payment.failed".equalsIgnoreCase(event)) {
            order.setStatus("FAILED");
            order.setNotes("Payment failed via gateway webhook");
            dataService.savePaymentOrder(order);
            return buildReceiptDTO(order, "Payment marked as failed via gateway webhook.");
        }

        PaymentVerificationRequest req = new PaymentVerificationRequest();
        req.setOrderId(orderId);
        req.setGatewayPaymentId((String) payload.getOrDefault("payment_id", "WH-PAY-" + System.currentTimeMillis()));
        req.setGatewaySignatureToken(order.getServerVerificationToken());
        req.setPaymentMethod((String) payload.getOrDefault("method", "UPI"));
        req.setReferenceNumber((String) payload.getOrDefault("reference_id", orderId));

        UserPrincipal systemPrincipal = new UserPrincipal("system", "SYSTEM_WEBHOOK", "", Role.ADMIN, order.getGroupId(), null);
        return verifyPayment(req, systemPrincipal);
    }

    public PaymentReceiptDTO getPaymentReceipt(String orderId, UserPrincipal principal) {
        PaymentOrder order = dataService.findPaymentOrderById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found: " + orderId));

        if (principal.getRole() == Role.USER || principal.getRole() == Role.MEMBER) {
            if (!order.getMemberId().equalsIgnoreCase(principal.getMemberId())) {
                throw new ForbiddenException("Cannot view receipt for another member.");
            }
        } else {
            groupSecurityService.validateGroupAccess(principal, order.getGroupId());
        }

        return buildReceiptDTO(order, "Receipt retrieved successfully.");
    }

    public List<PaymentOrder> getPaymentHistory(String groupId, String memberId, UserPrincipal principal) {
        if (principal.getRole() == Role.USER || principal.getRole() == Role.MEMBER) {
            return dataService.getPaymentOrdersByMemberId(principal.getMemberId());
        }

        String effectiveGroupId = groupSecurityService.resolveTargetGroupId(principal, groupId);
        groupSecurityService.validateGroupAccess(principal, effectiveGroupId);

        List<PaymentOrder> orders = dataService.getPaymentOrdersByGroupId(effectiveGroupId);
        if (memberId != null && !memberId.isBlank()) {
            orders = orders.stream()
                    .filter(o -> o.getMemberId().equalsIgnoreCase(memberId))
                    .collect(Collectors.toList());
        }
        return orders;
    }

    private PaymentReceiptDTO buildReceiptDTO(PaymentOrder order, String message) {
        PaymentReceiptDTO dto = new PaymentReceiptDTO();
        dto.setReceiptNumber(order.getReceiptNumber() != null ? order.getReceiptNumber() : "PENDING");
        dto.setOrderId(order.getOrderId());
        dto.setTransactionId(order.getReferenceNumber() != null ? order.getReferenceNumber() : order.getOrderId());
        dto.setGroupId(order.getGroupId());

        Group g = groupService.getGroupById(order.getGroupId());
        dto.setGroupName(g != null ? g.getName() : "Bachat Gat");
        dto.setGroupRegistrationNumber(g != null ? g.getRegistrationNumber() : "");

        dto.setMemberId(order.getMemberId());
        dto.setMemberName(order.getMemberName());
        dto.setAmount(order.getAmount());
        dto.setShareAmount(order.getShareAmount());
        dto.setLoanPrincipalAmount(order.getLoanPrincipalAmount());
        dto.setLoanInterestAmount(order.getLoanInterestAmount());
        dto.setLateFeeAmount(order.getLateFeeAmount());
        dto.setOtherAmount(order.getOtherAmount());
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setReferenceNumber(order.getReferenceNumber());
        dto.setPaymentDate(order.getCompletedAt() != null ? order.getCompletedAt() : order.getCreatedAt());
        dto.setStatus(order.getStatus());
        dto.setVerified("SUCCESS".equalsIgnoreCase(order.getStatus()));
        dto.setMessage(message);
        return dto;
    }

    private String generateSignatureToken(String orderId, BigDecimal amount, String memberId) {
        try {
            String data = orderId + "|" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + "|" + memberId;
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            return UUID.randomUUID().toString();
        }
    }

    private boolean verifySignatureToken(String orderId, BigDecimal amount, String memberId, String token) {
        if (token == null || token.isBlank()) return false;
        String expected = generateSignatureToken(orderId, amount, memberId);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }
}
