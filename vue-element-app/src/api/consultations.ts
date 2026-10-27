import type { Consultation } from '@/types'

import { apiJson } from './http'

export function listConsultations(): Promise<Consultation[]> {
  return apiJson<Consultation[]>('/api/admin/consultations')
}

export function getConsultation(id: string): Promise<Consultation> {
  return apiJson<Consultation>(`/api/admin/consultations/${encodeURIComponent(id)}`)
}

/** 标记为已跟进后，服务端把状态改为「已结束」。 */
export function followUpConsultation(id: string): Promise<Consultation> {
  return apiJson<Consultation>(`/api/admin/consultations/${encodeURIComponent(id)}/follow-up`, {
    method: 'POST',
  })
}
