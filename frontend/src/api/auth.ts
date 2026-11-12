import { request } from './client'

export interface AuthUser {
  email: string
}

export const LOCAL_EMAIL = 'local@calme.invalid'

export async function login(password: string): Promise<AuthUser> {
  return request<AuthUser>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ password }),
  })
}

export async function logout(): Promise<void> {
  await request<void>('/api/auth/logout', { method: 'POST' })
}

export async function fetchCurrentUser(): Promise<AuthUser | null> {
  const response = await fetch('/api/auth/me', { credentials: 'same-origin' })

  if (response.status === 401) {
    return null
  }
  if (!response.ok) {
    throw new Error('La session n’a pas pu être vérifiée.')
  }

  return (await response.json()) as AuthUser
}
