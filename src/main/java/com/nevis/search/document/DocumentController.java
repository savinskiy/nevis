package com.nevis.search.document;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/documents")
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "The document")
    @ApiResponse(responseCode = "404", description = "Document not found")
    @Operation(summary = "Get a document")
    public DocumentResponse get(@PathVariable String id) {
        return documentService.get(DocumentId.parse(id));
    }
}
