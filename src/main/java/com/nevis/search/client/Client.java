package com.nevis.search.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    private UUID id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private Email email;

    private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "social_links", nullable = false)
    private List<String> socialLinks = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Client() {
    }

    public Client(PersonName name, Email email, String description, List<String> socialLinks) {
        this.id = ClientId.random().value();
        this.firstName = name.first();
        this.lastName = name.last();
        this.email = email;
        this.description = description;
        this.socialLinks = new ArrayList<>(socialLinks);
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public ClientId getId() {
        return new ClientId(id);
    }

    public PersonName getName() {
        return new PersonName(firstName, lastName);
    }

    public Email getEmail() {
        return email;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public List<String> getSocialLinks() {
        return List.copyOf(socialLinks);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
