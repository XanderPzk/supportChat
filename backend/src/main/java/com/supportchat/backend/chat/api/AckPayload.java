package com.supportchat.backend.chat.api;

public record AckPayload(
        String conversationId,
        String clientMsgId,
        long seq
) {
}
