package com.supportchat.backend.chat.ws;

import com.supportchat.backend.chat.model.SenderRole;

public record SessionContext(
        String conversationId,
        SenderRole role,
        String clientId,
        String senderName
) {
}
