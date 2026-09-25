package com.supportchat.backend.chat.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportchat.backend.chat.api.AckPayload;
import com.supportchat.backend.chat.api.ChatEnvelope;
import com.supportchat.backend.chat.api.ErrorPayload;
import com.supportchat.backend.chat.api.MessagePayload;
import com.supportchat.backend.chat.api.PresencePayload;
import com.supportchat.backend.chat.api.SendPayload;
import com.supportchat.backend.chat.api.SyncPayload;
import com.supportchat.backend.chat.model.SenderRole;
import com.supportchat.backend.chat.service.AppendResult;
import com.supportchat.backend.chat.service.ChatService;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final Map<String, Set<WebSocketSession>> sessionsByConversation = new ConcurrentHashMap<>();
    private final Map<String, SessionContext> sessionContexts = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ObjectMapper objectMapper, ChatService chatService) {
        this.objectMapper = objectMapper;
        this.chatService = chatService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        SessionContext context = parseSessionContext(session.getUri());
        sessionContexts.put(session.getId(), context);
        sessionsByConversation
                .computeIfAbsent(context.conversationId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(session);

        broadcast(
                context.conversationId(),
                new ChatEnvelope("PRESENCE", new PresencePayload(
                        context.conversationId(),
                        context.role(),
                        context.clientId(),
                        context.senderName(),
                        "JOINED"
                ))
        );
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SessionContext context = sessionContexts.get(session.getId());
        if (context == null) {
            sendEnvelope(session, new ChatEnvelope("ERROR", new ErrorPayload("NO_CONTEXT", "Sesion sin contexto.")));
            return;
        }

        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText("");
        JsonNode payloadNode = root.path("payload");

        switch (type) {
            case "SEND" -> handleSend(session, context, payloadNode);
            case "SYNC" -> handleSync(session, context, payloadNode);
            default -> sendEnvelope(session, new ChatEnvelope("ERROR", new ErrorPayload("UNSUPPORTED_TYPE", "Tipo no soportado.")));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sendEnvelope(session, new ChatEnvelope("ERROR", new ErrorPayload("TRANSPORT_ERROR", exception.getMessage())));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        SessionContext context = sessionContexts.remove(session.getId());
        if (context == null) {
            return;
        }
        Set<WebSocketSession> sessions = sessionsByConversation.getOrDefault(context.conversationId(), Set.of());
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByConversation.remove(context.conversationId());
        }
        broadcast(
                context.conversationId(),
                new ChatEnvelope("PRESENCE", new PresencePayload(
                        context.conversationId(),
                        context.role(),
                        context.clientId(),
                        context.senderName(),
                        "LEFT"
                ))
        );
    }

    private void handleSend(WebSocketSession senderSession, SessionContext context, JsonNode payloadNode) throws Exception {
        SendPayload payload = objectMapper.treeToValue(payloadNode, SendPayload.class);
        SendPayload normalized = new SendPayload(
                payload.clientMsgId(),
                payload.body(),
                context.role(),
                context.senderName()
        );

        AppendResult appendResult = chatService.appendMessage(context.conversationId(), normalized);
        MessagePayload messagePayload = ChatService.toMessagePayload(appendResult.message());

        sendEnvelope(senderSession, new ChatEnvelope(
                "ACK",
                new AckPayload(context.conversationId(), appendResult.message().getClientMsgId(), appendResult.message().getSeq())
        ));

        // Reenviamos tambien en reintentos para mantener semantica al-menos-una-vez.
        broadcast(context.conversationId(), new ChatEnvelope("MESSAGE", messagePayload));
    }

    private void handleSync(WebSocketSession session, SessionContext context, JsonNode payloadNode) throws Exception {
        SyncPayload payload = objectMapper.treeToValue(payloadNode, SyncPayload.class);
        long lastSeq = payload.lastSeq() == null ? 0L : payload.lastSeq();
        for (MessagePayload message : chatService.listMessages(context.conversationId(), lastSeq)) {
            sendEnvelope(session, new ChatEnvelope("MESSAGE", message));
        }
    }

    private void broadcast(String conversationId, ChatEnvelope envelope) throws Exception {
        Set<WebSocketSession> sessions = sessionsByConversation.getOrDefault(conversationId, Set.of());
        for (WebSocketSession peer : sessions) {
            sendEnvelope(peer, envelope);
        }
    }

    private void sendEnvelope(WebSocketSession session, ChatEnvelope envelope) throws IOException {
        synchronized (session) {
            if (!session.isOpen()) {
                return;
            }
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
        }
    }

    private SessionContext parseSessionContext(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("URI de conexion requerida");
        }
        MultiValueMap<String, String> queryParams = UriComponentsBuilder.fromUri(uri).build().getQueryParams();

        String conversationId = queryParams.getFirst("conversationId");
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId es requerido");
        }

        String roleRaw = queryParams.getFirst("role");
        SenderRole role = roleRaw == null
                ? SenderRole.CUSTOMER
                : SenderRole.valueOf(roleRaw.trim().toUpperCase());

        String clientId = defaulted(queryParams.getFirst("clientId"), UUID.randomUUID().toString());
        String senderName = defaulted(queryParams.getFirst("senderName"), role.name());

        return new SessionContext(conversationId, role, clientId, senderName);
    }

    private String defaulted(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
