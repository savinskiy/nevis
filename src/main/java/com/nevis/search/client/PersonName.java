package com.nevis.search.client;

import com.nevis.search.common.InvalidValueException;

public record PersonName(String first, String last) {

    public PersonName {
        first = requireText(first, "first_name");
        last = requireText(last, "last_name");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException(field, "must not be blank");
        }
        return value.strip();
    }
}
