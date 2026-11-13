import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AgentPanel from './AgentPanel'

function response(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
}

describe('AgentPanel', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('charge la conversation et envoie un message', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/api/agent/messages')) {
        return response([
          {
            id: 1,
            role: 'AGENT',
            content: 'Bonjour, comment puis-je t’aider ?',
            createdAt: '2026-11-12T20:00:00Z',
          },
        ])
      }
      if (url.endsWith('/api/agent/chat') && init?.method === 'POST') {
        return response({
          message: "J'ai ajouté le cours.",
          actions: [{ tool: 'calendar_create', summary: 'Événement créé : « Cours ».' }],
          pendingConfirmation: null,
          undoableActionId: 5,
          error: false,
        })
      }
      return response({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<AgentPanel onUnauthorized={vi.fn()} />)

    expect(await screen.findByText('Bonjour, comment puis-je t’aider ?')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Votre message'), { target: { value: 'Ajoute un cours' } })
    fireEvent.click(screen.getByRole('button', { name: 'Envoyer' }))

    expect(await screen.findByText("J'ai ajouté le cours.")).toBeInTheDocument()
    expect(screen.getByText(/Événement créé : « Cours »/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Annuler la dernière action' })).toBeInTheDocument()

    const postCall = fetchMock.mock.calls.find(
      ([input, init]) => String(input).endsWith('/api/agent/chat') && init?.method === 'POST',
    )
    expect(JSON.parse(String(postCall?.[1]?.body))).toEqual({ message: 'Ajoute un cours' })
  })

  it('joint une image et l’envoie en multipart', async () => {
    const createObjectURL = vi.fn(() => 'blob:plan')
    Object.defineProperty(URL, 'createObjectURL', { value: createObjectURL, configurable: true })
    Object.defineProperty(URL, 'revokeObjectURL', { value: vi.fn(), configurable: true })

    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/api/agent/messages')) {
        return response([])
      }
      if (url.endsWith('/api/agent/chat') && init?.method === 'POST') {
        return response({
          message: 'J’ai lu le plan. Je te propose un créneau.',
          actions: [],
          pendingConfirmation: null,
          undoableActionId: null,
          error: false,
        })
      }
      return response({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<AgentPanel onUnauthorized={vi.fn()} />)
    await waitFor(() => expect(screen.queryByText(/chargement de la conversation/i)).not.toBeInTheDocument())
    const file = new File(['png'], 'plan.png', { type: 'image/png' })
    fireEvent.change(screen.getByLabelText('Joindre une image'), {
      target: { files: [file] },
    })

    expect(screen.getByAltText('Aperçu de l’image jointe')).toHaveAttribute('src', 'blob:plan')
    expect(screen.getByRole('button', { name: 'Envoyer' })).toBeDisabled()
    fireEvent.click(screen.getByLabelText(/J’accepte que cette image/))
    fireEvent.click(screen.getByRole('button', { name: 'Envoyer' }))

    expect(await screen.findByText('J’ai lu le plan. Je te propose un créneau.')).toBeInTheDocument()
    expect(screen.getByAltText('Image jointe à la conversation')).toHaveAttribute('src', 'blob:plan')

    const postCall = fetchMock.mock.calls.find(
      ([input, init]) => String(input).endsWith('/api/agent/chat') && init?.method === 'POST',
    )
    expect(postCall).toBeDefined()
    const body = postCall?.[1]?.body
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('message')).toBe('')
    expect((body as FormData).get('image')).toBe(file)
    expect((body as FormData).get('imageConsent')).toBe('true')
    expect(new Headers(postCall?.[1]?.headers).get('Content-Type')).toBeNull()
  })

  it('refuse une image invalide et retire l’aperçu précédent', async () => {
    Object.defineProperty(URL, 'createObjectURL', { value: vi.fn(() => 'blob:plan'), configurable: true })
    Object.defineProperty(URL, 'revokeObjectURL', { value: vi.fn(), configurable: true })
    vi.stubGlobal('fetch', vi.fn(async () => response([])))
    render(<AgentPanel onUnauthorized={vi.fn()} />)
    await waitFor(() => expect(screen.queryByText(/chargement de la conversation/i)).not.toBeInTheDocument())

    const valid = new File(['png'], 'plan.png', { type: 'image/png' })
    fireEvent.change(screen.getByLabelText('Joindre une image'), { target: { files: [valid] } })
    expect(screen.getByAltText('Aperçu de l’image jointe')).toBeInTheDocument()

    const invalid = new File(['txt'], 'notes.txt', { type: 'text/plain' })
    fireEvent.change(screen.getByLabelText('Joindre une image'), { target: { files: [invalid] } })

    expect(screen.getByRole('alert')).toHaveTextContent('Choisis une image JPEG')
    expect(screen.queryByAltText('Aperçu de l’image jointe')).not.toBeInTheDocument()
  })

  it('demande confirmation avant une suppression', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/api/agent/messages')) {
        return response([])
      }
      if (url.endsWith('/api/agent/chat') && init?.method === 'POST') {
        return response({
          message: 'Supprimer « Cours » du calendrier ?',
          actions: [],
          pendingConfirmation: {
            id: 'pending-1',
            tool: 'calendar_delete',
            summary: 'Supprimer « Cours » du calendrier ?',
          },
          undoableActionId: null,
          error: false,
        })
      }
      if (url.endsWith('/api/agent/confirm/pending-1') && init?.method === 'POST') {
        return response({
          message: "C'est fait. Événement supprimé : « Cours ».",
          actions: [{ tool: 'calendar_delete', summary: 'Événement supprimé : « Cours ».' }],
          pendingConfirmation: null,
          undoableActionId: 6,
          error: false,
        })
      }
      return response({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<AgentPanel onUnauthorized={vi.fn()} />)
    await waitFor(() => expect(screen.queryByText(/chargement de la conversation/i)).not.toBeInTheDocument())
    fireEvent.change(screen.getByLabelText('Votre message'), {
      target: { value: 'Supprime le cours' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Envoyer' }))

    expect(await screen.findByRole('button', { name: 'Confirmer' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Confirmer' }))

    expect(await screen.findByText("C'est fait. Événement supprimé : « Cours ».")).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Confirmer' })).not.toBeInTheDocument()
  })

  it('annule la dernière action depuis le chat', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/api/agent/messages')) {
        return response([])
      }
      if (url.endsWith('/api/agent/chat') && init?.method === 'POST') {
        return response({
          message: "J'ai créé le cours.",
          actions: [{ tool: 'calendar_create', summary: 'Événement créé : « Cours ».' }],
          pendingConfirmation: null,
          undoableActionId: 7,
          error: false,
        })
      }
      if (url.endsWith('/api/journal/7/undo') && init?.method === 'POST') {
        return response([])
      }
      return response({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<AgentPanel onUnauthorized={vi.fn()} />)
    await waitFor(() => expect(screen.queryByText(/chargement de la conversation/i)).not.toBeInTheDocument())
    fireEvent.change(screen.getByLabelText('Votre message'), { target: { value: 'Crée un cours' } })
    fireEvent.click(screen.getByRole('button', { name: 'Envoyer' }))
    await screen.findByRole('button', { name: 'Annuler la dernière action' })

    fireEvent.click(screen.getByRole('button', { name: 'Annuler la dernière action' }))

    expect(await screen.findByText('Dernière action annulée.')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Annuler la dernière action' }),
    ).not.toBeInTheDocument()
  })
})
