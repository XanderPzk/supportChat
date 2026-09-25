package com.supportchat.backend.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "messages",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_conversation_client_msg", columnNames = {"conversation_id", "client_msg_id"}),
                @UniqueConstraint(name = "uk_conversation_seq", columnNames = {"conversation_id", "seq_num"})
        },
        indexes = {
                @Index(name = "idx_messages_conversation_seq", columnList = "conversation_id,seq_num")
        }
)
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    @Column(name = "client_msg_id", nullable = false, length = 128)
    private String clientMsgId;

    @Column(name = "seq_num", nullable = false)
    private long seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SenderRole senderRole;

    @Column(nullable = false, length = 128)
    private String senderName;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected MessageEntity() {
    }

    public MessageEntity(
            String conversationId,
            String clientMsgId,
            long seq,
            SenderRole senderRole,
            String senderName,
            String body
    ) {
        this.conversationId = conversationId;
        this.clientMsgId = clientMsgId;
        this.seq = seq;
        this.senderRole = senderRole;
        this.senderName = senderName;
        this.body = body;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public long getSeq() {
        return seq;
    }

    public SenderRole getSenderRole() {
        return senderRole;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
