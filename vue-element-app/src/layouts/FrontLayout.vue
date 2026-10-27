<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import AppIcon from '@/components/AppIcon.vue'
import AuroraBackdrop from '@/components/AuroraBackdrop.vue'
import BrandLogo from '@/components/BrandLogo.vue'
import ParticleField from '@/components/ParticleField.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import { useAuth } from '@/composables/useAuth'
import { frontNav } from '@/data/nav'
import { prefetchRoute } from '@/router/prefetch'

const route = useRoute()
const router = useRouter()
const { user, logout } = useAuth()

const initial = computed(() => (user.value?.name ?? '来访者').slice(0, 1))

// 账号下拉：手写轻量菜单而不是 el-dropdown，避免把 popper 依赖打进首屏
const menuOpen = ref(false)
const accountRef = ref<HTMLElement | null>(null)

function toggleMenu(): void {
  menuOpen.value = !menuOpen.value
}

// 点菜单外或按 Esc 关闭
function onPointerDown(event: MouseEvent): void {
  if (!accountRef.value?.contains(event.target as Node)) menuOpen.value = false
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') menuOpen.value = false
}

const isActive = (to: string): boolean =>
  to === '/front' ? route.path === '/front' : route.path.startsWith(to)

// 悬停 / 聚焦导航时预取对应页面，点击后少等一次网络往返
function prefetch(name: string): void {
  prefetchRoute(router, name)
}

// ---- 滚动进度 + 吸顶态 ----
const scrolled = ref(false)
const progress = ref(0)
let scrollFrame = 0

function readScroll(): void {
  const root = document.documentElement
  const max = root.scrollHeight - root.clientHeight
  progress.value = max > 0 ? Math.min(1, Math.max(0, root.scrollTop / max)) : 0
  scrolled.value = root.scrollTop > 8
}

function onScroll(): void {
  if (scrollFrame) return
  scrollFrame = requestAnimationFrame(() => {
    scrollFrame = 0
    readScroll()
  })
}

// ---- 导航滑动指示器：直接量取当前项，避免为 4 个入口引入布局动画库 ----
const navEl = ref<HTMLElement | null>(null)
const indicator = reactive({ left: 0, width: 0, opacity: 0 })

function measureIndicator(): void {
  const active = navEl.value?.querySelector<HTMLElement>('.site__link.is-active')
  if (!active) {
    indicator.opacity = 0
    return
  }

  indicator.left = active.offsetLeft
  indicator.width = active.offsetWidth
  indicator.opacity = 1
}

watch(
  () => route.path,
  () => nextTick(measureIndicator),
)

let resizeObserver: ResizeObserver | undefined

onMounted(() => {
  document.addEventListener('pointerdown', onPointerDown)
  document.addEventListener('keydown', onKeydown)
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', measureIndicator, { passive: true })

  readScroll()
  measureIndicator()

  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(measureIndicator)
    if (navEl.value) resizeObserver.observe(navEl.value)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onPointerDown)
  document.removeEventListener('keydown', onKeydown)
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', measureIndicator)

  if (scrollFrame) cancelAnimationFrame(scrollFrame)
  resizeObserver?.disconnect()
})

// 前台退出：直接清登录态回登录页。
// 这里刻意不用 ElMessageBox：弹窗会把整块 dialog 样式带进首屏，退出按钮也用不上二次确认。
function signOut(): void {
  menuOpen.value = false
  logout()
  router.replace('/login')
}

function openProfile(): void {
  menuOpen.value = false
  void router.push('/front/profile')
}
</script>

<template>
  <div class="site theme-warm">
    <AuroraBackdrop />
    <ParticleField />

    <div class="site__progress" aria-hidden="true">
      <span :style="{ transform: `scaleX(${progress})` }" />
    </div>

    <header class="site__header" :class="{ 'is-scrolled': scrolled }">
      <div class="site__bar container">
        <RouterLink to="/front" class="site__brand" aria-label="心理健康 AI 助手首页">
          <BrandLogo />
        </RouterLink>

        <nav ref="navEl" class="site__nav" aria-label="主导航">
          <span
            class="site__nav-glow"
            aria-hidden="true"
            :style="{
              transform: `translate3d(${indicator.left}px, 0, 0)`,
              width: `${indicator.width}px`,
              opacity: indicator.opacity,
            }"
          />
          <RouterLink
            v-for="item in frontNav"
            :key="item.to"
            :to="item.to"
            class="site__link"
            :class="{ 'is-active': isActive(item.to) }"
            @mouseenter="prefetch(item.name)"
            @focus="prefetch(item.name)"
          >
            {{ item.label }}
          </RouterLink>
        </nav>

        <div class="site__actions">
          <ThemeToggle class="site__theme" compact />

          <!-- 前台只有 front 一个角色，头像下拉只做账号展示 + 退出 -->
          <div ref="accountRef" class="site__account-wrap">
            <button
              class="site__account"
              type="button"
              aria-haspopup="menu"
              :aria-expanded="menuOpen"
              :title="`${user?.name ?? '来访者'} · 退出登录`"
              @click="toggleMenu"
            >
              <span class="site__account-avatar">{{ initial }}</span>
              <span class="site__account-name">{{ user?.name ?? '来访者' }}</span>
            </button>

            <Transition name="site-menu">
              <div v-if="menuOpen" class="site__menu" role="menu">
                <p class="site__menu-info">{{ user?.name ?? '来访者' }} · {{ user?.title ?? '来访者' }}</p>
                <button class="site__menu-item" type="button" role="menuitem" @click="openProfile">
                  <AppIcon name="users" :size="14" />
                  个人资料
                </button>
                <button
                  class="site__menu-item site__menu-item--danger"
                  type="button"
                  role="menuitem"
                  @click="signOut"
                >
                  <AppIcon name="logout" :size="14" />
                  退出登录
                </button>
              </div>
            </Transition>
          </div>

          <el-button type="primary" round @click="router.push('/front/chat')">开始倾诉</el-button>
        </div>
      </div>
    </header>

    <main class="site__main">
      <router-view v-slot="{ Component }">
        <Transition name="page" mode="out-in">
          <component :is="Component" />
        </Transition>
      </router-view>
    </main>

    <SiteFooter />
  </div>
</template>

<style scoped lang="scss">
.site {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  overflow-x: clip;
}

// ---- 顶部滚动进度 ----
.site__progress {
  position: fixed;
  inset: 0 0 auto;
  z-index: 50;
  height: 2px;
  pointer-events: none;

  span {
    display: block;
    height: 100%;
    transform-origin: 0 50%;
    background: var(--warm-gradient);
    box-shadow:
      0 0 18px var(--fx-neon-soft),
      0 0 34px var(--fx-neon-soft);
  }
}

// ---- 玻璃吸顶导航 ----
.site__header {
  position: sticky;
  top: 0;
  z-index: 30;
  border-bottom: 1px solid transparent;
  background:
    linear-gradient(180deg, var(--warm-header-55), var(--warm-header-72));
  // 这里原本有 backdrop-filter。吸顶元素覆盖整行宽度，
  // 一旦滚动就要对整条背景重新模糊，是滚动卡顿的主要来源之一；
  // 用 .is-scrolled 的 90% 底色代替毛玻璃，观感几乎一致。
  transition:
    background 0.35s $ease-out,
    border-color 0.35s $ease-out,
    box-shadow 0.35s $ease-out;

  // 顶部一条极细的极光高光，滚动后浮现
  &::after {
    content: '';
    position: absolute;
    right: 0;
    bottom: -1px;
    left: 0;
    height: 1px;
    background: var(--fx-aurora);
    opacity: 0;
    transition: opacity 0.4s $ease-out;
  }

  &.is-scrolled {
    border-bottom-color: var(--warm-line);
    background: var(--warm-header-90);
    box-shadow:
      0 24px 60px -48px var(--fx-shadow),
      0 1px 0 0 var(--fx-rim-soft) inset;

    &::after {
      opacity: 0.55;
    }
  }
}

.site__bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: $space-5;
  min-height: 76px;
  padding-block: 10px;
}

.site__brand {
  border-radius: $radius-sm;

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

.site__nav {
  position: relative;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px;
  border: 1px solid var(--fx-rim-soft);
  border-radius: $radius-pill;
  background: var(--fx-glass);
  box-shadow:
    inset 0 1px 0 var(--fx-rim),
    0 18px 40px -36px var(--fx-shadow);
}

.site__nav-glow {
  position: absolute;
  top: 4px;
  bottom: 4px;
  left: 0;
  z-index: 0;
  border-radius: $radius-pill;
  background: linear-gradient(
    120deg,
    color-mix(in srgb, var(--warm-brand) 16%, var(--fx-glass-strong)),
    color-mix(in srgb, var(--warm-lilac) 16%, var(--fx-glass-strong)) 48%,
    color-mix(in srgb, var(--warm-apricot) 14%, var(--fx-glass-strong))
  );
  background-size: 240% 100%;
  border: 1px solid var(--fx-neon-soft);
  box-shadow:
    0 10px 26px -16px var(--fx-neon-soft),
    inset 0 1px 0 var(--fx-rim);
  animation: fx-shift 9s linear infinite;
  transition:
    transform 0.42s $ease-out-expo,
    width 0.42s $ease-out-expo,
    opacity 0.2s $ease-out;
  pointer-events: none;
}

.site__link {
  position: relative;
  z-index: 1;
  padding: 7px 15px;
  border-radius: $radius-pill;
  color: var(--warm-text-muted);
  font-size: 13.5px;
  transition:
    color 0.2s $ease-out,
    text-shadow 0.2s $ease-out;

  &:hover {
    color: var(--warm-text);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }

  &.is-active {
    color: var(--warm-brand-deep);
    font-weight: 600;
    text-shadow: 0 0 18px var(--fx-neon-soft);
  }
}

.site__actions {
  display: flex;
  align-items: center;
  gap: $space-4;
  margin-left: auto;
}

// 前台只有「front」账号，因此这里只做展示 + 退出，不提供角色切换
.site__account-wrap {
  position: relative;
}

.site__account {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px 4px 5px;
  border: 1px solid var(--fx-rim-soft);
  border-radius: $radius-pill;
  background: var(--fx-glass);
  color: inherit;
  font: inherit;
  cursor: pointer;
  transition:
    border-color 0.2s $ease-out,
    background 0.2s $ease-out,
    box-shadow 0.2s $ease-out,
    transform 0.2s $ease-out;

  &:hover {
    border-color: var(--fx-neon-soft);
    background: var(--fx-glass-strong);
    box-shadow: 0 12px 26px -20px var(--fx-neon-soft);
    transform: translateY(-1px);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

// 轻量下拉菜单：绝对定位 + 过渡，不引入 el-dropdown 的 popper 依赖
.site__menu {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  z-index: 30;
  min-width: 176px;
  padding: 6px;
  border: 1px solid var(--fx-rim-soft);
  border-radius: $radius-sm;
  background: var(--fx-glass-strong);
  box-shadow:
    0 30px 70px -36px var(--fx-shadow),
    inset 0 1px 0 var(--fx-rim);
}

.site__menu-info {
  margin: 2px 8px 6px;
  color: var(--warm-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.site__menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 9px 10px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--warm-text-regular);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition:
    color 0.2s $ease-out,
    background 0.2s $ease-out;

  &:hover {
    background: color-mix(in srgb, var(--warm-brand) 12%, transparent);
    color: var(--warm-brand-deep);
  }

  &.site__menu-item--danger:hover {
    background: color-mix(in srgb, var(--warm-rose) 16%, transparent);
    color: var(--warm-rose);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

.site-menu-enter-active,
.site-menu-leave-active {
  transition:
    opacity 0.18s $ease-out,
    transform 0.18s $ease-out;
}

.site-menu-enter-from,
.site-menu-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.98);
}

.site__account-avatar {
  position: relative;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: none;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--warm-brand-strong), var(--warm-sage-light));
  color: var(--warm-on-brand);
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 8px 18px -10px rgba(112, 80, 86, 0.75);

  &::after {
    content: '';
    position: absolute;
    right: -1px;
    bottom: -1px;
    width: 8px;
    height: 8px;
    border: 2px solid var(--warm-paper);
    border-radius: 50%;
    background: var(--warm-brand);
    box-shadow: 0 0 10px var(--fx-neon-soft);
  }
}

.site__account-name {
  max-width: 96px;
  color: var(--warm-text-regular);
  font-size: 13px;
  @include truncate;
}

.site__main {
  position: relative;
  z-index: 1;
  flex: 1;
}

// 路由转场保留一点位移，和暖纸底一起形成「翻页」的感觉
.page-enter-active,
.page-leave-active {
  transition:
    opacity 0.28s $ease-out,
    transform 0.28s $ease-out;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(14px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@include media-down($bp-md) {
  // 窄屏隐藏用户名，保留头像与退出按钮
  .site__account-name {
    display: none;
  }
}

// 手机：导航换到第二行做横向滑动，并把两行压紧，避免吸顶头部占掉 1/4 屏
@include media-down($bp-sm) {
  .site__bar {
    gap: 10px;
    min-height: 0;
    padding-block: 8px;
  }

  .site__nav {
    order: 3;
    width: 100%;
    gap: 4px;
    padding: 0;
    overflow-x: auto;
    border: 0;
    background: transparent;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }
  }

  .site__nav-glow {
    display: none;
  }

  .site__link {
    padding: 6px 12px;
    background: var(--warm-paper-70);
    font-size: 13px;
    white-space: nowrap;
    border: 1px solid var(--warm-line);

    &.is-active {
      border-color: rgba(136, 104, 110, 0.34);
      background: rgba(136, 104, 110, 0.12);
    }
  }

  .site__account {
    padding: 3px 10px 3px 4px;
  }
}
</style>
