import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { errorMessage, UnauthorizedError } from '../api/client'
import { doneTask } from '../api/planner'
import {
  createProject,
  fetchProject,
  fetchProjects,
  type ProjectDetail,
  type ProjectSummary,
} from '../api/projects'

interface ProjectsPanelProps {
  onUnauthorized: () => void
}

interface StepDraft {
  title: string
  estimate: number
  dependsOn: number[]
}

const STATUS_LABELS: Record<string, string> = {
  TODO: 'À faire',
  DOING: 'En cours',
  DONE: 'Fait',
  DEFERRED: 'Reporté',
}

function blankStep(): StepDraft {
  return { title: '', estimate: 30, dependsOn: [] }
}

function defaultDeadline(): string {
  const date = new Date()
  date.setDate(date.getDate() + 14)
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-')
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('fr-FR', {
    day: 'numeric',
    month: 'short',
  }).format(new Date(`${value}T12:00:00`))
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('fr-FR', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function ProgressBar({ value }: { value: number }) {
  return (
    <div className="h-1.5 overflow-hidden rounded-full bg-ink/10">
      <div className="h-full rounded-full bg-sage" style={{ width: `${Math.round(value * 100)}%` }} />
    </div>
  )
}

export default function ProjectsPanel({ onUnauthorized }: ProjectsPanelProps) {
  const [projects, setProjects] = useState<ProjectSummary[]>([])
  const [selected, setSelected] = useState<ProjectDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [actingTaskId, setActingTaskId] = useState<number | null>(null)

  const [title, setTitle] = useState('')
  const [goal, setGoal] = useState('')
  const [deadline, setDeadline] = useState(defaultDeadline)
  const [steps, setSteps] = useState<StepDraft[]>([blankStep()])

  const loadProjects = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setProjects(await fetchProjects())
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
    void loadProjects()
  }, [loadProjects])

  const resetForm = () => {
    setTitle('')
    setGoal('')
    setDeadline(defaultDeadline())
    setSteps([blankStep()])
    setFormError(null)
  }

  const startCreate = () => {
    resetForm()
    setSelected(null)
    setCreating(true)
  }

  const openProject = async (id: number) => {
    setError(null)
    try {
      setSelected(await fetchProject(id))
    } catch (failure) {
      if (failure instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setError(errorMessage(failure))
    }
  }

  const updateStep = (index: number, patch: Partial<StepDraft>) => {
    setSteps((current) =>
      current.map((step, stepIndex) => (stepIndex === index ? { ...step, ...patch } : step)),
    )
  }

  const removeStep = (index: number) => {
    setSteps((current) =>
      current
        .filter((_, stepIndex) => stepIndex !== index)
        .map((step) => ({
          ...step,
          dependsOn: step.dependsOn
            .filter((dependency) => dependency !== index)
            .map((dependency) => (dependency > index ? dependency - 1 : dependency)),
        })),
    )
  }

  const toggleDependency = (stepIndex: number, dependencyIndex: number) => {
    setSteps((current) =>
      current.map((step, index) => {
        if (index !== stepIndex) {
          return step
        }
        return {
          ...step,
          dependsOn: step.dependsOn.includes(dependencyIndex)
            ? step.dependsOn.filter((dependency) => dependency !== dependencyIndex)
            : [...step.dependsOn, dependencyIndex],
        }
      }),
    )
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setFormError(null)

    if (!title.trim() || !goal.trim()) {
      setFormError('Le titre et l’objectif sont obligatoires.')
      return
    }
    if (steps.some((step) => !step.title.trim())) {
      setFormError('Chaque étape doit avoir un titre.')
      return
    }

    setSubmitting(true)
    try {
      const created = await createProject({
        title: title.trim(),
        goal: goal.trim(),
        deadline,
        bufferRatio: 0.2,
        steps: steps.map((step) => ({
          title: step.title.trim(),
          estimateMin: step.estimate,
          estimateMax: step.estimate,
          priority: 'NORMAL',
          dependsOn: step.dependsOn,
        })),
      })
      setSelected(created)
      setCreating(false)
      resetForm()
      await loadProjects()
    } catch (failure) {
      if (failure instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setFormError(errorMessage(failure))
    } finally {
      setSubmitting(false)
    }
  }

  const handleDone = async (taskId: number) => {
    setActingTaskId(taskId)
    setError(null)
    try {
      await doneTask(taskId)
      if (selected) {
        setSelected(await fetchProject(selected.id))
      }
      await loadProjects()
    } catch (failure) {
      if (failure instanceof UnauthorizedError) {
        onUnauthorized()
        return
      }
      setError(errorMessage(failure))
    } finally {
      setActingTaskId(null)
    }
  }

  return (
    <section className="min-w-0 rounded-2xl border border-line bg-paper p-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-[11px] font-medium uppercase tracking-[0.18em] text-terracotta">Projets</p>
          <h2 className="mt-1 font-serif text-2xl leading-tight">Des plans avec de la marge.</h2>
        </div>
        {!creating && (
          <button
            type="button"
            onClick={startCreate}
            className="rounded-full border border-ink/15 px-4 py-1.5 text-sm text-ink/70 hover:border-ink/30 hover:text-ink"
          >
            Nouveau projet
          </button>
        )}
      </div>

      {error && (
        <p role="status" className="mt-4 text-sm leading-relaxed text-terracotta">
          {error}
        </p>
      )}

      {loading && <p className="mt-6 text-sm text-ink/50">Chargement des projets…</p>}

      {!loading && creating && (
        <form onSubmit={handleSubmit} className="mt-6 space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block">
              <span className="text-[11px] font-medium uppercase tracking-[0.14em] text-ink/45">
                Titre du projet
              </span>
              <input
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                maxLength={160}
                placeholder="Mémoire"
                className="mt-1 w-full rounded-xl border border-ink/15 bg-wash px-3 py-2 text-sm outline-none focus:border-terracotta/60"
              />
            </label>
            <label className="block">
              <span className="text-[11px] font-medium uppercase tracking-[0.14em] text-ink/45">
                Échéance
              </span>
              <input
                type="date"
                value={deadline}
                onChange={(event) => setDeadline(event.target.value)}
                className="mt-1 w-full rounded-xl border border-ink/15 bg-wash px-3 py-2 text-sm outline-none focus:border-terracotta/60"
              />
            </label>
          </div>

          <label className="block">
            <span className="text-[11px] font-medium uppercase tracking-[0.14em] text-ink/45">Objectif</span>
            <textarea
              value={goal}
              onChange={(event) => setGoal(event.target.value)}
              rows={2}
              maxLength={2000}
              placeholder="Finir le chapitre 2 avec une relecture."
              className="mt-1 w-full rounded-xl border border-ink/15 bg-wash px-3 py-2 text-sm outline-none focus:border-terracotta/60"
            />
          </label>

          <p className="text-xs text-ink/45">
            Marge de 20 % incluse. Les étapes sont placées après leurs dépendances.
          </p>

          <div className="space-y-3">
            {steps.map((step, index) => (
              <div key={index} className="rounded-2xl border border-line bg-wash/50 p-4">
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-medium uppercase tracking-[0.14em] text-ink/45">
                    Étape {index + 1}
                  </span>
                  {steps.length > 1 && (
                    <button
                      type="button"
                      onClick={() => removeStep(index)}
                      className="text-[11px] text-ink/40 underline-offset-4 hover:text-ink hover:underline"
                    >
                      Retirer
                    </button>
                  )}
                </div>

                <label className="mt-2 block">
                  <span className="sr-only">Titre de l'étape {index + 1}</span>
                  <input
                    value={step.title}
                    onChange={(event) => updateStep(index, { title: event.target.value })}
                    maxLength={160}
                    placeholder={`Titre de l'étape ${index + 1}`}
                    className="w-full rounded-xl border border-ink/15 bg-wash px-3 py-2 text-sm outline-none focus:border-terracotta/60"
                  />
                </label>

                <div className="mt-2 grid gap-3 sm:grid-cols-2">
                  <label className="block">
                    <span className="text-[11px] font-medium uppercase tracking-[0.14em] text-ink/45">
                      Durée de l'étape {index + 1}
                    </span>
                    <select
                      value={step.estimate}
                      onChange={(event) => updateStep(index, { estimate: Number(event.target.value) })}
                      className="mt-1 w-full rounded-xl border border-ink/15 bg-wash px-3 py-2 text-sm outline-none focus:border-terracotta/60"
                    >
                      {[15, 30, 45, 60].map((minutes) => (
                        <option key={minutes} value={minutes}>
                          {minutes} min
                        </option>
                      ))}
                    </select>
                  </label>

                  {index > 0 && (
                    <div>
                      <span className="text-[11px] font-medium uppercase tracking-[0.14em] text-ink/45">
                        Dépend de
                      </span>
                      <div className="mt-1 flex flex-wrap gap-2">
                        {steps.slice(0, index).map((_, dependencyIndex) => (
                          <label
                            key={dependencyIndex}
                            className="flex items-center gap-1.5 rounded-full border border-line px-2 py-1 text-[11px] text-ink/55"
                          >
                            <input
                              type="checkbox"
                              checked={step.dependsOn.includes(dependencyIndex)}
                              onChange={() => toggleDependency(index, dependencyIndex)}
                              className="accent-terracotta"
                            />
                            Étape {dependencyIndex + 1}
                          </label>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>

          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => setSteps((current) => [...current, blankStep()])}
              className="rounded-full border border-ink/15 px-4 py-2 text-sm text-ink/70 hover:border-ink/30 hover:text-ink"
            >
              Ajouter une étape
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="rounded-xl bg-terracotta px-4 py-2 text-sm font-medium text-cream hover:bg-terracotta/90 disabled:opacity-50"
            >
              {submitting ? 'Planification…' : 'Créer le projet'}
            </button>
            <button
              type="button"
              onClick={() => {
                setCreating(false)
                resetForm()
              }}
              className="rounded-full border border-ink/15 px-4 py-2 text-sm text-ink/70 hover:border-ink/30 hover:text-ink"
            >
              Annuler
            </button>
          </div>

          {formError && (
            <p role="alert" className="text-sm leading-relaxed text-terracotta">
              {formError}
            </p>
          )}
        </form>
      )}

      {!loading && !creating && selected && (
        <div className="mt-6">
          <button
            type="button"
            onClick={() => setSelected(null)}
            className="text-xs text-ink/45 underline-offset-4 hover:text-ink hover:underline"
          >
            ← Tous les projets
          </button>

          <h3 className="mt-3 break-words font-serif text-2xl leading-tight">{selected.title}</h3>
          <p className="mt-1 break-words text-sm leading-relaxed text-ink/55">{selected.goal}</p>
          <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-ink/45">
            <span>Échéance {formatDate(selected.deadline)}</span>
            <span>Marge {Math.round(selected.bufferRatio * 100)} %</span>
            <span>
              {selected.doneSteps}/{selected.totalSteps} étapes
            </span>
          </div>
          <div className="mt-4">
            <ProgressBar value={selected.progress} />
          </div>

          <div className="mt-5 space-y-2">
            {selected.steps.map((step) => (
              <div key={step.taskId} className="rounded-2xl border border-line bg-wash/50 p-3">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="break-words text-sm font-medium">{step.title}</p>
                    <p className="mt-1 text-xs text-ink/45">
                      {step.estimateMax} min · {STATUS_LABELS[step.status] ?? step.status}
                    </p>
                    {step.scheduledStart && step.scheduledEnd && (
                      <p className="mt-1 text-xs text-ink/40">
                        {formatDateTime(step.scheduledStart)} – {formatDateTime(step.scheduledEnd)}
                      </p>
                    )}
                    {step.dependsOnTaskIds.length > 0 && (
                      <p className="mt-1 text-xs text-ink/40">
                        Après :{' '}
                        {step.dependsOnTaskIds
                          .map((id) => selected.steps.find((candidate) => candidate.taskId === id)?.title ?? '')
                          .filter(Boolean)
                          .join(', ')}
                      </p>
                    )}
                  </div>
                  {step.status !== 'DONE' && (
                    <button
                      type="button"
                      onClick={() => handleDone(step.taskId)}
                      disabled={actingTaskId === step.taskId}
                      className="rounded-full border border-ink/15 px-3 py-1.5 text-xs text-ink/60 hover:border-ink/30 hover:text-ink disabled:opacity-50"
                    >
                      Terminer
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {!loading && !creating && !selected && (
        <div className="mt-6">
          {projects.length === 0 ? (
            <p className="text-sm leading-relaxed text-ink/55">
              Aucun projet. Crée un plan avec des étapes, des durées et des dépendances.
            </p>
          ) : (
            <div className="space-y-2">
              {projects.map((project) => (
                <button
                  key={project.id}
                  type="button"
                  onClick={() => openProject(project.id)}
                  className="w-full rounded-2xl border border-line bg-wash/50 p-4 text-left hover:border-ink/30"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="break-words text-sm font-medium">{project.title}</p>
                      <p className="mt-1 text-xs text-ink/45">
                        Échéance {formatDate(project.deadline)} · {project.doneSteps}/{project.totalSteps} étapes
                      </p>
                    </div>
                    <span className="shrink-0 text-xs text-ink/45">{Math.round(project.progress * 100)} %</span>
                  </div>
                  <div className="mt-3">
                    <ProgressBar value={project.progress} />
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  )
}
