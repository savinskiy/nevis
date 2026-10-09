package com.nevis.search.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PassagesTest {

    @Test
    void titleThenOneCollapsedPassagePerParagraph() {
        List<String> passages = Passages.of("Annual review", "First paragraph,\nover two lines.\n\n"
                + "   Second paragraph.\r\n\r\n\r\nThird.");

        assertThat(passages).containsExactly(
                "Annual review", "First paragraph, over two lines.", "Second paragraph.", "Third.");
    }

    @Test
    void longParagraphIsSplitAtSentenceEndsWithinTheLimit() {
        String sentence = "This sentence is exactly fifty characters long ok. ";
        List<String> passages = Passages.of("T", sentence.repeat(40).strip());

        assertThat(passages).hasSizeGreaterThan(2);
        assertThat(passages.subList(1, passages.size())).allSatisfy(passage -> {
            assertThat(passage.length()).isLessThanOrEqualTo(Passages.MAX_CHARS);
            assertThat(passage).endsWith(".");
        });
    }

    @Test
    void sentenceLongerThanTheLimitIsCut() {
        List<String> passages = Passages.of("T", "x".repeat(Passages.MAX_CHARS * 2 + 10));

        assertThat(passages).extracting(String::length)
                .containsExactly(1, Passages.MAX_CHARS, Passages.MAX_CHARS, 10);
    }
}
