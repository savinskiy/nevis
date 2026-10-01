package com.nevis.search.client;

import com.nevis.search.common.ConflictException;
import com.nevis.search.common.NotFoundException;
import com.nevis.search.search.SearchService;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientService {

    private final ClientRepository clients;
    private final SearchService search;

    public ClientService(ClientRepository clients, SearchService search) {
        this.clients = clients;
        this.search = search;
    }

    @Transactional
    public ClientResponse create(CreateClientRequest request) {
        Email email = new Email(request.email());
        if (clients.existsByEmail(email)) {
            throw new ConflictException("Client with email " + email + " already exists");
        }
        Client client = new Client(
                new PersonName(request.firstName(), request.lastName()),
                email,
                blankToNull(request.description()),
                request.socialLinks() == null ? List.of() : request.socialLinks().stream().map(String::trim).toList());
        ClientResponse saved = ClientResponse.from(clients.saveAndFlush(client));
        search.index(saved);
        return saved;
    }

    public List<ClientResponse> list() {
        return clients.findAll(Sort.by("lastName", "firstName")).stream().map(ClientResponse::from).toList();
    }

    public ClientResponse get(ClientId id) {
        return clients.findById(id.value()).map(ClientResponse::from)
                .orElseThrow(() -> new NotFoundException("Client " + id + " not found"));
    }

    public void requireExists(ClientId id) {
        if (!clients.existsById(id.value())) {
            throw new NotFoundException("Client " + id + " not found");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
