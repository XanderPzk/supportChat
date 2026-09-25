# SupportChat — chat de soporte en tiempo real

Prueba tecnica construida con:

- **Backend:** Java 21 + Spring Boot + WebSocket + JPA + H2
- **Frontend:** React + TypeScript + Vite + Tailwind + componentes estilo shadcn/ui
- **Metodologia:** OpenSpec (spec-driven development) versionada en el repositorio

## Estructura del repo

- `openspec/`: configuracion y propuesta de OpenSpec.
  - `openspec/project.md`
  - `openspec/changes/add-realtime-support-chat/`
- `backend/`: API REST + WebSocket en puerto `8642`.
- `frontend/`: cliente web en puerto `43127`.

## Requisitos

- Java 21
- Node.js 22+ (probado con Node 22)

## Como correr localmente

### 1) Backend

```bash
cd backend
./mvnw spring-boot:run
```

Servidor en `http://127.0.0.1:8642`.

### 2) Frontend

En otra terminal:

```bash
cd frontend
npm install
npm run dev
```

App en `http://127.0.0.1:43127`.

### Configuracion opcional del backend en frontend

El frontend resuelve REST y WebSocket desde una sola fuente:

- `VITE_BACKEND_HTTP_URL=http://127.0.0.1:8642` (default recomendado en local, conexion directa al backend)
- `VITE_BACKEND_HTTP_URL=same-origin` (usar mismo host/origen del frontend, util si se sirve detras de un reverse proxy)
- `VITE_BACKEND_HTTP_URL=http://127.0.0.1:43127` (modo proxy Vite para `/api` y `/ws`)

## Verificacion rapida

1. Abrir `http://127.0.0.1:43127`.
2. Crear una conversacion con **Nueva conversacion**.
3. Entrar como `CUSTOMER`.
4. Usar **Abrir vista del otro rol** para abrir otra pestaña como `AGENT`.
5. Enviar mensajes desde ambas pestañas y validar que:
   - aparecen sin recargar;
   - respetan el mismo orden (`seq`);
   - no se duplican aunque haya reconexion/reintento.

## Troubleshooting WebSocket

Si ves el error de navegador:
`WebSocket is closed before the connection is established`

1. Verifica que backend y frontend esten arriba:

```bash
curl -i http://127.0.0.1:8642/api/conversations
curl -i http://127.0.0.1:43127
```

2. Confirma que `VITE_BACKEND_HTTP_URL` apunta al backend correcto para tu modo de ejecucion.
3. Si usas proxy de Vite, usa:

```bash
cd frontend
VITE_BACKEND_HTTP_URL=http://127.0.0.1:43127 npm run dev -- --host 127.0.0.1 --port 43127
```

4. Si usas conexion directa (sin proxy), usa:

```bash
cd frontend
VITE_BACKEND_HTTP_URL=http://127.0.0.1:8642 npm run dev -- --host 127.0.0.1 --port 43127
```

## API y protocolo

### REST

- `POST /api/conversations`: crea conversacion.
- `GET /api/conversations`: lista conversaciones.
- `GET /api/conversations/{id}/messages?sinceSeq=N`: historial completo o incremental.

### WebSocket

Endpoint:

`/ws?conversationId=<id>&role=<CUSTOMER|AGENT>&clientId=<id>&senderName=<nombre>`

Tipos de sobre:

- `SEND`
- `ACK`
- `MESSAGE`
- `SYNC`
- `PRESENCE`
- `ERROR`

## Decisiones y trade-offs

### Problema clasico: orden y duplicados

Cuando hay reconexion o reintentos de red, el mismo mensaje puede llegar dos veces.
Cuando dos mensajes se publican casi al mismo tiempo, cada cliente puede ver distinto orden
si no existe una autoridad central.

### Solucion elegida

Se implemento **entrega al menos una vez** con consistencia de vista mediante:

1. **Idempotencia por `clientMsgId` en backend**
   - Cada mensaje enviado por cliente incluye `clientMsgId`.
   - Restriccion unica `(conversation_id, client_msg_id)`.
   - Si llega repetido, se retorna el mensaje canonico existente.

2. **Orden total por secuencia del servidor**
   - El backend asigna `seq` monotono por conversacion en una seccion critica por conversacion.
   - Restriccion unica `(conversation_id, seq_num)`.
   - El frontend renderiza por `seq`, no por timestamp local.

3. **Recuperacion tras reconexion con `SYNC(lastSeq)`**
   - El cliente pide solo `seq > lastSeq`.
   - El servidor hace replay incremental.
   - El cliente deduplica por `clientMsgId`.

### Trade-offs

- **A favor:** logica simple, robusta para MVP, facil de probar localmente.
- **En contra:** lock por conversacion prioriza consistencia sobre throughput maximo.
- **En contra:** H2 en memoria no preserva datos entre reinicios.
- **Posible evolucion:** mover persistencia a Postgres y/o usar broker para escalar a multi-nodo.

## OpenSpec (spec-driven)

La documentacion de diseño y decisiones esta en:

- `openspec/changes/add-realtime-support-chat/proposal.md`
- `openspec/changes/add-realtime-support-chat/design.md`
- `openspec/changes/add-realtime-support-chat/tasks.md`
- `openspec/changes/add-realtime-support-chat/specs/support-chat/spec.md`
