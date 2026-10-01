package com.nevis.search.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nevis.search.client.ClientResponse;
import com.nevis.search.document.DocumentResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResult(
        @Schema(allowableValues = {"client", "document"}) String type,
        @Schema(description = "Elasticsearch relevance score; results are sorted by it, best first") double score,
        @Schema(description = "Matching fragments per field, matches wrapped in <em>") Map<String, List<String>> highlights,
        @Schema(description = "Present when type=client") ClientResponse client,
        @Schema(description = "Present when type=document") DocumentResponse document) {
}
