/**
 * 全站共享类型。数据模型统一放这里，页面与组件只消费，不各自重复声明。
 */

/** 头像/封面配色，与 UserBadge、文章封面 class 对应 */
export type Tone = 'teal' | 'lilac' | 'sand'

/** AppIcon 支持的图标名 */
export type IconName =
  | 'home'
  | 'chat'
  | 'book'
  | 'mood'
  | 'chart'
  | 'users'
  | 'search'
  | 'bell'
  | 'arrowRight'
  | 'shield'
  | 'sparkles'
  | 'plus'
  | 'send'
  | 'close'
  | 'menu'
  | 'check'
  | 'clock'
  | 'heart'
  | 'filter'
  | 'download'
  | 'leaf'
  | 'refresh'
  | 'alert'
  | 'play'
  | 'pause'
  | 'chevronLeft'
  | 'chevronRight'
  | 'logout'
  | 'sun'
  | 'moon'
  | 'bookmark'
  | 'flame'
  | 'wind'
  | 'copy'
  | 'volume'
  | 'thumbUp'
  | 'thumbDown'

export type ArticleCategory = '焦虑' | '睡眠' | '关系' | '自我成长'
export type ArticleStatus = '已发布' | '草稿'
export type SessionStatus = '进行中' | '已结束' | '待跟进'
export type RiskLevel = '低' | '中' | '需关注'
export type MoodLevel = '低' | '中' | '高'
export type MoodTag = '焦虑' | '低落' | '平静' | '愉悦'
export type MessageRole = 'user' | 'ai'

export interface StatItem {
  key: string
  label: string
  value: number
  delta: number
  unit: string
  icon: IconName
  series: number[]
}

export interface TrendSeries {
  labels: string[]
  values: number[]
}

export interface MoodSlice {
  label: string
  value: number
  color: string
}

export interface TopicBar {
  label: string
  value: number
}

export interface Article {
  id: number
  title: string
  category: ArticleCategory
  author: string
  summary: string
  minutes: number
  views: number
  status: ArticleStatus
  publishedAt: string
  updated: string
  cover: Tone
  content: string[]
}

/** 后台新建/编辑文章的草稿（正文为纯文本，保存时按空行拆段） */
export interface ArticleDraft {
  title: string
  category: ArticleCategory
  summary: string
  content: string
  status: ArticleStatus
}

export interface SessionMessage {
  role: MessageRole
  text: string
  time: string
}

export interface Consultation {
  id: string
  user: string
  initials: string
  tone: Tone
  startedAt: string
  duration: number
  topic: string
  status: SessionStatus
  risk: RiskLevel
  mood: MoodTag
  /** 完整对话消息数，messages 只保存摘录 */
  messageCount: number
  messages: SessionMessage[]
}

export interface MoodLog {
  id: number
  sessionId: string
  user: string
  initials: string
  tone: Tone
  date: string
  score: number
  /** 表格中的「胜过指数」，0–100 */
  moodIndex: number
  tags: string[]
  note: string
  createdAt?: number
}

export interface MoodOption {
  score: number
  emoji: string
  label: string
  color: string
}

export interface ChatMessage {
  role: MessageRole
  text: string
  time: string
}

export interface MoodHistoryEntry {
  date: string
  score: number
  tags: string[]
  note: string
}

export interface SummaryItem {
  label: string
  value: string | number
  hint: string
}
