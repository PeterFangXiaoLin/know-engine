package com.knowengine.backend.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = ElasticSearchProperties.PREFIX)
public class ElasticSearchProperties {
    public static final String PREFIX = "elasticsearch";

    private String host;

    private String username;

    private String password;

    private String baseUrl;

    private String modelName;

    private String apiKey;

    private int dimensions;
}
