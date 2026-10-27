<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules, TableInstance } from 'element-plus'

import type { UserRole } from '@/api/auth'
import type { BatchResult } from '@/api/batch'
import {
  batchChangeUserStatus,
  batchDeleteAdminUsers,
  changeUserStatus,
  createAdminUser,
  deleteAdminUser,
  listAdminUsers,
  updateAdminUser,
  type AdminUser,
} from '@/api/users'
import AppIcon from '@/components/AppIcon.vue'
import PageHeader from '@/components/PageHeader.vue'
import PanelCard from '@/components/PanelCard.vue'
import SearchField from '@/components/SearchField.vue'
import TablePager from '@/components/TablePager.vue'
import { useAuth } from '@/composables/useAuth'

const { user: currentUser, refresh } = useAuth()

const rows = ref<AdminUser[]>([])
const loading = ref(true)
const selected = ref<AdminUser[]>([])
const batchBusy = ref(false)
const tableRef = ref<TableInstance>()

async function loadRows(): Promise<void> {
  loading.value = true
  try {
    rows.value = await listAdminUsers()
    selected.value = []
    tableRef.value?.clearSelection()
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '用户列表加载失败，请稍后重试',
      customClass: 'warm-toast',
    })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadRows()
})

const keyword = ref('')
const roleFilter = ref<UserRole | ''>('')
const statusFilter = ref<'' | 'enabled' | 'disabled'>('')

const list = computed<AdminUser[]>(() =>
  rows.value.filter((row) => {
    const key = keyword.value.trim().toLowerCase()
    if (key && !row.account.toLowerCase().includes(key)
      && !row.name.toLowerCase().includes(key)
      && !row.title.toLowerCase().includes(key)) return false
    if (roleFilter.value && row.role !== roleFilter.value) return false
    if (statusFilter.value === 'enabled' && !row.enabled) return false
    if (statusFilter.value === 'disabled' && row.enabled) return false
    return true
  }),
)

const page = ref(1)
const pageSize = ref(10)
const paged = computed(() =>
  list.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
)

watch([keyword, roleFilter, statusFilter], () => {
  page.value = 1
})

const roleLabel = (role: UserRole): string => (role === 'back' ? '后台成员' : '前台来访者')
const initialOf = (name: string): string => name.trim().slice(0, 1) || '?'
const isSelf = (row: AdminUser): boolean => row.id === currentUser.value?.id

function formatTime(value: number | null): string {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// ---- 新建 / 编辑 ----
const dialog = ref(false)
const editing = ref<AdminUser | null>(null)
const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive({
  account: '',
  password: '',
  name: '',
  title: '',
  role: 'front' as UserRole,
  enabled: true,
})

const isCreate = computed(() => editing.value === null)

const rules: FormRules = {
  account: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    {
      pattern: /^[A-Za-z][A-Za-z0-9_]{2,31}$/,
      message: '账号需以字母开头，由 3-32 位字母、数字或下划线组成',
      trigger: 'blur',
    },
  ],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  title: [{ required: true, message: '请输入身份', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

function resetForm(): void {
  form.account = ''
  form.password = ''
  form.name = ''
  form.title = ''
  form.role = 'front'
  form.enabled = true
}

function openCreate(): void {
  editing.value = null
  resetForm()
  dialog.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row: AdminUser): void {
  editing.value = row
  form.account = row.account
  form.password = ''
  form.name = row.name
  form.title = row.title
  form.role = row.role
  form.enabled = row.enabled
  dialog.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function closeDialog(): void {
  dialog.value = false
  editing.value = null
  resetForm()
}

async function saveUser(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (isCreate.value && !form.password) {
    ElMessage.warning({ message: '请设置初始密码', customClass: 'warm-toast' })
    return
  }
  if (form.password && form.password.length < 6) {
    ElMessage.warning({ message: '密码长度至少 6 位', customClass: 'warm-toast' })
    return
  }

  saving.value = true
  try {
    if (editing.value) {
      const target = editing.value
      const passwordChanged = Boolean(form.password)
      const updated = await updateAdminUser(target.id, {
        account: form.account.trim(),
        password: passwordChanged ? form.password : null,
        name: form.name.trim(),
        title: form.title.trim(),
        role: form.role,
        enabled: form.enabled,
      })
      rows.value = rows.value.map((row) => (row.id === updated.id ? updated : row))
      closeDialog()

      if (isSelf(updated)) await refresh()
      ElMessage.success({
        message: passwordChanged ? '密码已更新，其他设备的登录已退出' : '账号资料已更新',
        customClass: 'warm-toast',
      })
    } else {
      const created = await createAdminUser({
        account: form.account.trim(),
        password: form.password,
        name: form.name.trim(),
        title: form.title.trim(),
        role: form.role,
        enabled: form.enabled,
      })
      rows.value = [created, ...rows.value]
      closeDialog()
      ElMessage.success({
        message: `已创建账号「${created.account}」`,
        customClass: 'warm-toast',
      })
    }
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '保存失败，请稍后重试',
      customClass: 'warm-toast',
    })
  } finally {
    saving.value = false
  }
}

// ---- 启停用 / 删除 ----
const statusBusy = ref<number | null>(null)

async function toggleStatus(row: AdminUser): Promise<void> {
  if (isSelf(row)) return
  const next = !row.enabled
  if (!next) {
    try {
      await ElMessageBox.confirm(
        `停用后「${row.name}」将立即退出登录，且无法再次登录。确定停用吗？`,
        '停用账号',
        {
          type: 'warning',
          confirmButtonText: '停用',
          cancelButtonText: '取消',
          customClass: 'warm-message-box',
        },
      )
    } catch {
      return
    }
  }

  statusBusy.value = row.id
  try {
    const updated = await changeUserStatus(row.id, next)
    rows.value = rows.value.map((item) => (item.id === updated.id ? updated : item))
    ElMessage.success({
      message: next ? `已启用「${row.name}」` : `已停用「${row.name}」`,
      customClass: 'warm-toast',
    })
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '状态修改失败，请稍后重试',
      customClass: 'warm-toast',
    })
  } finally {
    statusBusy.value = null
  }
}

async function removeUser(row: AdminUser): Promise<void> {
  if (isSelf(row)) return
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.name}」的账号吗？删除后该账号无法登录，历史记录会保留。`,
      '删除账号',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        customClass: 'warm-message-box',
      },
    )
  } catch {
    return
  }

  try {
    await deleteAdminUser(row.id)
    rows.value = rows.value.filter((item) => item.id !== row.id)
    ElMessage.success('已删除账号')
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '删除失败，请稍后重试',
      customClass: 'warm-toast',
    })
  }
}

// ---- 批量操作 ----
function onSelectionChange(selection: AdminUser[]): void {
  selected.value = selection
}

function canSelect(row: AdminUser): boolean {
  return !isSelf(row)
}

function clearSelection(): void {
  selected.value = []
  tableRef.value?.clearSelection()
}

function batchMessage(result: BatchResult, action: string): void {
  const firstFailure = result.items.find((item) => !item.success)
  const suffix = firstFailure?.message ? `；首个失败原因：${firstFailure.message}` : ''
  if (result.failed > 0) {
    ElMessage.warning({
      message: `${action}完成：成功 ${result.succeeded} 项，失败 ${result.failed} 项${suffix}`,
      duration: 6000,
      customClass: 'warm-toast',
    })
  } else {
    ElMessage.success({
      message: `${action}成功，共 ${result.succeeded} 项`,
      customClass: 'warm-toast',
    })
  }
}

async function confirmBatch(message: string, title: string): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: '继续',
      cancelButtonText: '取消',
      customClass: 'warm-message-box',
    })
    return true
  } catch {
    return false
  }
}

async function batchSetEnabled(enabled: boolean): Promise<void> {
  if (!selected.value.length) return
  const verb = enabled ? '启用' : '停用'
  if (!enabled) {
    const confirmed = await confirmBatch(
      `将停用选中的 ${selected.value.length} 个账号，停用后其所有登录会话会立即失效。确定继续吗？`,
      '批量停用账号',
    )
    if (!confirmed) return
  }

  batchBusy.value = true
  try {
    const result = await batchChangeUserStatus(
      selected.value.map((row) => row.id),
      enabled,
    )
    batchMessage(result, `批量${verb}`)
    await loadRows()
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : `批量${verb}失败，请稍后重试`,
      customClass: 'warm-toast',
    })
  } finally {
    batchBusy.value = false
  }
}

async function batchRemove(): Promise<void> {
  if (!selected.value.length) return
  const confirmed = await confirmBatch(
    `确定删除选中的 ${selected.value.length} 个账号吗？删除后无法登录，历史记录会保留；受保护账号会自动跳过。`,
    '批量删除账号',
  )
  if (!confirmed) return

  batchBusy.value = true
  try {
    const result = await batchDeleteAdminUsers(selected.value.map((row) => row.id))
    batchMessage(result, '批量删除')
    await loadRows()
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '批量删除失败，请稍后重试',
      customClass: 'warm-toast',
    })
  } finally {
    batchBusy.value = false
  }
}
</script>

<template>
  <div class="users">
    <PageHeader eyebrow="Users" title="用户管理">
      <template #actions>
        <el-button :loading="loading" @click="loadRows">
          <AppIcon name="refresh" :size="15" />
          刷新
        </el-button>
        <el-button type="primary" @click="openCreate">
          <AppIcon name="plus" :size="15" />
          新建账号
        </el-button>
      </template>
    </PageHeader>

    <div class="filters">
      <span class="filters__label">
        <AppIcon name="filter" :size="14" />
        筛选
      </span>
      <SearchField
        v-model="keyword"
        class="filters__search"
        placeholder="搜索账号 / 姓名 / 身份"
      />
      <el-select v-model="roleFilter" placeholder="全部角色" clearable class="filters__select">
        <el-option label="前台来访者" value="front" />
        <el-option label="后台成员" value="back" />
      </el-select>
      <el-select v-model="statusFilter" placeholder="全部状态" clearable class="filters__select">
        <el-option label="已启用" value="enabled" />
        <el-option label="已停用" value="disabled" />
      </el-select>
      <span class="filters__count">
        {{ loading ? '加载中…' : `共 ${list.length} 个账号` }}
      </span>
    </div>

    <div v-if="selected.length" class="batch-bar" role="region" aria-label="批量操作">
      <span class="batch-bar__count">已选 {{ selected.length }} 个账号</span>
      <el-button
        size="small"
        :loading="batchBusy"
        :disabled="batchBusy"
        @click="batchSetEnabled(true)"
      >
        批量启用
      </el-button>
      <el-button
        size="small"
        :loading="batchBusy"
        :disabled="batchBusy"
        @click="batchSetEnabled(false)"
      >
        批量停用
      </el-button>
      <el-button
        size="small"
        type="danger"
        plain
        :loading="batchBusy"
        :disabled="batchBusy"
        @click="batchRemove"
      >
        批量删除
      </el-button>
      <el-button size="small" text :disabled="batchBusy" @click="clearSelection">
        取消选择
      </el-button>
    </div>

    <PanelCard class="table-panel" title="账号列表" description="停用与删除操作会立即生效">
      <div class="table-shell">
        <div class="table-wrap">
          <el-table
            ref="tableRef"
            v-loading="loading"
            :data="paged"
            row-key="id"
            height="100%"
            class="table"
            @selection-change="onSelectionChange"
          >
            <el-table-column type="selection" width="46" :selectable="canSelect" reserve-selection />
            <el-table-column label="用户" min-width="190">
              <template #default="{ row }">
                <span class="user-cell">
                  <span class="user-cell__avatar" :class="`is-${row.role}`">
                    {{ initialOf(row.name) }}
                  </span>
                  <span class="user-cell__text">
                    <strong>
                      {{ row.name }}
                      <em v-if="isSelf(row as AdminUser)">我</em>
                    </strong>
                    <small>{{ row.title }}</small>
                  </span>
                </span>
              </template>
            </el-table-column>

            <el-table-column label="账号" min-width="130">
              <template #default="{ row }">
                <span class="cell-account">{{ row.account }}</span>
              </template>
            </el-table-column>

            <el-table-column label="角色" min-width="124" align="center">
              <template #default="{ row }">
                <span class="role-pill" :class="`is-${row.role}`">
                  {{ roleLabel(row.role as UserRole) }}
                </span>
              </template>
            </el-table-column>

            <el-table-column label="状态" min-width="112" align="center">
              <template #default="{ row }">
                <span class="status-cell">
                  <el-switch
                    :model-value="row.enabled"
                    :loading="statusBusy === row.id"
                    :disabled="isSelf(row as AdminUser)"
                    :title="isSelf(row as AdminUser) ? '不能停用自己的账号' : '切换启用状态'"
                    @change="toggleStatus(row as AdminUser)"
                  />
                  <small>{{ row.enabled ? '已启用' : '已停用' }}</small>
                </span>
              </template>
            </el-table-column>

            <el-table-column label="最近登录" min-width="158" align="center">
              <template #default="{ row }">
                <span class="time-cell">{{ formatTime(row.lastLoginAt) }}</span>
              </template>
            </el-table-column>

            <el-table-column label="创建时间" min-width="158" align="center">
              <template #default="{ row }">
                <span class="time-cell">{{ formatTime(row.createdAt) }}</span>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="134" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEdit(row as AdminUser)">
                  编辑
                </el-button>
                <el-button
                  link
                  type="danger"
                  size="small"
                  :disabled="isSelf(row as AdminUser)"
                  @click="removeUser(row as AdminUser)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>

            <template #empty>
              <el-empty description="没有匹配的账号" />
            </template>
          </el-table>
        </div>

        <TablePager
          v-if="list.length"
          v-model:page="page"
          v-model:page-size="pageSize"
          :total="list.length"
          :page-sizes="[10, 20, 50]"
          unit="个"
        />
      </div>
    </PanelCard>

    <el-dialog
      v-model="dialog"
      :title="isCreate ? '新建账号' : `编辑账号 · ${editing?.account ?? ''}`"
      width="min(560px, 94vw)"
      class="user-dialog"
      @closed="closeDialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <div class="form-grid">
          <el-form-item label="账号" prop="account">
            <el-input
              v-model="form.account"
              maxlength="32"
              placeholder="例如 chenyu"
              autocomplete="off"
            />
          </el-form-item>

          <el-form-item label="角色" prop="role">
            <el-radio-group v-model="form.role">
              <el-radio-button value="front">前台来访者</el-radio-button>
              <el-radio-button value="back">后台成员</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="姓名" prop="name">
            <el-input v-model="form.name" maxlength="64" placeholder="用于页面展示" />
          </el-form-item>

          <el-form-item label="身份" prop="title">
            <el-input v-model="form.title" maxlength="64" placeholder="例如 来访者 / 咨询师" />
          </el-form-item>
        </div>

        <el-form-item :label="isCreate ? '初始密码' : '重置密码（留空则不修改）'">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            maxlength="64"
            placeholder="至少 6 位"
            autocomplete="new-password"
          />
        </el-form-item>

        <el-form-item label="账号状态">
          <div class="status-editor">
            <el-switch v-model="form.enabled" />
            <span>{{ form.enabled ? '启用：可以正常登录' : '停用：立即退出且无法登录' }}</span>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="closeDialog">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveUser">
          {{ isCreate ? '创建账号' : '保存修改' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.users {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: $space-3;
  margin-bottom: $space-4;
  padding: 12px 14px;
  border: 1px solid var(--warm-line);
  border-radius: 14px;
  background: linear-gradient(120deg, var(--warm-paper-92), var(--warm-paper-80));
  box-shadow:
    inset 0 1px 0 var(--warm-paper-90),
    0 18px 40px -38px rgba(74, 64, 46, 0.4);
  backdrop-filter: blur(12px);
}

.filters__label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding-right: 6px;
  color: var(--warm-brand-strong);
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.filters__search {
  width: 240px;
}

.filters__select {
  width: 148px;
}

.filters__count {
  margin-left: auto;
  padding: 4px 12px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 28%, transparent);
  border-radius: $radius-pill;
  background: color-mix(in srgb, var(--warm-brand) 12%, transparent);
  color: var(--warm-brand-strong);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
}

.batch-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: $space-4;
  padding: 10px 14px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 32%, transparent);
  border-radius: 12px;
  background: color-mix(in srgb, var(--warm-brand) 9%, var(--warm-paper-88));
  box-shadow: inset 0 1px 0 var(--warm-paper-90);
}

.batch-bar__count {
  margin-right: auto;
  color: var(--warm-brand-strong);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.table-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.table-shell {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.table-wrap {
  flex: 1;
  min-height: 0;
}

.table {
  width: 100%;
}

.user-cell {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.user-cell__avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--warm-brand-strong), var(--warm-sage-light));
  box-shadow: 0 8px 18px -12px rgba(112, 80, 86, 0.7);
  color: var(--warm-on-brand);
  font-size: 12.5px;
  font-weight: 700;

  &.is-back {
    background: linear-gradient(135deg, #ac6a77, #d8a4ae);
    box-shadow: 0 8px 18px -12px rgba(136, 104, 110, 0.7);
  }
}

.user-cell__text {
  display: grid;
  gap: 1px;
  min-width: 0;

  strong {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    color: var(--warm-text);
    font-size: 13px;
    font-weight: 600;
  }

  em {
    padding: 1px 5px;
    border-radius: 5px;
    background: color-mix(in srgb, var(--warm-brand) 14%, transparent);
    color: var(--warm-brand-strong);
    font-size: 9px;
    font-style: normal;
    font-weight: 600;
  }

  small {
    overflow: hidden;
    color: var(--warm-text-muted);
    font-size: 11px;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}

.cell-account {
  color: var(--warm-text-regular);
  font-family: $font-mono;
  font-size: 12px;
  letter-spacing: 0.03em;
}

.role-pill {
  display: inline-flex;
  padding: 3px 9px;
  border: 1px solid rgba(136, 104, 110, 0.24);
  border-radius: $radius-pill;
  background: rgba(136, 104, 110, 0.09);
  color: var(--warm-brand-strong);
  font-size: 10.5px;
  font-weight: 500;

  &.is-back {
    border-color: rgba(196, 143, 154, 0.3);
    background: rgba(196, 143, 154, 0.12);
    color: #88686e;
  }
}

.status-cell {
  display: inline-flex;
  align-items: center;
  gap: 7px;

  small {
    color: var(--warm-text-muted);
    font-size: 11px;
  }
}

.time-cell {
  color: var(--warm-text-muted);
  font-family: $font-mono;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.status-editor {
  display: flex;
  align-items: center;
  gap: 10px;

  span {
    color: var(--warm-text-muted);
    font-size: 12px;
  }
}

@include media-down($bp-md) {
  .users {
    height: auto;
    min-height: 100%;
  }

  .table-panel,
  .table-shell,
  .table-wrap {
    flex: none;
  }

  .table-wrap {
    height: min(420px, 54vh);
  }

  .filters {
    padding: 12px;
  }

  .filters__label {
    width: 100%;
  }

  .filters__search,
  .filters__select {
    width: 100%;
  }

  .filters__count {
    margin-left: 0;
  }
}

@include media-down($bp-sm) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
