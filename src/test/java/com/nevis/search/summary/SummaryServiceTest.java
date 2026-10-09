package com.nevis.search.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nevis.search.client.ClientId;
import com.nevis.search.document.Document;
import com.nevis.search.document.DocumentId;
import com.nevis.search.document.DocumentRepository;
import com.nevis.search.llm.LlmClient;
import com.nevis.search.search.SearchService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SummaryServiceTest {

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final LlmClient llm = mock(LlmClient.class);
    private final SearchService search = mock(SearchService.class);
    private final SummaryService service = new SummaryService(documents, llm, search);

    private final Document document = new Document(ClientId.random(), "Will", "Last will of John Doe.");
    private final DocumentId id = document.getId();

    @BeforeEach
    void setUp() {
        when(documents.findById(id.value())).thenReturn(Optional.of(document));
        when(documents.saveSummary(any(), anyString())).thenReturn(1);
        when(llm.complete(anyString(), contains("Last will of John Doe."), anyLong())).thenReturn(Optional.of("A will."));
    }

    @Test
    void generatesStoresAndIndexesTheSummary() {
        assertThat(service.summarize(id)).contains("A will.");

        verify(documents).saveSummary(id.value(), "A will.");
        verify(search).updateSummary(id.value(), "A will.");
    }

    @Test
    void existingSummaryIsKeptWithoutCallingTheLlm() {
        document.setSummary("Stored.");

        assertThat(service.summarize(id)).contains("Stored.");
        verify(llm, never()).complete(anyString(), anyString(), anyLong());
        verify(documents, never()).saveSummary(any(), anyString());
    }

    @Test
    void failedLlmCallStoresNothing() {
        when(llm.complete(anyString(), anyString(), anyLong())).thenReturn(Optional.empty());

        assertThat(service.summarize(id)).isEmpty();
        verify(documents, never()).saveSummary(any(), anyString());
    }

    @Test
    void documentDeletedWhileSummarisingIsNotRecreated() {
        when(documents.saveSummary(any(), anyString())).thenReturn(0);

        assertThat(service.summarize(id)).isEmpty();
        verify(search, never()).updateSummary(any(), anyString());
    }

    @Test
    void unknownDocumentIsSkipped() {
        assertThat(service.summarize(DocumentId.random())).isEmpty();
        verify(llm, never()).complete(anyString(), anyString(), anyLong());
    }
}
