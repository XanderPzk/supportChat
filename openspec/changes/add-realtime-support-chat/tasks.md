# Tareas: Chat de soporte en tiempo real

## 1. OpenSpec

- [x] Crear proposal con problema, alcance y decisión principal.
- [x] Crear diseño técnico con protocolo, modelo y casos borde.
- [x] Definir delta de spec para capacidades del chat.

## 2. Backend

- [ ] Crear proyecto Spring Boot con WebSocket/JPA/H2/Validation.
- [ ] Implementar entidades `Conversation` y `Message` + repositorios.
- [ ] Implementar servicio idempotente con `clientMsgId` y secuencia por conversación.
- [ ] Implementar handler WebSocket con sobres `SEND/ACK/MESSAGE/SYNC/PRESENCE/ERROR`.
- [ ] Exponer endpoints REST de apoyo para conversaciones e historial.
- [ ] Agregar tests de deduplicación, orden y replay.

## 3. Frontend

- [ ] Crear app React + TypeScript + Tailwind + shadcn/ui.
- [ ] Implementar selector de conversación y rol.
- [ ] Implementar chat en vivo con estado de conexión.
- [ ] Implementar outbox con reintentos y manejo de ACK.
- [ ] Implementar SYNC al reconectar y dedup por `clientMsgId`.

## 4. Documentación y validación

- [ ] Redactar README con instrucciones de ejecución local.
- [ ] Documentar sección "Decisiones y trade-offs".
- [ ] Validar ejecución backend/frontend y flujo en dos pestañas.
