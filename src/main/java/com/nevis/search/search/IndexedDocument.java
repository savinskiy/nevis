package com.nevis.search.search;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.nevis.search.document.DocumentResponse;
import java.util.List;

// A document as stored in Elasticsearch: the API fields plus its passages with their embeddings.
record IndexedDocument(@JsonUnwrapped DocumentResponse document, List<Passage> passages) {

    record Passage(String text, float[] vector) {
    }
}
