/**
 * 浏览器本地存储的小封装。
 * 无痕模式、存储配额已满或历史脏数据都不应该让页面崩掉，
 * 所以读写都走 try/catch，失败时安静地退回默认值。
 */

export function readJSON<T>(key: string, fallback: T): T {
  try {
    const raw = window.localStorage.getItem(key)
    if (!raw) return fallback
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

export function writeJSON(key: string, value: unknown): void {
  try {
    window.localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // 存不进去也不影响当前会话的交互
  }
}
