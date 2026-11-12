import { useEffect, useState } from 'react'

type ServerStatus = 'checking' | 'ready' | 'unavailable'

const STATUS_COPY: Record<ServerStatus, string> = {
  checking: 'Vérification du socle…',
  ready: 'Serveur et base de données prêts.',
  unavailable: 'Serveur non joignable pour le moment.',
}

const STATUS_DOT: Record<ServerStatus, string> = {
  checking: 'bg-terracotta/50',
  ready: 'bg-sage',
  unavailable: 'bg-ink/25',
}

export default function App() {
  const [status, setStatus] = useState<ServerStatus>('checking')

  useEffect(() => {
    let active = true

    fetch('/api/health')
      .then((response) => {
        if (!response.ok) {
          throw new Error('Health check failed')
        }
        return response.json() as Promise<{ status?: string }>
      })
      .then((body) => {
        if (active) {
          setStatus(body.status === 'UP' ? 'ready' : 'unavailable')
        }
      })
      .catch(() => {
        if (active) {
          setStatus('unavailable')
        }
      })

    return () => {
      active = false
    }
  }, [])

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-3xl flex-col justify-center px-6 py-16 sm:px-10">
      <p className="text-xs font-medium uppercase tracking-[0.22em] text-terracotta">Calme</p>
      <h1 className="mt-4 font-serif text-4xl leading-[1.1] text-balance sm:text-6xl">
        Une chose à la fois.
      </h1>
      <p className="mt-6 max-w-lg text-base leading-relaxed text-ink/70 sm:text-lg">
        Le socle technique est en place. Le calendrier, l'agent et les projets arriveront par petites étapes.
      </p>

      <section
        aria-live="polite"
        className="mt-10 rounded-3xl border border-ink/10 bg-white/50 p-6 shadow-[0_24px_60px_-48px_rgba(43,38,32,0.55)] sm:p-8"
      >
        <div className="flex items-center gap-3">
          <span className={`h-2.5 w-2.5 rounded-full ${STATUS_DOT[status]}`} aria-hidden="true" />
          <p className="text-sm text-ink/75">{STATUS_COPY[status]}</p>
        </div>
        <p className="mt-4 text-sm leading-relaxed text-ink/55">
          Pas encore d'authentification, d'horaires par défaut ni d'email : ces choix restent à décider.
        </p>
      </section>

      <p className="mt-10 text-sm text-ink/45">
        Prochaine étape : créer et modifier des événements à la main.
      </p>
    </main>
  )
}
