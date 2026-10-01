package com.nevis.search.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nevis.search.common.InvalidValueException;
import com.nevis.search.common.NotFoundException;
import com.nevis.search.document.DocumentId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ValueTypesTest {

    @Test
    void emailIsAlwaysTrimmedAndLowerCased() {
        assertThat(new Email("  John.Doe@NevisWealth.com ").value()).isEqualTo("john.doe@neviswealth.com");
        assertThat(new Email("JOHN@example.com")).isEqualTo(new Email("john@example.com"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "john", "john@", "@example.com", "a@b@c", "jo hn@example.com"})
    void rejectsMalformedEmail(String value) {
        assertThatThrownBy(() -> new Email(value))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getField()).isEqualTo("email");
    }

    @Test
    void personNameIsTrimmedAndRequiresBothParts() {
        assertThat(new PersonName(" John ", " Doe ")).isEqualTo(new PersonName("John", "Doe"));
        assertThatThrownBy(() -> new PersonName("John", " "))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getField()).isEqualTo("last_name");
        assertThatThrownBy(() -> new PersonName(null, "Doe"))
                .extracting(e -> ((InvalidValueException) e).getField()).isEqualTo("first_name");
    }

    @Test
    void malformedPathIdIsReportedAsNotFound() {
        assertThat(ClientId.parse("2f1b6d3e-0000-4000-8000-000000000000").value())
                .isEqualTo(UUID.fromString("2f1b6d3e-0000-4000-8000-000000000000"));
        assertThatThrownBy(() -> ClientId.parse("not-a-uuid")).isInstanceOf(NotFoundException.class)
                .hasMessage("Client not-a-uuid not found");
        assertThatThrownBy(() -> DocumentId.parse("x")).isInstanceOf(NotFoundException.class)
                .hasMessage("Document x not found");
    }
}
