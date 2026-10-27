<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'

import AppIcon from '@/components/AppIcon.vue'
import AuroraBackdrop from '@/components/AuroraBackdrop.vue'
import BrandLogo from '@/components/BrandLogo.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import { useActionGuard } from '@/composables/useActionGuard'
import { ROLE_HOME, useAuth } from '@/composables/useAuth'

interface LoginForm {
  account: string
  password: string
  remember: boolean
}

const route = useRoute()
const router = useRouter()
const { login } = useAuth()

const formRef = ref<FormInstance>()
const form = reactive<LoginForm>({
  account: 'front',
  password: 'front',
  remember: true,
})

const rules: FormRules<LoginForm> = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 4, message: '密码至少 4 位', trigger: 'blur' },
  ],
}

const highlights = [
  { icon: 'shield' as const, text: '账号由后端校验，登录态保存在本机浏览器' },
  { icon: 'chat' as const, text: '前台：倾诉、情绪打卡与心理科普' },
  { icon: 'chart' as const, text: '后台：咨询趋势、文章与情绪日志运营' },
]

// 登录页顶部两个快捷身份，点击直接填入对应演示账号（密码由后端种子账号校验）
const demoAccounts = [
  { key: 'front' as const, label: '前台体验', password: 'front' },
  { key: 'back' as const, label: '后台管理', password: 'back' },
]

function fillAccount(key: 'front' | 'back'): void {
  form.account = key
  form.password = key
  formRef.value?.clearValidate()
}

async function submitLogin(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  try {
    const nextUser = await login(form.account, form.password, form.remember)
    // 只有角色对得上才回原地址，否则回到该角色的首页
    const base = nextUser.role === 'back' ? '/back' : '/front'
    const wanted = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const target = wanted.startsWith(base) ? wanted : ROLE_HOME[nextUser.role]

    ElMessage.success({ message: `欢迎回来，${nextUser.name}`, customClass: 'warm-toast' })
    await router.replace(target)
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '登录失败，请稍后重试',
      customClass: 'warm-toast',
    })
  }
}

// 防连点：900ms 内只提交一次
const { pending, trigger: submit } = useActionGuard(submitLogin, { wait: 900 })
</script>

<template>
  <div class="login theme-warm">
    <AuroraBackdrop />

    <ThemeToggle class="login__theme" />

    <div class="login__inner">
      <section class="login__aside">
        <div class="login__brand">
          <BrandLogo />
          <span class="login__tag">Demo · 2026</span>
        </div>

        <div class="login__copy">
          <p class="eyebrow">A quieter place to talk</p>
          <h1>
            把心事<br />
            说给<span>懂你</span>的<br />
            AI 听
          </h1>
          <p>前台倾诉与情绪记录，后台内容与咨询运营。同一个入口，账号决定进入哪一端。</p>
        </div>

        <ol class="login__points">
          <li v-for="(item, index) in highlights" :key="item.text">
            <em>0{{ index + 1 }}</em>
            <span class="login__point-icon"><AppIcon :name="item.icon" :size="15" /></span>
            <p>{{ item.text }}</p>
          </li>
        </ol>

        <p class="login__safety">
          <AppIcon name="alert" :size="15" />
          演示环境，请勿填写真实来访者信息；紧急情况拨打 120 / 110 或心理援助热线 12356。
        </p>
      </section>

      <section class="login__panel">
        <span class="login__halo" aria-hidden="true" />

        <div class="login__form">
          <header class="login__head">
            <p class="eyebrow">Welcome back</p>
            <h2>登录</h2>
            <p class="login__desc">选择演示身份，或手动输入账号密码。</p>
          </header>

          <div class="login__accounts" role="group" aria-label="演示账号快捷填入">
            <button
              v-for="item in demoAccounts"
              :key="item.key"
              type="button"
              class="login__account"
              :class="{ 'login__account--active': form.account === item.key }"
              :aria-pressed="form.account === item.key"
              @click="fillAccount(item.key)"
            >
              <span class="login__account-avatar">
                <AppIcon :name="item.key === 'front' ? 'heart' : 'chart'" :size="16" />
              </span>
              <span class="login__account-text">
                <strong>{{ item.label }}</strong>
                <small>{{ item.key }} / {{ item.password }}</small>
              </span>
              <span class="login__account-mark" aria-hidden="true" />
            </button>
          </div>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            size="large"
            class="login__fields"
            @submit.prevent="submit"
          >
            <el-form-item label="账号" prop="account">
              <el-input v-model="form.account" placeholder="front 或 back" autocomplete="username">
                <template #prefix><AppIcon name="users" :size="16" /></template>
              </el-input>
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                placeholder="请输入密码"
                autocomplete="current-password"
                @keyup.enter="submit"
              >
                <template #prefix><AppIcon name="shield" :size="16" /></template>
              </el-input>
            </el-form-item>

            <div class="login__row">
              <el-checkbox v-model="form.remember">记住我</el-checkbox>
              <span class="login__demo">账号决定进入前台还是后台</span>
            </div>

            <el-button type="primary" class="login__submit" :loading="pending" @click="submit">
              登录
              <AppIcon name="arrowRight" :size="16" />
            </el-button>
          </el-form>

          <footer class="login__foot">
            <span>演示环境 · 登录请求由后端校验</span>
            <span>心理健康 AI 助手</span>
          </footer>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login {
  position: relative;
  min-height: 100vh;
  background: transparent;
}

.login__theme {
  position: absolute;
  top: clamp(18px, 2.6vw, 32px);
  right: clamp(18px, 2.6vw, 32px);
  z-index: 5;
}

.login__inner {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
  width: min(100%, 1320px);
  min-height: 100vh;
  margin-inline: auto;
  padding: clamp(24px, 3.4vw, 48px);
  gap: clamp(28px, 5vw, 72px);
  align-items: stretch;
}

// ---- 左侧品牌叙事 ----
.login__aside {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: clamp(26px, 4vh, 44px);
  padding: clamp(8px, 1.4vw, 20px) 0;
}

@keyframes login-rise {
  from {
    opacity: 0;
    transform: translateY(18px);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

.login__brand,
.login__copy,
.login__points,
.login__safety,
.login__form {
  animation: login-rise 0.8s $ease-out-expo both;
}

.login__brand {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $space-4;
  animation-delay: 40ms;
}

.login__copy {
  animation-delay: 140ms;
}

.login__points {
  animation-delay: 240ms;
}

.login__safety {
  animation-delay: 320ms;
}

.login__form {
  animation-delay: 180ms;
}

.login__tag {
  padding: 5px 12px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-pill;
  background: var(--warm-paper-66);
  color: var(--warm-text-muted);
  font-family: $font-mono;
  font-size: 10.5px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.login__copy {
  max-width: 480px;

  h1 {
    margin: 18px 0 0;
    color: var(--warm-text);
    font-family: $font-display;
    font-size: clamp(38px, 4.4vw, 58px);
    font-weight: 600;
    line-height: 1.16;
    letter-spacing: -0.03em;

    span {
      background: var(--warm-gradient);
      background-size: 200% auto;
      -webkit-background-clip: text;
      background-clip: text;
      color: transparent;
      animation: gradient-pan 7s ease-in-out infinite;
    }
  }

  p {
    max-width: 360px;
    margin: 20px 0 0;
    color: var(--warm-text-muted);
    font-size: 13.5px;
    line-height: 1.9;
  }
}

.login__points {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--warm-line);

  li {
    display: grid;
    grid-template-columns: 30px 26px minmax(0, 1fr);
    align-items: center;
    gap: 10px;
    padding: 14px 0;
    border-bottom: 1px solid var(--warm-line);
  }

  em {
    color: var(--warm-text-muted);
    opacity: 0.8;
    font-family: $font-mono;
    font-size: 10.5px;
    font-style: normal;
    letter-spacing: 0.1em;
  }

  p {
    margin: 0;
    color: var(--warm-text-regular);
    font-size: 12.5px;
  }
}

.login__point-icon {
  display: grid;
  place-items: center;
  color: var(--warm-brand-strong);
  filter: drop-shadow(0 2px 4px rgba(136, 104, 110, 0.18));
}

.login__safety {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: auto 0 0;
  color: var(--warm-text-muted);
  font-size: 11.5px;
  line-height: 1.7;

  svg {
    margin-top: 2px;
    color: var(--warm-apricot);
  }
}

// ---- 右侧玻璃表单 ----
.login__panel {
  position: relative;
  display: grid;
  place-items: center;
}

.login__halo {
  position: absolute;
  top: 50%;
  left: 50%;
  // 光晕不能超出所在的表单栏，否则绝对定位会把文档撑宽、撑高
  width: min(620px, 100%);
  height: min(620px, 100%);
  // 用独立的 translate 居中：下面的 breathe 动画会写 transform: scale(...)，
  // 如果居中也写在 transform 里会被动画整个覆盖，光晕被推到右下方并撑出滚动条
  translate: -50% -50%;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(136, 104, 110, 0.16), rgba(217, 169, 79, 0.06) 44%, transparent 64%);
  animation: breathe 10s ease-in-out infinite;
  pointer-events: none;
}

.login__form {
  position: relative;
  z-index: 1;
  width: min(430px, 100%);
  padding: clamp(24px, 3vw, 38px);
  border: 1px solid var(--warm-line);
  border-radius: $radius-xl;
  background: linear-gradient(165deg, var(--warm-paper-96), var(--warm-paper-90));
  box-shadow:
    0 50px 100px -64px rgba(74, 64, 46, 0.5),
    0 0 70px -46px rgba(136, 104, 110, 0.4),
    inset 0 1px 0 var(--warm-paper-92);
}

.login__head {
  margin-bottom: $space-5;

  h2 {
    margin: 8px 0 0;
    font-family: $font-display;
    font-size: 38px;
    font-weight: 600;
    line-height: 1.1;
    letter-spacing: -0.03em;
    color: var(--warm-text);
  }
}

.login__desc {
  margin: 10px 0 0;
  color: var(--warm-text-muted);
  font-size: 12.5px;
}

// 演示身份卡片：选中时用柔绿色描边 + 浅投影
.login__accounts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: $space-5;
}

.login__account {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-sm;
  background: var(--warm-paper-60);
  color: var(--warm-text-regular);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.25s $ease-out,
    background 0.25s $ease-out,
    box-shadow 0.25s $ease-out,
    transform 0.25s $ease-out-expo;

  &:hover {
    transform: translateY(-2px);
    border-color: rgba(136, 104, 110, 0.4);
    background: color-mix(in srgb, var(--warm-brand) 12%, transparent);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

.login__account-avatar {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: none;
  border-radius: 50%;
  background: rgba(136, 104, 110, 0.12);
  color: var(--warm-brand-strong);
  transition:
    background 0.25s $ease-out,
    color 0.25s $ease-out,
    box-shadow 0.25s $ease-out;
}

.login__account-text {
  display: grid;
  min-width: 0;
  line-height: 1.3;

  strong {
    color: var(--warm-text);
    font-size: 13px;
    font-weight: 600;
  }

  small {
    margin-top: 3px;
    overflow: hidden;
    color: var(--warm-text-muted);
    font-size: 10.5px;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}

.login__account-mark {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 7px;
  height: 7px;
  border: 1px solid var(--warm-line);
  border-radius: 50%;
  transition:
    background 0.25s $ease-out,
    border-color 0.25s $ease-out,
    box-shadow 0.25s $ease-out;
}

.login__account--active {
  border-color: rgba(136, 104, 110, 0.46);
  background: rgba(136, 104, 110, 0.08);
  box-shadow: 0 12px 28px -22px rgba(112, 80, 86, 0.6);

  .login__account-avatar {
    background: linear-gradient(135deg, var(--warm-brand-strong), var(--warm-sage-light));
    color: var(--warm-on-brand);
    box-shadow: 0 8px 18px -12px rgba(112, 80, 86, 0.7);
  }

  .login__account-mark {
    border-color: var(--warm-brand);
    background: var(--warm-brand);
    box-shadow: 0 0 0 3px rgba(136, 104, 110, 0.16);
  }
}

.login__fields {
  :deep(.el-form-item__label) {
    padding-bottom: 6px;
    color: var(--warm-text-muted);
    font-size: 11.5px;
    font-weight: 500;
    letter-spacing: 0.08em;
  }

  :deep(.el-input__wrapper) {
    height: 46px;
    padding-inline: 12px;
    border-radius: $radius-sm;
  }

  :deep(.el-input__prefix) {
    color: var(--warm-text-muted);
  }
}

.login__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $space-3;
  margin: 2px 0 20px;
}

.login__demo {
  color: var(--warm-text-muted);
  font-size: 11px;
}

.login__submit {
  width: 100%;
  height: 50px;
  gap: 8px;
  border: 0;
  border-radius: $radius-sm;
  background: linear-gradient(120deg, var(--warm-brand-strong), var(--warm-brand));
  color: var(--warm-on-brand);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.06em;
  box-shadow:
    0 18px 38px -20px rgba(112, 80, 86, 0.7),
    inset 0 1px 0 var(--warm-rim-26);
  transition:
    transform 0.25s $ease-out,
    box-shadow 0.25s $ease-out;

  svg {
    transition: transform 0.25s $ease-out;
  }

  &:hover {
    transform: translateY(-2px);
    background: linear-gradient(120deg, var(--warm-brand-deep), var(--warm-brand-strong));
    color: var(--warm-on-brand);
    box-shadow:
      0 24px 48px -20px rgba(112, 80, 86, 0.8),
      inset 0 1px 0 var(--warm-rim-34);

    svg {
      transform: translateX(3px);
    }
  }

  &:active {
    transform: translateY(0);
  }
}

.login__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-top: $space-5;
  padding-top: $space-4;
  border-top: 1px solid var(--warm-line);
  color: var(--warm-text-muted);
  font-size: 11px;
}

@media (prefers-reduced-motion: reduce) {
  .login__brand,
  .login__copy,
  .login__points,
  .login__safety,
  .login__form,
  .login__halo,
  .login__copy h1 span {
    animation: none !important;
  }
}

@include media-down($bp-lg) {
  .login__inner {
    grid-template-columns: 1fr;
    gap: 28px;
    padding: 24px 20px 40px;
  }

  .login__aside {
    gap: 16px;
    padding: 0;
  }

  .login__copy h1 {
    font-size: clamp(30px, 7vw, 40px);

    br {
      display: none;
    }
  }

  .login__copy p {
    max-width: 480px;
    margin-top: 12px;
    font-size: 12.5px;
  }

  .login__points,
  .login__safety {
    display: none;
  }

  .login__panel {
    padding-bottom: 12px;
  }

  .login__halo {
    width: min(460px, 100%);
    height: min(460px, 100%);
  }
}

@include media-down($bp-sm) {
  // 窄屏时品牌行右端会和右上角的主题切换按钮重叠，演示标签在页脚已重复说明
  .login__tag {
    display: none;
  }

  .login__accounts {
    grid-template-columns: 1fr;
  }

  .login__row {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .login__head h2 {
    font-size: 32px;
  }

  .login__form {
    padding: 22px 18px;
    border-radius: $radius-lg;
  }

  .login__foot {
    flex-direction: column;
    align-items: flex-start;
  }
}

// 单列布局（平板 / 窄桌面窗口）在较矮的可视区里也收紧一档，
// 避免明明是大屏窗口却因为单列堆叠而出现上下滚动条。
@media (max-width: $bp-lg) and (min-width: #{$bp-md + 1px}) and (max-height: 900px) {
  .login__inner {
    gap: 18px;
    padding: 12px 20px 16px;
  }

  .login__aside {
    gap: 10px;
  }

  .login__copy h1 {
    margin-top: 8px;
    font-size: clamp(26px, 4.6vw, 32px);
  }

  .login__copy p {
    margin-top: 8px;
    font-size: 12px;
    line-height: 1.6;
  }

  .login__form {
    padding: 18px 22px;
  }

  .login__head {
    margin-bottom: 10px;

    h2 {
      font-size: 28px;
    }
  }

  .login__desc {
    margin-top: 6px;
  }

  .login__accounts {
    gap: 8px;
    margin-bottom: 10px;
  }

  .login__account {
    padding: 9px 10px;
  }

  .login__account-avatar {
    width: 30px;
    height: 30px;
  }

  .login__fields :deep(.el-form-item) {
    margin-bottom: 10px;
  }

  .login__row {
    margin-bottom: 12px;
  }

  .login__submit {
    height: 44px;
  }

  .login__foot {
    margin-top: 12px;
    padding-top: 8px;
  }
}

// 矮屏（1366×768 笔记本的可视高度约 600~700px）：收紧纵向节奏，
// 让登录页一屏放完，不出现上下滚动条。
@media (min-width: #{$bp-lg + 1px}) and (max-height: 780px) {
  .login__inner {
    padding-block: 14px;
  }

  .login__aside {
    gap: 12px;
    padding-block: 0;
  }

  .login__copy h1 {
    margin-top: 10px;
    font-size: clamp(30px, 5.4vh, 44px);
  }

  .login__copy p {
    margin-top: 10px;
    line-height: 1.7;
  }

  .login__points li {
    padding: 7px 0;
  }

  .login__safety {
    font-size: 11px;
  }

  .login__form {
    padding: 20px 26px;
  }

  .login__head {
    margin-bottom: 12px;

    h2 {
      font-size: 30px;
    }
  }

  .login__desc {
    margin-top: 6px;
  }

  .login__accounts {
    gap: 8px;
    margin-bottom: 12px;
  }

  .login__account {
    padding: 9px 10px;
  }

  .login__account-avatar {
    width: 30px;
    height: 30px;
  }

  .login__fields :deep(.el-form-item) {
    margin-bottom: 10px;
  }

  .login__row {
    margin-bottom: 12px;
  }

  .login__submit {
    height: 44px;
  }

  .login__foot {
    margin-top: 12px;
    padding-top: 8px;
  }
}
</style>
