package com.nevis.search.summary;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.nevis.search.IntegrationTest;
import com.nevis.search.llm.LlmClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class SummaryApiTest extends IntegrationTest {

    @MockitoBean
    LlmClient llm;

    @BeforeEach
    void stubLlm() {
        when(llm.available()).thenReturn(true);
        when(llm.complete(anyString(), contains("<document>"), anyLong()))
                .thenReturn(Optional.of("Tax return for 2023 showing 2,400 pounds due by 31 January 2025."));
    }

    @Test
    void summaryIsWrittenInTheBackgroundAfterUpload() throws Exception {
        String client = mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content("""
                        {"first_name": "John", "last_name": "Doe", "email": "summary.test@example.com"}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String document = mvc.perform(post("/clients/" + JsonPath.read(client, "$.id") + "/documents")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title": "Tax return", "content": "Self assessment tax return for 2023 filed \
                                with HMRC. Tax due is 2,400 pounds by 31 January 2025."}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String documentId = JsonPath.read(document, "$.id");

        awaitSummary("/documents/" + documentId, "$.summary");
        awaitSummary("/search?q=tax%20return",
                "$[?(@.document.id == '" + documentId + "')].document.summary");
        mvc.perform(get("/documents/" + documentId))
                .andExpect(jsonPath("$.summary").value("Tax return for 2023 showing 2,400 pounds due by 31 January 2025."));
    }

    private void awaitSummary(String url, String path) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (true) {
            String body = mvc.perform(get(url)).andReturn().getResponse().getContentAsString();
            try {
                Object value = JsonPath.read(body, path);
                if (value instanceof List<?> list ? !list.isEmpty() : value != null) {
                    return;
                }
            } catch (PathNotFoundException e) {
                // not generated yet
            }
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("No summary at " + path + " of " + url + " after 10 s: " + body);
            }
            Thread.sleep(100);
        }
    }
}
