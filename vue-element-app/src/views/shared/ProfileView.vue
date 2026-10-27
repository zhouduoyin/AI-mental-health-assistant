<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  changePassword,
  fetchProfile,
  updateProfile,
  type Profile,
} from '@/api/profile'
import AppIcon from '@/components/AppIcon.vue'
import PageHeader from '@/components/PageHeader.vue'
import PanelCard from '@/components/PanelCard.vue'
import { useAuth } from '@/composables/useAuth'

const route = useRoute()
const router = useRouter()
const { user, refresh } = useAuth()

const isFront = computed(() => route.path.startsWith('/front'))
const profile = ref<Profile | null>(null)
const loading = ref(true)

const displayName = computed(() => profile.value?.name ?? user.value?.name ?? '用户')
const initial = computed(() => displayName.value.trim().slice(0, 1) || '用')
const roleText = computed(() => (profile.value?.role === 'back' ? '后台成员' : '前台来访者'))

function formatTime(value: number | null | undefined): string {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatDate(value: number | null | undefined): string {
  if (!value) return '—'
  return new Date(value).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
}

const memberDays = computed(() => {
  if (!profile.value?.createdAt) return '—'
  const days = Math.floor((Date.now() - profile.value.createdAt) / 86_400_000)
  return `${Math.max(days, 1)} 天`
})

const stats = computed(() => [
  {
    key: 'mood',
    label: '情绪记录',
    value: profile.value?.moodCount ?? 0,
    unit: '条',
    icon: 'mood' as const,
  },
  {
    key: 'consult',
    label: '咨询会话',
    value: profile.value?.consultationCount ?? 0,
    unit: '次',
    icon: 'chat' as const,
  },
  {
    key: 'role',
    label: '当前角色',
    value: roleText.value,
    unit: '',
    icon: 'shield' as const,
  },
  {
    key: 'days',
    label: '加入至今',
    value: memberDays.value,
    unit: '',
    icon: 'clock' as const,
  },
])

async function loadProfile(): Promise<void> {
  loading.value = true
  try {
    const data = await fetchProfile()
    profile.value = data
    editor.name = data.name
    editor.title = data.title
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '个人资料加载失败，请稍后重试',
      customClass: 'warm-toast',
    })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadProfile()
})

// ---- 基本资料 ----
const editor = reactive({ name: '', title: '' })
const savingProfile = ref(false)
const isDirty = computed(
  () => !!profile.value
    && (editor.name.trim() !== profile.value.name || editor.title.trim() !== profile.value.title),
)

async function saveProfile(): Promise<void> {
  if (!editor.name.trim()) {
    ElMessage.warning({ message: '请输入姓名', customClass: 'warm-toast' })
    return
  }
  if (!editor.title.trim()) {
    ElMessage.warning({ message: '请输入身份', customClass: 'warm-toast' })
    return
  }

  savingProfile.value = true
  try {
    const updated = await updateProfile({
      name: editor.name.trim(),
      title: editor.title.trim(),
    })
    profile.value = updated
    editor.name = updated.name
    editor.title = updated.title
    await refresh()
    ElMessage.success({ message: '个人资料已更新', customClass: 'warm-toast' })
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '保存失败，请稍后重试',
      customClass: 'warm-toast',
    })
  } finally {
    savingProfile.value = false
  }
}

function resetProfile(): void {
  if (!profile.value) return
  editor.name = profile.value.name
  editor.title = profile.value.title
}

// ---- 修改密码 ----
const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const savingPassword = ref(false)

async function submitPassword(): Promise<void> {
  if (!passwordForm.currentPassword) {
    ElMessage.warning({ message: '请输入当前密码', customClass: 'warm-toast' })
    return
  }
  if (passwordForm.newPassword.length < 6) {
    ElMessage.warning({ message: '新密码长度至少 6 位', customClass: 'warm-toast' })
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning({ message: '两次输入的新密码不一致', customClass: 'warm-toast' })
    return
  }
  if (passwordForm.newPassword === passwordForm.currentPassword) {
    ElMessage.warning({ message: '新密码不能与当前密码相同', customClass: 'warm-toast' })
    return
  }

  savingPassword.value = true
  try {
    await changePassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword,
    })
    passwordForm.currentPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    ElMessage.success({
      message: '密码已更新，其他设备的登录已退出',
      customClass: 'warm-toast',
    })
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '密码修改失败，请稍后重试',
      customClass: 'warm-toast',
    })
  } finally {
    savingPassword.value = false
  }
}

function goHome(): void {
  void router.push(isFront.value ? '/front' : '/back/dashboard')
}
</script>

<template>
  <div class="profile" :class="{ 'container profile--front': isFront, 'profile--back': !isFront }">
    <PageHeader
      eyebrow="Account"
      title="个人资料"
      description="查看账号信息，维护展示姓名与身份，并管理登录密码。"
    >
      <template #actions>
        <el-button @click="goHome">
          <AppIcon :name="isFront ? 'home' : 'chart'" :size="15" />
          {{ isFront ? '返回首页' : '返回工作台' }}
        </el-button>
      </template>
    </PageHeader>

    <div v-if="loading" class="profile__loading">
      <el-skeleton :rows="6" animated />
    </div>

    <template v-else-if="profile">
      <div class="profile__stats">
        <article
          v-for="stat in stats"
          :key="stat.key"
          class="stat-tile"
          :class="`stat-tile--${stat.key}`"
        >
          <span class="stat-tile__icon">
            <AppIcon :name="stat.icon" :size="17" />
          </span>
          <div class="stat-tile__text">
            <p>{{ stat.label }}</p>
            <strong>
              {{ stat.value }}<small v-if="stat.unit">{{ stat.unit }}</small>
            </strong>
          </div>
        </article>
      </div>

      <div class="profile__grid">
        <PanelCard title="基本信息" description="姓名与身份会展示在页面右上角及后台列表中">
          <div class="identity">
            <span class="identity__avatar">{{ initial }}</span>
            <div class="identity__text">
              <strong>{{ displayName }}</strong>
              <p>{{ profile.title }}</p>
              <span class="identity__badge" :class="`is-${profile.role}`">{{ roleText }}</span>
            </div>
            <span class="identity__status" :class="{ 'is-off': !profile.enabled }">
              {{ profile.enabled ? '账号正常' : '账号已停用' }}
            </span>
          </div>

          <el-form label-position="top" class="profile-form" @submit.prevent>
            <el-form-item label="登录账号">
              <el-input :model-value="profile.account" disabled>
                <template #prefix>
                  <AppIcon name="users" :size="14" />
                </template>
              </el-input>
              <p class="field-hint">账号由管理员维护，不支持自行修改。</p>
            </el-form-item>

            <div class="profile-form__row">
              <el-form-item label="展示姓名">
                <el-input
                  v-model="editor.name"
                  maxlength="64"
                  placeholder="请输入姓名"
                  @keyup.enter="saveProfile"
                />
              </el-form-item>
              <el-form-item label="身份">
                <el-input
                  v-model="editor.title"
                  maxlength="64"
                  placeholder="例如 来访者 / 咨询师"
                  @keyup.enter="saveProfile"
                />
              </el-form-item>
            </div>

            <div class="profile-form__actions">
              <el-button :disabled="!isDirty || savingProfile" @click="resetProfile">重置</el-button>
              <el-button
                type="primary"
                :loading="savingProfile"
                :disabled="!isDirty"
                @click="saveProfile"
              >
                保存资料
              </el-button>
            </div>
          </el-form>
        </PanelCard>

        <div class="profile__side">
          <PanelCard title="账号信息" description="登录与安全相关信息">
            <dl class="meta-list">
              <div>
                <dt>账号状态</dt>
                <dd>
                  <span class="state-dot" :class="{ 'is-off': !profile.enabled }" />
                  {{ profile.enabled ? '已启用' : '已停用' }}
                </dd>
              </div>
              <div>
                <dt>注册时间</dt>
                <dd>{{ formatDate(profile.createdAt) }}</dd>
              </div>
              <div>
                <dt>最近登录</dt>
                <dd>{{ formatTime(profile.lastLoginAt) }}</dd>
              </div>
              <div>
                <dt>账号 ID</dt>
                <dd class="mono">#{{ profile.id }}</dd>
              </div>
            </dl>
          </PanelCard>

          <PanelCard title="修改密码" description="修改后，其他设备上的登录会立即失效">
            <el-form label-position="top" class="password-form" @submit.prevent="submitPassword">
              <el-form-item label="当前密码">
                <el-input
                  v-model="passwordForm.currentPassword"
                  type="password"
                  show-password
                  autocomplete="current-password"
                  placeholder="请输入当前密码"
                />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input
                  v-model="passwordForm.newPassword"
                  type="password"
                  show-password
                  autocomplete="new-password"
                  placeholder="至少 6 位"
                />
              </el-form-item>
              <el-form-item label="确认新密码">
                <el-input
                  v-model="passwordForm.confirmPassword"
                  type="password"
                  show-password
                  autocomplete="new-password"
                  placeholder="再次输入新密码"
                />
              </el-form-item>
              <el-button
                class="password-form__submit"
                type="primary"
                :loading="savingPassword"
                @click="submitPassword"
              >
                更新密码
              </el-button>
            </el-form>
          </PanelCard>
        </div>
      </div>
    </template>

    <el-empty v-else description="暂时无法加载个人资料">
      <el-button type="primary" @click="loadProfile">重新加载</el-button>
    </el-empty>
  </div>
</template>

<style scoped lang="scss">
.profile {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-height: 0;
}

.profile--front {
  padding-block: clamp(30px, 5vw, 56px) 72px;
}

.profile--back {
  min-height: 100%;
}

.profile__loading {
  padding: 12px;
  border: 1px solid var(--warm-line);
  border-radius: 16px;
  background: var(--warm-paper-72);
}

.profile__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: $space-4;
}

.stat-tile {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 15px;
  border: 1px solid var(--warm-line);
  border-radius: 14px;
  background: linear-gradient(150deg, var(--warm-paper-94), var(--warm-paper-72));
  box-shadow:
    inset 0 1px 0 var(--warm-paper-90),
    0 18px 40px -38px rgba(74, 64, 46, 0.4);
  transition:
    transform 0.2s $ease-out,
    border-color 0.2s $ease-out;

  &:hover {
    transform: translateY(-1px);
    border-color: rgba(136, 104, 110, 0.28);
  }
}

.stat-tile__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 11px;
  background: color-mix(in srgb, var(--warm-brand) 13%, transparent);
  color: var(--warm-brand-strong);
}

.stat-tile--consult .stat-tile__icon {
  background: rgba(207, 138, 131, 0.14);
  color: #b8685b;
}

.stat-tile--role .stat-tile__icon {
  background: rgba(196, 143, 154, 0.16);
  color: #88686e;
}

.stat-tile--days .stat-tile__icon {
  background: rgba(209, 138, 92, 0.16);
  color: #a86a3c;
}

.stat-tile__text {
  display: grid;
  gap: 1px;

  p {
    margin: 0;
    color: var(--warm-text-muted);
    font-size: 11.5px;
  }

  strong {
    color: var(--warm-text);
    font-family: $font-display;
    font-size: 20px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    line-height: 1.2;

    small {
      margin-left: 3px;
      color: var(--warm-text-muted);
      font-family: inherit;
      font-size: 11px;
      font-weight: 500;
    }
  }
}

.profile__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.28fr) minmax(300px, 0.72fr);
  gap: $space-4;
  align-items: start;
}

.profile__side {
  display: grid;
  gap: $space-4;
}

.identity {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  padding: 16px;
  margin-bottom: $space-4;
  border: 1px solid var(--warm-line);
  border-radius: 15px;
  background:
    radial-gradient(90% 120% at 92% 0%, rgba(209, 138, 92, 0.1), transparent 60%),
    linear-gradient(150deg, var(--warm-paper-92), var(--warm-paper-72));
}

.identity__avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 58px;
  height: 58px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--warm-brand-strong), var(--warm-sage-light));
  box-shadow:
    0 0 0 1px var(--warm-paper-80),
    0 12px 26px -16px rgba(112, 80, 86, 0.8);
  color: var(--warm-on-brand);
  font-family: $font-display;
  font-size: 22px;
  font-weight: 700;
}

.identity__text {
  display: grid;
  gap: 2px;
  min-width: 0;

  strong {
    color: var(--warm-text);
    font-family: $font-display;
    font-size: 19px;
    font-weight: 600;
  }

  p {
    margin: 0;
    color: var(--warm-text-muted);
    font-size: 12.5px;
  }
}

.identity__badge {
  justify-self: start;
  margin-top: 3px;
  padding: 2px 8px;
  border: 1px solid rgba(136, 104, 110, 0.24);
  border-radius: $radius-pill;
  background: rgba(136, 104, 110, 0.09);
  color: var(--warm-brand-strong);
  font-size: 10px;

  &.is-back {
    border-color: rgba(196, 143, 154, 0.3);
    background: rgba(196, 143, 154, 0.12);
    color: #88686e;
  }
}

.identity__status {
  margin-left: auto;
  padding: 4px 10px;
  border: 1px solid rgba(136, 104, 110, 0.26);
  border-radius: $radius-pill;
  background: rgba(136, 104, 110, 0.1);
  color: var(--warm-brand-strong);
  font-size: 10.5px;

  &.is-off {
    border-color: rgba(184, 92, 76, 0.3);
    background: rgba(207, 138, 131, 0.12);
    color: #b85c4c;
  }
}

.profile-form__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 14px;
}

.field-hint {
  margin: 5px 0 0;
  color: var(--warm-text-muted);
  font-size: 10.5px;
}

.profile-form__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 4px;
}

.meta-list {
  display: grid;
  gap: 10px;
  margin: 0;

  > div {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    padding-bottom: 9px;
    border-bottom: 1px dashed var(--warm-line);

    &:last-child {
      padding-bottom: 0;
      border-bottom: 0;
    }
  }

  dt {
    color: var(--warm-text-muted);
    font-size: 11.5px;
  }

  dd {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    color: var(--warm-text-regular);
    font-size: 12px;
    text-align: right;
  }

  .mono {
    font-family: $font-mono;
    font-size: 11.5px;
  }
}

.state-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--warm-brand);
  box-shadow: 0 0 0 3px rgba(136, 104, 110, 0.14);

  &.is-off {
    background: #b85c4c;
    box-shadow: 0 0 0 3px rgba(184, 92, 76, 0.12);
  }
}

.password-form__submit {
  width: 100%;
}

@include media-down($bp-lg) {
  .profile__grid {
    grid-template-columns: 1fr;
  }

  .profile__side {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
  }
}

@include media-down($bp-md) {
  .profile__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .profile__side {
    grid-template-columns: 1fr;
  }
}

@include media-down($bp-sm) {
  .profile-form__row {
    grid-template-columns: 1fr;
  }

  .identity__status {
    margin-left: 0;
  }
}
</style>
