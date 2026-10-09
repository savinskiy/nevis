package com.nevis.search.search;

import static java.nio.charset.StandardCharsets.UTF_8;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.json.JsonData;
import com.nevis.search.client.ClientRepository;
import com.nevis.search.client.ClientResponse;
import com.nevis.search.document.DocumentRepository;
import com.nevis.search.document.DocumentResponse;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class SearchIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexInitializer.class);
    private static final String DEFINITION = "definition_sha256";

    private final ElasticsearchClient elasticsearch;
    private final ClientRepository clients;
    private final DocumentRepository documents;
    private final SearchService search;

    public SearchIndexInitializer(ElasticsearchClient elasticsearch, ClientRepository clients,
                                  DocumentRepository documents, SearchService search) {
        this.elasticsearch = elasticsearch;
        this.clients = clients;
        this.documents = documents;
        this.search = search;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (createOrUpgrade(SearchService.CLIENTS)) {
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            clients.findAll().stream().map(ClientResponse::from).forEach(client -> bulk.operations(op -> op
                    .index(i -> i.index(SearchService.CLIENTS).id(client.id().toString()).document(client))));
            reindex(SearchService.CLIENTS, bulk, clients.count());
        }
        if (createOrUpgrade(SearchService.DOCUMENTS)) {
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            documents.findAll().stream().map(DocumentResponse::from).forEach(document -> bulk.operations(op -> op
                    .index(i -> i.index(SearchService.DOCUMENTS).id(document.id().toString())
                            .document(search.toIndexed(document)))));
            reindex(SearchService.DOCUMENTS, bulk, documents.count());
        }
    }

    // true when the index was (re)created and must be filled from PostgreSQL. An index whose definition in
    // resources/elasticsearch has changed since it was created is dropped and recreated, so changes deploy themselves.
    private boolean createOrUpgrade(String index) throws IOException {
        String definition = new ClassPathResource("elasticsearch/" + index + ".json").getContentAsString(UTF_8);
        String fingerprint = sha256(definition);
        if (elasticsearch.indices().exists(e -> e.index(index)).value()) {
            JsonData stored = elasticsearch.indices().getMapping(g -> g.index(index))
                    .get(index).mappings().meta().get(DEFINITION);
            if (stored != null && fingerprint.equals(stored.to(String.class))) {
                return false;
            }
            elasticsearch.indices().delete(d -> d.index(index));
            log.info("Definition of Elasticsearch index {} changed; recreating it", index);
        }
        elasticsearch.indices().create(c -> c.index(index).withJson(new StringReader(definition)));
        elasticsearch.indices().putMapping(m -> m.index(index).meta(DEFINITION, JsonData.of(fingerprint)));
        log.info("Created Elasticsearch index {}", index);
        return true;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void reindex(String index, BulkRequest.Builder bulk, long count) throws IOException {
        if (count == 0) {
            return;
        }
        if (elasticsearch.bulk(bulk.build()).errors()) {
            throw new UncheckedIOException(new IOException("Reindexing " + index + " failed"));
        }
        log.info("Indexed {} existing rows into {}", count, index);
    }
}
