export type SenderRole = 'CUSTOMER' | 'AGENT'

export type ConnectionState = 'connecting' | 'connected' | 'reconnecting' | 'disconnected'

export interface Conversation {
  id: string
  createdAt: string
}

export interface ChatMessage {
  id?: number
  conversationId: string
  clientMsgId: string
  seq: number
  senderRole: SenderRole
  senderName: string
  body: string
  createdAt: string
  pending?: boolean
}

export interface Envelope<T = unknown> {
  type: 'SEND' | 'ACK' | 'MESSAGE' | 'SYNC' | 'PRESENCE' | 'ERROR'
  payload: T
}

export interface AckPayload {
  conversationId: string
  clientMsgId: string
  seq: number
}

export interface ErrorPayload {
  code: string
  message: string
}
