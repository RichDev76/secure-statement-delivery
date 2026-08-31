package com.example.statementservice.statement;

public class DuplicateStatementException extends RuntimeException {

    private static final String MESSAGE = "A statement already exists for this account number and statement date";

    public DuplicateStatementException() {
        super(MESSAGE);
    }

    public DuplicateStatementException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
