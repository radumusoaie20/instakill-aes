package com.instakill.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instakill.llm.application.LlmService;
import com.instakill.llm.client.OpenAiChatResponse;
import com.instakill.llm.client.OpenAiMessage;
import com.instakill.llm.client.OpenAiModelsResponse;
import com.instakill.llm.config.LlmProperties;
import com.instakill.llm.domain.LlmServiceException;
import com.instakill.llm.domain.SmartFallbackModelSelectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LlmServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LlmProperties properties;
    private MockRestServiceServer mockServer;
    private LlmService llmService;

    @BeforeEach
    void setUp() {
        properties = new LlmProperties();
        properties.setHost("localhost");
        properties.setPort(12345);
        properties.setPreferredModel("google/gemma-3-270m");
        properties.setTimeoutSeconds(5);

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        SmartFallbackModelSelectionStrategy strategy = new SmartFallbackModelSelectionStrategy();

        llmService = new LlmService(builder.build(), properties, strategy);
    }

    @Test
    @DisplayName("Should successfully fetch models and perform funny completion")
    void testSuccessfulFunnyfy() throws Exception {
        // Mock GET /v1/models
        OpenAiModelsResponse modelsResponse = new OpenAiModelsResponse(
                "list",
                List.of(
                        new OpenAiModelsResponse.ModelData("google/gemma-3-270m", "model"),
                        new OpenAiModelsResponse.ModelData("mistralai/codestral-22b-v0.1", "model")
                )
        );

        mockServer.expect(requestTo("http://localhost:12345/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(modelsResponse), MediaType.APPLICATION_JSON));

        // Mock POST /v1/chat/completions
        OpenAiChatResponse chatResponse = new OpenAiChatResponse(
                "chatcmpl-123",
                "google/gemma-3-270m",
                List.of(new OpenAiChatResponse.Choice(0, new OpenAiMessage("assistant", "Why did the developer go broke?"), "stop"))
        );

        mockServer.expect(requestTo("http://localhost:12345/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(chatResponse), MediaType.APPLICATION_JSON));

        LlmService.FunnyfyResult result = llmService.funnyfy("I wrote some code today.");

        assertThat(result.funnyText()).isEqualTo("Why did the developer go broke?");
        assertThat(result.modelUsed()).isEqualTo("google/gemma-3-270m");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw LlmServiceException when chat completions returns 500 error")
    void testChatCompletionsError() throws Exception {
        // Mock GET /v1/models
        OpenAiModelsResponse modelsResponse = new OpenAiModelsResponse(
                "list",
                List.of(new OpenAiModelsResponse.ModelData("google/gemma-3-270m", "model"))
        );

        mockServer.expect(requestTo("http://localhost:12345/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(modelsResponse), MediaType.APPLICATION_JSON));

        // Mock POST /v1/chat/completions failure
        mockServer.expect(requestTo("http://localhost:12345/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> llmService.funnyfy("Test post"))
                .isInstanceOf(LlmServiceException.class)
                .hasMessageContaining("Failed to communicate with local LLM server");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when input text is blank")
    void testBlankInput() {
        assertThatThrownBy(() -> llmService.funnyfy("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
