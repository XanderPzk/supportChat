package com.supportchat.backend.chat.api;

import com.supportchat.backend.chat.model.SenderRole;
import java.time.Instant;

public record MessagePayload(
        Long id,
        String conversationId,
        String clientMsgId,
        long seq,
        SenderRole senderRole,
        String senderName,
        String body,
        Instant createdAt
) {
}
