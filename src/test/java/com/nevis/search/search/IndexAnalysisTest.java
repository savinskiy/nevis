package com.nevis.search.search;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import com.nevis.search.IntegrationTest;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class IndexAnalysisTest extends IntegrationTest {

    @Test
    void emailIsSplitIntoWords() throws IOException {
        assertThat(analyze("email", "John.Doe@NevisWealth.com")).containsExactly("john", "doe", "neviswealth", "com");
        assertThat(analyze("email", "NevisWealth")).containsExactly("neviswealth");
    }

    @Test
    void accentedLettersInEmailsAreLettersNotSeparators() throws IOException {
        assertThat(analyze("email", "josé.garcía@example.com")).containsExactly("jose", "garcia", "example", "com");
    }

    @Test
    void namesIgnoreCaseAndAccents() throws IOException {
        assertThat(analyze("name", "María García-López")).containsExactly("maria", "garcia", "lopez");
    }

    @Test
    void partialAnalyzerIndexesEverySliceOfThreeOrMoreCharacters() throws IOException {
        assertThat(analyze("partial", "NevisWealth"))
                .contains("nev", "nevis", "wealth", "neviswealth")
                .allSatisfy(token -> assertThat(token).hasSizeGreaterThanOrEqualTo(3));
    }

    private List<String> analyze(String analyzer, String text) throws IOException {
        return elasticsearch.indices().analyze(a -> a.index("clients").analyzer(analyzer).text(text))
                .tokens().stream().map(AnalyzeToken::token).toList();
    }
}
