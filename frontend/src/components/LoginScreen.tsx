import { useState, type FormEvent } from 'react'
import { LOCAL_EMAIL, login, type AuthUser } from '../api/auth'
import { errorMessage } from '../api/client'

interface LoginScreenProps {
  onAuthenticated: (user: AuthUser) => void
}

export default function LoginScreen({ onAuthenticated }: LoginScreenProps) {
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(null)

    if (!password.trim()) {
      setError('Le mot de passe est obligatoire.')
      return
    }

    setSubmitting(true)
    try {
      onAuthenticated(await login(password))
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-cream px-6 py-12 text-ink">
      <div className="w-full max-w-md">
        <p className="text-xs font-medium uppercase tracking-[0.22em] text-terracotta">Calme</p>
        <h1 className="mt-4 font-serif text-4xl leading-tight">Un mot de passe suffit.</h1>
        <p className="mt-4 text-sm leading-relaxed text-ink/55">
          Compte local : <span className="text-ink/70">{LOCAL_EMAIL}</span>
        </p>

        <form
          onSubmit={handleSubmit}
          className="mt-8 rounded-3xl border border-ink/10 bg-white/55 p-6 shadow-[0_24px_60px_-48px_rgba(43,38,32,0.55)]"
        >
          <label className="block">
            <span className="text-xs font-medium uppercase tracking-[0.14em] text-ink/55">Mot de passe</span>
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoFocus
              autoComplete="current-password"
              className="mt-1 w-full rounded-xl border border-ink/15 bg-cream/60 px-3 py-2 text-sm outline-none transition focus:border-terracotta/60"
            />
          </label>

          {error && (
            <p role="alert" aria-live="assertive" className="mt-4 text-sm leading-relaxed text-terracotta">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="mt-5 w-full rounded-full bg-terracotta px-4 py-2 text-sm font-medium text-cream transition hover:bg-terracotta/90 disabled:opacity-50"
          >
            {submitting ? 'Ouverture…' : 'Entrer'}
          </button>
        </form>

        <p className="mt-6 text-xs leading-relaxed text-ink/40">
          Session locale, sans 2FA. Le mot de passe est défini dans <code className="text-ink/55">.env</code>.
        </p>
      </div>
    </main>
  )
}
