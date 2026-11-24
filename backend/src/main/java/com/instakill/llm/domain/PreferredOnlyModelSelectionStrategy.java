package com.instakill.llm.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("preferredOnlyModelSelectionStrategy")
public class PreferredOnlyModelSelectionStrategy implements ModelSelectionStrategy {

    private static final Logger log = LoggerFactory.getLogger(PreferredOnlyModelSelectionStrategy.class);

    @Override
    public String selectModel(List<String> availableModels, String preferredModel) {
        log.info("Using configured preferred model directly: '{}'", preferredModel);
        return preferredModel;
    }
}
