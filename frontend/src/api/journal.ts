import { request } from './client'

export type JournalStatus = 'APPLIED' | 'UNDONE' | 'FAILED'

export interface JournalEntry {
  id: number
  intent: string
  explanation: string
  status: JournalStatus
  createdAt: string
  undoable: boolean
}

export function fetchJournal(): Promise<JournalEntry[]> {
  return request<JournalEntry[]>('/api/journal')
}

export function undoJournal(id: number): Promise<JournalEntry[]> {
  return request<JournalEntry[]>(`/api/journal/${id}/undo`, { method: 'POST' })
}
