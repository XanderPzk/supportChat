package com.supportchat.backend.chat.api;

public record ErrorPayload(
        String code,
        String message
) {
}
