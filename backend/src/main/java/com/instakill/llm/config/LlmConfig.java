package com.instakill.llm.config;

import com.instakill.llm.domain.FirstAvailableModelSelectionStrategy;
import com.instakill.llm.domain.ModelSelectionStrategy;
import com.instakill.llm.domain.PreferredOnlyModelSelectionStrategy;
import com.instakill.llm.domain.SmartFallbackModelSelectionStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class LlmConfig {

    @Bean
    @Primary
    public ModelSelectionStrategy activeModelSelectionStrategy(
            LlmProperties properties,
            SmartFallbackModelSelectionStrategy smartStrategy,
            FirstAvailableModelSelectionStrategy firstStrategy,
            PreferredOnlyModelSelectionStrategy preferredStrategy
    ) {
        String strategyName = properties.getStrategy() != null
                ? properties.getStrategy().trim().toLowerCase()
                : "smart";

        return switch (strategyName) {
            case "first", "first-available" -> firstStrategy;
            case "preferred", "preferred-only" -> preferredStrategy;
            default -> smartStrategy;
        };
    }
}
