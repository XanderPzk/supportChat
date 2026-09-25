# Diseño: Chat de soporte en tiempo real

## Arquitectura

- Frontend (React + TypeScript) se conecta por WebSocket al backend.
- Backend (Spring Boot) persiste mensajes en H2 vía JPA.
- El backend mantiene el estado canónico de orden (`seq`) por conversación.

## Modelo de datos

### Conversation

- `id` (UUID string)
- `createdAt`

### Message

- `id` (long autoincremental)
- `conversationId` (FK lógica)
- `clientMsgId` (UUID string generado por frontend)
- `seq` (long monotónico por conversación)
- `senderRole` (`CUSTOMER` o `AGENT`)
- `senderName`
- `body`
- `createdAt`

Restricciones:

- `unique(conversation_id, client_msg_id)` para idempotencia.
- `unique(conversation_id, seq)` para preservar orden total.

## Protocolo WebSocket

Sobres JSON:

- `SEND`: cliente -> servidor para publicar mensaje.
- `ACK`: servidor -> emisor confirma recepción canónica (`clientMsgId`, `seq`).
- `MESSAGE`: servidor -> participantes con payload definitivo.
- `SYNC`: cliente -> servidor con `lastSeq` para replay.
- `PRESENCE`: servidor notifica entrada/salida.
- `ERROR`: servidor notifica error recuperable.

## Flujo de envío

1. Cliente envía `SEND` con `clientMsgId`.
2. Servidor procesa en sección crítica por conversación.
3. Si ya existe `(conversationId, clientMsgId)`, retorna mensaje existente.
4. Si no existe, calcula `nextSeq`, persiste y confirma.
5. Servidor envía `ACK` al emisor y `MESSAGE` a todas las sesiones de la conversación.

## Flujo de reconexión

1. Cliente reconecta WebSocket.
2. Cliente envía `SYNC` con `lastSeq` local.
3. Servidor responde con todos los mensajes con `seq > lastSeq`.
4. Cliente deduplica por `clientMsgId` y ordena por `seq`.

## Casos borde considerados

- Reintento del mismo mensaje por timeout de ACK.
- Mensajes concurrentes de emisor distinto.
- Caída y reconexión de una pestaña con backlog pendiente.
- Doble entrega de `MESSAGE` por reconexión.

## Trade-offs

- H2 en memoria simplifica la prueba técnica, pero no persiste entre reinicios.
- Lock por conversación favorece orden consistente sobre throughput máximo.
- Sin broker externo (Kafka/Rabbit) para mantener complejidad baja del MVP.
