package com.supportchat.backend.chat.api;

import java.time.Instant;

public record ConversationPayload(
        String id,
        Instant createdAt
) {
}
