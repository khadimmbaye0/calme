import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

describe('App', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('affiche le socle et confirme que le serveur répond', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ status: 'UP' }),
      }),
    )

    render(<App />)

    expect(screen.getByRole('heading', { name: /une chose à la fois/i })).toBeInTheDocument()
    expect(await screen.findByText(/serveur et base de données prêts/i)).toBeInTheDocument()
  })

  it('reste calme quand le serveur ne répond pas', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))

    render(<App />)

    expect(await screen.findByText(/serveur non joignable/i)).toBeInTheDocument()
  })
})
