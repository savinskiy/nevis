package com.nevis.search.search;

import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class SearchConfig {

    // index documents with the API's JSON mapping (snake_case), so search hits map straight back to API models
    @Bean
    JsonpMapper elasticsearchJsonpMapper(JsonMapper jsonMapper) {
        return new Jackson3JsonpMapper(jsonMapper);
    }
}
