import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import JournalPanel from './JournalPanel'
import type { JournalEntry } from '../api/journal'

function response(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
}

const ENTRY: JournalEntry = {
  id: 1,
  intent: 'event.create',
  explanation: 'Événement créé : « Cours ».',
  status: 'APPLIED',
  createdAt: '2026-11-12T20:00:00Z',
  undoable: true,
}

describe('JournalPanel', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('affiche les actions et annule la plus récente', async () => {
    let entries = [ENTRY]
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/api/journal') && (init?.method ?? 'GET') === 'GET') {
        return response(entries)
      }
      if (url.endsWith('/api/journal/1/undo')) {
        entries = [{ ...ENTRY, status: 'UNDONE', undoable: false }]
        return response(entries)
      }
      return response({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<JournalPanel onUnauthorized={vi.fn()} />)

    expect(await screen.findByText('Événement créé : « Cours ».')).toBeInTheDocument()
    expect(screen.getByText(/Faite/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Annuler' }))

    expect(await screen.findByText('Action annulée.')).toBeInTheDocument()
    expect(screen.getByText(/Annulée/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Annuler' })).not.toBeInTheDocument()
  })

  it('affiche un journal vide calmement', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response([])))

    render(<JournalPanel onUnauthorized={vi.fn()} />)

    expect(await screen.findByText('Aucune action pour le moment.')).toBeInTheDocument()
  })
})
