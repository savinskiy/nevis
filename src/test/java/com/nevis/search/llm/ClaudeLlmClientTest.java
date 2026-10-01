package com.nevis.search.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClaudeLlmClientTest {

    private static final LlmProperties PROPERTIES =
            new LlmProperties("test-key", "claude-opus-5-5", Duration.ofSeconds(30));

    private final AnthropicClient anthropic = mock(AnthropicClient.class, RETURNS_DEEP_STUBS);
    private final ClaudeLlmClient client = new ClaudeLlmClient(PROPERTIES, anthropic);

    @Test
    void withoutApiKeyIsUnavailableAndNeverCallsTheApi() {
        ClaudeLlmClient unconfigured = new ClaudeLlmClient(new LlmProperties("", "claude-opus-5-5", Duration.ofSeconds(30)));

        assertThat(unconfigured.available()).isFalse();
        assertThat(unconfigured.complete("system", "user", 100)).isEmpty();
    }

    @Test
    void returnsTheTextOfTheAnswer() {
        respondWith(BetaStopReason.END_TURN, "  A will ", "of John Doe.  ");

        assertThat(client.available()).isTrue();
        assertThat(client.complete("system", "user", 4_000)).contains("A will of John Doe.");
    }

    @Test
    void sendsTheConfiguredModelAndOptsIntoServerSideFallback() {
        respondWith(BetaStopReason.END_TURN, "ok");

        client.complete("Summarise.", "<document>…</document>", 4_000);

        ArgumentCaptor<MessageCreateParams> sent = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(anthropic.beta().messages()).create(sent.capture());
        MessageCreateParams params = sent.getValue();
        assertThat(params.model()).isEqualTo(Model.of("claude-opus-5-5"));
        assertThat(params.maxTokens()).isEqualTo(4_000);
        assertThat(params.betas()).hasValueSatisfying(betas ->
                assertThat(betas).contains(AnthropicBeta.of("server-side-fallback-2026-07-01")));
        assertThat(params._additionalBodyProperties()).containsEntry("fallbacks", JsonValue.from("default"));
    }

    @Test
    void refusalYieldsNothing() {
        respondWith(BetaStopReason.REFUSAL, "I can't help with that.");

        assertThat(client.complete("system", "user", 100)).isEmpty();
    }

    @Test
    void blankAnswerYieldsNothing() {
        respondWith(BetaStopReason.END_TURN, "   ");

        assertThat(client.complete("system", "user", 100)).isEmpty();
    }

    @Test
    void apiErrorYieldsNothingInsteadOfThrowing() {
        when(anthropic.beta().messages().create(any(MessageCreateParams.class)))
                .thenThrow(new AnthropicIoException("connection reset"));

        assertThat(client.complete("system", "user", 100)).isEmpty();
    }

    private void respondWith(BetaStopReason stopReason, String... texts) {
        List<BetaContentBlock> blocks = Arrays.stream(texts).map(text -> {
            BetaTextBlock textBlock = mock(BetaTextBlock.class);
            when(textBlock.text()).thenReturn(text);
            BetaContentBlock block = mock(BetaContentBlock.class);
            when(block.text()).thenReturn(Optional.of(textBlock));
            return block;
        }).toList();
        BetaMessage message = mock(BetaMessage.class);
        when(message.stopReason()).thenReturn(Optional.of(stopReason));
        when(message.content()).thenReturn(blocks);
        when(anthropic.beta().messages().create(any(MessageCreateParams.class))).thenReturn(message);
    }
}
