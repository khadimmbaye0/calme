import { useCallback, useEffect, useState } from 'react'
import { errorMessage, UnauthorizedError } from '../api/client'
import { fetchJournal, undoJournal, type JournalEntry } from '../api/journal'

interface JournalPanelProps {
  onUnauthorized: () => void
}

const STATUS_LABELS: Record<string, string> = {
  APPLIED: 'Faite',
  UNDONE: 'Annulée',
  FAILED: 'Échouée',
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('fr-FR', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export default function JournalPanel({ onUnauthorized }: JournalPanelProps) {
  const [entries, setEntries] = useState<JournalEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [undoingId, setUndoingId] = useState<number | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setEntries(await fetchJournal())
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

  const handleUndo = async (id: number) => {
    setUndoingId(id)
    setError(null)
    setNotice(null)
    try {
      setEntries(await undoJournal(id))
      setNotice('Action annulée.')
    } catch (failure) {
      if (failure instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setError(errorMessage(failure))
    } finally {
      setUndoingId(null)
    }
  }

  return (
    <section className="min-w-0 rounded-2xl border border-line bg-paper p-5 sm:p-6">
      <p className="text-[11px] font-medium uppercase tracking-[0.18em] text-terracotta">Journal</p>
      <h2 className="mt-1 font-serif text-2xl leading-tight">Les dernières actions, annulables.</h2>
      <p className="mt-2 text-sm leading-relaxed text-ink/50">
        Les 30 dernières actions restent visibles. La plus récente peut être annulée en un geste.
      </p>

      {loading && <p className="mt-6 text-sm text-ink/50">Chargement du journal…</p>}

      {error && (
        <p role="status" className="mt-6 text-sm leading-relaxed text-terracotta">
          {error}
        </p>
      )}

      {notice && (
        <p aria-live="polite" className="mt-4 text-xs text-ink/50">
          {notice}
        </p>
      )}

      {!loading && !error && entries.length === 0 && (
        <p className="mt-6 text-sm leading-relaxed text-ink/55">Aucune action pour le moment.</p>
      )}

      {!loading && !error && entries.length > 0 && (
        <ul className="mt-6 space-y-2">
          {entries.map((entry) => (
            <li
              key={entry.id}
              className="flex flex-wrap items-start justify-between gap-3 rounded-2xl border border-line bg-wash/50 p-3"
            >
              <div className="min-w-0">
                <p className={`break-words text-sm ${entry.status === 'UNDONE' ? 'text-ink/40 line-through' : 'text-ink/80'}`}>
                  {entry.explanation}
                </p>
                <p className="mt-1 text-[11px] text-ink/40">
                  {formatDateTime(entry.createdAt)} · {STATUS_LABELS[entry.status] ?? entry.status}
                </p>
              </div>
              {entry.undoable && (
                <button
                  type="button"
                  onClick={() => handleUndo(entry.id)}
                  disabled={undoingId === entry.id}
                  className="rounded-full border border-ink/15 px-3 py-1.5 text-xs text-ink/60 hover:border-ink/30 hover:text-ink disabled:opacity-50"
                >
                  {undoingId === entry.id ? 'Annulation…' : 'Annuler'}
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
