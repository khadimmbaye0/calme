import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { logout, type AuthUser } from '../api/auth'
import { errorMessage, UnauthorizedError } from '../api/client'
import {
  createEvent,
  deleteEvent,
  fetchEvents,
  updateEvent,
  type CalendarEvent,
  type EventInput,
  type EventKind,
} from '../api/events'
import NowPanel from './NowPanel'
import JournalPanel from './JournalPanel'
import ProjectsPanel from './ProjectsPanel'
import {
  addDays,
  combineDayAndTime,
  formatLongDay,
  formatTime,
  minutesFromStartOfDay,
  startOfDay,
  toTimeValue,
} from '../lib/date'

const HOUR_HEIGHT = 48
const HOURS = Array.from({ length: 24 }, (_, hour) => hour)

const KIND_LABELS: Record<EventKind, string> = {
  FIXED: 'Fixe',
  PLANNED: 'Planifié',
  AVAILABILITY: 'Disponibilité',
}

const KIND_STYLES: Record<EventKind, string> = {
  FIXED: 'border-sage/45 bg-sage/20 text-ink',
  PLANNED: 'border-terracotta/35 bg-terracotta/15 text-ink',
  AVAILABILITY: 'border-ink/10 bg-ink/5 text-ink/70',
}

type LoadStatus = 'loading' | 'ready' | 'error'
type View = 'now' | 'calendar' | 'projects' | 'journal'

interface CalendarScreenProps {
  user: AuthUser
  onLoggedOut: () => void
  onUnauthorized: () => void
  initialView?: View
}

export default function CalendarScreen({
  user,
  onLoggedOut,
  onUnauthorized,
  initialView,
}: CalendarScreenProps) {
  const [view, setView] = useState<View>(
    () => initialView ?? (window.innerWidth >= 1024 ? 'calendar' : 'now'),
  )
  const [selectedDay, setSelectedDay] = useState(() => startOfDay(new Date()))
  const [events, setEvents] = useState<CalendarEvent[]>([])
  const [status, setStatus] = useState<LoadStatus>('loading')
  const [loadError, setLoadError] = useState<string | null>(null)
  const [now, setNow] = useState(() => new Date())

  const [editing, setEditing] = useState<CalendarEvent | null>(null)
  const [title, setTitle] = useState('')
  const [startTime, setStartTime] = useState('09:00')
  const [endTime, setEndTime] = useState('10:00')
  const [kind, setKind] = useState<EventKind>('FIXED')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [loggingOut, setLoggingOut] = useState(false)

  const load = useCallback(async () => {
    setStatus('loading')
    setLoadError(null)
    try {
      setEvents(await fetchEvents(selectedDay))
      setStatus('ready')
    } catch (error) {
      if (error instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setLoadError(errorMessage(error))
      setStatus('error')
    }
  }, [onUnauthorized, selectedDay])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    const interval = window.setInterval(() => setNow(new Date()), 60_000)
    return () => window.clearInterval(interval)
  }, [])

  const resetForm = useCallback(() => {
    setEditing(null)
    setTitle('')
    setStartTime('09:00')
    setEndTime('10:00')
    setKind('FIXED')
    setFormError(null)
  }, [])

  const changeDay = (nextDay: Date) => {
    setSelectedDay(startOfDay(nextDay))
    resetForm()
  }

  const selectEvent = (event: CalendarEvent) => {
    setEditing(event)
    setTitle(event.title)
    setStartTime(toTimeValue(event.startAt))
    setEndTime(toTimeValue(event.endAt))
    setKind(event.kind)
    setFormError(null)
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setFormError(null)

    const trimmedTitle = title.trim()
    if (!trimmedTitle) {
      setFormError('Le titre est obligatoire.')
      return
    }

    const startAt = combineDayAndTime(selectedDay, startTime)
    const endAt = combineDayAndTime(selectedDay, endTime)
    if (!Number.isFinite(startAt.getTime()) || !Number.isFinite(endAt.getTime())) {
      setFormError('Les heures doivent être valides.')
      return
    }
    if (endAt.getTime() <= startAt.getTime()) {
      setFormError('La fin doit être après le début.')
      return
    }

    const input: EventInput = {
      title: trimmedTitle,
      startAt: startAt.toISOString(),
      endAt: endAt.toISOString(),
      kind,
    }

    setSubmitting(true)
    try {
      if (editing) {
        await updateEvent(editing.id, input)
      } else {
        await createEvent(input)
      }
      resetForm()
      await load()
    } catch (error) {
      if (error instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setFormError(errorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async () => {
    if (!editing) {
      return
    }

    setFormError(null)
    setSubmitting(true)
    try {
      await deleteEvent(editing.id)
      resetForm()
      await load()
    } catch (error) {
      if (error instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setFormError(errorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const handleLogout = async () => {
    setLoggingOut(true)
    try {
      await logout()
    } catch {
      // The local session is hidden even if the server is unreachable.
    } finally {
      setLoggingOut(false)
      onLoggedOut()
    }
  }

  const isToday = startOfDay(selectedDay).getTime() === startOfDay(now).getTime()
  const nowMinutes = minutesFromStartOfDay(now, selectedDay)
  const sortedEvents = [...events].sort(
    (left, right) => new Date(left.startAt).getTime() - new Date(right.startAt).getTime(),
  )

  return (
    <main className="min-h-screen bg-cream px-4 py-8 text-ink sm:px-8">
      <div className="mx-auto max-w-7xl">
        <header className="mb-8 space-y-6">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="text-xs font-medium uppercase tracking-[0.22em] text-terracotta">Calme</p>
              <h1 className="mt-3 font-serif text-4xl leading-tight sm:text-5xl">Une journée à la fois.</h1>
              <p className="mt-2 max-w-md text-sm leading-relaxed text-ink/55">
                Les événements fixes passent avant le reste. Les créneaux en conflit sont signalés, jamais imposés.
              </p>
            </div>

            <div className="flex flex-col items-end gap-1">
              <button
                type="button"
                onClick={handleLogout}
                disabled={loggingOut}
                className="text-xs text-ink/50 underline-offset-4 transition hover:text-ink hover:underline disabled:opacity-50"
              >
                {loggingOut ? 'Déconnexion…' : 'Se déconnecter'}
              </button>
              <span className="text-[11px] text-ink/35">{user.email}</span>
            </div>
          </div>

          <div className="flex gap-2" role="tablist" aria-label="Vue">
            <button
              type="button"
              role="tab"
              aria-selected={view === 'now'}
              onClick={() => setView('now')}
              className={`rounded-full px-4 py-1.5 text-sm transition ${
                view === 'now'
                  ? 'bg-ink text-cream'
                  : 'border border-ink/15 text-ink/70 hover:border-ink/30'
              }`}
            >
              Maintenant
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={view === 'calendar'}
              onClick={() => setView('calendar')}
              className={`rounded-full px-4 py-1.5 text-sm transition ${
                view === 'calendar'
                  ? 'bg-ink text-cream'
                  : 'border border-ink/15 text-ink/70 hover:border-ink/30'
              }`}
            >
              Calendrier
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={view === 'projects'}
              onClick={() => setView('projects')}
              className={`rounded-full px-4 py-1.5 text-sm transition ${
                view === 'projects'
                  ? 'bg-ink text-cream'
                  : 'border border-ink/15 text-ink/70 hover:border-ink/30'
              }`}
            >
              Projets
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={view === 'journal'}
              onClick={() => setView('journal')}
              className={`rounded-full px-4 py-1.5 text-sm transition ${
                view === 'journal'
                  ? 'bg-ink text-cream'
                  : 'border border-ink/15 text-ink/70 hover:border-ink/30'
              }`}
            >
              Journal
            </button>
          </div>

          <div
            className={`flex items-center gap-2 ${view === 'calendar' ? '' : 'hidden'}`}
            role="group"
            aria-label="Navigation dans le calendrier"
          >
            <button
              type="button"
              onClick={() => changeDay(addDays(selectedDay, -1))}
              aria-label="Jour précédent"
              className="rounded-full border border-ink/15 px-3 py-1.5 text-sm text-ink/70 transition hover:border-ink/30 hover:text-ink"
            >
              ←
            </button>
            <button
              type="button"
              onClick={() => changeDay(new Date())}
              className="rounded-full border border-ink/15 px-4 py-1.5 text-sm text-ink/70 transition hover:border-ink/30 hover:text-ink"
            >
              Aujourd'hui
            </button>
            <button
              type="button"
              onClick={() => changeDay(addDays(selectedDay, 1))}
              aria-label="Jour suivant"
              className="rounded-full border border-ink/15 px-3 py-1.5 text-sm text-ink/70 transition hover:border-ink/30 hover:text-ink"
            >
              →
            </button>
          </div>
        </header>

        <div className="grid gap-6 lg:grid-cols-[20rem_minmax(0,1fr)_20rem]">
          <div className={view === 'now' ? 'block' : 'hidden lg:block'}>
            <NowPanel onUnauthorized={onUnauthorized} />
          </div>

          <div className={view === 'calendar' ? 'block' : 'hidden'}>
          <section
            aria-label={`Calendrier du ${formatLongDay(selectedDay)}`}
            className="rounded-3xl border border-ink/10 bg-white/40 p-4 shadow-[0_24px_60px_-48px_rgba(43,38,32,0.55)] sm:p-6"
          >
            <div className="mb-5 flex items-baseline justify-between gap-4">
              <h2 className="font-serif text-2xl capitalize">{formatLongDay(selectedDay)}</h2>
              <span className="shrink-0 text-xs text-ink/45">
                {events.length} moment{events.length > 1 ? 's' : ''}
              </span>
            </div>

            {status === 'loading' && <p className="mb-4 text-sm text-ink/55">Chargement de la journée…</p>}
            {status === 'error' && (
              <p role="status" className="mb-4 text-sm text-terracotta">
                {loadError}
              </p>
            )}
            {status === 'ready' && events.length === 0 && (
              <p className="mb-4 text-sm text-ink/55">Rien de prévu. La journée peut rester libre.</p>
            )}

            <div className="relative h-[72rem] overflow-hidden rounded-2xl bg-cream/40">
              {HOURS.map((hour) => (
                <div
                  key={hour}
                  className="absolute left-0 right-0 border-t border-ink/8"
                  style={{ top: `${hour * HOUR_HEIGHT}px` }}
                >
                  <span className="absolute left-2 top-1 w-8 text-right text-[11px] tabular-nums text-ink/35">
                    {String(hour).padStart(2, '0')}:00
                  </span>
                </div>
              ))}

              {sortedEvents.map((event) => {
                const startMinutes = minutesFromStartOfDay(new Date(event.startAt), selectedDay)
                const endMinutes = minutesFromStartOfDay(new Date(event.endAt), selectedDay)
                const visibleStart = Math.max(0, startMinutes)
                const visibleEnd = Math.min(1440, endMinutes)
                if (visibleEnd <= visibleStart) {
                  return null
                }

                const top = (visibleStart / 60) * HOUR_HEIGHT
                const height = Math.max(24, ((visibleEnd - visibleStart) / 60) * HOUR_HEIGHT)
                const isAvailability = event.kind === 'AVAILABILITY'

                return (
                  <button
                    key={event.id}
                    type="button"
                    onClick={() => selectEvent(event)}
                    aria-label={`${event.title}, ${KIND_LABELS[event.kind]}, de ${formatTime(event.startAt)} à ${formatTime(event.endAt)}`}
                    aria-pressed={editing?.id === event.id}
                    className={`absolute left-12 right-3 overflow-hidden rounded-xl border px-2 py-1 text-left text-xs transition hover:shadow-sm ${KIND_STYLES[event.kind]} ${
                      isAvailability ? 'z-0 opacity-80' : 'z-10'
                    } ${editing?.id === event.id ? 'ring-2 ring-terracotta/45' : ''}`}
                    style={{ top: `${top}px`, height: `${height}px` }}
                  >
                    <span className="block truncate font-medium">{event.title}</span>
                    <span className="block truncate text-[11px] text-ink/55">
                      {formatTime(event.startAt)}–{formatTime(event.endAt)}
                    </span>
                  </button>
                )
              })}

              {isToday && nowMinutes >= 0 && nowMinutes < 1440 && (
                <div
                  className="pointer-events-none absolute left-12 right-2 z-20 flex items-center gap-2"
                  style={{ top: `${(nowMinutes / 60) * HOUR_HEIGHT}px` }}
                >
                  <span className="h-2 w-2 rounded-full bg-terracotta" aria-hidden="true" />
                  <span className="h-px flex-1 bg-terracotta/60" aria-hidden="true" />
                  <span className="rounded-full bg-cream px-2 py-0.5 text-[10px] uppercase tracking-[0.16em] text-terracotta">
                    maintenant
                  </span>
                </div>
              )}
            </div>
          </section>
          </div>

          <div className={view === 'calendar' ? 'block' : 'hidden'}>
          <aside className="lg:sticky lg:top-8 lg:self-start">
            <form
              onSubmit={handleSubmit}
              className="rounded-3xl border border-ink/10 bg-white/55 p-5 shadow-[0_24px_60px_-48px_rgba(43,38,32,0.55)]"
            >
              <h2 className="font-serif text-xl">{editing ? 'Modifier ce moment' : 'Ajouter un moment'}</h2>
              <p className="mt-1 text-xs leading-relaxed text-ink/45">
                {editing ? 'Ajuste les informations ou supprime cet événement.' : 'Un titre et deux heures suffisent.'}
              </p>

              <div className="mt-5 space-y-4">
                <label className="block">
                  <span className="text-xs font-medium uppercase tracking-[0.14em] text-ink/55">Titre</span>
                  <input
                    value={title}
                    onChange={(event) => setTitle(event.target.value)}
                    maxLength={160}
                    placeholder="Relire le plan du chapitre 2"
                    className="mt-1 w-full rounded-xl border border-ink/15 bg-cream/60 px-3 py-2 text-sm outline-none transition placeholder:text-ink/30 focus:border-terracotta/60"
                  />
                </label>

                <div className="grid grid-cols-2 gap-3">
                  <label className="block">
                    <span className="text-xs font-medium uppercase tracking-[0.14em] text-ink/55">Début</span>
                    <input
                      type="time"
                      value={startTime}
                      onChange={(event) => setStartTime(event.target.value)}
                      step={300}
                      className="mt-1 w-full rounded-xl border border-ink/15 bg-cream/60 px-3 py-2 text-sm outline-none transition focus:border-terracotta/60"
                    />
                  </label>
                  <label className="block">
                    <span className="text-xs font-medium uppercase tracking-[0.14em] text-ink/55">Fin</span>
                    <input
                      type="time"
                      value={endTime}
                      onChange={(event) => setEndTime(event.target.value)}
                      step={300}
                      className="mt-1 w-full rounded-xl border border-ink/15 bg-cream/60 px-3 py-2 text-sm outline-none transition focus:border-terracotta/60"
                    />
                  </label>
                </div>

                <label className="block">
                  <span className="text-xs font-medium uppercase tracking-[0.14em] text-ink/55">Type</span>
                  <select
                    value={kind}
                    onChange={(event) => setKind(event.target.value as EventKind)}
                    className="mt-1 w-full rounded-xl border border-ink/15 bg-cream/60 px-3 py-2 text-sm outline-none transition focus:border-terracotta/60"
                  >
                    {(Object.keys(KIND_LABELS) as EventKind[]).map((value) => (
                      <option key={value} value={value}>
                        {KIND_LABELS[value]}
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              {formError && (
                <p role="alert" aria-live="assertive" className="mt-4 text-sm leading-relaxed text-terracotta">
                  {formError}
                </p>
              )}

              <div className="mt-5 flex flex-wrap gap-2">
                <button
                  type="submit"
                  disabled={submitting}
                  className="rounded-full bg-terracotta px-4 py-2 text-sm font-medium text-cream transition hover:bg-terracotta/90 disabled:opacity-50"
                >
                  {submitting ? 'Enregistrement…' : editing ? 'Enregistrer' : 'Ajouter'}
                </button>
                {editing && (
                  <button
                    type="button"
                    onClick={resetForm}
                    disabled={submitting}
                    className="rounded-full border border-ink/15 px-4 py-2 text-sm text-ink/70 transition hover:border-ink/30 hover:text-ink disabled:opacity-50"
                  >
                    Annuler
                  </button>
                )}
              </div>

              {editing && (
                <button
                  type="button"
                  onClick={handleDelete}
                  disabled={submitting}
                  className="mt-3 text-xs text-ink/50 underline-offset-4 transition hover:text-ink hover:underline disabled:opacity-50"
                >
                  Supprimer ce moment
                </button>
              )}
            </form>

            <p className="mt-4 px-2 text-xs leading-relaxed text-ink/40">
              Les événements fixes ne sont jamais déplacés automatiquement. Un créneau qui chevauche un événement
              fixe ou planifié est refusé avec une explication.
            </p>
          </aside>
          </div>

          <div className={view === 'projects' ? 'block lg:col-span-2' : 'hidden'}>
            {view === 'projects' && <ProjectsPanel onUnauthorized={onUnauthorized} />}
          </div>

          <div className={view === 'journal' ? 'block lg:col-span-2' : 'hidden'}>
            {view === 'journal' && <JournalPanel onUnauthorized={onUnauthorized} />}
          </div>
        </div>
      </div>
    </main>
  )
}
