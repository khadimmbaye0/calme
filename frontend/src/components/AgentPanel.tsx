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

const MAX_IMAGE_BYTES = 8 * 1024 * 1024
const ACCEPTED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])

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
  const [image, setImage] = useState<File | null>(null)
  const [imagePreview, setImagePreview] = useState<string | null>(null)
  const [imageError, setImageError] = useState<string | null>(null)
  const [imageConsent, setImageConsent] = useState(false)
  const transcriptRef = useRef<HTMLDivElement | null>(null)
  const objectUrls = useRef<string[]>([])

  useEffect(() => {
    const urls = objectUrls.current
    return () => {
      urls.forEach((url) => URL.revokeObjectURL(url))
    }
  }, [])

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
        hasImage: false,
      },
    ])
  }

  const clearAttachment = () => {
    if (imagePreview) {
      URL.revokeObjectURL(imagePreview)
      objectUrls.current = objectUrls.current.filter((url) => url !== imagePreview)
    }
    setImage(null)
    setImagePreview(null)
    setImageConsent(false)
  }

  const handleImage = (file: File | null) => {
    setImageError(null)
    if (!file) {
      return
    }
    if (!ACCEPTED_IMAGE_TYPES.has(file.type)) {
      clearAttachment()
      setImageError('Choisis une image JPEG, PNG, GIF ou WebP.')
      return
    }
    if (file.size > MAX_IMAGE_BYTES) {
      clearAttachment()
      setImageError('L’image doit faire 8 Mo maximum.')
      return
    }

    if (imagePreview) {
      URL.revokeObjectURL(imagePreview)
      objectUrls.current = objectUrls.current.filter((url) => url !== imagePreview)
    }
    const preview = URL.createObjectURL(file)
    objectUrls.current.push(preview)
    setImage(file)
    setImagePreview(preview)
    setImageConsent(false)
  }

  const removeImage = () => {
    clearAttachment()
    setImageError(null)
  }

  const handleSend = async (event?: FormEvent) => {
    event?.preventDefault()
    const message = input.trim()
    if ((!message && !image) || sending || loading || pending) {
      return
    }

    const attachedImage = image
    const attachedPreview = imagePreview
    const attachedConsent = imageConsent
    const optimisticId = -Date.now() - 1
    setSending(true)
    setError(null)
    setNotice(null)
    setActions([])
    setUndoableActionId(null)
    setInput('')
    setImage(null)
    setImagePreview(null)
    setImageConsent(false)
    setMessages((current) => [
      ...current,
      {
        id: optimisticId,
        role: 'USER',
        content: message || 'Image jointe.',
        createdAt: new Date().toISOString(),
        hasImage: Boolean(attachedImage),
        imageUrl: attachedPreview ?? undefined,
      },
    ])

    try {
      const response = await sendAgentMessage(message, attachedImage, attachedConsent)
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
      setMessages((current) => current.filter((entry) => entry.id !== optimisticId))
      setInput(message)
      if (attachedImage) {
        setImage(attachedImage)
        setImagePreview(attachedPreview)
        setImageConsent(attachedConsent)
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
    <section className="min-w-0 rounded-2xl border border-line bg-paper p-5 sm:p-6">
      <p className="text-[11px] font-medium uppercase tracking-[0.18em] text-terracotta">Agent</p>
      <h2 className="mt-1 font-serif text-2xl leading-tight">Parle à Calme.</h2>
      <p className="mt-2 max-w-xl text-sm leading-relaxed text-ink/50">
        Écris ce que tu veux organiser. Je propose, le serveur vérifie, et tout reste annulable.
      </p>

      <div
        ref={transcriptRef}
        className="mt-5 min-w-0 max-h-[45vh] space-y-4 overflow-y-auto pr-1 sm:max-h-[28rem]"
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
            className={`min-w-0 ${message.role === 'USER' ? 'ml-auto max-w-[88%]' : 'max-w-[92%]'}`}
          >
            <p className="mb-1 text-[10px] uppercase tracking-[0.16em] text-ink/35">
              {message.role === 'USER' ? 'Vous' : 'Calme'} · {formatTime(message.createdAt)}
            </p>
            <div
              className={
                message.role === 'USER'
                  ? 'break-words rounded-xl bg-wash px-4 py-3 text-sm leading-relaxed text-ink/80'
                  : 'break-words text-sm leading-relaxed text-ink/80'
              }
            >
              {message.imageUrl && (
                <img
                  src={message.imageUrl}
                  alt="Image jointe à la conversation"
                  className="mb-2 max-h-64 max-w-full rounded-lg border border-line object-contain"
                />
              )}
              {message.content}
            </div>
          </div>
        ))}

        {pending && (
          <div className="rounded-xl border border-terracotta/30 bg-terracotta/5 p-4">
            <p className="break-words text-sm leading-relaxed text-ink/80">{pending.summary}</p>
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
            <p key={`${action.tool}-${action.summary}`} className="break-words text-xs text-ink/50">
              · {action.summary}
            </p>
          ))}
        </div>
      )}

      {error && (
        <p role="status" className="mt-3 break-words text-sm leading-relaxed text-terracotta">
          {error}
        </p>
      )}
      {notice && <p className="mt-3 break-words text-xs text-ink/50">{notice}</p>}

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
        {imagePreview && (
          <div className="mb-3 flex items-center gap-3 rounded-xl border border-line bg-wash/50 p-2">
            <img
              src={imagePreview}
              alt="Aperçu de l’image jointe"
              className="h-14 w-14 rounded-lg border border-line object-cover"
            />
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs font-medium text-ink/70">{image?.name}</p>
              <p className="text-[10px] text-ink/40">
                {image ? `${Math.max(1, Math.round(image.size / 1024))} Ko` : ''}
              </p>
            </div>
            <button
              type="button"
              onClick={removeImage}
              className="shrink-0 rounded-full border border-line px-2.5 py-1 text-[10px] text-ink/50 hover:border-ink/30 hover:text-ink"
            >
              Retirer
            </button>
            <label className="mt-1 flex items-start gap-2 text-[10px] leading-relaxed text-ink/45">
              <input
                type="checkbox"
                checked={imageConsent}
                onChange={(event) => setImageConsent(event.target.checked)}
                className="mt-0.5"
              />
              J’accepte que cette image soit envoyée à DeepSeek pour être lue.
            </label>
          </div>
        )}
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
          disabled={sending || loading || pending !== null}
          placeholder="Par exemple : ajoute un cours demain à 9h…"
          className="min-w-0 w-full resize-none rounded-xl border border-line bg-wash px-3 py-2.5 text-sm leading-relaxed text-ink outline-none placeholder:text-ink/30 focus:border-terracotta/50 disabled:opacity-60"
        />
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <label className="inline-flex cursor-pointer items-center rounded-full border border-line px-3 py-1.5 text-[11px] text-ink/55 hover:border-ink/30 hover:text-ink">
            <input
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp"
              className="sr-only"
              disabled={sending || loading || pending !== null}
              onChange={(event) => {
                handleImage(event.target.files?.[0] ?? null)
                event.target.value = ''
              }}
            />
            Joindre une image
          </label>
          <p className="hidden text-[11px] text-ink/35 sm:block">
            JPEG, PNG, GIF ou WebP · 8 Mo max.
          </p>
          <button
            type="submit"
            disabled={
              sending ||
              loading ||
              (!input.trim() && !image) ||
              (Boolean(image) && !imageConsent) ||
              pending !== null
            }
            className="ml-auto rounded-xl bg-terracotta px-4 py-2 text-sm font-medium text-cream hover:bg-terracotta/90 disabled:opacity-40"
          >
            {sending ? 'Envoi…' : 'Envoyer'}
          </button>
        </div>
        {imageError && (
          <p role="alert" className="mt-2 text-xs text-terracotta">
            {imageError}
          </p>
        )}
      </form>

      <p className="mt-3 text-[11px] leading-relaxed text-ink/35">
        L&apos;agent utilise DeepSeek. Les images sont envoyées pour être lues ; n&apos;y mets pas de données
        confidentielles.
      </p>
    </section>
  )
}
