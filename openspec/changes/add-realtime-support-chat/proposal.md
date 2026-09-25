# Propuesta: Chat de soporte en tiempo real

## Problema

Se necesita un chat de soporte mínimo viable donde dos participantes (cliente y agente)
puedan intercambiar mensajes sin recargar la página. El sistema debe funcionar en tiempo
real o casi real y manejar correctamente reconexiones.

El riesgo principal es el problema clásico de mensajería:

- Duplicados por reintentos/reconexión.
- Orden inconsistente cuando mensajes llegan casi al mismo tiempo.

## Alcance

Esta propuesta implementa:

1. Backend Spring Boot con transporte WebSocket y persistencia en H2.
2. Frontend React para dos roles (cliente y agente) con intercambio en vivo.
3. Protocolo con ACK, reintentos, sincronización por `lastSeq` y deduplicación.
4. Documentación de decisiones y trade-offs.

Fuera de alcance:

- Autenticación robusta.
- Adjuntos, rich text o indicadores avanzados de presencia.
- Escalamiento distribuido multi-nodo.

## Decisión principal

Se adopta entrega **al menos una vez** con efecto **exactamente una vez lógico**
mediante:

- `clientMsgId` generado por cliente para idempotencia.
- Restricción única `(conversation_id, client_msg_id)` en backend.
- Secuencia monotónica `seq` asignada por el servidor para orden total por conversación.
- `SYNC(lastSeq)` para recuperar mensajes faltantes al reconectar.

## Beneficios esperados

- Ambos participantes observan el mismo orden de mensajes.
- Reenvíos no generan mensajes duplicados visibles.
- Recuperación simple tras caídas temporales de red.
