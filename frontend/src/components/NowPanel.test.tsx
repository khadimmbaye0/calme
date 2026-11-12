import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import NowPanel from './NowPanel'
import type { NowResponse } from '../api/planner'

function response(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
}

const TASK = { id: 1, title: 'Relire le plan', estimateMax: 15, priority: 'NORMAL' as const }

const REST_NOW: NowResponse = {
  task: null,
  energy: 'GOOD' as const,
  blockType: 'SCHOOL' as const,
  capacityMinutes: 162,
  message: 'Rien à faire maintenant. Repose-toi.',
}

const TASK_NOW: NowResponse = {
  task: TASK,
  energy: 'GOOD' as const,
  blockType: 'SCHOOL' as const,
  capacityMinutes: 162,
  message: null,
}

describe('NowPanel', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('affiche un message de repos quand rien n’est dû', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response(REST_NOW)))

    render(<NowPanel onUnauthorized={vi.fn()} />)

    expect(await screen.findByText('Rien à faire maintenant.')).toBeInTheDocument()
    expect(screen.getByText('Repose-toi.')).toBeInTheDocument()
    expect(screen.getByText('École')).toBeInTheDocument()
  })

  it('démarre puis termine une tâche', async () => {
    let now: NowResponse = TASK_NOW
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (url.includes('/api/now') && method === 'GET') {
        return response(now)
      }
      if (url.includes('/api/now/start')) {
        return response({ ...TASK, estimateMin: 15, status: 'DOING', dueDate: '2026-11-12' })
      }
      if (url.includes('/api/tasks/1/done')) {
        now = REST_NOW
        return response(undefined, 204)
      }
      return response({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<NowPanel onUnauthorized={vi.fn()} />)

    expect(await screen.findByText('Relire le plan')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Je commence' }))
    expect(await screen.findByText('En cours')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Terminé' }))
    expect(await screen.findByText('Rien à faire maintenant.')).toBeInTheDocument()
  })

  it('reporte une tâche avec Plus tard', async () => {
    let now: NowResponse = TASK_NOW
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (url.includes('/api/now') && method === 'GET') {
        return response(now)
      }
      if (url.includes('/api/now/later')) {
        now = { ...REST_NOW, message: "C'est reporté à demain." }
        return response(now)
      }
      return response({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<NowPanel onUnauthorized={vi.fn()} />)

    expect(await screen.findByText('Relire le plan')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Plus tard' }))

    expect((await screen.findAllByText(/reporté à demain/i)).length).toBeGreaterThan(0)
  })

  it('propose autre chose avec Trop lourd ce soir', async () => {
    let now: NowResponse = TASK_NOW
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (url.includes('/api/now') && method === 'GET') {
        return response(now)
      }
      if (url.includes('/api/now/too-heavy')) {
        now = { ...REST_NOW, message: "Rien d'autre ce soir. Repose-toi." }
        return response(now)
      }
      return response({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<NowPanel onUnauthorized={vi.fn()} />)

    expect(await screen.findByText('Relire le plan')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /trop lourd ce soir/i }))

    expect((await screen.findAllByText(/Rien d'autre ce soir/i)).length).toBeGreaterThan(0)
  })

  it('allège la journée quand l’énergie passe à bas', async () => {
    let now: NowResponse = TASK_NOW
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (url.includes('/api/now') && method === 'GET') {
        return response(now)
      }
      if (url.includes('/api/energy/today')) {
        now = { ...REST_NOW, energy: 'LOW' }
        return response({ date: '2026-11-12', level: 'LOW', deferredCount: 1 })
      }
      return response({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<NowPanel onUnauthorized={vi.fn()} />)

    expect(await screen.findByText('Relire le plan')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Bas/ }))

    expect(await screen.findByText('La journée a été allégée.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Bas/ })).toHaveAttribute('aria-pressed', 'true')
  })

  it('ajoute une tâche manuellement', async () => {
    let now: NowResponse = REST_NOW
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (url.includes('/api/now') && method === 'GET') {
        return response(now)
      }
      if (url.includes('/api/tasks') && method === 'POST') {
        now = TASK_NOW
        return response(
          { ...TASK, estimateMin: 15, status: 'TODO', dueDate: '2026-11-12' },
          201,
        )
      }
      return response({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<NowPanel onUnauthorized={vi.fn()} />)
    await screen.findByText('Rien à faire maintenant.')

    fireEvent.click(screen.getByText('Ajouter une tâche'))
    fireEvent.change(screen.getByLabelText('Titre de la tâche'), {
      target: { value: 'Relire le plan' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Ajouter la tâche' }))

    expect(await screen.findByText('Relire le plan')).toBeInTheDocument()
    const postCall = fetchMock.mock.calls.find(
      ([input, init]) => String(input).includes('/api/tasks') && init?.method === 'POST',
    )
    expect(postCall).toBeDefined()
  })
})
