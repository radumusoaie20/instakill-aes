package com.instakill.llm;

import com.instakill.llm.config.LlmProperties;
import com.instakill.llm.domain.FirstAvailableModelSelectionStrategy;
import com.instakill.llm.domain.PreferredOnlyModelSelectionStrategy;
import com.instakill.llm.domain.SmartFallbackModelSelectionStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SmartFallbackModelSelectionStrategyTest {

    private final SmartFallbackModelSelectionStrategy smartStrategy = new SmartFallbackModelSelectionStrategy();
    private final FirstAvailableModelSelectionStrategy firstStrategy = new FirstAvailableModelSelectionStrategy();
    private final PreferredOnlyModelSelectionStrategy preferredStrategy = new PreferredOnlyModelSelectionStrategy();

    @Test
    @DisplayName("Should select exact matching model when present in available models")
    void selectExactMatch() {
        List<String> models = List.of("mistralai/codestral-22b-v0.1", "google/gemma-3-270m", "nomic-embed");
        String result = smartStrategy.selectModel(models, "google/gemma-3-270m");
        assertThat(result).isEqualTo("google/gemma-3-270m");
    }

    @Test
    @DisplayName("Should select normalized match when dot vs slash differs")
    void selectNormalizedMatch() {
        List<String> models = List.of("mistralai/codestral-22b-v0.1", "google.gemma-3-270m");
        String result = smartStrategy.selectModel(models, "google/gemma-3-270m");
        assertThat(result).isEqualTo("google.gemma-3-270m");
    }

    @Test
    @DisplayName("Should select partial match by model family or name")
    void selectPartialMatch() {
        List<String> models = List.of("mistralai/codestral-22b-v0.1", "lmstudio-community/gemma-3-270m-it-GGUF");
        String result = smartStrategy.selectModel(models, "gemma-3-270m");
        assertThat(result).isEqualTo("lmstudio-community/gemma-3-270m-it-GGUF");
    }

    @Test
    @DisplayName("Should fallback to first non-embedding chat model when preferred model is not available")
    void fallbackToFirstChatModel() {
        List<String> models = List.of("text-embedding-nomic-embed-text-v1.5", "bge-large-en", "mistralai/codestral-22b-v0.1");
        String result = smartStrategy.selectModel(models, "google/gemma-3-270m");
        assertThat(result).isEqualTo("mistralai/codestral-22b-v0.1");
    }

    @Test
    @DisplayName("Should fallback to first model when only embedding models are available")
    void fallbackToFirstModelWhenOnlyEmbeddings() {
        List<String> models = List.of("text-embedding-nomic-embed-text-v1.5", "bge-large-en");
        String result = smartStrategy.selectModel(models, "google/gemma-3-270m");
        assertThat(result).isEqualTo("text-embedding-nomic-embed-text-v1.5");
    }

    @Test
    @DisplayName("Should return preferred model if available models list is empty or null")
    void fallbackToPreferredWhenNoModels() {
        assertThat(smartStrategy.selectModel(List.of(), "google/gemma-3-270m")).isEqualTo("google/gemma-3-270m");
        assertThat(smartStrategy.selectModel(null, "google/gemma-3-270m")).isEqualTo("google/gemma-3-270m");
    }

    @Test
    @DisplayName("FirstAvailable strategy should select first available model")
    void firstAvailableStrategy() {
        List<String> models = List.of("model-a", "model-b");
        assertThat(firstStrategy.selectModel(models, "preferred-x")).isEqualTo("model-a");
        assertThat(firstStrategy.selectModel(List.of(), "preferred-x")).isEqualTo("preferred-x");
    }

    @Test
    @DisplayName("PreferredOnly strategy should always return preferred model")
    void preferredOnlyStrategy() {
        List<String> models = List.of("model-a", "model-b");
        assertThat(preferredStrategy.selectModel(models, "my-preferred")).isEqualTo("my-preferred");
    }

    @Test
    @DisplayName("LlmProperties should correctly parse and format URLs")
    void llmPropertiesUrlResolution() {
        LlmProperties props = new LlmProperties();
        props.setHost("127.0.0.1");
        props.setPort(12345);

        assertThat(props.getBaseUrl()).isEqualTo("http://127.0.0.1:12345");
        assertThat(props.getChatCompletionsUrl()).isEqualTo("http://127.0.0.1:12345/v1/chat/completions");
        assertThat(props.getModelsUrl()).isEqualTo("http://127.0.0.1:12345/v1/models");

        // When full URL is supplied
        props.setUrl("http://remote-server:9999/v1/chat/completions");
        assertThat(props.getBaseUrl()).isEqualTo("http://remote-server:9999");
        assertThat(props.getChatCompletionsUrl()).isEqualTo("http://remote-server:9999/v1/chat/completions");
        assertThat(props.getModelsUrl()).isEqualTo("http://remote-server:9999/v1/models");
    }
}
