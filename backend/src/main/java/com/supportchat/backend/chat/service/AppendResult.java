package com.supportchat.backend.chat.service;

import com.supportchat.backend.chat.model.MessageEntity;

public record AppendResult(
        MessageEntity message,
        boolean created
) {
}
