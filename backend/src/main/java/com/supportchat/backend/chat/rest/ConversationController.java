package com.supportchat.backend.chat.rest;

import com.supportchat.backend.chat.api.ConversationPayload;
import com.supportchat.backend.chat.api.MessagePayload;
import com.supportchat.backend.chat.service.ChatService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ChatService chatService;

    public ConversationController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ConversationPayload createConversation() {
        return chatService.createConversation();
    }

    @GetMapping
    public List<ConversationPayload> listConversations() {
        return chatService.listConversations();
    }

    @GetMapping("/{conversationId}/messages")
    public List<MessagePayload> listMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") long sinceSeq
    ) {
        return chatService.listMessages(conversationId, sinceSeq);
    }
}
