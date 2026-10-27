/**
 * 后端 API 基地址。
 * 开发时默认走 Vite proxy（见 vite.config.ts 的 /api 代理），所以留空即可；
 * 部署到其它域名时用 VITE_API_BASE 覆盖，例如 VITE_API_BASE=https://api.example.com。
 */
const BASE = (import.meta.env.VITE_API_BASE ?? '').replace(/\/$/, '')

const GATEWAY_HINT = BASE || 'http://localhost:8080'

/** 502/503/504 通常不是业务错误，而是后端没启动或正在重启。 */
function isBackendUnavailable(status: number): boolean {
  return status === 502 || status === 503 || status === 504
}

function backendUnavailableMessage(status: number): string {
  return `后端服务不可用（HTTP ${status}）。请确认后端已在 ${GATEWAY_HINT} 启动，然后重试。`
}

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status = 0) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

const TOKEN_KEY = 'xinli.auth.token'
export const AUTH_USER_KEY = 'xinli.auth.user'

function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY) ?? sessionStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

/** 登录成功后保存令牌；remember 决定关浏览器后是否仍保留。 */
export function saveAuthToken(token: string, remember = true): void {
  const storage = remember ? localStorage : sessionStorage
  const other = remember ? sessionStorage : localStorage
  other.removeItem(TOKEN_KEY)
  storage.setItem(TOKEN_KEY, token)
}

export function clearAuthToken(): void {
  localStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(TOKEN_KEY)
}

/** 令牌失效（例如后端重启）时清空登录态，并回到登录页。 */
function handleUnauthorized(): void {
  clearAuthToken()
  localStorage.removeItem(AUTH_USER_KEY)
  sessionStorage.removeItem(AUTH_USER_KEY)
  if (window.location.pathname === '/login') return
  const redirect = `${window.location.pathname}${window.location.search}`
  window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`)
}

/** 给请求附带 Bearer 令牌；已有请求头（如 Content-Type）原样保留。 */
function withAuth(init?: RequestInit): RequestInit {
  const headers = new Headers(init?.headers)
  const token = readToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  return { ...init, headers }
}

export async function apiJson<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${BASE}${path}`, withAuth(init))
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError('无法连接后端服务（默认 http://localhost:8080），请确认后端已启动')
  }

  if (!response.ok) {
    if (response.status === 401 && readToken()) {
      handleUnauthorized()
    }
    if (isBackendUnavailable(response.status)) {
      throw new ApiError(backendUnavailableMessage(response.status), response.status)
    }
    const detail = await readErrorDetail(response)
    throw new ApiError(detail || `请求失败（HTTP ${response.status}）`, response.status)
  }
  if (response.status === 204) {
    return undefined as T
  }
  const text = await response.text()
  if (!text) {
    return undefined as T
  }
  return JSON.parse(text) as T
}

/** 从 Spring 的 ProblemDetail / 校验错误响应里尽量取出可读的提示。 */
export async function readErrorDetail(response: Response): Promise<string> {
  try {
    const text = await response.text()
    if (!text) return ''
    try {
      const data = JSON.parse(text) as {
        detail?: string
        message?: string
        title?: string
        errors?: Array<{ defaultMessage?: string }>
      }
      if (data.detail) return data.detail
      const messages = data.errors?.map((item) => item.defaultMessage).filter(Boolean)
      if (messages?.length) return messages.join('；')
      if (data.message) return data.message
      if (data.title) return data.title
    } catch {
      return text.slice(0, 200)
    }
    return text.slice(0, 200)
  } catch {
    return ''
  }
}

export { BASE as API_BASE }
