import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

function response(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
}

type Handler = (url: string, init?: RequestInit) => Response

function stubFetch(handler: Handler) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => handler(String(input), init))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const LOCAL_EMAIL = 'local@calme.invalid'
const REST_NOW = {
  task: null,
  energy: 'GOOD',
  blockType: null,
  capacityMinutes: 0,
  message: 'Rien à faire maintenant. Repose-toi.',
}

describe('App authentification', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('affiche l’écran de connexion sans session', async () => {
    stubFetch((url) => {
      if (url.includes('/api/auth/me')) {
        return response({}, 401)
      }
      return response({}, 404)
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: /un mot de passe suffit/i })).toBeInTheDocument()
    expect(screen.getByText(LOCAL_EMAIL)).toBeInTheDocument()
  })

  it('se connecte et affiche l’agent', async () => {
    let loginBody: Record<string, unknown> | null = null
    stubFetch((url, init) => {
      if (url.includes('/api/auth/me')) {
        return response({}, 401)
      }
      if (url.includes('/api/auth/login')) {
        loginBody = JSON.parse(String(init?.body)) as Record<string, unknown>
        return response({ email: LOCAL_EMAIL })
      }
      if (url.includes('/api/now')) {
        return response(REST_NOW)
      }
      if (url.includes('/api/events')) {
        return response([])
      }
      if (url.includes('/api/agent/messages')) {
        return response([])
      }
      return response({}, 404)
    })

    render(<App />)
    await screen.findByRole('heading', { name: /un mot de passe suffit/i })

    fireEvent.change(screen.getByLabelText('Mot de passe'), { target: { value: 'secret' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrer' }))

    expect(await screen.findByRole('heading', { name: /une journée à la fois/i })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: /parle à calme/i })).toBeInTheDocument()
    expect(loginBody).toEqual({ password: 'secret' })
  })

  it('ouvre directement l’agent avec une session existante', async () => {
    stubFetch((url) => {
      if (url.includes('/api/auth/me')) {
        return response({ email: LOCAL_EMAIL })
      }
      if (url.includes('/api/now')) {
        return response(REST_NOW)
      }
      if (url.includes('/api/events')) {
        return response([])
      }
      if (url.includes('/api/agent/messages')) {
        return response([])
      }
      return response({}, 404)
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: /une journée à la fois/i })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: /parle à calme/i })).toBeInTheDocument()
  })

  it('affiche calmement un mauvais mot de passe', async () => {
    stubFetch((url) => {
      if (url.includes('/api/auth/me')) {
        return response({}, 401)
      }
      if (url.includes('/api/auth/login')) {
        return response(
          { title: 'Identifiants invalides', detail: 'Le mot de passe ne correspond pas.' },
          401,
        )
      }
      return response({}, 404)
    })

    render(<App />)
    await screen.findByRole('heading', { name: /un mot de passe suffit/i })

    fireEvent.change(screen.getByLabelText('Mot de passe'), { target: { value: 'wrong' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrer' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Le mot de passe ne correspond pas.')
  })

  it('revient à la connexion après déconnexion', async () => {
    let loggedOut = false
    stubFetch((url, init) => {
      if (url.includes('/api/auth/me')) {
        return response({ email: LOCAL_EMAIL })
      }
      if (url.includes('/api/now')) {
        return response(REST_NOW)
      }
      if (url.includes('/api/events')) {
        return response([])
      }
      if (url.includes('/api/agent/messages')) {
        return response([])
      }
      if (url.includes('/api/auth/logout') && init?.method === 'POST') {
        loggedOut = true
        return response(undefined, 204)
      }
      return response({}, 404)
    })

    render(<App />)
    await screen.findByRole('heading', { name: /une journée à la fois/i })

    fireEvent.click(screen.getByRole('button', { name: /se déconnecter/i }))

    expect(await screen.findByRole('heading', { name: /un mot de passe suffit/i })).toBeInTheDocument()
    expect(loggedOut).toBe(true)
  })
})
