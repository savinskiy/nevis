package com.nevis.search.search;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    @ApiResponse(responseCode = "200", description = "Matching clients and documents, best first; empty when nothing matches")
    @ApiResponse(responseCode = "400", description = "Missing or blank query")
    @Operation(summary = "Search clients and documents",
            description = """
                    Returns clients and documents in one list ordered by relevance.

                    * **Clients** match on name, email and description, case-insensitive and including parts \
                    of words (`NevisWealth` or `nevis` finds `john.doe@neviswealth.com`).
                    * **Documents** match on title and content by words, including related terms \
                    (`address proof` finds a document containing `utility bill`), and by meaning through \
                    embeddings (`retiring early` finds notes about stopping work at 60).""")
    public List<SearchResult> search(
            @Parameter(description = "Search text", example = "address proof")
            @RequestParam @NotBlank @Size(max = 500) String q) {
        return searchService.search(q);
    }
}
