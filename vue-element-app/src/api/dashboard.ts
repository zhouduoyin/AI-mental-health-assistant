import type { IconName, MoodSlice, StatItem, TopicBar, TrendSeries } from '@/types'

import { apiJson } from './http'

export interface DashboardStat {
  key: string
  label: string
  value: number
  delta: number
  unit: string
  icon: string
  series: number[]
}

export interface DashboardFeed {
  who: string
  action: string
  tone: string
  at: string
}

export interface DashboardApiResponse {
  stats: DashboardStat[]
  moodTrend: TrendSeries
  consultStats: TopicBar[]
  activeTrend: TrendSeries
  levelSlices: MoodSlice[]
  triggerTop: TopicBar[]
  moodTotal: number
  live: {
    online: number
    avgResponseSeconds: number | null
    updatedAt: string
    feed: DashboardFeed[]
  }
}

export interface DashboardResponse extends Omit<DashboardApiResponse, 'stats'> {
  stats: StatItem[]
}

/** 后端返回的 icon 是字符串，前端图标组件只认已知名称，这里做一次收敛。 */
const ICONS: IconName[] = ['users', 'mood', 'chat', 'sparkles']

function toStatItem(stat: DashboardStat): StatItem {
  const icon = ICONS.includes(stat.icon as IconName) ? (stat.icon as IconName) : 'sparkles'
  return {
    key: stat.key,
    label: stat.label,
    value: stat.value,
    delta: stat.delta,
    unit: stat.unit,
    icon,
    series: stat.series,
  }
}

export async function fetchDashboard(): Promise<DashboardResponse> {
  const data = await apiJson<DashboardApiResponse>('/api/admin/dashboard')
  return { ...data, stats: data.stats.map(toStatItem) }
}
