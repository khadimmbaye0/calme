import { useCallback, useEffect, useState } from 'react'
import { errorMessage, UnauthorizedError } from '../api/client'
import {
  BLOCK_LABELS,
  ENERGY_LABELS,
  PRIORITY_LABELS,
  doneTask,
  fetchNow,
  laterTask,
  setEnergy,
  startTask,
  tooHeavyTask,
  type EnergyLevel,
  type NowResponse,
  type TaskSummary,
} from '../api/planner'
import TaskForm from './TaskForm'

interface NowPanelProps {
  onUnauthorized: () => void
}

const ENERGY_LEVELS: EnergyLevel[] = ['LOW', 'MID', 'GOOD']

export default function NowPanel({ onUnauthorized }: NowPanelProps) {
  const [now, setNow] = useState<NowResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [acting, setActing] = useState(false)
  const [startedTask, setStartedTask] = useState<TaskSummary | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setNow(await fetchNow())
      setStartedTask(null)
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

  const handleEnergy = async (level: EnergyLevel) => {
    setActing(true)
    setError(null)
    setNotice(null)
    try {
      const result = await setEnergy(level)
      setNotice(result.deferredCount > 0 ? 'La journée a été allégée.' : null)
      await load()
    } catch (failure) {
      if (failure instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setError(errorMessage(failure))
    } finally {
      setActing(false)
    }
  }

  const handleStart = async () => {
    if (!now?.task) {
      return
    }
    setActing(true)
    setError(null)
    try {
      await startTask(now.task.id)
      setStartedTask(now.task)
      setNotice("C'est parti.")
    } catch (failure) {
      if (failure instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setError(errorMessage(failure))
    } finally {
      setActing(false)
    }
  }

  const handleDone = async () => {
    if (!startedTask) {
      return
    }
    setActing(true)
    setError(null)
    try {
      await doneTask(startedTask.id)
      setStartedTask(null)
      setNotice("C'est terminé.")
      await load()
    } catch (failure) {
      if (failure instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setError(errorMessage(failure))
    } finally {
      setActing(false)
    }
  }

  const handleLater = async () => {
    if (!now?.task) {
      return
    }
    setActing(true)
    setError(null)
    try {
      const next = await laterTask(now.task.id)
      setNow(next)
      setNotice(next.message ?? "C'est reporté à demain.")
    } catch (failure) {
      if (failure instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setError(errorMessage(failure))
    } finally {
      setActing(false)
    }
  }

  const handleTooHeavy = async () => {
    if (!now?.task) {
      return
    }
    setActing(true)
    setError(null)
    try {
      const next = await tooHeavyTask(now.task.id)
      setNow(next)
      setNotice(next.message ?? 'Je te propose autre chose.')
    } catch (failure) {
      if (failure instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setError(errorMessage(failure))
    } finally {
      setActing(false)
    }
  }

  const blockLabel = now?.blockType ? BLOCK_LABELS[now.blockType] : 'Hors bloc'

  return (
    <section className="min-w-0 rounded-2xl border border-line bg-paper p-5 lg:sticky lg:top-8">
      <div className="flex items-center justify-between gap-3">
        <p className="text-[11px] font-medium uppercase tracking-[0.18em] text-terracotta">Maintenant</p>
        <span className="text-[11px] text-ink/35">{blockLabel}</span>
      </div>

      <div className="mt-4 flex flex-wrap gap-2" role="group" aria-label="Niveau d'énergie">
        {ENERGY_LEVELS.map((level) => {
          const active = now?.energy === level
          return (
            <button
              key={level}
              type="button"
              onClick={() => handleEnergy(level)}
              disabled={acting}
              aria-pressed={active}
              className={`flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] disabled:opacity-50 ${
                active
                  ? 'border-terracotta/40 bg-terracotta/10 text-ink'
                  : 'border-line text-ink/50 hover:border-ink/25'
              }`}
            >
              <span
                className={`h-2 w-2 rounded-full ${
                  level === 'LOW' ? 'bg-ink/35' : level === 'MID' ? 'bg-terracotta/60' : 'bg-sage'
                }`}
                aria-hidden="true"
              />
              {ENERGY_LABELS[level]}
            </button>
          )
        })}
      </div>

      {loading && <p className="mt-6 text-sm text-ink/50">Chargement…</p>}

      {error && (
        <p role="status" className="mt-6 text-sm leading-relaxed text-terracotta">
          {error}
        </p>
      )}

      {!loading && !error && now && (
        <div className="mt-6">
          {startedTask ? (
            <>
              <p className="text-[11px] font-medium uppercase tracking-[0.16em] text-sage">En cours</p>
              <h2 className="mt-2 break-words font-serif text-2xl leading-tight">{startedTask.title}</h2>
              <p className="mt-2 text-sm text-ink/50">{startedTask.estimateMax} min</p>
              <button
                type="button"
                onClick={handleDone}
                disabled={acting}
                className="mt-5 w-full rounded-xl bg-sage px-4 py-2 text-sm font-medium text-cream hover:bg-sage/90 disabled:opacity-50"
              >
                Terminé
              </button>
            </>
          ) : now.task ? (
            <>
              <p className="text-[11px] font-medium uppercase tracking-[0.16em] text-terracotta">Une chose</p>
              <h2 className="mt-2 break-words font-serif text-2xl leading-tight">{now.task.title}</h2>
              <p className="mt-2 text-sm text-ink/50">
                {now.task.estimateMax} min · {PRIORITY_LABELS[now.task.priority]}
              </p>

              <button
                type="button"
                onClick={handleStart}
                disabled={acting}
                className="mt-5 w-full rounded-xl bg-terracotta px-4 py-2 text-sm font-medium text-cream hover:bg-terracotta/90 disabled:opacity-50"
              >
                Je commence
              </button>
              <div className="mt-2 flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={handleLater}
                  disabled={acting}
                  className="rounded-full border border-ink/15 px-3 py-1.5 text-xs text-ink/60 hover:border-ink/30 hover:text-ink disabled:opacity-50"
                >
                  Plus tard
                </button>
                <button
                  type="button"
                  onClick={handleTooHeavy}
                  disabled={acting}
                  className="rounded-full border border-ink/15 px-3 py-1.5 text-xs text-ink/60 hover:border-ink/30 hover:text-ink disabled:opacity-50"
                >
                  Trop lourd ce soir
                </button>
              </div>
            </>
          ) : (
            <>
              <h2 className="font-serif text-2xl leading-tight">Rien à faire maintenant.</h2>
              <p className="mt-2 text-sm leading-relaxed text-ink/55">
                {now.message === 'Rien à faire maintenant. Repose-toi.' ? 'Repose-toi.' : now.message}
              </p>
            </>
          )}
        </div>
      )}

      {notice && (
        <p aria-live="polite" className="mt-4 text-xs leading-relaxed text-ink/45">
          {notice}
        </p>
      )}

      <div className="mt-6 border-t border-line pt-4">
        <TaskForm onCreated={load} onUnauthorized={onUnauthorized} />
      </div>
    </section>
  )
}
