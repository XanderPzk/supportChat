# support-chat Specification Delta

## ADDED Requirements

### Requirement: Intercambio bidireccional en tiempo real

El sistema MUST permitir que cliente y agente envíen y reciban mensajes en la misma
conversación sin recargar la página.

#### Scenario: Envío básico entre dos participantes

- **WHEN** el cliente envía un mensaje válido
- **THEN** el agente lo recibe en la UI activa en tiempo real o casi real
- **AND** el mensaje queda disponible en historial de conversación

### Requirement: Orden consistente de mensajes

El sistema MUST asignar una secuencia monotónica por conversación para que ambos
participantes vean el mismo orden.

#### Scenario: Mensajes concurrentes

- **GIVEN** dos mensajes enviados casi simultáneamente
- **WHEN** el backend los procesa
- **THEN** ambos mensajes reciben `seq` único en la conversación
- **AND** los clientes renderizan ordenados por `seq`

### Requirement: Idempotencia ante reintentos

El sistema MUST evitar duplicados lógicos cuando se reenvía un mensaje con el mismo
identificador de cliente.

#### Scenario: Reenvío con mismo clientMsgId

- **GIVEN** un mensaje previamente procesado
- **WHEN** el cliente lo reenvía con igual `clientMsgId`
- **THEN** el backend no crea una segunda fila
- **AND** responde con el mismo `seq` canónico

### Requirement: Recuperación tras reconexión

El sistema MUST permitir sincronización incremental desde la última secuencia conocida.

#### Scenario: Replay de mensajes faltantes

- **GIVEN** que el cliente perdió conexión y conoce `lastSeq = N`
- **WHEN** reconecta y solicita `SYNC`
- **THEN** el backend retorna solo mensajes con `seq > N`
- **AND** la UI queda consistente sin duplicados visibles
