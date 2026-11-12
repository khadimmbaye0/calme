import { useCallback, useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import {
  cancelAgentAction,
  confirmAgentAction,
  fetchAgentMessages,
  sendAgentMessage,
  type AgentAction,
  type AgentMessage,
  type AgentPendingConfirmation,
} from '../api/agent'
import { errorMessage, UnauthorizedError } from '../api/client'
import { undoJournal } from '../api/journal'

interface AgentPanelProps {
  onUnauthorized: () => void
}

const EXAMPLES = [
  "Qu'est-ce que je fais maintenant ?",
  'Ajoute un cours demain de 9h à 10h.',
  'Planifie mon mémoire pour dans deux semaines.',
]

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('fr-FR', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export default function AgentPanel({ onUnauthorized }: AgentPanelProps) {
  const [messages, setMessages] = useState<AgentMessage[]>([])
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
  const [input, setInput] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [pending, setPending] = useState<AgentPendingConfirmation | null>(null)
  const [actions, setActions] = useState<AgentAction[]>([])
  const [undoableActionId, setUndoableActionId] = useState<number | null>(null)
  const transcriptRef = useRef<HTMLDivElement | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setMessages(await fetchAgentMessages())
    } catch (failure) {
      if (failure instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setError(errorMessage(failure))
    } finally {
      setLoading(false)
    }
  }, [onUnauthorized])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    const element = transcriptRef.current
    if (element) {
      element.scrollTop = element.scrollHeight
    }
  }, [messages, pending])

  const appendAgent = (message: string) => {
    setMessages((current) => [
      ...current,
      {
        id: -Date.now(),
        role: 'AGENT',
        content: message,
        createdAt: new Date().toISOString(),
      },
    ])
  }

  const handleSend = async (event?: FormEvent) => {
    event?.preventDefault()
    const message = input.trim()
    if (!message || sending || pending) {
      return
    }

    setSending(true)
    setError(null)
    setNotice(null)
    setActions([])
    setUndoableActionId(null)
    setInput('')
    setMessages((current) => [
      ...current,
      {
        id: -Date.now() - 1,
        role: 'USER',
        content: message,
        createdAt: new Date().toISOString(),
      },
    ])

    try {
      const response = await sendAgentMessage(message)
      appendAgent(response.message)
      setPending(response.pendingConfirmation)
      setActions(response.actions)
      setUndoableActionId(response.undoableActionId)
      if (response.error) {
        setError(null)
      }
    } catch (failure) {
      if (failure instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setError(errorMessage(failure))
    } finally {
      setSending(false)
    }
  }

  const handleConfirm = async () => {
    if (!pending || sending) {
      return
    }
    setSending(true)
    setError(null)
    try {
      const response = await confirmAgentAction(pending.id)
      appendAgent(response.message)
      setPending(null)
      setActions(response.actions)
      setUndoableActionId(response.undoableActionId)
    } catch (failure) {
      if (failure instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setError(errorMessage(failure))
    } finally {
      setSending(false)
    }
  }

  const handleCancel = async () => {
    if (!pending || sending) {
      return
    }
    setSending(true)
    setError(null)
    try {
      const response = await cancelAgentAction(pending.id)
      appendAgent(response.message)
      setPending(null)
      setActions([])
      setUndoableActionId(null)
    } catch (failure) {
      if (failure instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setError(errorMessage(failure))
    } finally {
      setSending(false)
    }
  }

  const handleUndo = async () => {
    if (!undoableActionId || sending) {
      return
    }
    setSending(true)
    setError(null)
    setNotice(null)
    try {
      await undoJournal(undoableActionId)
      setUndoableActionId(null)
      setActions([])
      setNotice('Dernière action annulée.')
    } catch (failure) {
      if (failure instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setError(errorMessage(failure))
    } finally {
      setSending(false)
    }
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      void handleSend()
    }
  }

  return (
    <section className="rounded-2xl border border-line bg-paper p-5 sm:p-6">
      <p className="text-[11px] font-medium uppercase tracking-[0.18em] text-terracotta">Agent</p>
      <h2 className="mt-1 font-serif text-2xl leading-tight">Parle à Calme.</h2>
      <p className="mt-2 max-w-xl text-sm leading-relaxed text-ink/50">
        Écris ce que tu veux organiser. Je propose, le serveur vérifie, et tout reste annulable.
      </p>

      <div
        ref={transcriptRef}
        className="mt-5 max-h-[28rem] space-y-4 overflow-y-auto pr-1"
        aria-live="polite"
      >
        {loading && <p className="text-sm text-ink/50">Chargement de la conversation…</p>}

        {!loading && messages.length === 0 && (
          <div className="rounded-xl border border-line bg-wash/60 p-4">
            <p className="text-sm text-ink/65">
              Bonjour. Tu peux me parler de ta journée, d&apos;un cours ou d&apos;un projet.
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              {EXAMPLES.map((example) => (
                <button
                  key={example}
                  type="button"
                  onClick={() => setInput(example)}
                  className="rounded-full border border-line px-3 py-1.5 text-left text-xs text-ink/60 hover:border-ink/30 hover:text-ink"
                >
                  {example}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((message) => (
          <div
            key={message.id}
            className={message.role === 'USER' ? 'ml-auto max-w-[88%]' : 'max-w-[92%]'}
          >
            <p className="mb-1 text-[10px] uppercase tracking-[0.16em] text-ink/35">
              {message.role === 'USER' ? 'Vous' : 'Calme'} · {formatTime(message.createdAt)}
            </p>
            <div
              className={
                message.role === 'USER'
                  ? 'rounded-xl bg-wash px-4 py-3 text-sm leading-relaxed text-ink/80'
                  : 'text-sm leading-relaxed text-ink/80'
              }
            >
              {message.content}
            </div>
          </div>
        ))}

        {pending && (
          <div className="rounded-xl border border-terracotta/30 bg-terracotta/5 p-4">
            <p className="text-sm leading-relaxed text-ink/80">{pending.summary}</p>
            <div className="mt-3 flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => void handleConfirm()}
                disabled={sending}
                className="rounded-xl bg-terracotta px-4 py-2 text-sm font-medium text-cream hover:bg-terracotta/90 disabled:opacity-50"
              >
                Confirmer
              </button>
              <button
                type="button"
                onClick={() => void handleCancel()}
                disabled={sending}
                className="rounded-full border border-line px-4 py-2 text-sm text-ink/60 hover:border-ink/30 hover:text-ink disabled:opacity-50"
              >
                Annuler
              </button>
            </div>
          </div>
        )}

        {sending && !pending && <p className="text-xs text-ink/40">Calme réfléchit…</p>}
      </div>

      {actions.length > 0 && (
        <div className="mt-4 space-y-1 border-t border-line pt-3">
          {actions.map((action) => (
            <p key={`${action.tool}-${action.summary}`} className="text-xs text-ink/50">
              · {action.summary}
            </p>
          ))}
        </div>
      )}

      {error && (
        <p role="status" className="mt-3 text-sm leading-relaxed text-terracotta">
          {error}
        </p>
      )}
      {notice && <p className="mt-3 text-xs text-ink/50">{notice}</p>}

      {undoableActionId !== null && (
        <button
          type="button"
          onClick={() => void handleUndo()}
          disabled={sending}
          className="mt-3 rounded-full border border-line px-3 py-1.5 text-xs text-ink/55 hover:border-ink/30 hover:text-ink disabled:opacity-50"
        >
          Annuler la dernière action
        </button>
      )}

      <form onSubmit={handleSend} className="mt-5">
        <label htmlFor="agent-message" className="sr-only">
          Votre message
        </label>
        <textarea
          id="agent-message"
          value={input}
          onChange={(event) => setInput(event.target.value)}
          onKeyDown={handleKeyDown}
          rows={2}
          maxLength={2000}
          disabled={sending || pending !== null}
          placeholder="Par exemple : ajoute un cours demain à 9h…"
          className="w-full resize-none rounded-xl border border-line bg-wash px-3 py-2.5 text-sm leading-relaxed text-ink outline-none placeholder:text-ink/30 focus:border-terracotta/50 disabled:opacity-60"
        />
        <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
          <p className="text-[11px] text-ink/35">
            Entrée pour envoyer · Maj+Entrée pour une nouvelle ligne
          </p>
          <button
            type="submit"
            disabled={sending || input.trim().length === 0 || pending !== null}
            className="rounded-xl bg-terracotta px-4 py-2 text-sm font-medium text-cream hover:bg-terracotta/90 disabled:opacity-40"
          >
            {sending ? 'Envoi…' : 'Envoyer'}
          </button>
        </div>
      </form>

      <p className="mt-3 text-[11px] leading-relaxed text-ink/35">
        L&apos;agent utilise DeepSeek. N&apos;écris pas de données confidentielles.
      </p>
    </section>
  )
}
