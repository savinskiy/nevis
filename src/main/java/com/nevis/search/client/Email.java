package com.nevis.search.client;

import com.nevis.search.common.InvalidValueException;
import java.util.Locale;
import java.util.regex.Pattern;

public record Email(String value) {

    private static final Pattern SHAPE = Pattern.compile("[^@\\s]+@[^@\\s]+");

    public Email {
        if (value == null || !SHAPE.matcher(value.strip()).matches()) {
            throw new InvalidValueException("email", "must be a well-formed email address");
        }
        value = value.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return value;
    }
}
