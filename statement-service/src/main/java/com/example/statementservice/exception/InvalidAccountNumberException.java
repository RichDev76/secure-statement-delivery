package com.example.statementservice.exception;

/**
 * Thrown when the provided account number does not meet validation rules.
 */
public class InvalidAccountNumberException extends RuntimeException {
    public InvalidAccountNumberException(String message) {
        super(message);
    }
}
