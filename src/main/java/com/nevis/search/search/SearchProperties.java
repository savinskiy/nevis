package com.nevis.search.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nevis.search")
public record SearchProperties(int maxResults, double minSimilarity) {
}
