import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import KnowledgeGraphPanel from './KnowledgeGraphPanel'
import type { GraphData } from '../api/graph'

function response(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
}

const GRAPH: GraphData = {
  nodes: [
    {
      id: 'project:1',
      type: 'PROJECT',
      label: 'Mémoire',
      subtitle: '13 nov. · 0/2 étapes',
      status: 'ACTIVE',
      weight: 3.4,
    },
    {
      id: 'task:1',
      type: 'TASK',
      label: 'Cadrage',
      subtitle: '30–30 min · 12 nov.',
      status: 'TODO',
      weight: 2,
    },
    {
      id: 'event:1',
      type: 'EVENT',
      label: 'Cadrage',
      subtitle: '12 nov. · 17:30–18:00',
      status: 'PLANNED',
      weight: 2,
    },
  ],
  edges: [
    { id: 'e1', source: 'project:1', target: 'task:1', type: 'CONTAINS', label: 'contient' },
    { id: 'e2', source: 'event:1', target: 'task:1', type: 'SCHEDULED_FOR', label: 'planifie' },
  ],
}

describe('KnowledgeGraphPanel', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('affiche le graphe, les statistiques et les liens', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response(GRAPH)))

    render(<KnowledgeGraphPanel onUnauthorized={vi.fn()} />)

    expect(await screen.findByRole('heading', { name: 'Le fil de tout.' })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Projet : Mémoire' })).toBeInTheDocument()
    expect(screen.getByText(/3 nœuds/)).toBeInTheDocument()
    expect(screen.getByText(/2 liens/)).toBeInTheDocument()
  })

  it('ouvre le détail et les connexions d’un nœud', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response(GRAPH)))

    render(<KnowledgeGraphPanel onUnauthorized={vi.fn()} />)
    fireEvent.click(await screen.findByRole('button', { name: 'Projet : Mémoire' }))

    expect(screen.getByRole('heading', { name: 'Mémoire' })).toBeInTheDocument()
    expect(screen.getByText('13 nov. · 0/2 étapes')).toBeInTheDocument()
    expect(screen.getByText('En cours')).toBeInTheDocument()
    expect(screen.getByText('contient')).toBeInTheDocument()
    expect(screen.getAllByText('Cadrage').length).toBeGreaterThan(0)
  })

  it('filtre par famille et peut tout réafficher', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response(GRAPH)))

    render(<KnowledgeGraphPanel onUnauthorized={vi.fn()} />)
    await screen.findByRole('button', { name: 'Projet : Mémoire' })

    fireEvent.click(screen.getByRole('button', { name: /^Projets/ }))
    expect(screen.queryByRole('button', { name: 'Projet : Mémoire' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Tout' }))
    expect(await screen.findByRole('button', { name: 'Projet : Mémoire' })).toBeInTheDocument()
  })

  it('affiche un état vide calme', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response({ nodes: [], edges: [] })))

    render(<KnowledgeGraphPanel onUnauthorized={vi.fn()} />)

    expect(await screen.findByText(/Le graphe se remplira/)).toBeInTheDocument()
  })

  it('signale une erreur sans casser la vue', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response({ detail: 'Le graphe est indisponible.' }, 500)))

    render(<KnowledgeGraphPanel onUnauthorized={vi.fn()} />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Le graphe est indisponible.')
    expect(screen.getByRole('button', { name: 'Réessayer' })).toBeInTheDocument()
  })

  it('rafraîchit les données', async () => {
    const fetchMock = vi.fn(async () => response(GRAPH))
    vi.stubGlobal('fetch', fetchMock)
    render(<KnowledgeGraphPanel onUnauthorized={vi.fn()} />)
    await screen.findByRole('button', { name: 'Projet : Mémoire' })

    fireEvent.click(screen.getByRole('button', { name: 'Actualiser' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
  })
})
