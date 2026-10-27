import { apiJson, clearAuthToken, saveAuthToken } from './http'

export type UserRole = 'front' | 'back'

export interface AuthUser {
  id: number
  account: string
  name: string
  title: string
  role: UserRole
}

export interface LoginResult {
  token: string
  user: AuthUser
}

export async function loginRequest(
  account: string,
  password: string,
  remember = true,
): Promise<LoginResult> {
  const result = await apiJson<LoginResult>('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, password }),
  })
  saveAuthToken(result.token, remember)
  return result
}

export async function fetchCurrentUser(): Promise<AuthUser> {
  return apiJson<AuthUser>('/api/auth/me')
}

export async function logoutRequest(): Promise<void> {
  try {
    await apiJson<void>('/api/auth/logout', { method: 'POST' })
  } finally {
    clearAuthToken()
  }
}

export { clearAuthToken }
