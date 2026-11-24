package com.instakill.llm.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component("smartModelSelectionStrategy")
public class SmartFallbackModelSelectionStrategy implements ModelSelectionStrategy {

    private static final Logger log = LoggerFactory.getLogger(SmartFallbackModelSelectionStrategy.class);

    private static final List<String> NON_CHAT_KEYWORDS = List.of(
            "embed", "embedding", "rerank", "bge-", "nomic-embed"
    );

    @Override
    public String selectModel(List<String> availableModels, String preferredModel) {
        if (availableModels == null || availableModels.isEmpty()) {
            log.info("No models returned by LLM server. Using preferred model '{}' directly.", preferredModel);
            return preferredModel;
        }

        if (preferredModel != null && !preferredModel.isBlank()) {
            String preferredTrimmed = preferredModel.trim();

            // 1. Exact match (case-insensitive)
            for (String model : availableModels) {
                if (model.equalsIgnoreCase(preferredTrimmed)) {
                    log.info("Found exact match for preferred model '{}': using '{}'", preferredModel, model);
                    return model;
                }
            }

            // 2. Normalized match (e.g. "google.gemma-3-270m" vs "google/gemma-3-270m")
            String normalizedPref = normalize(preferredTrimmed);
            for (String model : availableModels) {
                if (normalize(model).equalsIgnoreCase(normalizedPref)) {
                    log.info("Found normalized match for preferred model '{}': using '{}'", preferredModel, model);
                    return model;
                }
            }

            // 3. Substring match by simple name (e.g. "gemma-3-270m")
            String simplePrefName = extractSimpleName(preferredTrimmed).toLowerCase(Locale.ROOT);
            for (String model : availableModels) {
                if (model.toLowerCase(Locale.ROOT).contains(simplePrefName)) {
                    log.info("Found partial/name match for preferred model '{}': using '{}'", preferredModel, model);
                    return model;
                }
            }
        }

        // 4. Fallback to first chat model (skipping embedding / reranking models)
        List<String> chatModels = availableModels.stream()
                .filter(this::isLikelyChatModel)
                .toList();

        if (!chatModels.isEmpty()) {
            String chosen = chatModels.get(0);
            log.warn("Preferred model '{}' not present in available models {}. Falling back to first chat model: '{}'",
                    preferredModel, availableModels, chosen);
            return chosen;
        }

        // 5. Fallback to the first available model
        String fallback = availableModels.get(0);
        log.warn("Preferred model '{}' not found and no clear chat model detected. Falling back to first model: '{}'",
                preferredModel, fallback);
        return fallback;
    }

    private boolean isLikelyChatModel(String modelId) {
        String lower = modelId.toLowerCase(Locale.ROOT);
        for (String keyword : NON_CHAT_KEYWORDS) {
            if (lower.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    private String normalize(String modelId) {
        return modelId.replace('/', '.').replace('\\', '.').trim();
    }

    private String extractSimpleName(String modelId) {
        int idx = Math.max(modelId.lastIndexOf('/'), modelId.lastIndexOf('.'));
        return idx >= 0 ? modelId.substring(idx + 1) : modelId;
    }
}
