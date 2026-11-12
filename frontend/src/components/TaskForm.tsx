import { useState, type FormEvent } from 'react'
import { errorMessage, UnauthorizedError } from '../api/client'
import {
  createTask,
  PRIORITY_LABELS,
  type TaskPriority,
} from '../api/planner'

interface TaskFormProps {
  onCreated: () => void
  onUnauthorized: () => void
}

function localDateString(date: Date): string {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-')
}

export default function TaskForm({ onCreated, onUnauthorized }: TaskFormProps) {
  const [title, setTitle] = useState('')
  const [duration, setDuration] = useState(15)
  const [priority, setPriority] = useState<TaskPriority>('NORMAL')
  const [due, setDue] = useState<'today' | 'tomorrow'>('today')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(null)
    setNotice(null)

    const trimmedTitle = title.trim()
    if (!trimmedTitle) {
      setError('Le titre est obligatoire.')
      return
    }

    const dueDate = new Date()
    if (due === 'tomorrow') {
      dueDate.setDate(dueDate.getDate() + 1)
    }

    setSubmitting(true)
    try {
      await createTask({
        title: trimmedTitle,
        estimateMin: duration,
        estimateMax: duration,
        priority,
        dueDate: localDateString(dueDate),
      })
      setTitle('')
      setNotice('Tâche ajoutée.')
      onCreated()
    } catch (failure) {
      if (failure instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setError(errorMessage(failure))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <details className="group">
      <summary className="cursor-pointer list-none text-xs font-medium text-ink/50 underline-offset-4 hover:text-ink hover:underline">
        Ajouter une tâche
      </summary>

      <form onSubmit={handleSubmit} className="mt-4 space-y-3">
        <label className="block">
          <span className="text-[11px] font-medium uppercase tracking-[0.14em] text-ink/45">
            Titre de la tâche
          </span>
          <input
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            maxLength={80}
            placeholder="Relire le plan du chapitre 2"
            className="mt-1 w-full rounded-xl border border-ink/15 bg-wash px-3 py-2 text-sm outline-none placeholder:text-ink/30 focus:border-terracotta/60"
          />
        </label>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <label className="block">
            <span className="text-[11px] font-medium uppercase tracking-[0.14em] text-ink/45">Durée</span>
            <select
              value={duration}
              onChange={(event) => setDuration(Number(event.target.value))}
              className="mt-1 w-full rounded-xl border border-ink/15 bg-wash px-3 py-2 text-sm outline-none focus:border-terracotta/60"
            >
              {[10, 15, 20, 25].map((minutes) => (
                <option key={minutes} value={minutes}>
                  {minutes} min
                </option>
              ))}
            </select>
          </label>

          <label className="block">
            <span className="text-[11px] font-medium uppercase tracking-[0.14em] text-ink/45">Priorité</span>
            <select
              value={priority}
              onChange={(event) => setPriority(event.target.value as TaskPriority)}
              className="mt-1 w-full rounded-xl border border-ink/15 bg-wash px-3 py-2 text-sm outline-none focus:border-terracotta/60"
            >
              {(Object.keys(PRIORITY_LABELS) as TaskPriority[]).map((value) => (
                <option key={value} value={value}>
                  {PRIORITY_LABELS[value]}
                </option>
              ))}
            </select>
          </label>
        </div>

        <label className="block">
          <span className="text-[11px] font-medium uppercase tracking-[0.14em] text-ink/45">Pour</span>
          <select
            value={due}
            onChange={(event) => setDue(event.target.value as 'today' | 'tomorrow')}
            className="mt-1 w-full rounded-xl border border-ink/15 bg-wash px-3 py-2 text-sm outline-none focus:border-terracotta/60"
          >
            <option value="today">Aujourd'hui</option>
            <option value="tomorrow">Demain</option>
          </select>
        </label>

        {error && (
          <p role="alert" className="text-sm leading-relaxed text-terracotta">
            {error}
          </p>
        )}
        {notice && (
          <p aria-live="polite" className="text-xs text-ink/50">
            {notice}
          </p>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-full border border-ink/15 px-4 py-2 text-sm text-ink/70 hover:border-ink/30 hover:text-ink disabled:opacity-50"
        >
          {submitting ? 'Ajout…' : 'Ajouter la tâche'}
        </button>
      </form>
    </details>
  )
}
