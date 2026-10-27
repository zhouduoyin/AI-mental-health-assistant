import { computed, reactive } from 'vue'

import {
  clearAuthToken,
  fetchCurrentUser,
  loginRequest,
  logoutRequest,
  type AuthUser,
  type UserRole,
} from '@/api/auth'
import { AUTH_USER_KEY } from '@/api/http'

export type { AuthUser, UserRole }

/** 登录后各角色默认落点 */
export const ROLE_HOME: Record<UserRole, string> = {
  front: '/front',
  back: '/back/dashboard',
}

const STORAGE_KEY = AUTH_USER_KEY

interface AuthState {
  user: AuthUser | null
}

// 登录态放在模块级单例里：路由守卫、登录页、前后台布局读的是同一份数据
const state = reactive<AuthState>({ user: readUser() })

function readUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY) ?? sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<AuthUser>
    // 旧版本存过没有 token 的演示账号，结构不完整时按未登录处理
    if (!parsed.account || !parsed.role || !parsed.name || typeof parsed.id !== 'number') {
      localStorage.removeItem(STORAGE_KEY)
      sessionStorage.removeItem(STORAGE_KEY)
      clearAuthToken()
      return null
    }
    return parsed as AuthUser
  } catch {
    // 存储被禁用或内容损坏时按未登录处理
    return null
  }
}

function persistUser(user: AuthUser, remember = true): void {
  const storage = remember ? localStorage : sessionStorage
  const other = remember ? sessionStorage : localStorage
  other.removeItem(STORAGE_KEY)
  storage.setItem(STORAGE_KEY, JSON.stringify(user))
}

function clearPersistedUser(): void {
  localStorage.removeItem(STORAGE_KEY)
  sessionStorage.removeItem(STORAGE_KEY)
}

export function useAuth() {
  const user = computed(() => state.user)
  const isAuthenticated = computed(() => state.user !== null)

  /**
   * 真实登录：由后端校验账号密码并签发令牌。
   * remember 为 true 存 localStorage（关浏览器仍在），否则存 sessionStorage。
   */
  async function login(account: string, password: string, remember = true): Promise<AuthUser> {
    const result = await loginRequest(account.trim(), password, remember)
    state.user = result.user
    persistUser(result.user, remember)
    return result.user
  }

  /** 用已保存的令牌刷新用户信息；令牌失效时清空登录态。 */
  async function refresh(): Promise<AuthUser | null> {
    if (!state.user) return null
    try {
      const next = await fetchCurrentUser()
      state.user = next
      persistUser(next, localStorage.getItem(STORAGE_KEY) !== null)
      return next
    } catch {
      state.user = null
      clearPersistedUser()
      clearAuthToken()
      return null
    }
  }

  function logout(): void {
    state.user = null
    clearPersistedUser()
    // 先让请求带着当前令牌发出，后端注销后再清除本地令牌
    void logoutRequest().catch(() => clearAuthToken())
  }

  return { user, isAuthenticated, login, logout, refresh }
}
