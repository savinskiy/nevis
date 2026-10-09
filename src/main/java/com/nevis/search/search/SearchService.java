package com.nevis.search.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.InnerHitsResult;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.util.NamedValue;
import com.nevis.search.client.ClientResponse;
import com.nevis.search.document.DocumentResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    static final String CLIENTS = "clients";
    static final String DOCUMENTS = "documents";
    private static final List<String> FIELDS =
            List.of("first_name", "last_name", "email", "description", "title", "content");
    // substrings of names and emails ("nevis" in "neviswealth"); weighted below whole-word matches
    private static final List<String> PARTIAL_FIELDS =
            List.of("first_name.partial", "last_name.partial", "email.partial");
    private static final List<String> QUERY_FIELDS = Stream.concat(FIELDS.stream(),
            PARTIAL_FIELDS.stream().map(field -> field + "^0.5")).toList();
    private static final List<NamedValue<HighlightField>> HIGHLIGHTED = Stream.concat(FIELDS.stream(),
            PARTIAL_FIELDS.stream()).map(field -> NamedValue.of(field, HighlightField.of(f -> f))).toList();

    private final ElasticsearchClient elasticsearch;
    private final EmbeddingService embeddings;
    private final SearchProperties properties;

    public SearchService(ElasticsearchClient elasticsearch, EmbeddingService embeddings,
                         SearchProperties properties) {
        this.elasticsearch = elasticsearch;
        this.embeddings = embeddings;
        this.properties = properties;
    }

    public void index(ClientResponse client) {
        index(CLIENTS, client.id().toString(), client);
    }

    public void index(DocumentResponse document) {
        index(DOCUMENTS, document.id().toString(), toIndexed(document));
    }

    // a summary does not change what is searchable, so the passages and their embeddings are kept
    public void updateSummary(UUID documentId, String summary) {
        try {
            elasticsearch.update(u -> u.index(DOCUMENTS).id(documentId.toString())
                    .doc(Map.of("summary", summary)).refresh(Refresh.WaitFor), Object.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    IndexedDocument toIndexed(DocumentResponse document) {
        List<String> texts = Passages.of(document.title(), document.content());
        List<float[]> vectors = embeddings.embedAll(texts);
        List<IndexedDocument.Passage> passages = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            passages.add(new IndexedDocument.Passage(texts.get(i), vectors.get(i)));
        }
        return new IndexedDocument(document, passages);
    }

    // Hybrid: a result matches by words (BM25 with synonyms and partial names) or by meaning (the query's
    // embedding is close to one of the document's passages); matching both ranks it higher.
    public List<SearchResult> search(String query) {
        Query byWords = Query.of(q -> q.multiMatch(m -> m.query(query).fields(QUERY_FIELDS)));
        Query byMeaning = Query.of(q -> q.nested(n -> n
                .path("passages")
                .ignoreUnmapped(true) // the clients index has no passages
                .scoreMode(ChildScoreMode.Max)
                .query(inner -> inner.knn(k -> k
                        .field("passages.vector")
                        .queryVector(asList(embeddings.embed(query)))
                        .k(20)
                        .numCandidates(100)
                        .similarity((float) properties.minSimilarity())
                        .boost(2f)))
                .innerHits(ih -> ih.size(1).source(src -> src.filter(f -> f.includes("passages.text"))))));
        try {
            SearchResponse<JsonData> response = elasticsearch.search(s -> s
                    .index(CLIENTS, DOCUMENTS)
                    .size(properties.maxResults())
                    .query(q -> q.bool(b -> b.should(byWords, byMeaning)))
                    .source(src -> src.filter(f -> f.excludes("passages")))
                    .highlight(h -> h.fields(HIGHLIGHTED)), JsonData.class);
            return response.hits().hits().stream().map(SearchService::toResult).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SearchResult toResult(Hit<JsonData> hit) {
        double score = hit.score() == null ? 0 : hit.score();
        Map<String, List<String>> highlights = highlights(hit);
        return CLIENTS.equals(hit.index())
                ? new SearchResult("client", score, highlights, hit.source().to(ClientResponse.class), null)
                : new SearchResult("document", score, highlights, null, hit.source().to(DocumentResponse.class));
    }

    // report "email.partial" under "email": callers care which field matched, not how;
    // "passage" is the passage closest in meaning to the query, when the document matched that way
    private static Map<String, List<String>> highlights(Hit<JsonData> hit) {
        Map<String, List<String>> highlights = new LinkedHashMap<>();
        hit.highlight().forEach((field, fragments) -> {
            if (!field.endsWith(".partial")) {
                highlights.put(field, fragments);
            }
        });
        hit.highlight().forEach((field, fragments) -> {
            if (field.endsWith(".partial")) {
                highlights.putIfAbsent(field.substring(0, field.length() - ".partial".length()), fragments);
            }
        });
        InnerHitsResult passages = hit.innerHits().get("passages");
        if (passages != null && !passages.hits().hits().isEmpty()) {
            Object text = passages.hits().hits().get(0).source().to(Map.class).get("text");
            if (text != null) {
                highlights.put("passage", List.of(text.toString()));
            }
        }
        return highlights;
    }

    private static List<Float> asList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float value : vector) {
            list.add(value);
        }
        return list;
    }

    private void index(String index, String id, Object source) {
        try {
            elasticsearch.index(i -> i.index(index).id(id).document(source).refresh(Refresh.WaitFor));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
