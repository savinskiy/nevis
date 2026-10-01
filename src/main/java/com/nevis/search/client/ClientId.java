package com.nevis.search.client;

import com.nevis.search.common.Ids;
import java.util.Objects;
import java.util.UUID;

public record ClientId(UUID value) {

    public ClientId {
        Objects.requireNonNull(value, "value");
    }

    public static ClientId random() {
        return new ClientId(UUID.randomUUID());
    }

    public static ClientId parse(String id) {
        return new ClientId(Ids.parse(id, "Client"));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
