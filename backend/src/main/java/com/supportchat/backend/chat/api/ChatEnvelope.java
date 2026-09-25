package com.supportchat.backend.chat.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatEnvelope(
        String type,
        Object payload
) {
}
