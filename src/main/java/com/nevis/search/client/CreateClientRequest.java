package com.nevis.search.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateClientRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 10_000) String description,
        @Size(max = 20) List<@NotBlank @Size(max = 2048) String> socialLinks) {
}
