import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ProjectsPanel from './ProjectsPanel'
import type { ProjectDetail, ProjectSummary } from '../api/projects'

function response(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
}

const SUMMARY: ProjectSummary = {
  id: 1,
  title: 'Mémoire',
  deadline: '2026-11-30',
  status: 'ACTIVE',
  totalSteps: 2,
  doneSteps: 0,
  progress: 0,
}

const DETAIL: ProjectDetail = {
  ...SUMMARY,
  goal: 'Finir le chapitre 2',
  bufferRatio: 0.2,
  steps: [
    {
      taskId: 1,
      title: 'Cadrage',
      estimateMin: 30,
      estimateMax: 30,
      priority: 'NORMAL',
      status: 'TODO',
      dueDate: '2026-11-12',
      scheduledStart: '2026-11-12T17:30:00Z',
      scheduledEnd: '2026-11-12T18:00:00Z',
      dependsOnTaskIds: [],
    },
    {
      taskId: 2,
      title: 'Rédaction',
      estimateMin: 60,
      estimateMax: 60,
      priority: 'NORMAL',
      status: 'TODO',
      dueDate: '2026-11-12',
      scheduledStart: '2026-11-12T18:00:00Z',
      scheduledEnd: '2026-11-12T19:00:00Z',
      dependsOnTaskIds: [1],
    },
  ],
}

describe('ProjectsPanel', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('affiche la liste des projets et leur avancement', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response([SUMMARY])))

    render(<ProjectsPanel onUnauthorized={vi.fn()} />)

    expect(await screen.findByText('Mémoire')).toBeInTheDocument()
    expect(screen.getByText(/0\/2 étapes/)).toBeInTheDocument()
  })

  it('ouvre le détail, les dépendances et les créneaux', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).endsWith('/api/projects/1')) {
        return response(DETAIL)
      }
      return response([SUMMARY])
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<ProjectsPanel onUnauthorized={vi.fn()} />)
    fireEvent.click(await screen.findByRole('button', { name: /Mémoire/ }))

    expect(await screen.findByText('Finir le chapitre 2')).toBeInTheDocument()
    expect(screen.getByText('Rédaction')).toBeInTheDocument()
    expect(screen.getByText(/Après : Cadrage/)).toBeInTheDocument()
    expect(screen.getByText(/Marge 20 %/)).toBeInTheDocument()
  })

  it('crée un projet avec une étape et une marge de 20 %', async () => {
    let list: ProjectSummary[] = []
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (url.endsWith('/api/projects') && method === 'GET') {
        return response(list)
      }
      if (url.endsWith('/api/projects') && method === 'POST') {
        list = [SUMMARY]
        return response(DETAIL, 201)
      }
      return response({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<ProjectsPanel onUnauthorized={vi.fn()} />)
    await screen.findByText(/Aucun projet/);
    fireEvent.click(screen.getByRole('button', { name: 'Nouveau projet' }))
    fireEvent.change(screen.getByLabelText('Titre du projet'), { target: { value: 'Mémoire' } })
    fireEvent.change(screen.getByLabelText('Objectif'), { target: { value: 'Finir le chapitre 2' } })
    fireEvent.change(screen.getByLabelText("Titre de l'étape 1"), { target: { value: 'Cadrage' } })
    fireEvent.click(screen.getByRole('button', { name: 'Créer le projet' }))

    expect(await screen.findByText('Mémoire')).toBeInTheDocument()
    const postCall = fetchMock.mock.calls.find(
      ([input, init]) => String(input).endsWith('/api/projects') && init?.method === 'POST',
    )
    expect(postCall).toBeDefined()
    const body = JSON.parse(String(postCall?.[1]?.body)) as {
      bufferRatio: number
      steps: { title: string; dependsOn: number[] }[]
    }
    expect(body.bufferRatio).toBe(0.2)
    expect(body.steps).toHaveLength(1)
    expect(body.steps[0]?.title).toBe('Cadrage')
  })

  it('affiche calmement un refus de planification', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/api/projects') && (init?.method ?? 'GET') === 'GET') {
        return response([])
      }
      return response(
        { title: 'Projet non planifiable', detail: 'Les créneaux disponibles ne suffisent pas.' },
        409,
      )
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<ProjectsPanel onUnauthorized={vi.fn()} />)
    await screen.findByText(/Aucun projet/);
    fireEvent.click(screen.getByRole('button', { name: 'Nouveau projet' }))
    fireEvent.change(screen.getByLabelText('Titre du projet'), { target: { value: 'Trop long' } })
    fireEvent.change(screen.getByLabelText('Objectif'), { target: { value: 'Objectif' } })
    fireEvent.change(screen.getByLabelText("Titre de l'étape 1"), { target: { value: 'Étape' } })
    fireEvent.click(screen.getByRole('button', { name: 'Créer le projet' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Les créneaux disponibles ne suffisent pas.',
    )
  })

  it('marque une étape terminée et rafraîchit la progression', async () => {
    let detail = DETAIL
    let list = [SUMMARY]
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (url.endsWith('/api/projects') && method === 'GET') {
        return response(list)
      }
      if (url.endsWith('/api/projects/1')) {
        return response(detail)
      }
      if (url.endsWith('/api/tasks/1/done')) {
        detail = { ...DETAIL, doneSteps: 1, progress: 0.5 }
        list = [{ ...SUMMARY, doneSteps: 1, progress: 0.5 }]
        return response(undefined, 204)
      }
      return response({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<ProjectsPanel onUnauthorized={vi.fn()} />)
    fireEvent.click(await screen.findByRole('button', { name: /Mémoire/ }))
    await screen.findByText('Cadrage')
    fireEvent.click(screen.getAllByRole('button', { name: 'Terminer' })[0]!)

    await waitFor(() => {
      expect(screen.getByText(/1\/2 étapes/)).toBeInTheDocument()
    })
  })
})
