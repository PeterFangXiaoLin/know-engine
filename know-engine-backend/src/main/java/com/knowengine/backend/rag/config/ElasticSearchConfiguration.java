package com.knowengine.backend.rag.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(ElasticSearchProperties.class)
public class ElasticSearchConfiguration {

    private final ElasticSearchProperties properties;

    public static final String INDEX_NAME = "know-engine-vector";

    public ElasticSearchConfiguration(ElasticSearchProperties properties) {
        this.properties = properties;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RestClient restClient() {
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(properties.getUsername(), properties.getPassword()));

        return RestClient
                .builder(HttpHost.create(properties.getHost()))
                .setHttpClientConfigCallback(httpClientBuilder ->
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
                .build();
    }

    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        // The RestClient bean owns the connection lifecycle and closes it on shutdown.
        return new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    @Primary
    @ConditionalOnMissingBean
    @Bean
    public ElasticsearchEmbeddingStore elasticsearchEmbeddingStore(ElasticsearchClient client) {
        return ElasticsearchEmbeddingStore.builder()
                .client(client)
                .indexName(INDEX_NAME)
                .build();
    }

    @Bean
    public OpenAiEmbeddingModel openAiEmbeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .modelName(properties.getModelName())
                .dimensions(properties.getDimensions())
                .baseUrl(properties.getBaseUrl())
                .maxSegmentsPerBatch(9)
                .apiKey(properties.getApiKey()).build();
    }
}
