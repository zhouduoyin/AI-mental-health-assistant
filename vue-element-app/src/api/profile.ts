import { apiJson } from './http'
import type { UserRole } from './auth'

export interface Profile {
  id: number
  account: string
  name: string
  title: string
  role: UserRole
  enabled: boolean
  createdAt: number | null
  lastLoginAt: number | null
  moodCount: number
  consultationCount: number
}

export interface ProfileUpdatePayload {
  name: string
  title: string
}

export interface PasswordChangePayload {
  currentPassword: string
  newPassword: string
}

export async function fetchProfile(): Promise<Profile> {
  return apiJson<Profile>('/api/profile')
}

export async function updateProfile(payload: ProfileUpdatePayload): Promise<Profile> {
  return apiJson<Profile>('/api/profile', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export async function changePassword(payload: PasswordChangePayload): Promise<void> {
  await apiJson<void>('/api/profile/password', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}
