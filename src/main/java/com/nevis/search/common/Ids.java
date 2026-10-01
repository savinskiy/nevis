package com.nevis.search.common;

import java.util.UUID;

public final class Ids {

    private Ids() {
    }

    public static UUID parse(String id, String resource) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(resource + " " + id + " not found");
        }
    }
}
