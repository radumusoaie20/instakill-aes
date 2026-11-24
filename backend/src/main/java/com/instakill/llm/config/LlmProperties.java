package com.instakill.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "instakill.llm")
public class LlmProperties {

    /**
     * Optional full URL (e.g. http://localhost:12345/v1/chat/completions or http://localhost:12345).
     * If specified, takes precedence over host and port.
     */
    private String url;

    /**
     * Server host where local LLM is running (e.g. localhost, 127.0.0.1).
     */
    private String host = "localhost";

    /**
     * Server port where local LLM is running (e.g. 12345).
     */
    private int port = 12345;

    /**
     * Preferred model identifier (e.g. google/gemma-3-270m).
     */
    private String preferredModel = "google/gemma-3-270m";

    /**
     * Model selection strategy: "smart" (default), "first", or "preferred".
     */
    private String strategy = "smart";

    /**
     * HTTP request timeout in seconds.
     */
    private int timeoutSeconds = 30;

    /**
     * Temperature parameter for text generation.
     */
    private double temperature = 0.7;

    /**
     * Maximum tokens to generate in response.
     */
    private int maxTokens = 256;

    /**
     * System prompt instructing the model how to rewrite text.
     */
    private String systemPrompt = "Always reply with the user's message, but rewritten in a more funny style.";

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPreferredModel() {
        return preferredModel;
    }

    public void setPreferredModel(String preferredModel) {
        this.preferredModel = preferredModel;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    /**
     * Resolves the base URL (e.g. http://localhost:12345) stripped of endpoint paths.
     */
    public String getBaseUrl() {
        if (url != null && !url.isBlank()) {
            String clean = url.trim();
            if (clean.endsWith("/chat/completions")) {
                clean = clean.substring(0, clean.length() - "/chat/completions".length());
            }
            if (clean.endsWith("/v1")) {
                clean = clean.substring(0, clean.length() - "/v1".length());
            }
            while (clean.endsWith("/")) {
                clean = clean.substring(0, clean.length() - 1);
            }
            return clean;
        }

        String h = (host != null && !host.isBlank()) ? host.trim() : "localhost";
        if (!h.startsWith("http://") && !h.startsWith("https://")) {
            h = "http://" + h;
        }
        while (h.endsWith("/")) {
            h = h.substring(0, h.length() - 1);
        }
        return h + ":" + port;
    }

    /**
     * Resolves the /v1/chat/completions endpoint URL.
     */
    public String getChatCompletionsUrl() {
        return getBaseUrl() + "/v1/chat/completions";
    }

    /**
     * Resolves the /v1/models endpoint URL.
     */
    public String getModelsUrl() {
        return getBaseUrl() + "/v1/models";
    }
}
