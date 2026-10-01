package com.nevis.search.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String description,
        List<String> socialLinks) {

    public static ClientResponse from(Client client) {
        PersonName name = client.getName();
        return new ClientResponse(client.getId().value(), name.first(), name.last(), client.getEmail().value(),
                client.getDescription().orElse(null), List.copyOf(client.getSocialLinks()));
    }
}
