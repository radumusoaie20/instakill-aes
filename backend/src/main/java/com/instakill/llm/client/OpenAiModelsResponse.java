package com.instakill.llm.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiModelsResponse(
        String object,
        List<ModelData> data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelData(
            String id,
            String object
    ) {}
}
