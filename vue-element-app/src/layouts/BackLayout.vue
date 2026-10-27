<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { DropdownInstance } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import AdminNav from '@/components/AdminNav.vue'
import AppIcon from '@/components/AppIcon.vue'
import BrandLogo from '@/components/BrandLogo.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import { useAuth } from '@/composables/useAuth'

const route = useRoute()
const router = useRouter()
const { user, logout } = useAuth()

const drawer = ref(false)

// 侧栏收起状态：记到 localStorage，刷新后保持上次选择
const COLLAPSED_KEY = 'xinli.admin.collapsed'
const collapsed = ref(localStorage.getItem(COLLAPSED_KEY) === '1')
watch(collapsed, (value) => localStorage.setItem(COLLAPSED_KEY, value ? '1' : '0'))

const currentTitle = computed(() => route.meta.title ?? '工作台')
const initial = computed(() => (user.value?.name ?? '林医生').slice(0, 1))

function toggleCollapsed(): void {
  collapsed.value = !collapsed.value
}

// 退出登录：侧栏账号卡、抽屉账号卡、顶栏头像菜单都走这里
async function signOut(): Promise<void> {
  try {
    await ElMessageBox.confirm('退出后需要重新登录才能进入后台。', '退出登录', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      customClass: 'warm-message-box',
    })
  } catch {
    return
  }

  logout()
  ElMessage.success({ message: '已退出登录', customClass: 'warm-toast' })
  await router.replace('/login')
}

// 自定义账号面板：先收起浮层，再弹退出确认，避免两层浮层叠在一起
const userMenu = ref<DropdownInstance>()

function logoutFromMenu(): void {
  userMenu.value?.handleClose()
  void signOut()
}

function openProfile(): void {
  userMenu.value?.handleClose()
  void router.push('/back/profile')
}
</script>

<template>
  <div class="admin theme-warm" :class="{ 'admin--collapsed': collapsed }">
    <div class="admin__ambient" aria-hidden="true">
      <span class="admin__blob admin__blob--sage" />
      <span class="admin__blob admin__blob--apricot" />
      <span class="admin__stars" />
      <span class="admin__grid" />
    </div>

    <aside class="admin__aside" :class="{ 'admin__aside--collapsed': collapsed }">
      <RouterLink to="/back/dashboard" class="admin__brand" aria-label="心理健康 AI 助手后台首页">
        <BrandLogo />
      </RouterLink>

      <p class="admin__group">工作台</p>

      <AdminNav :collapsed="collapsed" />
    </aside>

    <el-drawer v-model="drawer" direction="ltr" size="248px" :with-header="false" class="admin__drawer">
      <BrandLogo />
      <p class="admin__group">工作台</p>
      <AdminNav aria-label="后台导航（抽屉）" @select="drawer = false" />
    </el-drawer>

    <div class="admin__body">
      <header class="admin__top">
        <button
          class="admin__collapse-btn"
          type="button"
          :aria-label="collapsed ? '展开菜单' : '收起菜单'"
          :aria-expanded="!collapsed"
          :title="collapsed ? '展开菜单' : '收起菜单'"
          @click="toggleCollapsed"
        >
          <AppIcon :name="collapsed ? 'chevronRight' : 'chevronLeft'" :size="18" />
        </button>

        <button class="admin__menu-btn" type="button" aria-label="打开菜单" @click="drawer = true">
          <AppIcon name="menu" :size="20" />
        </button>

        <nav class="admin__crumb" aria-label="面包屑">
          <span>工作台</span>
          <i aria-hidden="true">/</i>
          <strong>{{ currentTitle }}</strong>
        </nav>

        <div class="admin__top-actions">
          <ThemeToggle class="admin__theme" />

          <el-dropdown
            ref="userMenu"
            trigger="click"
            placement="bottom-end"
            :show-arrow="false"
            popper-class="admin-user-pop"
          >
            <button class="admin__top-user" type="button" :title="`${user?.name ?? '林医生'} · 账号菜单`">
              <span class="admin__avatar admin__avatar--sm">{{ initial }}</span>
              <span class="admin__top-caret" aria-hidden="true">
                <AppIcon name="chevronRight" :size="12" />
              </span>
            </button>

            <template #dropdown>
              <div class="user-menu">
                <header class="user-menu__head">
                  <span class="user-menu__avatar">{{ initial }}</span>
                  <div class="user-menu__identity">
                    <strong>{{ user?.name ?? '林医生' }}</strong>
                    <p>{{ user?.title ?? '咨询师 · 管理员' }}</p>
                  </div>
                  <span class="user-menu__badge">ADMIN</span>
                </header>

                <dl class="user-menu__meta">
                  <div>
                    <dt>账号</dt>
                    <dd>{{ user?.account ?? 'back' }}</dd>
                  </div>
                  <div>
                    <dt>权限</dt>
                    <dd>后台管理</dd>
                  </div>
                </dl>

                <button class="user-menu__entry" type="button" @click="openProfile">
                  <span class="user-menu__entry-icon">
                    <AppIcon name="users" :size="15" />
                  </span>
                  <span class="user-menu__entry-text">
                    <strong>个人资料</strong>
                    <small>姓名、身份与登录密码</small>
                  </span>
                  <AppIcon name="arrowRight" :size="14" class="user-menu__entry-arrow" />
                </button>

                <button class="user-menu__logout" type="button" @click="logoutFromMenu">
                  <span class="user-menu__logout-icon">
                    <AppIcon name="logout" :size="15" />
                  </span>
                  <span class="user-menu__logout-text">
                    <strong>退出登录</strong>
                    <small>退出后需要重新验证身份</small>
                  </span>
                  <AppIcon name="arrowRight" :size="14" class="user-menu__logout-arrow" />
                </button>
              </div>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="admin__main">
        <router-view v-slot="{ Component }">
          <Transition name="page" mode="out-in">
            <component :is="Component" />
          </Transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.admin {
  position: relative;
  min-height: 100vh;
  overflow-x: clip;
  background:
    radial-gradient(1100px 620px at 84% -12%, rgba(186, 166, 170, 0.2), transparent 62%),
    radial-gradient(900px 540px at 2% 108%, rgba(209, 138, 92, 0.14), transparent 60%),
    linear-gradient(180deg, var(--warm-paper-alt) 0%, var(--warm-paper-alt) 54%, var(--warm-paper-alt) 100%);
}

// 背景氛围层：日光团 + 点阵，固定在视口，滚动时内容像浮在暖纸上
.admin__ambient {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.admin__blob {
  position: absolute;
  width: 620px;
  height: 620px;
  border-radius: 50%;
  // 径向渐变本身边缘就很柔，不再叠 filter: blur()：省掉两个全屏模糊层
  opacity: 0.32;

  &--sage {
    top: -220px;
    right: -140px;
    background: radial-gradient(circle, rgba(186, 166, 170, 0.55), transparent 66%);
    animation: aurora-drift-a 22s ease-in-out infinite;
  }

  &--apricot {
    bottom: -280px;
    left: 16%;
    background: radial-gradient(circle, rgba(209, 138, 92, 0.28), transparent 66%);
    animation: aurora-drift-b 27s ease-in-out infinite;
  }
}

// 夜间的细碎星光：纯静态径向渐变，不参与动画，只在上部渐隐
.admin__stars {
  position: absolute;
  inset: 0;
  opacity: 0;
  background-image:
    radial-gradient(circle at 7% 11%, rgba(250, 242, 238, 0.9) 0 1.1px, transparent 1.7px),
    radial-gradient(circle at 14% 27%, rgba(232, 201, 143, 0.55) 0 0.9px, transparent 1.5px),
    radial-gradient(circle at 21% 8%, rgba(250, 242, 238, 0.6) 0 0.8px, transparent 1.4px),
    radial-gradient(circle at 29% 19%, rgba(226, 192, 138, 0.7) 0 1.2px, transparent 1.8px),
    radial-gradient(circle at 36% 33%, rgba(250, 242, 238, 0.45) 0 0.8px, transparent 1.4px),
    radial-gradient(circle at 43% 13%, rgba(250, 242, 238, 0.7) 0 1px, transparent 1.6px),
    radial-gradient(circle at 51% 25%, rgba(232, 201, 143, 0.5) 0 0.9px, transparent 1.5px),
    radial-gradient(circle at 58% 7%, rgba(250, 242, 238, 0.55) 0 0.8px, transparent 1.4px),
    radial-gradient(circle at 64% 21%, rgba(226, 192, 138, 0.75) 0 1.1px, transparent 1.8px),
    radial-gradient(circle at 72% 12%, rgba(250, 242, 238, 0.5) 0 0.8px, transparent 1.4px),
    radial-gradient(circle at 79% 29%, rgba(250, 242, 238, 0.7) 0 1px, transparent 1.6px),
    radial-gradient(circle at 86% 16%, rgba(232, 201, 143, 0.6) 0 1px, transparent 1.6px),
    radial-gradient(circle at 92% 34%, rgba(250, 242, 238, 0.45) 0 0.8px, transparent 1.4px),
    radial-gradient(circle at 96% 9%, rgba(226, 192, 138, 0.65) 0 1.2px, transparent 1.8px),
    radial-gradient(circle at 18% 44%, rgba(250, 242, 238, 0.35) 0 0.8px, transparent 1.4px),
    radial-gradient(circle at 47% 47%, rgba(232, 201, 143, 0.4) 0 0.9px, transparent 1.5px),
    radial-gradient(circle at 68% 41%, rgba(250, 242, 238, 0.35) 0 0.8px, transparent 1.4px),
    radial-gradient(circle at 88% 52%, rgba(226, 192, 138, 0.4) 0 0.9px, transparent 1.5px);
  background-repeat: no-repeat;
  mask-image: linear-gradient(180deg, #000 0%, rgba(0, 0, 0, 0.55) 52%, transparent 80%);
  -webkit-mask-image: linear-gradient(180deg, #000 0%, rgba(0, 0, 0, 0.55) 52%, transparent 80%);
  transition: opacity 0.6s $ease-out;
  pointer-events: none;
}

.admin__grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgba(120, 100, 70, 0.2) 1px, transparent 1px);
  background-size: 28px 28px;
  opacity: 0.28;
  mask-image: radial-gradient(ellipse 90% 70% at 62% 4%, #000 8%, transparent 72%);
  -webkit-mask-image: radial-gradient(ellipse 90% 70% at 62% 4%, #000 8%, transparent 72%);
}

.admin__aside {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  width: $sidebar-width;
  padding: 22px 14px;
  background:
    radial-gradient(120% 42% at 50% 0%, rgba(186, 166, 170, 0.22), transparent 64%),
    linear-gradient(180deg, var(--warm-paper-96) 0%, var(--warm-paper-alt) 100%);
  border-right: 1px solid var(--warm-line);
  box-shadow: 28px 0 70px -60px rgba(74, 64, 46, 0.55);
  color: var(--warm-text);
  // 侧栏是固定的，背后没有滚动内容，不需要 backdrop-filter 的实时模糊
  // 收起 / 展开时宽度与内边距一起过渡，避免文字瞬间换行
  transition:
    width 0.24s $ease-out,
    padding 0.24s $ease-out;

  // 右缘的一条绿色细线，把侧栏和内容区分开
  &::after {
    content: '';
    position: absolute;
    top: 18px;
    bottom: 18px;
    right: -1px;
    width: 1px;
    background: linear-gradient(180deg, transparent, rgba(136, 104, 110, 0.34), transparent);
    opacity: 0.65;
    pointer-events: none;
  }
}

// 收起：只留图标轨道，品牌文字、分组标题、用户信息都隐藏
.admin__aside--collapsed {
  width: $sidebar-collapsed-width;
  padding-inline: 10px;

  .admin__brand {
    justify-self: center;
    padding: 0;

    :deep(.brand__text) {
      display: none;
    }
  }

  .admin__group {
    display: none;
  }
}

.admin__brand {
  padding: 0 6px;

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

.admin__group {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 26px 10px 8px;
  color: var(--warm-text-muted);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.2em;
  text-transform: uppercase;

  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: linear-gradient(90deg, rgba(92, 77, 52, 0.16), transparent);
  }
}

// 顶栏头像：点击弹出账号面板
.admin__top-user {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 5px 3px 3px;
  border: 1px solid transparent;
  border-radius: $radius-pill;
  background: var(--warm-paper-50);
  cursor: pointer;
  transition:
    border-color 0.2s $ease-out,
    background 0.2s $ease-out,
    box-shadow 0.2s $ease-out;

  &:hover {
    border-color: rgba(136, 104, 110, 0.26);
    background: var(--warm-paper-90);
    box-shadow: 0 10px 24px -18px rgba(136, 104, 110, 0.55);

    .admin__top-caret {
      color: var(--warm-brand-strong);
    }
  }

  &:focus-visible {
    @include focus-ring;
  }
}

.admin__top-caret {
  display: grid;
  place-items: center;
  width: 14px;
  color: var(--warm-text-muted);
  transform: rotate(90deg);
  transition: color 0.2s $ease-out;
}

.admin__avatar {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--warm-brand-strong), var(--warm-sage-light));
  color: var(--warm-on-brand);
  font-size: 13px;
  font-weight: 600;
  box-shadow:
    0 0 0 1px var(--warm-paper-65),
    0 8px 20px -12px rgba(112, 80, 86, 0.7);
  transition: box-shadow 0.28s $ease-out;

  &--sm {
    width: 32px;
    height: 32px;
  }
}

.admin__top-user:hover .admin__avatar {
  box-shadow:
    0 0 0 1px var(--warm-paper-85),
    0 10px 24px -10px rgba(112, 80, 86, 0.8);
}

.admin__body {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  min-height: 0;
  overflow: hidden;
  margin-left: $sidebar-width;
  transition: margin-left 0.24s $ease-out;
}

.admin--collapsed .admin__body {
  margin-left: $sidebar-collapsed-width;
}

.admin__top {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: $space-4;
  padding: 15px 30px;
  background: var(--warm-header-72);
  border-bottom: 1px solid var(--warm-line);
  box-shadow: 0 24px 50px -48px rgba(74, 64, 46, 0.5);
  // 顶栏与内容区彼此独立滚动，没有内容从它下面经过，去掉实时模糊更省 GPU

  &::after {
    content: '';
    position: absolute;
    right: 10%;
    bottom: -1px;
    left: 10%;
    height: 1px;
    background: linear-gradient(90deg, transparent, rgba(136, 104, 110, 0.22), transparent);
    pointer-events: none;
  }
}

.admin__menu-btn,
.admin__collapse-btn {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid var(--warm-line);
  border-radius: 11px;
  background: var(--warm-paper-72);
  color: var(--warm-text-regular);
  cursor: pointer;
  transition:
    border-color $duration-fast $ease-out,
    color $duration-fast $ease-out,
    background $duration-fast $ease-out,
    box-shadow $duration-fast $ease-out;

  &:hover {
    border-color: rgba(136, 104, 110, 0.38);
    background: var(--warm-paper);
    color: var(--warm-brand-strong);
    box-shadow: 0 10px 22px -18px rgba(136, 104, 110, 0.6);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

// 桌面端显示收起按钮，移动端改用抽屉按钮
.admin__menu-btn {
  display: none;
}

.admin__crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: var(--warm-text-muted);
  font-size: 12.5px;

  i {
    color: rgba(92, 77, 52, 0.32);
    font-style: normal;
  }

  strong {
    overflow: hidden;
    color: var(--warm-text);
    font-family: $font-display;
    font-size: 15px;
    font-weight: 600;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}

.admin__top-actions {
  display: flex;
  align-items: center;
  gap: $space-3;
  margin-left: auto;
}

.admin__main {
  flex: 1;
  min-height: 0;
  width: 100%;
  // 后台以表格和图表为主，内容区跟随视口铺开，只在大屏留一个上限
  max-width: 1800px;
  margin-inline: auto;
  padding: 30px clamp(24px, 2.2vw, 44px) 64px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

@include media-down($bp-lg) {
  .admin__aside {
    display: none;
  }

  .admin__body {
    margin-left: 0;
    // 断点切换时不要给 margin 做过渡，否则窄屏首帧会被 236px 侧栏挤出一条横向滚动
    transition: none;
  }

  // 移动端起效：抽屉按钮出现，桌面收起按钮隐藏
  .admin__menu-btn {
    display: grid;
  }

  .admin__collapse-btn {
    display: none;
  }
}

@include media-down($bp-md) {
  .admin__top {
    padding: 12px 16px;
  }

  .admin__crumb span,
  .admin__crumb i {
    display: none;
  }

  .admin__main {
    padding: 16px 16px 40px;
  }
}
</style>

<style lang="scss">
// 抽屉被 teleport 到 body，用全局样式限定在 .admin__drawer 内。
.admin__drawer {
  background:
    radial-gradient(120% 42% at 50% 0%, rgba(186, 166, 170, 0.22), transparent 64%),
    linear-gradient(180deg, var(--warm-paper-alt) 0%, var(--warm-paper-alt) 100%);
  border-right: 1px solid var(--warm-line);
  box-shadow: 40px 0 100px -60px rgba(74, 64, 46, 0.55);

  .el-drawer__body {
    display: flex;
    flex-direction: column;
    padding: 22px 14px;
    background: transparent;
  }

  .admin__group {
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 26px 10px 8px;
    color: var(--warm-text-muted);
    font-size: 10.5px;
    font-weight: 600;
    letter-spacing: 0.2em;
    text-transform: uppercase;

    &::after {
      content: '';
      flex: 1;
      height: 1px;
      background: linear-gradient(90deg, rgba(92, 77, 52, 0.16), transparent);
    }
  }

  .el-menu {
    --el-menu-bg-color: transparent;
    --el-menu-text-color: var(--warm-text-regular);
    --el-menu-active-color: var(--warm-brand-strong);
    --el-menu-hover-bg-color: color-mix(in srgb, var(--warm-brand) 8%, transparent);

    border-right: none;
  }

  .el-menu-item {
    height: 44px;
    margin: 4px 0;
    padding: 0 12px;
    gap: 10px;
    border-radius: 11px;
    color: var(--warm-text-regular);
    font-size: 14px;
    font-weight: 500;

    &.is-active {
      background: linear-gradient(
        90deg,
        color-mix(in srgb, var(--warm-brand) 15%, transparent),
        color-mix(in srgb, var(--warm-brand) 4%, transparent)
      );
      box-shadow:
        inset 0 0 0 1px color-mix(in srgb, var(--warm-brand) 22%, transparent),
        0 10px 22px -18px color-mix(in srgb, var(--warm-brand) 60%, transparent);
      color: var(--warm-brand-strong);
      font-weight: 600;
    }
  }

  .brand {
    padding: 0 6px;
  }
}

// 头像账号面板（teleport 到 body，用 popper-class 定位）
.admin-user-pop.el-popper {
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--warm-line);
  border-radius: 16px;
  background:
    radial-gradient(120% 80% at 88% -10%, rgba(209, 138, 92, 0.1), transparent 62%),
    linear-gradient(165deg, var(--warm-paper) 0%, var(--warm-paper-alt) 100%);
  box-shadow:
    0 34px 72px -40px rgba(74, 64, 46, 0.45),
    0 0 44px -30px rgba(136, 104, 110, 0.35);

  .el-dropdown__list {
    padding: 0;
  }
}

.user-menu {
  position: relative;
  width: 268px;
  padding: 10px;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    right: 16%;
    left: 16%;
    height: 1px;
    background: linear-gradient(90deg, transparent, rgba(136, 104, 110, 0.45), rgba(209, 138, 92, 0.32), transparent);
    pointer-events: none;
  }
}

.user-menu__head {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 8px 8px 13px;
}

.user-menu__avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--warm-brand-strong), var(--warm-sage-light));
  box-shadow:
    0 0 0 1px var(--warm-paper-70),
    0 8px 20px -12px rgba(112, 80, 86, 0.7);
  color: var(--warm-on-brand);
  font-family: $font-display;
  font-size: 16px;
  font-weight: 700;
}

.user-menu__identity {
  display: grid;
  gap: 2px;
  min-width: 0;

  strong {
    overflow: hidden;
    color: var(--warm-text);
    font-family: $font-display;
    font-size: 14.5px;
    font-weight: 600;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  p {
    margin: 0;
    overflow: hidden;
    color: var(--warm-text-muted);
    font-size: 11.5px;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}

.user-menu__badge {
  margin-left: auto;
  padding: 3px 7px;
  border: 1px solid rgba(196, 143, 154, 0.3);
  border-radius: $radius-pill;
  background: rgba(196, 143, 154, 0.12);
  color: #88686e;
  font-family: $font-mono;
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.user-menu__meta {
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  gap: 8px;
  margin: 0 4px 10px;

  > div {
    min-width: 0;
    padding: 8px 10px;
    border: 1px solid var(--warm-line);
    border-radius: 10px;
    background: var(--warm-paper-72);
  }

  dt {
    margin-bottom: 3px;
    color: var(--warm-text-muted);
    font-size: 9.5px;
    letter-spacing: 0.08em;
  }

  dd {
    margin: 0;
    overflow: hidden;
    color: var(--warm-text-regular);
    font-family: $font-mono;
    font-size: 11.5px;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}

.user-menu__entry {
  display: flex;
  align-items: center;
  gap: 10px;
  width: calc(100% - 8px);
  margin: 0 4px 8px;
  padding: 8px 10px;
  border: 1px solid var(--warm-line);
  border-radius: 11px;
  background: var(--warm-paper-72);
  color: var(--warm-text-regular);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.2s $ease-out,
    background 0.2s $ease-out,
    color 0.2s $ease-out;

  &:hover {
    border-color: rgba(136, 104, 110, 0.34);
    background: color-mix(in srgb, var(--warm-brand) 8%, transparent);
    color: var(--warm-brand-strong);

    .user-menu__entry-icon {
      border-color: rgba(136, 104, 110, 0.28);
      background: color-mix(in srgb, var(--warm-brand) 12%, transparent);
      color: var(--warm-brand-strong);
    }

    .user-menu__entry-arrow {
      opacity: 1;
      transform: translateX(2px);
    }
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

.user-menu__entry-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid var(--warm-line);
  border-radius: 9px;
  background: var(--warm-paper-80);
  color: var(--warm-text-muted);
  transition:
    border-color 0.2s $ease-out,
    background 0.2s $ease-out,
    color 0.2s $ease-out;
}

.user-menu__entry-text {
  display: grid;
  gap: 1px;

  strong {
    font-size: 12.5px;
    font-weight: 600;
  }

  small {
    color: var(--warm-text-muted);
    font-size: 10.5px;
  }
}

.user-menu__entry-arrow {
  margin-left: auto;
  color: var(--warm-text-muted);
  opacity: 0.45;
  transition:
    opacity 0.2s $ease-out,
    transform 0.2s $ease-out;
}

.user-menu__logout {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin: 0 4px;
  padding: 8px 10px;
  border: 1px solid var(--warm-line);
  border-radius: 11px;
  background: var(--warm-paper-72);
  color: var(--warm-text-regular);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.2s $ease-out,
    background 0.2s $ease-out,
    color 0.2s $ease-out;

  &:hover {
    border-color: rgba(207, 138, 131, 0.38);
    background: rgba(207, 138, 131, 0.1);
    color: #b85c4c;

    .user-menu__logout-icon {
      border-color: rgba(207, 138, 131, 0.32);
      background: rgba(207, 138, 131, 0.14);
      color: #b85c4c;
    }

    .user-menu__logout-arrow {
      opacity: 1;
      transform: translateX(2px);
    }
  }

  &:focus-visible {
    @include focus-ring(#f08b7a);
  }
}

.user-menu__logout-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid var(--warm-line);
  border-radius: 9px;
  background: var(--warm-paper-80);
  color: var(--warm-text-muted);
  transition:
    border-color 0.2s $ease-out,
    background 0.2s $ease-out,
    color 0.2s $ease-out;
}

.user-menu__logout-text {
  display: grid;
  gap: 1px;

  strong {
    font-size: 12.5px;
    font-weight: 600;
  }

  small {
    color: var(--warm-text-muted);
    font-size: 10.5px;
  }
}

.user-menu__logout-arrow {
  margin-left: auto;
  color: var(--warm-text-muted);
  opacity: 0.45;
  transition:
    opacity 0.2s $ease-out,
    transform 0.2s $ease-out;
}
</style>
