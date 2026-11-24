package com.instakill.llm.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("firstAvailableModelSelectionStrategy")
public class FirstAvailableModelSelectionStrategy implements ModelSelectionStrategy {

    private static final Logger log = LoggerFactory.getLogger(FirstAvailableModelSelectionStrategy.class);

    @Override
    public String selectModel(List<String> availableModels, String preferredModel) {
        if (availableModels != null && !availableModels.isEmpty()) {
            String selected = availableModels.get(0);
            log.info("Selected first available model: '{}'", selected);
            return selected;
        }
        log.info("No available models found; falling back to preferred model: '{}'", preferredModel);
        return preferredModel;
    }
}
