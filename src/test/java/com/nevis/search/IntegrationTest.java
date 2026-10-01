package com.nevis.search;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.jayway.jsonpath.JsonPath;
import com.nevis.search.client.ClientRepository;
import com.nevis.search.document.DocumentRepository;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Full application against real PostgreSQL and Elasticsearch; each test starts from empty stores.
@SpringBootTest(properties = "nevis.llm.api-key=") // never call a real LLM from tests
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ElasticsearchClient elasticsearch;
    @Autowired
    protected ClientRepository clients;
    @Autowired
    protected DocumentRepository documents;

    @BeforeEach
    void cleanState() throws IOException {
        documents.deleteAll();
        clients.deleteAll();
        elasticsearch.deleteByQuery(d -> d.index("clients", "documents").query(q -> q.matchAll(m -> m)).refresh(true));
    }

    protected ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected String createClient(String firstName, String lastName, String email, String description)
            throws Exception {
        String body = postJson("/clients", """
                {"first_name": "%s", "last_name": "%s", "email": "%s", "description": "%s"}"""
                .formatted(firstName, lastName, email, description))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    protected String createDocument(String clientId, String title, String content) throws Exception {
        String body = postJson("/clients/" + clientId + "/documents", """
                {"title": "%s", "content": "%s"}""".formatted(title, content))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
