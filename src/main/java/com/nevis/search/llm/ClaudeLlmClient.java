package com.nevis.search.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ClaudeLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLlmClient.class);

    private final LlmProperties properties;
    private final AnthropicClient client;

    @Autowired
    public ClaudeLlmClient(LlmProperties properties) {
        this(properties, properties.configured()
                ? AnthropicOkHttpClient.builder().apiKey(properties.apiKey()).timeout(properties.timeout()).maxRetries(1).build()
                : null);
    }

    ClaudeLlmClient(LlmProperties properties, AnthropicClient client) {
        this.properties = properties;
        this.client = client;
        log.info("LLM features {}", client == null ? "disabled (no ANTHROPIC_API_KEY): documents get no summary"
                : "enabled with model " + properties.model());
    }

    @Override
    public boolean available() {
        return client != null;
    }

    @Override
    public String model() {
        return properties.model();
    }

    @Override
    public Optional<String> complete(String system, String user, long maxTokens) {
        if (client == null) {
            return Optional.empty();
        }
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(properties.model())
                    .maxTokens(maxTokens)
                    .system(system)
                    .addUserMessage(user)
                    // short, well-scoped tasks: keep thinking shallow for latency and cost
                    .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
                    // if the model's safety classifiers decline a request, let the API retry on a fallback model
                    .addBeta("server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                    .build();
            BetaMessage message = client.beta().messages().create(params);
            if (message.stopReason().filter(BetaStopReason.REFUSAL::equals).isPresent()) {
                log.warn("LLM declined the request");
                return Optional.empty();
            }
            String text = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(block -> block.text())
                    .collect(Collectors.joining())
                    .strip();
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (AnthropicException e) {
            log.warn("LLM request failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
