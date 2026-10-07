package com.bachatgat.exception;

import java.math.BigDecimal;

public class InsufficientGroupFundsException extends RuntimeException {
    private final BigDecimal availableFund;
    private final BigDecimal requestedAmount;

    public InsufficientGroupFundsException(BigDecimal availableFund, BigDecimal requestedAmount) {
        super("Funds are not available currently. The requested loan amount cannot be provided from the available BachatGat funds.");
        this.availableFund = availableFund;
        this.requestedAmount = requestedAmount;
    }

    public BigDecimal getAvailableFund() { return availableFund; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
}
