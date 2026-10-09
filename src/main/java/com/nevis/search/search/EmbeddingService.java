package com.nevis.search.search;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import java.util.List;
import org.springframework.stereotype.Service;

// Sentence embeddings (384 dimensions) computed in-process with the bundled all-MiniLM-L6-v2 ONNX model.
@Service
public class EmbeddingService {

    private final EmbeddingModel model = new AllMiniLmL6V2QuantizedEmbeddingModel();

    public float[] embed(String text) {
        return model.embed(text).content().vector();
    }

    public List<float[]> embedAll(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        return model.embedAll(texts.stream().map(TextSegment::from).toList()).content().stream()
                .map(Embedding::vector)
                .toList();
    }
}
