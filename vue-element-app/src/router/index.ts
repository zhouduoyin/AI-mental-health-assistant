import type { RouteRecordRaw, RouterScrollBehavior } from 'vue-router'
import { createRouter, createWebHistory } from 'vue-router'

import FrontLayout from '@/layouts/FrontLayout.vue'
import { ROLE_HOME, useAuth } from '@/composables/useAuth'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/front' },

  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { title: '登录' },
  },

  {
    path: '/front',
    component: FrontLayout,
    // 前台只允许「front」账号访问，未登录先去登录页
    meta: { requiresAuth: true, role: 'front' },
    children: [
      {
        path: '',
        name: 'front-home',
        component: () => import('@/views/front/HomeView.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'chat',
        name: 'front-chat',
        component: () => import('@/views/front/ChatView.vue'),
        meta: { title: 'AI 咨询' },
      },
      {
        path: 'articles',
        name: 'front-articles',
        component: () => import('@/views/front/ArticlesView.vue'),
        meta: { title: '知识库' },
      },
      {
        path: 'mood',
        name: 'front-mood',
        component: () => import('@/views/front/MoodView.vue'),
        meta: { title: '情绪日记' },
      },
      {
        path: 'lab',
        name: 'front-lab',
        component: () => import('@/views/front/LabView.vue'),
        meta: { title: '性能实验室' },
      },
      {
        path: 'profile',
        name: 'front-profile',
        component: () => import('@/views/shared/ProfileView.vue'),
        meta: { title: '个人资料' },
      },
    ],
  },

  {
    path: '/back',
    // 后台布局静态引入会把 Element Plus 菜单/抽屉带进首屏，改成按需加载
    component: () => import('@/layouts/BackLayout.vue'),
    redirect: '/back/dashboard',
    meta: { requiresAuth: true, role: 'back' },
    children: [
      {
        path: 'dashboard',
        name: 'back-dashboard',
        component: () => import('@/views/back/DashboardView.vue'),
        meta: { title: '数据分析' },
      },
      {
        path: 'users',
        name: 'back-users',
        component: () => import('@/views/back/UsersView.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'articles',
        name: 'back-articles',
        component: () => import('@/views/back/ArticlesView.vue'),
        meta: { title: '知识文章' },
      },
      {
        path: 'knowledge',
        name: 'back-knowledge',
        component: () => import('@/views/back/KnowledgeView.vue'),
        meta: { title: '知识库' },
      },
      {
        path: 'consultations',
        name: 'back-consultations',
        component: () => import('@/views/back/ConsultationsView.vue'),
        meta: { title: '咨询记录' },
      },
      {
        path: 'mood-logs',
        name: 'back-mood-logs',
        component: () => import('@/views/back/MoodLogsView.vue'),
        meta: { title: '情绪日志' },
      },
      {
        path: 'profile',
        name: 'back-profile',
        component: () => import('@/views/shared/ProfileView.vue'),
        meta: { title: '个人资料' },
      },
    ],
  },

  { path: '/:pathMatch(.*)*', redirect: '/front' },
]

const scrollBehavior: RouterScrollBehavior = (_to, _from, savedPosition) =>
  savedPosition ?? { top: 0 }

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior,
  routes,
})

// 登录守卫：未登录 → 登录页并记住原地址；角色不符 → 回到本角色首页；已登录访问登录页 → 回首页
router.beforeEach((to) => {
  const { user, isAuthenticated } = useAuth()
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)
  const requiredRole = to.matched.find((record) => record.meta.role)?.meta.role

  if (requiresAuth && !isAuthenticated.value) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (requiredRole && user.value && user.value.role !== requiredRole) {
    return ROLE_HOME[user.value.role]
  }

  if (to.path === '/login' && user.value) {
    return ROLE_HOME[user.value.role]
  }
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 心理健康 AI 助手` : '心理健康 AI 助手'
})

export default router
