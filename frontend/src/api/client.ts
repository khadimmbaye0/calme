export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export class UnauthorizedError extends ApiError {
  constructor(message: string) {
    super(message, 401)
    this.name = 'UnauthorizedError'
  }
}

async function problemDetail(response: Response, fallback: string): Promise<string> {
  try {
    const body = (await response.json()) as { detail?: string; title?: string }
    return body.detail ?? body.title ?? fallback
  } catch {
    return fallback
  }
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(path, {
    ...init,
    headers,
    credentials: 'same-origin',
  })

  if (response.status === 401) {
    throw new UnauthorizedError(
      await problemDetail(response, 'La session a expiré. Reconnecte-toi.'),
    )
  }

  if (!response.ok) {
    throw new ApiError(
      await problemDetail(response, 'Une erreur est survenue.'),
      response.status,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.message.trim()) {
    return error.message
  }
  if (error instanceof TypeError) {
    return 'Le serveur n’est pas joignable pour le moment.'
  }
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return 'Une erreur est survenue.'
}
