import { request } from './client'
import type { TaskPriority, TaskStatus } from './planner'

export interface ProjectSummary {
  id: number
  title: string
  deadline: string
  status: string
  totalSteps: number
  doneSteps: number
  progress: number
}

export interface ProjectStep {
  taskId: number
  title: string
  estimateMin: number
  estimateMax: number
  priority: TaskPriority
  status: TaskStatus
  dueDate: string | null
  scheduledStart: string | null
  scheduledEnd: string | null
  dependsOnTaskIds: number[]
}

export interface ProjectDetail extends ProjectSummary {
  goal: string
  bufferRatio: number
  steps: ProjectStep[]
}

export interface ProjectStepInput {
  title: string
  estimateMin: number
  estimateMax: number
  priority: TaskPriority
  dependsOn: number[]
}

export interface ProjectInput {
  title: string
  goal: string
  deadline: string
  bufferRatio: number
  steps: ProjectStepInput[]
}

export function fetchProjects(): Promise<ProjectSummary[]> {
  return request<ProjectSummary[]>('/api/projects')
}

export function fetchProject(id: number): Promise<ProjectDetail> {
  return request<ProjectDetail>(`/api/projects/${id}`)
}

export function createProject(input: ProjectInput): Promise<ProjectDetail> {
  return request<ProjectDetail>('/api/projects', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}
