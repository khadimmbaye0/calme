import { request } from './client'

export type AgentRole = 'USER' | 'AGENT'

export interface AgentMessage {
  id: number
  role: AgentRole
  content: string
  createdAt: string
  hasImage?: boolean
  imageUrl?: string
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

export async function fetchAgentMessages(): Promise<AgentMessage[]> {
  const messages = await request<AgentMessage[]>('/api/agent/messages')
  return messages.map((message) => ({
    ...message,
    imageUrl: message.hasImage ? `/api/agent/messages/${message.id}/image` : undefined,
  }))
}

export function sendAgentMessage(
  message: string,
  image?: File | null,
  imageConsent = false,
): Promise<AgentChatResponse> {
  if (image) {
    const body = new FormData()
    body.append('message', message)
    body.append('image', image)
    body.append('imageConsent', imageConsent ? 'true' : 'false')
    return request<AgentChatResponse>('/api/agent/chat', { method: 'POST', body })
  }

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
