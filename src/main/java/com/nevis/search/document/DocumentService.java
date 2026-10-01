package com.nevis.search.document;

import com.nevis.search.client.ClientId;
import com.nevis.search.client.ClientService;
import com.nevis.search.common.NotFoundException;
import com.nevis.search.search.SearchService;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private final DocumentRepository documents;
    private final ClientService clientService;
    private final SearchService search;
    private final ApplicationEventPublisher events;

    public DocumentService(DocumentRepository documents, ClientService clientService, SearchService search,
                           ApplicationEventPublisher events) {
        this.documents = documents;
        this.clientService = clientService;
        this.search = search;
        this.events = events;
    }

    @Transactional
    public DocumentResponse create(ClientId clientId, CreateDocumentRequest request) {
        clientService.requireExists(clientId);
        Document saved = documents.saveAndFlush(
                new Document(clientId, request.title().trim(), request.content().trim()));
        DocumentResponse response = DocumentResponse.from(saved);
        search.index(response);
        events.publishEvent(new DocumentCreated(saved.getId()));
        return response;
    }

    public List<DocumentResponse> listByClient(ClientId clientId) {
        clientService.requireExists(clientId);
        return documents.findByClientIdOrderByCreatedAtDesc(clientId.value()).stream()
                .map(DocumentResponse::from).toList();
    }

    public DocumentResponse get(DocumentId id) {
        return documents.findById(id.value()).map(DocumentResponse::from)
                .orElseThrow(() -> new NotFoundException("Document " + id + " not found"));
    }
}
