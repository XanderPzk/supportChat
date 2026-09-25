package com.supportchat.backend.chat.api;

import com.supportchat.backend.chat.model.SenderRole;

public record PresencePayload(
        String conversationId,
        SenderRole role,
        String clientId,
        String senderName,
        String state
) {
}
