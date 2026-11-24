package com.instakill.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instakill.infrastructure.security.JwtTokenService;
import com.instakill.llm.api.rest.FunnyfyRequest;
import com.instakill.llm.api.rest.FunnyfyRestController;
import com.instakill.llm.application.LlmService;
import com.instakill.llm.domain.LlmServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FunnyfyRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class FunnyfyRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LlmService llmService;

    @MockBean
    private JwtTokenService tokenService;

    @Test
    @DisplayName("POST /api/funnyfy should return funnier version of text")
    void testFunnyfySuccess() throws Exception {
        when(llmService.funnyfy(eq("Hello world")))
                .thenReturn(new LlmService.FunnyfyResult("Hello goofy world! 😂", "google/gemma-3-270m"));

        FunnyfyRequest request = new FunnyfyRequest("Hello world", null);

        mockMvc.perform(post("/api/funnyfy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.funnyText").value("Hello goofy world! 😂"))
                .andExpect(jsonPath("$.text").value("Hello goofy world! 😂"))
                .andExpect(jsonPath("$.originalText").value("Hello world"))
                .andExpect(jsonPath("$.model").value("google/gemma-3-270m"));
    }

    @Test
    @DisplayName("POST /api/funnyfy with empty text should return 400 Bad Request")
    void testFunnyfyEmptyText() throws Exception {
        FunnyfyRequest request = new FunnyfyRequest("   ", "");

        mockMvc.perform(post("/api/funnyfy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/funnyfy when LLM server is down should return 503 Service Unavailable")
    void testFunnyfyServiceUnavailable() throws Exception {
        when(llmService.funnyfy(eq("Hello world")))
                .thenThrow(new LlmServiceException("Failed to communicate with local LLM server"));

        FunnyfyRequest request = new FunnyfyRequest("Hello world", null);

        mockMvc.perform(post("/api/funnyfy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("LLM Service Unavailable"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Failed to communicate with local LLM server")));
    }
}
