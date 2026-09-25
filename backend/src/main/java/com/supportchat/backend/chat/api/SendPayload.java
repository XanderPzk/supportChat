package com.supportchat.backend.chat.api;

import com.supportchat.backend.chat.model.SenderRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendPayload(
        @NotBlank String clientMsgId,
        @NotBlank String body,
        @NotNull SenderRole senderRole,
        @NotBlank String senderName
) {
}
