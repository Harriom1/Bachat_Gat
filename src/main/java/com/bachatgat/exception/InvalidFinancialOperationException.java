package com.bachatgat.exception;

public class InvalidFinancialOperationException extends RuntimeException {
    public InvalidFinancialOperationException(String message) {
        super(message);
    }
}
