package com.nevis.search.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class EmbeddingServiceTest {

    private final EmbeddingService embeddings = new EmbeddingService();

    // swapping the model for one with a different vector size must also change the index mapping
    @Test
    void vectorSizeMatchesTheIndexMapping() throws IOException {
        int mappedDims;
        try (InputStream definition = new ClassPathResource("elasticsearch/documents.json").getInputStream()) {
            JsonNode passages = JsonMapper.builder().build().readTree(definition)
                    .path("mappings").path("properties").path("passages");
            mappedDims = passages.path("properties").path("vector").path("dims").asInt();
        }

        assertThat(embeddings.embed("utility bill")).hasSize(mappedDims);
    }

    @Test
    void embedsEachTextAndNothingForNoTexts() {
        assertThat(embeddings.embedAll(List.of("passport", "tax return"))).hasSize(2);
        assertThat(embeddings.embedAll(List.of())).isEmpty();
    }
}
