package com.instakill.llm.api.rest;

public record FunnyfyResponse(
        String funnyText,
        String text,
        String originalText,
        String model
) {}
