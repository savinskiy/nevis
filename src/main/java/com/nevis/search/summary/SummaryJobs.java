package com.nevis.search.summary;

import com.nevis.search.document.DocumentCreated;
import com.nevis.search.document.DocumentId;
import com.nevis.search.document.DocumentRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SummaryJobs {

    private static final Logger log = LoggerFactory.getLogger(SummaryJobs.class);

    private final SummaryService summaries;
    private final DocumentRepository documents;
    private final Executor executor;
    private final Set<DocumentId> queued = ConcurrentHashMap.newKeySet();

    public SummaryJobs(SummaryService summaries, DocumentRepository documents,
                       @Qualifier("summaryExecutor") Executor executor) {
        this.summaries = summaries;
        this.documents = documents;
        this.executor = executor;
    }

    @TransactionalEventListener
    void onDocumentCreated(DocumentCreated event) {
        if (summaries.enabled()) {
            submit(event.id());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void summarizeBacklog() {
        queueMissing("startup");
    }

    @Scheduled(initialDelayString = "${nevis.summary.retry-interval}",
            fixedDelayString = "${nevis.summary.retry-interval}")
    void retryMissing() {
        queueMissing("retry");
    }

    private void queueMissing(String reason) {
        if (!summaries.enabled()) {
            return;
        }
        List<UUID> ids = documents.findIdsWithoutSummary();
        ids.forEach(id -> submit(new DocumentId(id)));
        if (!ids.isEmpty()) {
            log.info("Queued {} documents without summary ({})", ids.size(), reason);
        }
    }

    void submit(DocumentId documentId) {
        if (!queued.add(documentId)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    summaries.summarize(documentId);
                } catch (RuntimeException e) {
                    log.warn("Summarising document {} failed", documentId, e);
                } finally {
                    queued.remove(documentId);
                }
            });
        } catch (RuntimeException e) {
            queued.remove(documentId);
            log.warn("Could not queue summary of document {}", documentId, e);
        }
    }
}
