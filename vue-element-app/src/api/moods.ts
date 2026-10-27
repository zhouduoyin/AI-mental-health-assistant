import type { MoodLog } from '@/types'

import { apiJson } from './http'

export interface MoodPayload {
  /** YYYY-MM-DD；不传则后端使用今天 */
  date?: string
  score: number
  tags: string[]
  note?: string
}

export function listMyMoods(): Promise<MoodLog[]> {
  return apiJson<MoodLog[]>('/api/moods')
}

/** 同一天重复保存会覆盖当天记录。 */
export function saveMood(payload: MoodPayload): Promise<MoodLog> {
  return apiJson<MoodLog>('/api/moods', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function deleteMood(id: number): Promise<void> {
  return apiJson<void>(`/api/moods/${id}`, { method: 'DELETE' })
}

export function listAllMoods(): Promise<MoodLog[]> {
  return apiJson<MoodLog[]>('/api/admin/moods')
}

export function deleteMoodAsAdmin(id: number): Promise<void> {
  return apiJson<void>(`/api/admin/moods/${id}`, { method: 'DELETE' })
}
