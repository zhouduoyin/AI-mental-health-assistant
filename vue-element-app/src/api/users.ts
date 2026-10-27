import { apiJson } from './http'
import type { BatchResult } from './batch'
import type { UserRole } from './auth'

export interface AdminUser {
  id: number
  account: string
  name: string
  title: string
  role: UserRole
  enabled: boolean
  createdAt: number | null
  lastLoginAt: number | null
}

export interface UserCreatePayload {
  account: string
  password: string
  name: string
  title: string
  role: UserRole
  enabled: boolean
}

export interface UserUpdatePayload {
  account: string
  /** 留空则保持原密码 */
  password?: string | null
  name: string
  title: string
  role: UserRole
  enabled: boolean
}

export interface UserQuery {
  keyword?: string
  role?: UserRole | ''
}

export async function listAdminUsers(query: UserQuery = {}): Promise<AdminUser[]> {
  const params = new URLSearchParams()
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
  if (query.role) params.set('role', query.role)
  const suffix = params.toString() ? `?${params.toString()}` : ''
  return apiJson<AdminUser[]>(`/api/admin/users${suffix}`)
}

export async function createAdminUser(payload: UserCreatePayload): Promise<AdminUser> {
  return apiJson<AdminUser>('/api/admin/users', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export async function updateAdminUser(id: number, payload: UserUpdatePayload): Promise<AdminUser> {
  return apiJson<AdminUser>(`/api/admin/users/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export async function changeUserStatus(id: number, enabled: boolean): Promise<AdminUser> {
  return apiJson<AdminUser>(`/api/admin/users/${id}/status`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  })
}

export async function deleteAdminUser(id: number): Promise<void> {
  await apiJson<void>(`/api/admin/users/${id}`, { method: 'DELETE' })
}

/** 批量启停账号；服务端逐项执行并返回失败明细。 */
export async function batchChangeUserStatus(ids: number[], enabled: boolean): Promise<BatchResult> {
  return apiJson<BatchResult>('/api/admin/users/batch/status', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ids, enabled }),
  })
}

/** 批量删除账号；不能删除自己或最后一个可用后台管理员。 */
export async function batchDeleteAdminUsers(ids: number[]): Promise<BatchResult> {
  return apiJson<BatchResult>('/api/admin/users/batch/delete', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ids }),
  })
}
