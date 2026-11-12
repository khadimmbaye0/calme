import { request } from './client'

export type AgentRole = 'USER' | 'AGENT'

export interface AgentMessage {
  id: number
  role: AgentRole
  content: string
  createdAt: string
}

export interface AgentAction {
  tool: string
  summary: string
}

export interface AgentPendingConfirmation {
  id: string
  tool: string
  summary: string
}

export interface AgentChatResponse {
  message: string
  actions: AgentAction[]
  pendingConfirmation: AgentPendingConfirmation | null
  undoableActionId: number | null
  error: boolean
}

export function fetchAgentMessages(): Promise<AgentMessage[]> {
  return request<AgentMessage[]>('/api/agent/messages')
}

export function sendAgentMessage(message: string): Promise<AgentChatResponse> {
  return request<AgentChatResponse>('/api/agent/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message }),
  })
}

export function confirmAgentAction(id: string): Promise<AgentChatResponse> {
  return request<AgentChatResponse>(`/api/agent/confirm/${id}`, { method: 'POST' })
}

export function cancelAgentAction(id: string): Promise<AgentChatResponse> {
  return request<AgentChatResponse>(`/api/agent/cancel/${id}`, { method: 'POST' })
}
