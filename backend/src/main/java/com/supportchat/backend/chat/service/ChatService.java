package com.supportchat.backend.chat.service;

import com.supportchat.backend.chat.api.ConversationPayload;
import com.supportchat.backend.chat.api.MessagePayload;
import com.supportchat.backend.chat.api.SendPayload;
import com.supportchat.backend.chat.model.ConversationEntity;
import com.supportchat.backend.chat.model.MessageEntity;
import com.supportchat.backend.chat.repo.ConversationRepository;
import com.supportchat.backend.chat.repo.MessageRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConcurrentMap<String, ReentrantLock> conversationLocks = new ConcurrentHashMap<>();

    public ChatService(ConversationRepository conversationRepository, MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public ConversationPayload createConversation() {
        ConversationEntity conversation = new ConversationEntity(UUID.randomUUID().toString());
        ConversationEntity saved = conversationRepository.save(conversation);
        return toConversationPayload(saved);
    }

    public List<ConversationPayload> listConversations() {
        return conversationRepository.findAll()
                .stream()
                .map(this::toConversationPayload)
                .toList();
    }

    @Transactional
    public AppendResult appendMessage(String conversationId, SendPayload payload) {
        ReentrantLock lock = conversationLocks.computeIfAbsent(conversationId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            ensureConversation(conversationId);

            MessageEntity existing = messageRepository
                    .findByConversationIdAndClientMsgId(conversationId, payload.clientMsgId())
                    .orElse(null);
            if (existing != null) {
                return new AppendResult(existing, false);
            }

            long nextSeq = messageRepository
                    .findTopByConversationIdOrderBySeqDesc(conversationId)
                    .map(MessageEntity::getSeq)
                    .orElse(0L) + 1L;

            MessageEntity saved = messageRepository.save(new MessageEntity(
                    conversationId,
                    payload.clientMsgId(),
                    nextSeq,
                    payload.senderRole(),
                    payload.senderName(),
                    payload.body()
            ));
            return new AppendResult(saved, true);
        } finally {
            lock.unlock();
        }
    }

    public List<MessagePayload> listMessages(String conversationId, long sinceSeq) {
        ensureConversation(conversationId);
        List<MessageEntity> messages = sinceSeq <= 0
                ? messageRepository.findByConversationIdOrderBySeqAsc(conversationId)
                : messageRepository.findByConversationIdAndSeqGreaterThanOrderBySeqAsc(conversationId, sinceSeq);
        return messages.stream().map(ChatService::toMessagePayload).toList();
    }

    public static MessagePayload toMessagePayload(MessageEntity message) {
        return new MessagePayload(
                message.getId(),
                message.getConversationId(),
                message.getClientMsgId(),
                message.getSeq(),
                message.getSenderRole(),
                message.getSenderName(),
                message.getBody(),
                message.getCreatedAt()
        );
    }

    private void ensureConversation(String conversationId) {
        if (!conversationRepository.existsById(conversationId)) {
            conversationRepository.save(new ConversationEntity(conversationId));
        }
    }

    private ConversationPayload toConversationPayload(ConversationEntity entity) {
        return new ConversationPayload(entity.getId(), entity.getCreatedAt());
    }
}
