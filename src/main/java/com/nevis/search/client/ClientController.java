package com.nevis.search.client;

import com.nevis.search.document.CreateDocumentRequest;
import com.nevis.search.document.DocumentResponse;
import com.nevis.search.document.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/clients")
@Tag(name = "Clients")
public class ClientController {

    private final ClientService clientService;
    private final DocumentService documentService;

    public ClientController(ClientService clientService, DocumentService documentService) {
        this.clientService = clientService;
        this.documentService = documentService;
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Client created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "409", description = "Email already in use")
    @Operation(summary = "Create a client", description = "Emails are normalised to lower case and must be unique (409 otherwise).")
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody CreateClientRequest request) {
        ClientResponse client = clientService.create(request);
        return ResponseEntity.created(URI.create("/clients/" + client.id())).body(client);
    }

    @GetMapping
    @Operation(summary = "List all clients", description = "Sorted by last name, then first name.")
    public List<ClientResponse> list() {
        return clientService.list();
    }

    @GetMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "The client")
    @ApiResponse(responseCode = "404", description = "Client not found")
    @Operation(summary = "Get a client")
    public ClientResponse get(@PathVariable String id) {
        return clientService.get(ClientId.parse(id));
    }

    @PostMapping("/{id}/documents")
    @ApiResponse(responseCode = "201", description = "Document created and indexed")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Client not found")
    @Operation(summary = "Add a document to a client",
            description = "The document is indexed before this call returns, so it is immediately searchable.")
    public ResponseEntity<DocumentResponse> createDocument(@PathVariable String id,
                                                           @Valid @RequestBody CreateDocumentRequest request) {
        DocumentResponse document = documentService.create(ClientId.parse(id), request);
        return ResponseEntity.created(URI.create("/documents/" + document.id())).body(document);
    }

    @GetMapping("/{id}/documents")
    @ApiResponse(responseCode = "200", description = "The client's documents, newest first")
    @ApiResponse(responseCode = "404", description = "Client not found")
    @Operation(summary = "List the documents of a client")
    public List<DocumentResponse> listDocuments(@PathVariable String id) {
        return documentService.listByClient(ClientId.parse(id));
    }
}
