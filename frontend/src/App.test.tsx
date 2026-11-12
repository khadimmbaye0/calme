import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import type { CalendarEvent, EventInput } from './api/events'

function response(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
}

function todayAt(hours: number, minutes = 0): Date {
  const date = new Date()
  date.setHours(hours, minutes, 0, 0)
  return date
}

function eventAt(
  id: number,
  title: string,
  startAt: Date,
  endAt: Date,
  kind: CalendarEvent['kind'] = 'FIXED',
): CalendarEvent {
  return {
    id,
    title,
    startAt: startAt.toISOString(),
    endAt: endAt.toISOString(),
    kind,
    createdBy: 'USER',
  }
}

describe('App calendrier', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('affiche les événements du jour et la barre maintenant', async () => {
    const event = eventAt(1, 'Cours', todayAt(9), todayAt(10))
    vi.stubGlobal('fetch', vi.fn(async () => response([event])))

    render(<App />)

    expect(screen.getByRole('heading', { name: /une journée à la fois/i })).toBeInTheDocument()
    expect(await screen.findByText('Cours')).toBeInTheDocument()
    expect(screen.getByText(/maintenant/i)).toBeInTheDocument()
  })

  it('ajoute un moment et recharge la journée', async () => {
    let store: CalendarEvent[] = []
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      if (method === 'GET') {
        return response(store)
      }
      if (method === 'POST') {
        const input = JSON.parse(String(init?.body)) as EventInput
        const created: CalendarEvent = { id: 2, ...input, createdBy: 'USER' }
        store = [created]
        return response(created, 201)
      }
      return response({}, 500)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)
    await screen.findByText(/rien de prévu/i)

    fireEvent.change(screen.getByLabelText('Titre'), { target: { value: 'Relire le plan' } })
    fireEvent.change(screen.getByLabelText('Début'), { target: { value: '09:00' } })
    fireEvent.change(screen.getByLabelText('Fin'), { target: { value: '09:30' } })
    fireEvent.click(screen.getByRole('button', { name: 'Ajouter' }))

    expect(await screen.findByText('Relire le plan')).toBeInTheDocument()

    const postCall = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')
    expect(postCall).toBeDefined()
    const body = JSON.parse(String(postCall?.[1]?.body)) as EventInput
    expect(body.title).toBe('Relire le plan')
    expect(body.kind).toBe('FIXED')
    expect(body.startAt).toMatch(/T09:00:00/)
  })

  it('modifie un moment sélectionné', async () => {
    const event = eventAt(1, 'Cours', todayAt(9), todayAt(10))
    let store: CalendarEvent[] = [event]
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      if (method === 'GET') {
        return response(store)
      }
      if (method === 'PUT') {
        const input = JSON.parse(String(init?.body)) as EventInput
        store = store.map((item) => (item.id === event.id ? { ...item, ...input } : item))
        return response(store[0])
      }
      return response({}, 500)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)
    fireEvent.click(await screen.findByRole('button', { name: /Cours/ }))
    fireEvent.change(screen.getByLabelText('Titre'), { target: { value: 'Cours décalé' } })
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }))

    expect(await screen.findByText('Cours décalé')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(true)
  })

  it('affiche calmement un conflit signalé par le serveur', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      if ((init?.method ?? 'GET') === 'GET') {
        return response([])
      }
      return response({ title: 'Créneau indisponible', detail: 'Ce créneau chevauche « Cours ».' }, 409)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)
    await screen.findByText(/rien de prévu/i)

    fireEvent.change(screen.getByLabelText('Titre'), { target: { value: 'Réunion' } })
    fireEvent.click(screen.getByRole('button', { name: 'Ajouter' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ce créneau chevauche « Cours ».')
    expect(screen.queryByText(/erreur/i)).not.toBeInTheDocument()
  })

  it('affiche un message calme quand le serveur ne répond pas', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    render(<App />)

    expect(await screen.findByText(/pas joignable pour le moment/i)).toBeInTheDocument()
  })

  it('permet de supprimer un moment sélectionné', async () => {
    const event = eventAt(1, 'Cours', todayAt(9), todayAt(10))
    let store: CalendarEvent[] = [event]
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      if (method === 'GET') {
        return response(store)
      }
      if (method === 'DELETE') {
        store = []
        return response(undefined, 204)
      }
      return response({}, 500)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)
    fireEvent.click(await screen.findByRole('button', { name: /Cours/ }))
    fireEvent.click(screen.getByRole('button', { name: /supprimer ce moment/i }))

    await waitFor(() => {
      expect(screen.queryByText('Cours')).not.toBeInTheDocument()
    })
    expect(screen.getByText(/rien de prévu/i)).toBeInTheDocument()
  })
})
