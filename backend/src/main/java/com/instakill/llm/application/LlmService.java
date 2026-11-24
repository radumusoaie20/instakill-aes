package com.instakill.llm.application;

import com.instakill.llm.client.OpenAiChatRequest;
import com.instakill.llm.client.OpenAiChatResponse;
import com.instakill.llm.client.OpenAiMessage;
import com.instakill.llm.client.OpenAiModelsResponse;
import com.instakill.llm.config.LlmProperties;
import com.instakill.llm.domain.LlmServiceException;
import com.instakill.llm.domain.ModelSelectionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final RestClient restClient;
    private final LlmProperties properties;
    private final ModelSelectionStrategy modelSelectionStrategy;

    @Autowired
    public LlmService(
            RestClient.Builder restClientBuilder,
            LlmProperties properties,
            ModelSelectionStrategy modelSelectionStrategy
    ) {
        this(buildRestClient(restClientBuilder, properties), properties, modelSelectionStrategy);
    }

    public LlmService(
            RestClient restClient,
            LlmProperties properties,
            ModelSelectionStrategy modelSelectionStrategy
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.modelSelectionStrategy = modelSelectionStrategy;
    }

    private static RestClient buildRestClient(RestClient.Builder restClientBuilder, LlmProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Math.max(1000, properties.getTimeoutSeconds() * 1000);
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMillis));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMillis));
        return restClientBuilder.requestFactory(requestFactory).build();
    }

    /**
     * Retrieves the list of model identifiers currently exposed by the local LLM server.
     */
    public List<String> fetchAvailableModels() {
        String modelsUrl = properties.getModelsUrl();
        log.debug("Fetching available models from: {}", modelsUrl);
        try {
            OpenAiModelsResponse response = restClient.get()
                    .uri(modelsUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(OpenAiModelsResponse.class);

            if (response != null && response.data() != null) {
                List<String> modelIds = response.data().stream()
                        .map(OpenAiModelsResponse.ModelData::id)
                        .filter(id -> id != null && !id.isBlank())
                        .toList();
                log.debug("Found {} models: {}", modelIds.size(), modelIds);
                return modelIds;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Could not query models endpoint at {}: {}", modelsUrl, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Resolves which model to use using the configured strategy.
     */
    public String resolveModel() {
        List<String> availableModels = fetchAvailableModels();
        return modelSelectionStrategy.selectModel(availableModels, properties.getPreferredModel());
    }

    /**
     * Sends the text to the local LLM to make it funnier.
     *
     * @param text input text to be rewritten
     * @return FunnyfyResult containing rewritten text and the model used
     */
    public FunnyfyResult funnyfy(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text cannot be blank");
        }

        String chosenModel = resolveModel();
        String chatUrl = properties.getChatCompletionsUrl();
        log.info("Sending funnyfy request to {} using model '{}'", chatUrl, chosenModel);

        OpenAiChatRequest request = new OpenAiChatRequest(
                chosenModel,
                List.of(
                        new OpenAiMessage("system", properties.getSystemPrompt()),
                        new OpenAiMessage("user", text)
                ),
                properties.getTemperature(),
                properties.getMaxTokens(),
                false
        );

        try {
            OpenAiChatResponse response = restClient.post()
                    .uri(chatUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(OpenAiChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new LlmServiceException("Local LLM server returned an empty completion response");
            }

            OpenAiMessage message = response.choices().get(0).message();
            if (message == null || message.content() == null) {
                throw new LlmServiceException("Local LLM completion did not include message content");
            }

            String funnyText = message.content().trim();
            log.info("Successfully received funny rewrite (length: {})", funnyText.length());
            return new FunnyfyResult(funnyText, chosenModel);

        } catch (LlmServiceException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format(
                    "Failed to communicate with local LLM server at %s: %s. Please ensure LM Studio is running on port %d.",
                    chatUrl, e.getMessage(), properties.getPort()
            );
            log.error(errorMsg, e);
            throw new LlmServiceException(errorMsg, e);
        }
    }

    public record FunnyfyResult(String funnyText, String modelUsed) {}
}
