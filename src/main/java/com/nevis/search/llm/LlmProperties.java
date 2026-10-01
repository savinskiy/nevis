package com.nevis.search.llm;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nevis.llm")
public record LlmProperties(String apiKey, String model, Duration timeout) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
