package com.instakill.llm.domain;

import java.util.List;

public interface ModelSelectionStrategy {

    /**
     * Selects an appropriate model identifier from the list of available models.
     *
     * @param availableModels list of model IDs reported by the local LLM server
     * @param preferredModel  user/system-configured preferred model ID
     * @return the resolved model ID to use for chat completions
     */
    String selectModel(List<String> availableModels, String preferredModel);
}
