package com.nevis.search.document;

import com.nevis.search.common.Ids;
import java.util.Objects;
import java.util.UUID;

public record DocumentId(UUID value) {

    public DocumentId {
        Objects.requireNonNull(value, "value");
    }

    public static DocumentId random() {
        return new DocumentId(UUID.randomUUID());
    }

    public static DocumentId parse(String id) {
        return new DocumentId(Ids.parse(id, "Document"));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
