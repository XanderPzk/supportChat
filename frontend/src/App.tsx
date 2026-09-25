import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useChatSocket } from './chat/useChatSocket'
import type {
  ConnectionState,
  Conversation,
  SenderRole,
} from './chat/types'
import { Badge } from './components/ui/badge'
import { Button } from './components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from './components/ui/card'
import { Input } from './components/ui/input'
import { buildApiUrl, getBackendHttpBase } from './lib/backend-url'

interface Session {
  conversationId: string
  senderName: string
  senderRole: SenderRole
}

function connectionBadgeVariant(state: ConnectionState) {
  if (state === 'connected') return 'success'
  if (state === 'reconnecting') return 'warning'
  if (state === 'disconnected') return 'danger'
  return 'secondary'
}

function ChatRoom({ session }: { session: Session }) {
  const [draft, setDraft] = useState('')
  const { connectionState, messages, lastError, startupIssue, sendMessage } = useChatSocket({
    conversationId: session.conversationId,
    senderRole: session.senderRole,
    senderName: session.senderName,
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    sendMessage(draft)
    setDraft('')
  }

  return (
    <Card className="h-full">
      <CardHeader className="border-b border-slate-100">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <CardTitle>Conversacion {session.conversationId.slice(0, 8)}</CardTitle>
            <CardDescription>
              Rol: {session.senderRole} | Participante: {session.senderName}
            </CardDescription>
          </div>
          <Badge variant={connectionBadgeVariant(connectionState)}>
            {connectionState === 'connected'
              ? 'Conectado'
              : connectionState === 'reconnecting'
                ? 'Reconectando'
                : connectionState === 'connecting'
                  ? 'Conectando'
                  : 'Desconectado'}
          </Badge>
        </div>
        {lastError ? <p className="text-sm text-amber-700">{lastError}</p> : null}
        {startupIssue ? (
          <p className="rounded border border-amber-200 bg-amber-50 p-2 text-sm text-amber-800">
            {startupIssue}
          </p>
        ) : null}
      </CardHeader>
      <CardContent className="flex h-[70vh] flex-col gap-4 p-4">
        <div className="flex-1 space-y-2 overflow-y-auto rounded-md border border-slate-200 bg-slate-50 p-3">
          {messages.length === 0 ? (
            <p className="text-sm text-slate-500">
              Aun no hay mensajes. Escribe uno para comenzar.
            </p>
          ) : (
            messages.map((message) => {
              const mine = message.senderRole === session.senderRole
              return (
                <div
                  key={`${message.clientMsgId}-${message.seq}`}
                  className={`max-w-[85%] rounded-lg px-3 py-2 text-sm shadow-sm ${
                    mine
                      ? 'ml-auto bg-slate-900 text-white'
                      : 'bg-white text-slate-900'
                  }`}
                >
                  <p className="mb-1 text-xs opacity-80">
                    {message.senderName} {message.pending ? '· pendiente' : `· seq ${message.seq}`}
                  </p>
                  <p>{message.body}</p>
                </div>
              )
            })
          )}
        </div>
        <form className="flex gap-2" onSubmit={handleSubmit}>
          <Input
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="Escribe un mensaje..."
            maxLength={2000}
          />
          <Button type="submit">Enviar</Button>
        </form>
      </CardContent>
    </Card>
  )
}

function App() {
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [conversationsLoading, setConversationsLoading] = useState(true)
  const [conversationsError, setConversationsError] = useState<string | null>(null)
  const [creatingConversation, setCreatingConversation] = useState(false)

  const [senderRole, setSenderRole] = useState<SenderRole>('CUSTOMER')
  const [senderName, setSenderName] = useState('Cliente')
  const [conversationId, setConversationId] = useState('')
  const [session, setSession] = useState<Session | null>(null)

  const deepLink = useMemo(() => {
    if (!session) return null
    const oppositeRole: SenderRole =
      session.senderRole === 'CUSTOMER' ? 'AGENT' : 'CUSTOMER'
    const oppositeName = oppositeRole === 'CUSTOMER' ? 'Cliente-2' : 'Agente-2'
    const params = new URLSearchParams({
      conversationId: session.conversationId,
      role: oppositeRole,
      name: oppositeName,
    })
    return `${window.location.origin}?${params.toString()}`
  }, [session])

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const seedConversation = params.get('conversationId')
    const seedRole = params.get('role') as SenderRole | null
    const seedName = params.get('name')
    if (seedConversation && seedRole && seedName) {
      setConversationId(seedConversation)
      setSenderRole(seedRole)
      setSenderName(seedName)
      setSession({
        conversationId: seedConversation,
        senderRole: seedRole,
        senderName: seedName,
      })
    }
  }, [])

  async function loadConversations() {
    setConversationsLoading(true)
    setConversationsError(null)
    try {
      const response = await fetch(buildApiUrl('/api/conversations'))
      if (!response.ok) throw new Error('No se pudieron cargar conversaciones.')
      const data = (await response.json()) as Conversation[]
      setConversations(data)
      if (!conversationId && data.length > 0) {
        setConversationId(data[0].id)
      }
    } catch (error) {
      setConversationsError((error as Error).message)
    } finally {
      setConversationsLoading(false)
    }
  }

  useEffect(() => {
    if (session) return
    loadConversations()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session])

  async function createConversation() {
    setCreatingConversation(true)
    setConversationsError(null)
    try {
      const response = await fetch(buildApiUrl('/api/conversations'), { method: 'POST' })
      if (!response.ok) throw new Error('No se pudo crear la conversacion.')
      const created = (await response.json()) as Conversation
      setConversations((prev) => [created, ...prev])
      setConversationId(created.id)
    } catch (error) {
      setConversationsError((error as Error).message)
    } finally {
      setCreatingConversation(false)
    }
  }

  function startSession(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!conversationId.trim() || !senderName.trim()) {
      setConversationsError('Debes indicar conversacion y nombre.')
      return
    }
    setSession({
      conversationId: conversationId.trim(),
      senderName: senderName.trim(),
      senderRole,
    })
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-5xl flex-col p-4 sm:p-8">
      <h1 className="mb-6 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
        SupportChat · Prueba tecnica
      </h1>

      {!session ? (
        <Card>
          <CardHeader>
            <CardTitle>Ingresar a una conversacion</CardTitle>
            <CardDescription>
              Crea una conversacion o selecciona una existente para chatear en tiempo real.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <form onSubmit={startSession} className="grid gap-3">
              <label className="text-sm font-medium text-slate-700">
                Rol
                <select
                  className="mt-1 h-9 w-full rounded-md border border-slate-300 bg-white px-3 text-sm"
                  value={senderRole}
                  onChange={(event) => setSenderRole(event.target.value as SenderRole)}
                >
                  <option value="CUSTOMER">Cliente</option>
                  <option value="AGENT">Agente</option>
                </select>
              </label>

              <label className="text-sm font-medium text-slate-700">
                Nombre
                <Input
                  value={senderName}
                  onChange={(event) => setSenderName(event.target.value)}
                  placeholder="Ej: Laura"
                />
              </label>

              <label className="text-sm font-medium text-slate-700">
                Conversation ID
                <Input
                  value={conversationId}
                  onChange={(event) => setConversationId(event.target.value)}
                  placeholder="UUID de la conversacion"
                />
              </label>

              <div className="flex flex-wrap gap-2">
                <Button type="submit">Entrar al chat</Button>
                <Button
                  type="button"
                  variant="secondary"
                  disabled={creatingConversation}
                  onClick={createConversation}
                >
                  {creatingConversation ? 'Creando...' : 'Nueva conversacion'}
                </Button>
                <Button type="button" variant="outline" onClick={loadConversations}>
                  Refrescar lista
                </Button>
              </div>
            </form>

            {conversationsLoading ? (
              <p className="text-sm text-slate-500">Cargando conversaciones...</p>
            ) : conversations.length === 0 ? (
              <p className="text-sm text-slate-500">
                No hay conversaciones todavia. Crea una para comenzar.
              </p>
            ) : (
              <div className="rounded-md border border-slate-200 p-3">
                <p className="mb-2 text-sm font-medium">Conversaciones disponibles</p>
                <div className="space-y-1">
                  {conversations.map((conversation) => (
                    <button
                      key={conversation.id}
                      type="button"
                      className="w-full rounded border border-slate-200 bg-white px-3 py-2 text-left text-xs hover:bg-slate-50"
                      onClick={() => setConversationId(conversation.id)}
                    >
                      {conversation.id}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {conversationsError ? (
              <p className="rounded border border-red-200 bg-red-50 p-2 text-sm text-red-700">
                {conversationsError}
              </p>
            ) : null}
            <p className="text-xs text-slate-500">
              Backend actual: <code>{getBackendHttpBase()}</code>
            </p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <Badge variant="secondary">Sesion activa: {session.senderRole}</Badge>
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => setSession(null)}>
                Cambiar sesion
              </Button>
              {deepLink ? (
                <a
                  href={deepLink}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex h-9 items-center rounded-md border border-slate-300 bg-white px-4 text-sm font-medium hover:bg-slate-50"
                >
                  Abrir vista del otro rol
                </a>
              ) : null}
            </div>
          </div>
          <ChatRoom session={session} />
        </div>
      )}
    </main>
  )
}

export default App
