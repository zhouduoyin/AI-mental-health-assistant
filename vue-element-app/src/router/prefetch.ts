import type { Router } from 'vue-router'

// 已预取的路由名，避免重复触发动态 import
const prefetched = new Set<string>()

/**
 * 预取某个路由的页面 chunk：直接复用路由表里的懒加载函数，
 * Vite 会命中同一份模块，不会重复打包。
 *
 * 只在用户悬停/聚焦导航时才预取，不做首屏空闲批量预取，
 * 避免首屏加载完成后又额外下载三个路由 chunk。
 */
export function prefetchRoute(router: Router, name: string): void {
  if (prefetched.has(name)) return
  prefetched.add(name)

  const loader = router.getRoutes().find((record) => record.name === name)?.components?.default
  if (typeof loader === 'function') {
    // 懒加载组件本身就是 () => import(...)，调用即开始下载；
    // 失败不打断页面（真正跳转时会重新请求并报错）
    void (loader as () => Promise<unknown>)().catch(() => {})
  }
}
