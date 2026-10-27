import type { IconName } from '@/types'

/** 导航项：路由名用于预取，路径用于跳转与高亮 */
export interface NavItem {
  to: string
  name: string
  label: string
}

export interface AdminNavItem extends NavItem {
  icon: IconName
}

/** 前台主导航 */
export const frontNav: NavItem[] = [
  { to: '/front', name: 'front-home', label: '首页' },
  { to: '/front/chat', name: 'front-chat', label: 'AI 咨询' },
  { to: '/front/mood', name: 'front-mood', label: '情绪日记' },
  { to: '/front/articles', name: 'front-articles', label: '知识库' },
  { to: '/front/lab', name: 'front-lab', label: '实验室' },
]

/** 后台侧栏 / 抽屉共用同一份菜单配置 */
export const adminNav: AdminNavItem[] = [
  { to: '/back/dashboard', name: 'back-dashboard', label: '数据分析', icon: 'chart' },
  { to: '/back/users', name: 'back-users', label: '用户管理', icon: 'users' },
  { to: '/back/articles', name: 'back-articles', label: '知识文章', icon: 'book' },
  { to: '/back/knowledge', name: 'back-knowledge', label: '知识库', icon: 'sparkles' },
  { to: '/back/consultations', name: 'back-consultations', label: '咨询记录', icon: 'chat' },
  { to: '/back/mood-logs', name: 'back-mood-logs', label: '情绪日志', icon: 'mood' },
]
