package com.nevis.search.search;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.elastic.clients.json.JsonData;
import com.nevis.search.IntegrationTest;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

class SearchIndexInitializerTest extends IntegrationTest {

    @Autowired
    SearchIndexInitializer initializer;

    @Test
    void lostIndexIsRebuiltFromPostgres() throws Exception {
        String john = createClient("John", "Doe", "john.doe@neviswealth.com", "");
        createDocument(john, "Scan", "Utility bill from EDF Energy for March 2024.");
        createDocument(john, "Plans", "John wants to stop working at 58 and live off his savings and investments.");
        elasticsearch.indices().delete(d -> d.index("clients", "documents"));

        startUp();

        search("neviswealth").andExpect(jsonPath("$[0].client.id").value(john));
        search("address proof")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].document.title").value("Scan"))
                .andExpect(jsonPath("$[*].type", not(hasItem("client"))));
        // embeddings are recomputed during the rebuild, so matches by meaning work again
        search("retiring early").andExpect(jsonPath("$[0].document.title").value("Plans"));
    }

    @Test
    void indexWithOutdatedDefinitionIsRecreatedFromPostgres() throws Exception {
        String john = createClient("John", "Doe", "john.doe@neviswealth.com", "");
        elasticsearch.indices().putMapping(m -> m.index("clients")
                .meta("definition_sha256", JsonData.of("an older definition")));

        startUp();

        search("nevis").andExpect(jsonPath("$[0].client.id").value(john));
    }

    @Test
    void currentIndexIsLeftAlone() throws Exception {
        createClient("John", "Doe", "john@example.com", "");
        clients.deleteAll(); // the index now holds an entry the database no longer has

        startUp();

        search("john").andExpect(jsonPath("$", hasSize(1)));
    }

    private void startUp() throws IOException {
        initializer.run(null);
        elasticsearch.indices().refresh(r -> r.index("clients", "documents"));
    }

    private ResultActions search(String q) throws Exception {
        return mvc.perform(get("/search").param("q", q)).andExpect(status().isOk());
    }
}
