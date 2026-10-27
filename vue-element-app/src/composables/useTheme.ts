import { computed, ref } from 'vue'

export type ThemeMode = 'day' | 'night'

const STORAGE_KEY = 'mha-theme'

// 模块级单例：所有布局和切换按钮共享同一份状态
const mode = ref<ThemeMode>('day')
let initialized = false
let switchTimer = 0

function readStored(): ThemeMode | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === 'day' || value === 'night' ? value : null
  } catch {
    return null
  }
}

function apply(next: ThemeMode): void {
  if (typeof document === 'undefined') return
  document.documentElement.dataset.theme = next
  document.documentElement.style.colorScheme = next === 'night' ? 'dark' : 'light'
  // 浏览器地址栏 / 移动端状态栏跟随主题
  const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
  if (meta) meta.content = next === 'night' ? '#0a0c13' : '#f6eee2'
}

/** 切换的一瞬间给全局加过渡类，避免颜色硬切；500ms 后移除，避免拖慢日常交互 */
function animateSwitch(): void {
  const root = document.documentElement
  root.classList.add('theme-switch')
  window.clearTimeout(switchTimer)
  switchTimer = window.setTimeout(() => root.classList.remove('theme-switch'), 520)
}

function setMode(next: ThemeMode, persist = true): void {
  if (next === mode.value) return
  animateSwitch()
  mode.value = next
  apply(next)
  if (persist) {
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // 隐私模式下 localStorage 可能不可写，主题仍然在当前会话生效
    }
  }
}

/** 在应用挂载前调用一次：读取本地偏好并写到 <html data-theme>，避免首屏闪白 */
export function initTheme(): ThemeMode {
  if (initialized) return mode.value
  initialized = true
  mode.value = readStored() ?? 'day'
  apply(mode.value)
  return mode.value
}

export function useTheme() {
  initTheme()

  const isNight = computed(() => mode.value === 'night')
  const label = computed(() => (isNight.value ? '切换到晨雾日间' : '切换到深空夜间'))

  function toggleTheme(): void {
    setMode(mode.value === 'day' ? 'night' : 'day')
  }

  return { mode, isNight, label, setMode, toggleTheme }
}
