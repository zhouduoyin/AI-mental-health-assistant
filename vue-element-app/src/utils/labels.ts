import type { MoodLevel, RiskLevel, SessionStatus } from '@/types'

/** el-tag 的 type 取值 */
type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

const STATUS_TAG: Record<SessionStatus, TagType> = {
  进行中: 'primary',
  已结束: 'info',
  待跟进: 'warning',
}

const RISK_TAG: Record<RiskLevel, TagType> = {
  低: 'success',
  中: 'warning',
  需关注: 'danger',
}

export const statusTagType = (status: SessionStatus): TagType => STATUS_TAG[status]

export const riskTagType = (risk: RiskLevel): TagType => RISK_TAG[risk]

export const moodLevel = (score: number): MoodLevel => {
  if (score >= 4) return '高'
  if (score === 3) return '中'
  return '低'
}

export const moodLevelTagType = (score: number): TagType => {
  if (score >= 4) return 'success'
  if (score === 3) return 'warning'
  return 'danger'
}
