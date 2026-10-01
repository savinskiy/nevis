package com.nevis.search.document;

import com.nevis.search.client.ClientId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String content;

    private String summary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Document() {
    }

    public Document(ClientId clientId, String title, String content) {
        this.id = DocumentId.random().value();
        this.clientId = clientId.value();
        this.title = title;
        this.content = content;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public DocumentId getId() {
        return new DocumentId(id);
    }

    public ClientId getClientId() {
        return new ClientId(clientId);
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Optional<String> getSummary() {
        return Optional.ofNullable(summary);
    }


    public Instant getCreatedAt() {
        return createdAt;
    }
}
