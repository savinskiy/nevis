package com.nevis.search.common;

public class InvalidValueException extends RuntimeException {

    private final String field;

    public InvalidValueException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
