import { fireEvent, render, screen } from '@testing-library/react'
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
