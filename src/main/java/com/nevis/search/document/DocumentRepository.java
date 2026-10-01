package com.nevis.search.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByClientIdOrderByCreatedAtDesc(UUID clientId);

    @Query("select d.id from Document d where d.summary is null")
    List<UUID> findIdsWithoutSummary();

    @Transactional
    @Modifying
    @Query("update Document d set d.summary = :summary where d.id = :id")
    int saveSummary(UUID id, String summary);
}
