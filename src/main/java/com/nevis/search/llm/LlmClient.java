package com.nevis.search.llm;

import java.util.Optional;

public interface LlmClient {

    boolean available();

    String model();

    Optional<String> complete(String system, String user, long maxTokens);
}
