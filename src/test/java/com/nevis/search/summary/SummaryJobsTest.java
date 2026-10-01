package com.nevis.search.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nevis.search.document.DocumentCreated;
import com.nevis.search.document.DocumentId;
import com.nevis.search.document.DocumentRepository;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SummaryJobsTest {

    /** Holds submitted jobs until the test runs them, to make concurrency deterministic. */
    static final class ManualExecutor implements Executor {
        final Queue<Runnable> queued = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            queued.add(task);
        }

        void runAll() {
            while (!queued.isEmpty()) {
                queued.poll().run();
            }
        }
    }

    private final SummaryService summaries = mock(SummaryService.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final ManualExecutor executor = new ManualExecutor();
    private final SummaryJobs jobs = new SummaryJobs(summaries, documents, executor);
    private final DocumentId id = DocumentId.random();

    @BeforeEach
    void setUp() {
        when(summaries.enabled()).thenReturn(true);
    }

    @Test
    void newDocumentIsSummarisedInTheBackground() {
        jobs.onDocumentCreated(new DocumentCreated(id));

        verify(summaries, never()).summarize(any());
        executor.runAll();
        verify(summaries).summarize(id);
    }

    @Test
    void startupAndRetryQueueEveryDocumentStillWithoutSummary() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(documents.findIdsWithoutSummary()).thenReturn(List.of(first, second));

        jobs.summarizeBacklog();
        executor.runAll();
        jobs.retryMissing();
        executor.runAll();

        verify(summaries, times(2)).summarize(new DocumentId(first));
        verify(summaries, times(2)).summarize(new DocumentId(second));
    }

    @Test
    void documentAlreadyQueuedIsNotQueuedAgain() {
        jobs.submit(id);
        jobs.submit(id);
        assertThat(executor.queued).hasSize(1);

        executor.runAll();
        jobs.submit(id); // finished, so it can be queued again
        assertThat(executor.queued).hasSize(1);
    }

    @Test
    void failingJobIsLoggedAndReleased() {
        when(summaries.summarize(id)).thenThrow(new IllegalStateException("database down"));

        jobs.submit(id);
        assertThatCode(executor::runAll).doesNotThrowAnyException();

        jobs.submit(id);
        assertThat(executor.queued).hasSize(1);
    }

    @Test
    void rejectedJobIsReleased() {
        Executor rejecting = task -> {
            throw new RejectedExecutionException("shutting down");
        };
        SummaryJobs shuttingDown = new SummaryJobs(summaries, documents, rejecting);

        assertThatCode(() -> shuttingDown.submit(id)).doesNotThrowAnyException();
        assertThatCode(() -> shuttingDown.submit(id)).doesNotThrowAnyException();
    }

    @Test
    void nothingIsQueuedWithoutAnLlm() {
        when(summaries.enabled()).thenReturn(false);

        jobs.onDocumentCreated(new DocumentCreated(id));
        jobs.summarizeBacklog();
        jobs.retryMissing();

        assertThat(executor.queued).isEmpty();
        verifyNoInteractions(documents);
    }
}
