package com.instakill.llm.api.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FunnyfyRequest(
        String text,
        String content
) {
    /**
     * Resolves either 'text' or 'content' provided in the request payload.
     */
    public String resolveText() {
        if (text != null && !text.isBlank()) {
            return text.trim();
        }
        if (content != null && !content.isBlank()) {
            return content.trim();
        }
        return "";
    }
}
