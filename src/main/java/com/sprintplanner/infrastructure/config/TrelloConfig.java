package com.sprintplanner.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@ConfigurationProperties(prefix = "trello")
@Component
public class TrelloConfig {

    private String apiKey = "";
    private String token = "";
    private String baseUrl = "https://api.trello.com/1";

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
