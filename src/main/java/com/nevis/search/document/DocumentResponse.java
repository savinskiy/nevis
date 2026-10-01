package com.nevis.search.document;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentResponse(
        UUID id,
        UUID clientId,
        String title,
        String content,
        @Schema(description = "Summary written by Claude shortly after upload; absent until ready or without an API key") String summary,
        Instant createdAt) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(document.getId().value(), document.getClientId().value(), document.getTitle(),
                document.getContent(), document.getSummary().orElse(null), document.getCreatedAt());
    }
}
