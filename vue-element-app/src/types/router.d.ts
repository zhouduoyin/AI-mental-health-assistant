import type { UserRole } from '@/composables/useAuth'
import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题，同时用于 document.title */
    title?: string
    /** 需要登录：路由守卫会跳转到 /login 并带上 redirect */
    requiresAuth?: boolean
    /** 该分支只允许对应角色访问（前台 / 后台） */
    role?: UserRole
  }
}
