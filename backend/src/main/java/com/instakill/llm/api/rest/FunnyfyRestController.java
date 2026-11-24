package com.instakill.llm.api.rest;

import com.instakill.common.error.BadRequestException;
import com.instakill.llm.application.LlmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "LLM / Funnyfy", description = "Endpoints for LLM-powered text transformations")
public class FunnyfyRestController {

    private final LlmService llmService;

    public FunnyfyRestController(LlmService llmService) {
        this.llmService = llmService;
    }

    @PostMapping("/funnyfy")
    @Operation(summary = "Rewrite text in a funnier style using a local LLM")
    public FunnyfyResponse funnyfy(@RequestBody FunnyfyRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body cannot be null");
        }
        String input = request.resolveText();
        if (input.isBlank()) {
            throw new BadRequestException("Text to make funnier cannot be blank");
        }

        LlmService.FunnyfyResult result = llmService.funnyfy(input);
        return new FunnyfyResponse(
                result.funnyText(),
                result.funnyText(),
                input,
                result.modelUsed()
        );
    }
}
