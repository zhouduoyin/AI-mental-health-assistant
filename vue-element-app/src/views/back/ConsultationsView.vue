<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import type { Consultation, MoodTag } from '@/types'
import {
  followUpConsultation,
  getConsultation,
  listConsultations,
} from '@/api/consultations'
import AppIcon from '@/components/AppIcon.vue'
import PageHeader from '@/components/PageHeader.vue'
import PanelCard from '@/components/PanelCard.vue'
import SearchField from '@/components/SearchField.vue'
import TablePager from '@/components/TablePager.vue'
import UserBadge from '@/components/UserBadge.vue'
import { useActionGuard } from '@/composables/useActionGuard'
import { riskTagType } from '@/utils/labels'

const moodOptions: MoodTag[] = ['焦虑', '低落', '平静', '愉悦']

// 状态色：进行中松绿、已结束灰、待跟进琥珀
const statusTone: Record<Consultation['status'], string> = {
  进行中: 'live',
  已结束: 'done',
  待跟进: 'follow',
}

// 情绪标签的展示色：焦虑陶土、低落灰粉、平静松绿、愉悦亮松绿
const moodTone: Record<MoodTag, string> = {
  焦虑: 'accent',
  低落: 'lilac',
  平静: 'calm',
  愉悦: 'bright',
}

const keyword = ref('')
const mood = ref<MoodTag | ''>('')

const rows = ref<Consultation[]>([])
const loading = ref(true)

async function loadRows(): Promise<void> {
  loading.value = true
  try {
    rows.value = await listConsultations()
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '咨询记录加载失败，请稍后重试',
      customClass: 'warm-toast',
    })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadRows()
})

const list = computed<Consultation[]>(() =>
  rows.value.filter((row) => {
    const key = keyword.value.trim().toLowerCase()
    if (key && !row.id.toLowerCase().includes(key) && !row.user.includes(key)) return false
    if (mood.value && row.mood !== mood.value) return false
    return true
  }),
)

// 表格分页：搜索或情绪筛选变化时回到第一页
const page = ref(1)
const pageSize = ref(20)
const paged = computed(() =>
  list.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
)

watch([keyword, mood], () => {
  page.value = 1
})

const drawer = ref(false)
const current = ref<Consultation | null>(null)

async function open(row: Consultation): Promise<void> {
  current.value = row
  drawer.value = true
  try {
    const fresh = await getConsultation(row.id)
    current.value = fresh
    rows.value = rows.value.map((item) => (item.id === fresh.id ? fresh : item))
  } catch {
    // 列表里已有完整摘录，详情刷新失败时继续展示列表数据
  }
}

function csvCell(value: string | number): string {
  return `"${String(value).replaceAll('"', '""')}"`
}

/** 导出当前筛选结果为 CSV。 */
function exportRecords(): void {
  if (!list.value.length) {
    ElMessage.info('当前没有可导出的咨询记录')
    return
  }
  const header = ['会话ID', '用户', '情绪', '主题', '消息数', '开始时间', '状态', '风险', '时长(分钟)']
  const lines = list.value.map((row) =>
    [row.id, row.user, row.mood, row.topic, row.messageCount, row.startedAt, row.status, row.risk, row.duration]
      .map(csvCell)
      .join(','),
  )
  const blob = new Blob([`﻿${[header.map(csvCell).join(','), ...lines].join('\n')}`], {
    type: 'text/csv;charset=utf-8',
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `咨询记录-${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${list.value.length} 条记录`)
}

async function followUp(): Promise<void> {
  if (!current.value) return
  try {
    const updated = await followUpConsultation(current.value.id)
    current.value = updated
    rows.value = rows.value.map((item) => (item.id === updated.id ? updated : item))
    ElMessage.success('已标记为已跟进')
    drawer.value = false
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '标记失败，请稍后重试',
      customClass: 'warm-toast',
    })
  }
}

// 导出与「标记已跟进」都防连点，pending 直接绑按钮 loading
const { pending: exporting, trigger: notifyExport } = useActionGuard(exportRecords, { wait: 900 })
const { pending: following, trigger: markFollowed } = useActionGuard(followUp, { wait: 600 })
</script>

<template>
  <div class="consults">
    <PageHeader eyebrow="Sessions" title="咨询记录">
      <template #actions>
        <el-button :loading="exporting" @click="notifyExport">
          <AppIcon name="download" :size="15" />
          导出记录
        </el-button>
      </template>
    </PageHeader>

    <div class="filters">
      <span class="filters__label">
        <AppIcon name="filter" :size="14" />
        筛选
      </span>
      <SearchField v-model="keyword" class="filters__search" placeholder="搜索会话 ID / 用户" />
      <el-select v-model="mood" placeholder="全部情绪" clearable class="filters__select">
        <el-option v-for="item in moodOptions" :key="item" :label="item" :value="item" />
      </el-select>
      <span class="filters__count">
        {{ loading ? '加载中…' : `共 ${list.length} 条` }}
      </span>
    </div>

    <PanelCard class="table-panel" title="会话记录" description="点击任意一行，查看完整对话摘录与风险标签">
      <div class="table-shell">
        <div class="table-wrap">
          <el-table :data="paged" height="100%" class="table" @row-click="open">
            <el-table-column label="会话ID" min-width="150" align="center">
              <template #default="{ row }">
                <span class="cell-id">{{ row.id }}</span>
              </template>
            </el-table-column>
            <el-table-column label="用户" min-width="160" align="center">
              <template #default="{ row }">
                <span class="user-cell">
                  <UserBadge :initials="row.initials" :tone="row.tone" :size="28" />
                  <span>{{ row.user }}</span>
                </span>
              </template>
            </el-table-column>
            <el-table-column label="情绪标签" min-width="128" align="center">
              <template #default="{ row }">
                <span class="mood" :class="`mood--${moodTone[row.mood as MoodTag]}`">{{ row.mood }}</span>
              </template>
            </el-table-column>
            <el-table-column label="消息数" min-width="110" align="center">
              <template #default="{ row }">{{ row.messageCount }} 条</template>
            </el-table-column>
            <el-table-column prop="startedAt" label="时间" min-width="180" align="center" />
            <el-table-column label="状态" min-width="120" align="center">
              <template #default="{ row }">
                <span class="status-pill" :class="`is-${statusTone[row.status as Consultation['status']]}`">
                  <i aria-hidden="true" />
                  {{ row.status }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click.stop="open(row as Consultation)">
                  详情
                </el-button>
              </template>
            </el-table-column>

            <template #empty>
              <el-empty description="没有匹配的会话记录" />
            </template>
          </el-table>
        </div>

        <TablePager
          v-if="list.length"
          v-model:page="page"
          v-model:page-size="pageSize"
          :total="list.length"
          unit="条"
        />
      </div>
    </PanelCard>

    <el-drawer
      v-model="drawer"
      size="min(520px, 94vw)"
      :title="current ? `会话 ${current.id}` : '会话详情'"
      class="admin-drawer"
    >
      <div v-if="current" class="detail">
        <div class="detail__user">
          <UserBadge :initials="current.initials" :tone="current.tone" :size="46" />
          <div class="detail__identity">
            <strong>{{ current.user }}</strong>
            <p>{{ current.topic }}</p>
            <small>开始时间 {{ current.startedAt }}</small>
          </div>
        </div>

        <el-descriptions :column="2" border size="small" class="detail__meta">
          <el-descriptions-item label="情绪">{{ current.mood }}</el-descriptions-item>
          <el-descriptions-item label="消息数">{{ current.messageCount }} 条</el-descriptions-item>
          <el-descriptions-item label="时长">{{ current.duration }} 分钟</el-descriptions-item>
          <el-descriptions-item label="编号">{{ current.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <span class="status-pill" :class="`is-${statusTone[current.status]}`">
              <i aria-hidden="true" />
              {{ current.status }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="风险">
            <el-tag :type="riskTagType(current.risk)" size="small" effect="plain">{{ current.risk }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <h3 class="detail__subtitle">对话记录 · 节选 {{ current.messages.length }} 条</h3>
        <ol class="thread">
          <li
            v-for="(message, index) in current.messages"
            :key="index"
            class="thread__node"
            :class="`thread__node--${message.role}`"
          >
            <span class="thread__avatar" :class="`thread__avatar--${message.role}`">
              {{ message.role === 'ai' ? 'AI' : current.initials }}
            </span>
            <div class="thread__content">
              <div class="thread__meta">
                <span class="thread__role">{{ message.role === 'ai' ? 'AI 助手' : '用户' }}</span>
                <time>{{ message.time }}</time>
              </div>
              <p>{{ message.text }}</p>
            </div>
          </li>
        </ol>

        <div class="detail__actions">
          <el-button type="primary" :loading="following" @click="markFollowed">标记已跟进</el-button>
          <el-button @click="drawer = false">关闭</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.consults {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
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

  svg {
    filter: drop-shadow(0 1px 3px rgba(136, 104, 110, 0.16));
  }
}

.filters__search {
  width: 240px;
}

.filters__select {
  width: 132px;
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

.table {
  width: 100%;

  :deep(.el-table__row) {
    cursor: pointer;
  }
}

.cell-id {
  color: var(--warm-brand-strong);
  font-family: $font-mono;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.06em;
}

.user-cell {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  color: var(--warm-text);
  font-size: 13px;

  :deep(.user-badge) {
    box-shadow: 0 0 0 1px var(--warm-paper-75);
  }
}

.mood {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-pill;
  background: var(--warm-paper-70);
  font-size: 11.5px;
  font-weight: 500;

  &::before {
    content: '';
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
    box-shadow: 0 0 0 3px color-mix(in srgb, currentColor 14%, transparent);
  }

  &--accent {
    color: var(--warm-apricot);
  }

  &--lilac {
    color: #88686e;
  }

  &--calm {
    color: var(--warm-brand);
  }

  &--bright {
    color: var(--warm-brand-strong);
  }
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border: 1px solid transparent;
  border-radius: $radius-pill;
  font-size: 11.5px;
  white-space: nowrap;

  i {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: currentColor;
  }

  &.is-live {
    border-color: rgba(136, 104, 110, 0.26);
    background: rgba(136, 104, 110, 0.1);
    color: var(--warm-brand-strong);

    i {
      box-shadow: 0 0 0 3px rgba(136, 104, 110, 0.14);
    }
  }

  &.is-follow {
    border-color: rgba(209, 138, 92, 0.28);
    background: rgba(209, 138, 92, 0.1);
    color: var(--warm-apricot);

    i {
      box-shadow: 0 0 0 3px rgba(209, 138, 92, 0.14);
    }
  }

  &.is-done {
    border-color: var(--warm-line);
    background: var(--warm-paper-68);
    color: var(--warm-text-muted);
  }
}

.detail__user {
  display: flex;
  align-items: center;
  gap: $space-3;
  margin-bottom: $space-4;
  padding: 14px;
  border: 1px solid var(--warm-line);
  border-radius: 14px;
  background: linear-gradient(160deg, var(--warm-paper-90), var(--warm-paper-72));
}

.detail__identity {
  display: grid;
  gap: 2px;
  line-height: 1.4;

  strong {
    color: var(--warm-text);
    font-size: 15px;
  }

  p {
    margin: 0;
    color: var(--warm-text-muted);
    font-size: 12.5px;
  }

  small {
    color: var(--warm-text-muted);
    font-family: $font-mono;
    font-size: 10.5px;
    letter-spacing: 0.02em;
  }
}

.detail__meta {
  margin-bottom: $space-5;
}

.detail__subtitle {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 0 0 $space-3;
  color: var(--warm-text);
  font-size: 14px;
  font-weight: 600;

  &::before {
    content: '';
    width: 3px;
    height: 14px;
    border-radius: $radius-pill;
    background: linear-gradient(180deg, var(--warm-brand), rgba(136, 104, 110, 0.12));
    box-shadow: 0 2px 6px rgba(136, 104, 110, 0.2);
  }
}

.thread {
  position: relative;
  display: grid;
  gap: 16px;
  margin: 0;
  padding: 0;
  list-style: none;

  &::before {
    content: '';
    position: absolute;
    top: 16px;
    bottom: 16px;
    left: 15px;
    width: 1px;
    background: linear-gradient(180deg, rgba(136, 104, 110, 0.3), rgba(92, 77, 52, 0.06));
  }
}

.thread__node {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 12px;
}

.thread__avatar {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--warm-line);
  border-radius: 50%;
  background: var(--warm-paper);
  color: var(--warm-text-regular);
  font-size: 11.5px;
  font-weight: 600;

  &--ai {
    border-color: transparent;
    background: linear-gradient(135deg, var(--warm-brand), var(--warm-brand-strong));
    color: var(--warm-on-brand);
    font-family: $font-mono;
    font-size: 10.5px;
    letter-spacing: 0.02em;
    box-shadow: 0 8px 18px -12px rgba(112, 80, 86, 0.7);
  }
}

.thread__content {
  padding: 11px 14px;
  border: 1px solid var(--warm-line);
  border-radius: 12px;
  background: var(--warm-paper-82);
}

.thread__node--ai .thread__content {
  border-color: rgba(136, 104, 110, 0.2);
  background: linear-gradient(120deg, rgba(136, 104, 110, 0.09), var(--warm-paper-60));
}

.thread__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $space-2;
  margin-bottom: 5px;

  time {
    color: var(--warm-text-muted);
    font-family: $font-mono;
    font-size: 10px;
  }
}

.thread__role {
  color: var(--warm-text-muted);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.thread__node--ai .thread__role {
  color: var(--warm-brand-strong);
}

.thread__content p {
  margin: 0;
  color: var(--warm-text-regular);
  font-size: 13px;
  line-height: 1.8;
}

.detail__actions {
  display: flex;
  gap: $space-3;
  margin-top: $space-5;
}

@include media-down($bp-md) {
  // 窄屏空间有限：表格保留固定可视高度，页面允许滚动，避免只剩一行
  .consults {
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
</style>
