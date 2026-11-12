import { addDays, startOfDay } from '../lib/date'

export type EventKind = 'FIXED' | 'PLANNED' | 'AVAILABILITY'
export type EventCreator = 'USER' | 'AGENT'

export interface CalendarEvent {
  id: number
  title: string
  startAt: string
  endAt: string
  kind: EventKind
  createdBy: EventCreator
}

export interface EventInput {
  title: string
  startAt: string
  endAt: string
  kind: EventKind
}

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
  })

  if (!response.ok) {
    let detail = 'Une erreur est survenue.'
    try {
      const body = (await response.json()) as { detail?: string; title?: string }
      detail = body.detail ?? body.title ?? detail
    } catch {
      // The response may not be JSON; keep the calm generic message.
    }
    throw new ApiError(detail, response.status)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

export async function fetchEvents(day: Date): Promise<CalendarEvent[]> {
  const from = startOfDay(day)
  const to = addDays(from, 1)
  const query = new URLSearchParams({
    from: from.toISOString(),
    to: to.toISOString(),
  })

  return request<CalendarEvent[]>(`/api/events?${query.toString()}`)
}

export async function createEvent(input: EventInput): Promise<CalendarEvent> {
  return request<CalendarEvent>('/api/events', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export async function updateEvent(id: number, input: EventInput): Promise<CalendarEvent> {
  return request<CalendarEvent>(`/api/events/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export async function deleteEvent(id: number): Promise<void> {
  await request<void>(`/api/events/${id}`, { method: 'DELETE' })
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.message.trim()) {
    return error.message
  }
  if (error instanceof TypeError) {
    return 'Le calendrier n’est pas joignable pour le moment.'
  }
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return 'Une erreur est survenue.'
}
