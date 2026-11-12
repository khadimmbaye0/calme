import { request } from './client'

export type EnergyLevel = 'LOW' | 'MID' | 'GOOD'
export type TaskPriority = 'CRITICAL' | 'NORMAL' | 'OPTIONAL'
export type TaskStatus = 'TODO' | 'DOING' | 'DONE' | 'DEFERRED'
export type BlockType = 'SCHOOL' | 'COMPANY'

export interface TaskSummary {
  id: number
  title: string
  estimateMax: number
  priority: TaskPriority
}

export interface NowResponse {
  task: TaskSummary | null
  energy: EnergyLevel
  blockType: BlockType | null
  capacityMinutes: number
  message: string | null
}

export interface TaskResponse {
  id: number
  title: string
  estimateMin: number
  estimateMax: number
  priority: TaskPriority
  status: TaskStatus
  dueDate: string
}

export interface TaskInput {
  title: string
  estimateMin: number
  estimateMax: number
  priority: TaskPriority
  dueDate: string
}

export interface EnergyResponse {
  date: string
  level: EnergyLevel
  deferredCount: number
}

export const ENERGY_LABELS: Record<EnergyLevel, string> = {
  LOW: 'Bas',
  MID: 'Moyen',
  GOOD: 'Bon',
}

export const PRIORITY_LABELS: Record<TaskPriority, string> = {
  CRITICAL: 'Essentiel',
  NORMAL: 'Normal',
  OPTIONAL: 'Optionnel',
}

export const BLOCK_LABELS: Record<BlockType, string> = {
  SCHOOL: 'École',
  COMPANY: 'Entreprise',
}

export function fetchNow(): Promise<NowResponse> {
  return request<NowResponse>('/api/now')
}

export function startTask(taskId: number): Promise<TaskResponse> {
  return request<TaskResponse>('/api/now/start', {
    method: 'POST',
    body: JSON.stringify({ taskId }),
  })
}

export function laterTask(taskId: number): Promise<NowResponse> {
  return request<NowResponse>('/api/now/later', {
    method: 'POST',
    body: JSON.stringify({ taskId }),
  })
}

export function tooHeavyTask(taskId: number): Promise<NowResponse> {
  return request<NowResponse>('/api/now/too-heavy', {
    method: 'POST',
    body: JSON.stringify({ taskId }),
  })
}

export async function doneTask(taskId: number): Promise<void> {
  await request<void>(`/api/tasks/${taskId}/done`, { method: 'POST' })
}

export function createTask(input: TaskInput): Promise<TaskResponse> {
  return request<TaskResponse>('/api/tasks', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function setEnergy(level: EnergyLevel): Promise<EnergyResponse> {
  return request<EnergyResponse>('/api/energy/today', {
    method: 'PUT',
    body: JSON.stringify({ level }),
  })
}
