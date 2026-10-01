package com.nevis.search.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.util.NamedValue;
import com.nevis.search.client.ClientResponse;
import com.nevis.search.document.DocumentResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final SearchProperties properties;

    public SearchService(ElasticsearchClient elasticsearch, SearchProperties properties) {
        this.elasticsearch = elasticsearch;
        this.properties = properties;
    }

    public void index(ClientResponse client) {
        index(CLIENTS, client.id().toString(), client);
    }

    public void index(DocumentResponse document) {
        index(DOCUMENTS, document.id().toString(), document);
    }

    public List<SearchResult> search(String query) {
        try {
            SearchResponse<JsonData> response = elasticsearch.search(s -> s
                    .index(CLIENTS, DOCUMENTS)
                    .size(properties.maxResults())
                    .query(q -> q.multiMatch(m -> m.query(query).fields(QUERY_FIELDS)))
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

    // report "email.partial" under "email": callers care which field matched, not how
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
        return highlights;
    }

    private void index(String index, String id, Object source) {
        try {
            elasticsearch.index(i -> i.index(index).id(id).document(source).refresh(Refresh.WaitFor));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}