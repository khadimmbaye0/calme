import { useEffect, useState } from 'react'
import { fetchCurrentUser, type AuthUser } from './api/auth'
import CalendarScreen from './components/CalendarScreen'
import LoginScreen from './components/LoginScreen'

export default function App() {
  const [user, setUser] = useState<AuthUser | null | undefined>(undefined)

  useEffect(() => {
    let active = true
    fetchCurrentUser()
      .then((current) => {
        if (active) {
          setUser(current)
        }
      })
      .catch(() => {
        if (active) {
          setUser(null)
        }
      })
    return () => {
      active = false
    }
  }, [])

  if (user === undefined) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-cream px-6 text-ink">
        <p className="text-sm text-ink/55">Ouverture de Calme…</p>
      </main>
    )
  }

  if (user === null) {
    return <LoginScreen onAuthenticated={(authenticated) => setUser(authenticated)} />
  }

  return (
    <CalendarScreen
      user={user}
      onLoggedOut={() => setUser(null)}
      onUnauthorized={() => setUser(null)}
    />
  )
}
