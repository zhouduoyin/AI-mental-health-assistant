import type { ArticleCategory, MoodOption } from '@/types'

/**
 * 与业务数据无关的固定目录（表单选项、颜色映射）。
 * 文章、情绪记录等业务数据全部来自后端接口。
 */
export const articleCategories: ArticleCategory[] = ['焦虑', '睡眠', '关系', '自我成长']

export const moodTags: string[] = [
  '工作压力',
  '睡眠',
  '亲密关系',
  '家庭',
  '焦虑',
  '自我成长',
  '运动',
  '放松',
]

export const moodOptions: MoodOption[] = [
  { score: 1, emoji: '😞', label: '很低落', color: '#8a5b68' },
  { score: 2, emoji: '😕', label: '有点低', color: '#b78490' },
  { score: 3, emoji: '😐', label: '一般', color: '#b5884d' },
  { score: 4, emoji: '🙂', label: '还不错', color: '#5aa79b' },
  { score: 5, emoji: '😄', label: '很好', color: '#4f8668' },
]
