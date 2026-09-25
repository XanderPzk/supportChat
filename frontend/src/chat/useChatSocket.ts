import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type {
  AckPayload,
  ChatMessage,
  ConnectionState,
  Envelope,
  ErrorPayload,
  SenderRole,
} from './types'
import { buildWebSocketUrl } from '../lib/backend-url'

interface UseChatSocketOptions {
  conversationId: string
  senderRole: SenderRole
  senderName: string
}

interface PendingOutbound {
  clientMsgId: string
  body: string
  senderName: string
  senderRole: SenderRole
}

const MAX_BACKOFF_MS = 10_000
const OUTBOX_NAMESPACE = 'supportchat:outbox'

function wsUrl(conversationId: string, senderRole: SenderRole, senderName: string) {
  const query = new URLSearchParams({
    conversationId,
    role: senderRole,
    senderName,
    clientId: `${senderRole.toLowerCase()}-${senderName.toLowerCase().replace(/\s+/g, '-')}`,
  })
  return buildWebSocketUrl('/ws', query)
}

function dedupAndSort(messages: ChatMessage[]) {
  const byClientId = new Map<string, ChatMessage>()
  for (const message of messages) {
    const existing = byClientId.get(message.clientMsgId)
    if (!existing || existing.pending) {
      byClientId.set(message.clientMsgId, message)
    }
  }
  return [...byClientId.values()].sort((a, b) => a.seq - b.seq)
}

export function useChatSocket({
  conversationId,
  senderRole,
  senderName,
}: UseChatSocketOptions) {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [connectionState, setConnectionState] = useState<ConnectionState>('connecting')
  const [lastError, setLastError] = useState<string | null>(null)
  const [startupIssue, setStartupIssue] = useState<string | null>(null)
  const wsRef = useRef<WebSocket | null>(null)
  const reconnectAttemptsRef = useRef(0)
  const reconnectTimerRef = useRef<number | null>(null)
  const unmountedRef = useRef(false)
  const messagesRef = useRef<ChatMessage[]>([])
  const hasConnectedOnceRef = useRef(false)

  const outboxKey = useMemo(
    () => `${OUTBOX_NAMESPACE}:${conversationId}:${senderRole}:${senderName}`,
    [conversationId, senderRole, senderName],
  )
  const outboxRef = useRef<Map<string, PendingOutbound>>(new Map())

  const persistOutbox = useCallback(() => {
    const list = [...outboxRef.current.values()]
    localStorage.setItem(outboxKey, JSON.stringify(list))
  }, [outboxKey])

  const loadOutbox = useCallback(() => {
    const raw = localStorage.getItem(outboxKey)
    if (!raw) return
    try {
      const parsed = JSON.parse(raw) as PendingOutbound[]
      outboxRef.current = new Map(parsed.map((item) => [item.clientMsgId, item]))
    } catch {
      outboxRef.current = new Map()
    }
  }, [outboxKey])

  const sendEnvelope = useCallback((envelope: Envelope) => {
    const ws = wsRef.current
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      return false
    }
    ws.send(JSON.stringify(envelope))
    return true
  }, [])

  const handleAck = useCallback(
    (payload: AckPayload) => {
      outboxRef.current.delete(payload.clientMsgId)
      persistOutbox()
      setMessages((prev) => {
        const next = prev.map((message) =>
          message.clientMsgId === payload.clientMsgId
            ? { ...message, pending: false, seq: payload.seq }
            : message,
        )
        messagesRef.current = next
        return next
      })
    },
    [persistOutbox],
  )

  const handleMessage = useCallback((message: ChatMessage) => {
    setMessages((prev) => {
      const next = dedupAndSort([...prev, { ...message, pending: false }])
      messagesRef.current = next
      return next
    })
  }, [])

  const replayMissing = useCallback(() => {
    const lastSeq = messagesRef.current.reduce(
      (max, msg) => (!msg.pending && msg.seq > max ? msg.seq : max),
      0,
    )
    sendEnvelope({ type: 'SYNC', payload: { lastSeq } })
  }, [sendEnvelope])

  const flushOutbox = useCallback(() => {
    for (const pending of outboxRef.current.values()) {
      sendEnvelope({
        type: 'SEND',
        payload: {
          clientMsgId: pending.clientMsgId,
          body: pending.body,
          senderRole: pending.senderRole,
          senderName: pending.senderName,
        },
      })
    }
  }, [sendEnvelope])

  const connect = useCallback(() => {
    if (unmountedRef.current) {
      return
    }
    setConnectionState((prev) => (prev === 'connected' ? prev : 'connecting'))
    const ws = new WebSocket(wsUrl(conversationId, senderRole, senderName))
    wsRef.current = ws

    ws.onopen = () => {
      reconnectAttemptsRef.current = 0
      hasConnectedOnceRef.current = true
      setConnectionState('connected')
      setLastError(null)
      setStartupIssue(null)
      replayMissing()
      flushOutbox()
    }

    ws.onmessage = (event) => {
      try {
        const envelope = JSON.parse(event.data) as Envelope
        if (envelope.type === 'ACK') {
          handleAck(envelope.payload as AckPayload)
        } else if (envelope.type === 'MESSAGE') {
          handleMessage(envelope.payload as ChatMessage)
        } else if (envelope.type === 'ERROR') {
          const err = envelope.payload as ErrorPayload
          setLastError(err.message)
        }
      } catch {
        setLastError('No se pudo interpretar un mensaje del servidor.')
      }
    }

    ws.onclose = () => {
      if (unmountedRef.current) {
        return
      }
      if (!hasConnectedOnceRef.current) {
        setStartupIssue(
          'No se pudo abrir el WebSocket. Verifica que el backend este activo en 8642 o configura VITE_BACKEND_HTTP_URL correctamente.',
        )
      }
      const attempt = reconnectAttemptsRef.current + 1
      reconnectAttemptsRef.current = attempt
      setConnectionState('reconnecting')
      const timeout = Math.min(1000 * 2 ** (attempt - 1), MAX_BACKOFF_MS)
      reconnectTimerRef.current = window.setTimeout(connect, timeout)
    }

    ws.onerror = () => {
      setLastError('Conexion inestable; intentando reconectar...')
      ws.close()
    }
  }, [conversationId, senderRole, senderName, replayMissing, flushOutbox, handleAck, handleMessage])

  useEffect(() => {
    // React StrictMode invokes effects twice in dev; reset this flag so the
    // second (real) mount can still establish the socket connection.
    unmountedRef.current = false
    loadOutbox()
    connect()
    return () => {
      unmountedRef.current = true
      if (reconnectTimerRef.current) {
        window.clearTimeout(reconnectTimerRef.current)
      }
      wsRef.current?.close()
      setConnectionState('disconnected')
    }
  }, [connect, loadOutbox])

  const sendMessage = useCallback(
    (body: string) => {
      const trimmed = body.trim()
      if (!trimmed) return
      const clientMsgId = crypto.randomUUID()
      const pending: PendingOutbound = { clientMsgId, body: trimmed, senderName, senderRole }
      outboxRef.current.set(clientMsgId, pending)
      persistOutbox()

      setMessages((prev) =>
        {
          const next = dedupAndSort([
          ...prev,
          {
            conversationId,
            clientMsgId,
            senderRole,
            senderName,
            body: trimmed,
            createdAt: new Date().toISOString(),
            pending: true,
            seq: Number.MAX_SAFE_INTEGER,
          },
          ])
          messagesRef.current = next
          return next
        },
      )

      sendEnvelope({
        type: 'SEND',
        payload: {
          clientMsgId,
          body: trimmed,
          senderRole,
          senderName,
        },
      })
    },
    [conversationId, persistOutbox, sendEnvelope, senderName, senderRole],
  )

  return {
    messages,
    connectionState,
    lastError,
    startupIssue,
    sendMessage,
  }
}
