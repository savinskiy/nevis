package com.nevis.search.summary;

import com.nevis.search.document.Document;
import com.nevis.search.document.DocumentId;
import com.nevis.search.document.DocumentRepository;
import com.nevis.search.llm.LlmClient;
import com.nevis.search.search.SearchService;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SummaryService {

    private static final String SYSTEM_PROMPT = """
            You summarise client documents for wealth advisors who need to grasp a document at a glance.
            Write 2-3 plain sentences: what kind of document it is, who and what it concerns, and the key facts \
            (dates, amounts, decisions, required actions). Use only information stated in the document. \
            Output the summary only - no preamble, headings or bullet points.""";

    private final DocumentRepository documents;
    private final LlmClient llm;
    private final SearchService search;

    public SummaryService(DocumentRepository documents, LlmClient llm, SearchService search) {
        this.documents = documents;
        this.llm = llm;
        this.search = search;
    }

    public boolean enabled() {
        return llm.available();
    }

    public Optional<String> summarize(DocumentId documentId) {
        Optional<Document> found = documents.findById(documentId.value());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Document document = found.get();
        if (document.getSummary().isPresent()) {
            return document.getSummary();
        }
        Optional<String> summary = llm.complete(SYSTEM_PROMPT,
                "<document>\n<title>" + document.getTitle() + "</title>\n<content>\n" + document.getContent()
                        + "\n</content>\n</document>", 4_000);
        if (summary.isEmpty() || documents.saveSummary(documentId.value(), summary.get()) == 0) {
            return Optional.empty();
        }
        search.updateSummary(documentId.value(), summary.get());
        return summary;
    }
}
