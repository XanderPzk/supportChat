package com.supportchat.backend.chat.repo;

import com.supportchat.backend.chat.model.MessageEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    Optional<MessageEntity> findByConversationIdAndClientMsgId(String conversationId, String clientMsgId);

    Optional<MessageEntity> findTopByConversationIdOrderBySeqDesc(String conversationId);

    List<MessageEntity> findByConversationIdOrderBySeqAsc(String conversationId);

    List<MessageEntity> findByConversationIdAndSeqGreaterThanOrderBySeqAsc(String conversationId, long seq);
}
