import { request } from './client'
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
