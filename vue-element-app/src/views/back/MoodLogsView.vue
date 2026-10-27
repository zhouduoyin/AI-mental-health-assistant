<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import type { MoodLevel, MoodLog } from '@/types'
import { deleteMoodAsAdmin, listAllMoods } from '@/api/moods'
import AppIcon from '@/components/AppIcon.vue'
import MoodDots from '@/components/MoodDots.vue'
import PageHeader from '@/components/PageHeader.vue'
import PanelCard from '@/components/PanelCard.vue'
import SearchField from '@/components/SearchField.vue'
import TablePager from '@/components/TablePager.vue'
import UserBadge from '@/components/UserBadge.vue'
import { useActionGuard } from '@/composables/useActionGuard'
import { moodOptions } from '@/data/catalog'
import { moodLevel } from '@/utils/labels'

const rows = ref<MoodLog[]>([])
const loading = ref(true)

async function loadRows(): Promise<void> {
  loading.value = true
  try {
    rows.value = await listAllMoods()
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '情绪日志加载失败，请稍后重试',
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
const level = ref<MoodLevel | ''>('')

const list = computed<MoodLog[]>(() =>
  rows.value.filter((row) => {
    const key = keyword.value.trim().toLowerCase()
    if (key && !row.user.includes(key) && !row.sessionId.toLowerCase().includes(key)) return false
    if (level.value && moodLevel(row.score) !== level.value) return false
    return true
  }),
)

// 表格分页：筛选变化时回到第一页
const page = ref(1)
const pageSize = ref(20)
const paged = computed(() =>
  list.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
)

watch([keyword, level], () => {
  page.value = 1
})

const emojiOf = (score: number): string =>
  moodOptions.find((option) => option.score === score)?.emoji ?? '😐'

const drawer = ref(false)
const current = ref<MoodLog | null>(null)

function openDetail(row: MoodLog): void {
  current.value = row
  drawer.value = true
}

function removeLog(row: MoodLog): void {
  ElMessageBox.confirm(`确定删除「${row.user}」${row.date} 的情绪记录吗？删除后不可恢复。`, '删除记录', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    customClass: 'warm-message-box',
  })
    .then(async () => {
      try {
        await deleteMoodAsAdmin(row.id)
        rows.value = rows.value.filter((item) => item.id !== row.id)
        ElMessage.success('已删除')
      } catch (error) {
        ElMessage.error({
          message: error instanceof Error ? error.message : '删除失败，请稍后重试',
          customClass: 'warm-toast',
        })
      }
    })
    .catch(() => {})
}

function csvCell(value: string | number): string {
  return `"${String(value).replaceAll('"', '""')}"`
}

/** 将当前筛选结果导出为 CSV，带 BOM 方便 Excel 正确识别中文。 */
function exportRecords(): void {
  if (!list.value.length) {
    ElMessage.info('当前没有可导出的情绪记录')
    return
  }
  const header = ['ID', '会话ID', '用户', '日期', '评分', '胜过指数', '触发因素', '日记']
  const lines = list.value.map((row) =>
    [
      row.id,
      row.sessionId,
      row.user,
      row.date,
      row.score,
      row.moodIndex,
      row.tags.join(' / '),
      row.note,
    ]
      .map(csvCell)
      .join(','),
  )
  const blob = new Blob([`﻿${[header.map(csvCell).join(','), ...lines].join('\n')}`], {
    type: 'text/csv;charset=utf-8',
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `情绪日志-${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${list.value.length} 条记录`)
}

// 导出与删除都防连点
const { pending: exporting, trigger: notifyExport } = useActionGuard(exportRecords, { wait: 900 })
const { trigger: remove } = useActionGuard(removeLog, { wait: 500 })
</script>

<template>
  <div class="logs">
    <PageHeader eyebrow="Mood" title="情绪日志">
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
      <SearchField v-model="keyword" class="filters__search" placeholder="搜索用户 / 会话 ID" />
      <el-select v-model="level" placeholder="全部情绪等级" clearable class="filters__select">
        <el-option label="低（1–2 分）" value="低" />
        <el-option label="中（3 分）" value="中" />
        <el-option label="高（4–5 分）" value="高" />
      </el-select>
      <span class="filters__count">
        {{ loading ? '加载中…' : `共 ${list.length} 条` }}
      </span>
    </div>

    <PanelCard class="table-panel" title="情绪记录" description="按日期倒序展示，低评分记录建议优先回访">
      <div class="table-shell">
        <div class="table-wrap">
          <el-table :data="paged" height="100%" class="table">
            <el-table-column label="ID" min-width="66" align="center">
              <template #default="{ row }">
                <span class="cell-id">#{{ row.id }}</span>
              </template>
            </el-table-column>
            <el-table-column label="会话ID" min-width="118" align="center">
              <template #default="{ row }">
                <span class="cell-id">{{ row.sessionId }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="date" label="记录日期" min-width="118" align="center" />
            <el-table-column label="情绪评分" min-width="132" align="center">
              <template #default="{ row }">
                <span class="mood-cell">
                  <span class="mood-cell__emoji">{{ emojiOf(row.score) }}</span>
                  <MoodDots :score="row.score" />
                  <small>{{ row.score }} 分</small>
                </span>
              </template>
            </el-table-column>
            <el-table-column label="胜过指数" min-width="120" align="center">
              <template #default="{ row }">
                <span class="index-cell">
                  <strong>{{ row.moodIndex }}</strong>
                  <i class="index-cell__bar">
                    <b :style="{ width: `${row.moodIndex}%` }" />
                  </i>
                </span>
              </template>
            </el-table-column>
            <el-table-column label="情绪触发因素" min-width="170" align="center">
              <template #default="{ row }">
                <span class="tags">
                  <span v-for="tag in row.tags" :key="tag">{{ tag }}</span>
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="note" label="日记内容" min-width="220" show-overflow-tooltip align="center" />
            <el-table-column label="操作" width="120" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openDetail(row as MoodLog)">
                  详情
                </el-button>
                <el-button link type="danger" size="small" @click="remove(row as MoodLog)">
                  删除
                </el-button>
              </template>
            </el-table-column>

            <template #empty>
              <el-empty description="没有匹配的情绪记录" />
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
      size="min(480px, 94vw)"
      :title="current ? `${current.user} · ${current.date}` : '情绪记录'"
      class="admin-drawer"
    >
      <div v-if="current" class="detail">
        <div class="detail__user">
          <UserBadge :initials="current.initials" :tone="current.tone" :size="44" />
          <div class="detail__identity">
            <strong>{{ current.user }}</strong>
            <p>{{ current.date }}</p>
            <small>会话 {{ current.sessionId }}</small>
          </div>
        </div>

        <el-descriptions :column="2" border size="small" class="detail__meta">
          <el-descriptions-item label="情绪评分">
            {{ current.score }} 分 {{ emojiOf(current.score) }}
          </el-descriptions-item>
          <el-descriptions-item label="胜过指数">{{ current.moodIndex }}</el-descriptions-item>
          <el-descriptions-item label="记录日期">{{ current.date }}</el-descriptions-item>
          <el-descriptions-item label="会话 ID">{{ current.sessionId }}</el-descriptions-item>
          <el-descriptions-item label="触发因素" :span="2">
            <span class="tags">
              <span v-for="tag in current.tags" :key="tag">{{ tag }}</span>
            </span>
          </el-descriptions-item>
        </el-descriptions>

        <h3 class="detail__subtitle">日记内容</h3>
        <p class="detail__note">{{ current.note }}</p>

        <el-button class="detail__close" @click="drawer = false">关闭</el-button>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.logs {
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
  width: 220px;
}

.filters__select {
  width: 156px;
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
}

.cell-id {
  color: var(--warm-brand-strong);
  font-family: $font-mono;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.06em;
}

.mood-cell {
  display: inline-flex;
  align-items: center;
  gap: 7px;

  small {
    color: var(--warm-text-muted);
    font-size: 11.5px;
  }
}

.mood-cell__emoji {
  font-size: 16px;
  line-height: 1;
  filter: drop-shadow(0 2px 4px rgba(74, 64, 46, 0.14));
}

.index-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;

  strong {
    width: 24px;
    color: var(--warm-text);
    font-family: $font-display;
    font-size: 15px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }
}

.index-cell__bar {
  position: relative;
  width: 44px;
  height: 4px;
  overflow: hidden;
  border-radius: $radius-pill;
  background: rgba(92, 77, 52, 0.1);

  b {
    display: block;
    height: 100%;
    border-radius: inherit;
    background: linear-gradient(90deg, var(--warm-brand-strong), var(--warm-brand));
    box-shadow: 0 1px 4px rgba(136, 104, 110, 0.3);
  }
}

.tags {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 5px;

  span {
    padding: 2px 8px;
    border: 1px solid var(--warm-line);
    border-radius: $radius-pill;
    background: var(--warm-paper-72);
    color: var(--warm-text-muted);
    font-size: 10.5px;
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

.detail__note {
  margin: 0;
  padding: 15px 17px;
  border: 1px solid var(--warm-line);
  border-left: 2px solid rgba(136, 104, 110, 0.55);
  border-radius: 12px;
  background: linear-gradient(120deg, rgba(136, 104, 110, 0.07), var(--warm-paper-50));
  color: var(--warm-text-regular);
  font-size: 13.5px;
  line-height: 1.9;
}

.detail__close {
  margin-top: $space-5;
}

@include media-down($bp-md) {
  // 窄屏空间有限：表格保留固定可视高度，页面允许滚动，避免只剩一行
  .logs {
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
