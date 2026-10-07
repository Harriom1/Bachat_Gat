package com.bachatgat.controller;

import com.bachatgat.dto.*;
import com.bachatgat.model.PaymentOrder;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Step 1: Create Payment Order
     * Initiated by Member or Admin before interacting with Payment Gateway.
     */
    @PostMapping("/order")
    public ResponseEntity<ApiResponse<PaymentOrder>> createPaymentOrder(
            @Valid @RequestBody PaymentOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        PaymentOrder order = paymentService.createPaymentOrder(request, principal);
        return ResponseEntity.ok(ApiResponse.ok("Payment order initiated successfully", order));
    }

    /**
     * Step 2: Verify Payment Callback
     * Cryptographically verifies payment token, prevents replay, updates ledger, returns receipt.
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PaymentReceiptDTO>> verifyPayment(
            @Valid @RequestBody PaymentVerificationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        PaymentReceiptDTO receipt = paymentService.verifyPayment(request, principal);
        return ResponseEntity.ok(ApiResponse.ok("Payment verified successfully and receipt generated", receipt));
    }

    /**
     * Step 3: Record Manual Cash / Bank Payment
     * Authorized for President / Admin.
     */
    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PRESIDENT', 'SECRETARY', 'TREASURER')")
    public ResponseEntity<ApiResponse<PaymentReceiptDTO>> recordManualPayment(
            @Valid @RequestBody ManualPaymentRequest request,
            @AuthenticationPrincipal UserPrincipal adminPrincipal) {
        PaymentReceiptDTO receipt = paymentService.recordManualPayment(request, adminPrincipal);
        return ResponseEntity.ok(ApiResponse.ok("Manual payment recorded successfully and receipt generated", receipt));
    }

    /**
     * Step 4: Webhook Receiver
     * For automated asynchronous payment gateway notifications (Razorpay, Cashfree, UPI).
     */
    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<PaymentReceiptDTO>> handleWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        PaymentReceiptDTO receipt = paymentService.processWebhook(payload, signature);
        return ResponseEntity.ok(ApiResponse.ok("Webhook processed successfully", receipt));
    }

    /**
     * Step 5: Get Digital Payment Receipt
     */
    @GetMapping("/receipt/{orderId}")
    public ResponseEntity<ApiResponse<PaymentReceiptDTO>> getReceipt(
            @PathVariable String orderId,
            @AuthenticationPrincipal UserPrincipal principal) {
        PaymentReceiptDTO receipt = paymentService.getPaymentReceipt(orderId, principal);
        return ResponseEntity.ok(ApiResponse.ok(receipt));
    }

    /**
     * Step 6: Payment History
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<PaymentOrder>>> getPaymentHistory(
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) String memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<PaymentOrder> history = paymentService.getPaymentHistory(groupId, memberId, principal);
        return ResponseEntity.ok(ApiResponse.ok(history));
    }
}
