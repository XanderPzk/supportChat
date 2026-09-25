package com.supportchat.backend.chat.repo;

import com.supportchat.backend.chat.model.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {
}
