package com.bachatgat.dto;

import jakarta.validation.constraints.NotBlank;

public class PaymentVerificationRequest {
    @NotBlank(message = "Order ID is required")
    private String orderId;

    private String gatewayPaymentId;
    private String gatewaySignatureToken;
    private String paymentMethod = "UPI";
    private String referenceNumber;
    private String notes;

    public PaymentVerificationRequest() {}

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getGatewayPaymentId() { return gatewayPaymentId; }
    public void setGatewayPaymentId(String gatewayPaymentId) { this.gatewayPaymentId = gatewayPaymentId; }

    public String getGatewaySignatureToken() { return gatewaySignatureToken; }
    public void setGatewaySignatureToken(String gatewaySignatureToken) { this.gatewaySignatureToken = gatewaySignatureToken; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
