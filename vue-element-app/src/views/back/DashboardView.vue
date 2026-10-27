<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { fetchDashboard, type DashboardResponse } from '@/api/dashboard'
import AppIcon from '@/components/AppIcon.vue'
import AreaChart from '@/components/AreaChart.vue'
import BarChart from '@/components/BarChart.vue'
import DonutChart from '@/components/DonutChart.vue'
import LivePulse from '@/components/LivePulse.vue'
import PageHeader from '@/components/PageHeader.vue'
import PanelCard from '@/components/PanelCard.vue'
import StatCard from '@/components/StatCard.vue'
import { useActionGuard } from '@/composables/useActionGuard'

const data = ref<DashboardResponse | null>(null)
const loading = ref(true)
const errorMessage = ref('')

const overviewStats = computed(() => data.value?.stats ?? [])
const moodTrend = computed(() => data.value?.moodTrend ?? { labels: [], values: [] })
const activeTrend = computed(() => data.value?.activeTrend ?? { labels: [], values: [] })
const consultStats = computed(() => data.value?.consultStats ?? [])
const levelSlices = computed(() => data.value?.levelSlices ?? [])
const triggerTop = computed(() => data.value?.triggerTop ?? [])
const moodTotal = computed(() => data.value?.moodTotal ?? 0)
const online = computed(() => data.value?.live.online ?? 0)
const latency = computed(() => data.value?.live.avgResponseSeconds ?? null)
const updatedAt = computed(() => data.value?.live.updatedAt ?? '—')
const feed = computed(() => data.value?.live.feed ?? [])

async function loadDashboard(quiet = false): Promise<void> {
  if (!quiet) loading.value = true
  try {
    data.value = await fetchDashboard()
    errorMessage.value = ''
  } catch (error) {
    if (!quiet || !data.value) {
      errorMessage.value = error instanceof Error ? error.message : '仪表盘加载失败'
    }
  } finally {
    if (!quiet) loading.value = false
  }
}

// 趋势区间切换：保留最近 7 / 14 天
const range = ref<7 | 14>(14)
const rangeOptions = [7, 14] as const

const moodView = computed(() => {
  const labels = moodTrend.value.labels.slice(-range.value)
  const values = moodTrend.value.values.slice(-range.value)
  if (!values.length) {
    return { labels, values, avg: '0.00', delta: '0.00', peak: '0.0', lowDays: 0, first: 0, last: 0 }
  }

  const avg = values.reduce((sum, value) => sum + value, 0) / values.length

  // 用区间前半段和后半段对比，14 天没有「上一周期」时也能给出变化
  const half = Math.floor(values.length / 2)
  const before = values.slice(0, half)
  const after = values.slice(half)
  const avgOf = (list: number[]): number =>
    list.length ? list.reduce((sum, value) => sum + value, 0) / list.length : avg
  const delta = avgOf(after) - avgOf(before)

  return {
    labels,
    values,
    avg: avg.toFixed(2),
    delta: delta.toFixed(2),
    peak: Math.max(...values).toFixed(1),
    lowDays: values.filter((value) => value < 3.2).length,
    first: values[0] ?? 0,
    last: values[values.length - 1] ?? 0,
  }
})

const activeView = computed(() => ({
  labels: activeTrend.value.labels.slice(-range.value),
  values: activeTrend.value.values.slice(-range.value),
}))

const activeDelta = computed(() => overviewStats.value.find((stat) => stat.key === 'users')?.delta ?? 0)

const insight = computed(() => {
  if (!moodView.value.values.length) return '正在等待第一份情绪记录，有了数据就能看见趋势。'
  const diff = moodView.value.last - moodView.value.first
  if (diff >= 0.2) return `整体情绪缓慢回升，较区间起点上升 ${diff.toFixed(1)} 分。`
  if (diff <= -0.2) return '情绪有小幅波动，建议关注低分段日期的记录。'
  return '情绪整体平稳，保持当前的记录节奏即可。'
})

const consultTotal = computed(() => consultStats.value.reduce((sum, item) => sum + item.value, 0))

// ---- 实时层：在线人数与事件流来自后端；前端只负责轮换展示 ----
const feedIndex = ref(0)
const currentFeed = computed(() => feed.value[feedIndex.value % Math.max(feed.value.length, 1)] ?? null)

function rotateFeed(): void {
  if (feed.value.length) feedIndex.value = (feedIndex.value + 1) % feed.value.length
}

let refreshTimer = 0
let feedTimer = 0

function startTimers(): void {
  if (refreshTimer) return
  refreshTimer = window.setInterval(() => void loadDashboard(true), 15000)
  feedTimer = window.setInterval(rotateFeed, 4200)
}

function stopTimers(): void {
  window.clearInterval(refreshTimer)
  window.clearInterval(feedTimer)
  refreshTimer = 0
  feedTimer = 0
}

function onVisibility(): void {
  if (document.hidden) {
    stopTimers()
  } else {
    void loadDashboard(true)
    startTimers()
  }
}

onMounted(() => {
  void loadDashboard()
  startTimers()
  document.addEventListener('visibilitychange', onVisibility)
})

onBeforeUnmount(() => {
  stopTimers()
  document.removeEventListener('visibilitychange', onVisibility)
})

// ---- 导出当前仪表盘数据（统计卡 + 趋势），CSV 带 BOM ----
function csvCell(value: string | number): string {
  return `"${String(value).replaceAll('"', '""')}"`
}

function downloadReport(): void {
  if (!data.value) {
    ElMessage.info('数据还在加载，请稍后再试')
    return
  }
  const rows: string[][] = [['指标', '数值', '单位', '较上周(%)']]
  for (const stat of overviewStats.value) {
    rows.push([stat.label, String(stat.value), stat.unit, String(stat.delta)])
  }
  rows.push([])
  rows.push(['日期', '平均情绪分'])
  const labels = data.value.moodTrend.labels
  data.value.moodTrend.values.forEach((value, index) => {
    rows.push([labels[index] ?? '', String(value)])
  })
  rows.push([])
  rows.push(['日期', '活跃用户数'])
  data.value.activeTrend.values.forEach((value, index) => {
    rows.push([data.value?.activeTrend.labels[index] ?? '', String(value)])
  })
  const csv = rows.map((row) => row.map(csvCell).join(',')).join('\n')
  const blob = new Blob([`﻿${csv}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `运营仪表盘-${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success('报表已导出')
}

const { pending: exporting, trigger: exportReport } = useActionGuard(downloadReport, { wait: 800 })
</script>

<template>
  <div class="dash">
    <PageHeader eyebrow="Overview · Live" title="数据分析">
      <template #actions>
        <el-button :loading="exporting" @click="exportReport">
          <AppIcon name="download" :size="15" />
          导出报表
        </el-button>
      </template>
    </PageHeader>

    <div v-if="errorMessage" class="dash__error">
      <AppIcon name="alert" :size="15" />
      <span>{{ errorMessage }}</span>
      <button type="button" @click="loadDashboard()">重试</button>
    </div>

    <!-- 实时状态条：脉冲波形 + 在线指标 + 动态事件 -->
    <section class="dash__livebar" v-reveal="{ delay: 40 }">
      <span class="dash__live-tag">
        <i class="dash__live-dot" />
        LIVE
      </span>

      <LivePulse class="dash__pulse" :bars="44" :height="26" />

      <div class="dash__ticker">
        <Transition name="ticker" mode="out-in">
          <p v-if="currentFeed" :key="feedIndex">
            <i class="dash__ticker-dot" :class="`is-${currentFeed.tone}`" />
            <span class="dash__ticker-who">{{ currentFeed.who }}</span>
            {{ currentFeed.action }}
          </p>
          <p v-else key="empty" class="dash__ticker-empty">暂无新的用户动态</p>
        </Transition>
      </div>

      <dl class="dash__live-meta">
        <div>
          <dt>在线</dt>
          <dd>{{ online }}<small>人</small></dd>
        </div>
        <div>
          <dt>平均响应</dt>
          <dd>
            {{ latency === null ? '—' : latency }}<small v-if="latency !== null">s</small>
          </dd>
        </div>
        <div>
          <dt>更新于</dt>
          <dd class="is-clock">{{ updatedAt }}</dd>
        </div>
      </dl>
    </section>

    <div class="dash__stats">
      <StatCard
        v-for="(stat, index) in overviewStats"
        :key="stat.key"
        :stat="stat"
        v-reveal="{ delay: index * 70 }"
      />
    </div>

    <PanelCard
      v-reveal="{ delay: 60 }"
      class="dash__hero"
      title="情绪趋势分析图"
      :description="`近 ${range} 天 · 平均情绪分（满分 5 分）`"
    >
      <template #actions>
        <div class="dash__hero-actions">
          <div class="dash__range" role="group" aria-label="趋势区间">
            <button
              v-for="option in rangeOptions"
              :key="option"
              type="button"
              :class="{ 'is-active': range === option }"
              :aria-pressed="range === option"
              @click="range = option"
            >
              {{ option }} 天
            </button>
          </div>
        </div>
      </template>

      <div class="dash__hero-body">
        <div class="dash__chart">
          <AreaChart
            :labels="moodView.labels"
            :values="moodView.values"
            :height="286"
            color="var(--warm-brand)"
            suffix=" 分"
            panel
            live
          />
        </div>

        <aside class="dash__rail">
          <div class="dash__kpi">
            <span>区间均值</span>
            <strong>{{ moodView.avg }}<small>分</small></strong>
            <em :class="Number(moodView.delta) >= 0 ? 'is-up' : 'is-warn'">
              {{ Number(moodView.delta) >= 0 ? '↑' : '↓' }} {{ Math.abs(Number(moodView.delta)).toFixed(2) }}
            </em>
          </div>
          <div class="dash__kpi">
            <span>区间峰值</span>
            <strong>{{ moodView.peak }}<small>分</small></strong>
            <em>最高点</em>
          </div>
          <div class="dash__kpi">
            <span>低情绪日</span>
            <strong>{{ moodView.lowDays }}<small>天</small></strong>
            <em class="is-warn">建议回访</em>
          </div>

          <div class="dash__note">
            <AppIcon name="sparkles" :size="15" />
            <p>{{ insight }}</p>
          </div>
        </aside>
      </div>
    </PanelCard>

    <div class="dash__grid">
      <PanelCard
        v-reveal="{ delay: 60 }"
        title="咨询会话统计图"
        description="近 7 天 · 按会话开始时间统计"
      >
        <template #actions>
          <span class="panel__tag panel__tag--accent">
            <i class="panel__tag-dot" />
            本周 {{ consultTotal }} 次
          </span>
        </template>
        <BarChart :items="consultStats" color="var(--warm-apricot)" panel live />
      </PanelCard>

      <PanelCard
        v-reveal="{ delay: 120 }"
        title="用户活跃趋势图"
        :description="`近 ${range} 天 · 活跃用户数`"
      >
        <template #actions>
          <span class="panel__tag panel__tag--lilac">
            {{ activeDelta >= 0 ? '↑' : '↓' }} {{ Math.abs(activeDelta).toFixed(1) }}%
          </span>
        </template>
        <AreaChart
          :labels="activeView.labels"
          :values="activeView.values"
          :height="240"
          color="var(--warm-lilac)"
          suffix=" 人"
          panel
          live
        />
      </PanelCard>
    </div>

    <div class="dash__grid dash__grid--even">
      <PanelCard v-reveal="{ delay: 60 }" title="情绪等级分布" description="全部记录 · 按记录占比">
        <DonutChart
          :items="levelSlices"
          :size="176"
          :thickness="22"
          center-label="条记录"
          :center-value="moodTotal"
        />
      </PanelCard>

      <PanelCard v-reveal="{ delay: 120 }" title="高频触发因素" description="全部记录 · 出现次数 Top 5">
        <BarChart :items="triggerTop" color="var(--warm-butter)" panel />
      </PanelCard>
    </div>
  </div>
</template>

<style scoped lang="scss">
.dash {
  position: relative;
}

.dash__error {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 2px 0 16px;
  padding: 10px 14px;
  border: 1px solid color-mix(in srgb, var(--warm-apricot) 40%, transparent);
  border-radius: 12px;
  background: color-mix(in srgb, var(--warm-apricot) 10%, transparent);
  color: var(--warm-text-regular);
  font-size: 12.5px;

  span {
    flex: 1;
    min-width: 0;
  }

  button {
    padding: 4px 10px;
    border: 1px solid color-mix(in srgb, var(--warm-apricot) 46%, transparent);
    border-radius: $radius-pill;
    background: transparent;
    color: var(--warm-apricot);
    font-size: 12px;
    cursor: pointer;
  }
}

// ---- 实时状态条 ----
.dash__livebar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 2px 0 16px;
  padding: 9px 14px;
  overflow: hidden;
  border: 1px solid var(--warm-line);
  border-radius: 14px;
  background:
    radial-gradient(120% 180% at 0% 0%, color-mix(in srgb, var(--warm-brand) 8%, transparent), transparent 60%),
    var(--warm-paper-72);
  box-shadow:
    inset 0 1px 0 var(--warm-rim-60),
    0 18px 40px -38px var(--warm-shadow);
}

.dash__live-tag {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 6px;
  padding: 4px 9px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 30%, transparent);
  border-radius: $radius-pill;
  background: color-mix(in srgb, var(--warm-brand) 10%, transparent);
  color: var(--warm-brand-strong);
  font-family: $font-mono;
  font-size: 9.5px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.dash__live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--warm-brand);
  box-shadow: 0 0 0 0 color-mix(in srgb, var(--warm-brand) 65%, transparent);
  animation: live-dot 2s ease-out infinite;
}

.dash__pulse {
  flex: 0 1 220px;
  min-width: 96px;
  max-width: 260px;
}

.dash__ticker {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--warm-text-regular);
  font-size: 12.5px;
  white-space: nowrap;

  p {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.dash__ticker-empty {
  color: var(--warm-text-muted);
}

.dash__ticker-dot {
  flex: none;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--warm-brand);

  &.is-apricot {
    background: var(--warm-apricot);
  }

  &.is-lilac {
    background: var(--warm-lilac);
  }

  &.is-butter {
    background: var(--warm-butter);
  }
}

.dash__ticker-who {
  color: var(--warm-text);
  font-weight: 600;
}

.dash__live-meta {
  display: flex;
  flex: none;
  align-items: center;
  gap: 18px;
  margin: 0;

  div {
    display: grid;
    gap: 1px;
  }

  dt {
    color: var(--warm-text-muted);
    font-size: 9.5px;
    letter-spacing: 0.08em;
  }

  dd {
    margin: 0;
    color: var(--warm-text);
    font-family: $font-display;
    font-size: 14px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;

    small {
      margin-left: 2px;
      color: var(--warm-text-muted);
      font-family: $font-body;
      font-size: 9.5px;
      font-weight: 400;
    }

    &.is-clock {
      font-family: $font-mono;
      font-size: 11.5px;
      font-weight: 500;
      letter-spacing: 0.02em;
    }
  }
}

.ticker-enter-active,
.ticker-leave-active {
  transition:
    opacity 0.24s $ease-out,
    transform 0.24s $ease-out;
}

.ticker-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.ticker-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

// ---- 指标卡 ----
.dash__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: $space-4;
}

.dash__hero {
  margin-top: $space-4;
}

.dash__hero-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 232px;
  gap: 28px;
  align-items: stretch;
}

.dash__chart {
  min-width: 0;
  padding-top: 14px;
}

.dash__rail {
  display: grid;
  align-content: start;
  gap: 10px;
  padding-left: 24px;
  border-left: 1px solid var(--warm-line);
}

.dash__kpi {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: baseline;
  gap: 2px 8px;
  padding: 11px 13px;
  border: 1px solid var(--warm-line);
  border-radius: 12px;
  background: var(--warm-paper-70);
  transition:
    border-color 0.3s $ease-out,
    background 0.3s $ease-out;

  &:hover {
    border-color: color-mix(in srgb, var(--warm-brand) 28%, transparent);
    background: var(--warm-paper-85);
  }

  span {
    grid-column: 1 / -1;
    color: var(--warm-text-muted);
    font-size: 10.5px;
    letter-spacing: 0.08em;
  }

  strong {
    color: var(--warm-text);
    font-family: $font-display;
    font-size: 24px;
    font-weight: 600;
    letter-spacing: -0.02em;
    line-height: 1.15;
    font-variant-numeric: tabular-nums;

    small {
      margin-left: 3px;
      color: var(--warm-text-muted);
      font-family: $font-body;
      font-size: 11px;
      font-weight: 400;
    }
  }

  em {
    padding: 2px 7px;
    border: 1px solid color-mix(in srgb, var(--warm-brand) 22%, transparent);
    border-radius: $radius-pill;
    background: color-mix(in srgb, var(--warm-brand) 10%, transparent);
    color: var(--warm-brand-strong);
    font-size: 10px;
    font-style: normal;
    font-weight: 600;
    white-space: nowrap;

    &.is-warn {
      border-color: color-mix(in srgb, var(--warm-apricot) 28%, transparent);
      background: color-mix(in srgb, var(--warm-apricot) 10%, transparent);
      color: var(--warm-apricot);
    }
  }
}

.dash__note {
  display: flex;
  gap: 8px;
  margin: 2px 0 0;
  padding: 12px 13px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 18%, transparent);
  border-radius: 12px;
  background: linear-gradient(
    120deg,
    color-mix(in srgb, var(--warm-brand) 8%, transparent),
    var(--warm-paper-50)
  );
  color: var(--warm-text-muted);
  font-size: 11.5px;
  line-height: 1.75;

  svg {
    flex: none;
    margin-top: 3px;
    color: var(--warm-brand-strong);
  }

  p {
    margin: 0;
  }
}

.dash__hero-actions {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 8px;
}

.dash__range {
  display: flex;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-pill;
  background: var(--warm-paper-60);

  button {
    padding: 5px 13px;
    border: 0;
    border-radius: $radius-pill;
    background: transparent;
    color: var(--warm-text-muted);
    font-size: 11.5px;
    white-space: nowrap;
    cursor: pointer;
    transition:
      color 0.2s $ease-out,
      background 0.2s $ease-out,
      box-shadow 0.2s $ease-out;

    &:hover {
      color: var(--warm-text);
    }

    &:focus-visible {
      @include focus-ring($warm-brand);
    }

    &.is-active {
      background: var(--warm-brand-soft);
      box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--warm-brand) 24%, transparent);
      color: var(--warm-brand-strong);
      font-weight: 600;
    }
  }
}

.dash__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.45fr);
  align-items: start;
  gap: $space-4;
  margin-top: $space-4;
}

.dash__grid--even {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.panel__tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 22%, transparent);
  border-radius: $radius-pill;
  background: color-mix(in srgb, var(--warm-brand) 9%, transparent);
  box-shadow: inset 0 1px 0 var(--warm-rim-60);
  color: var(--warm-brand-strong);
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;

  &--accent {
    border-color: color-mix(in srgb, var(--warm-apricot) 28%, transparent);
    background: color-mix(in srgb, var(--warm-apricot) 10%, transparent);
    color: var(--warm-apricot);
  }

  &--lilac {
    border-color: color-mix(in srgb, var(--warm-lilac) 30%, transparent);
    background: color-mix(in srgb, var(--warm-lilac) 12%, transparent);
    color: var(--warm-lilac);
  }
}

.panel__tag-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  animation: live-dot 2.2s ease-out infinite;
}

@keyframes live-dot {
  0% {
    box-shadow: 0 0 0 0 color-mix(in srgb, currentColor 55%, transparent);
  }

  70% {
    box-shadow: 0 0 0 6px transparent;
  }

  100% {
    box-shadow: 0 0 0 0 transparent;
  }
}

@include media-down(1280px) {
  .dash__livebar {
    flex-wrap: wrap;
    gap: 10px 14px;
  }

  .dash__pulse {
    flex: 1 1 180px;
  }

  .dash__ticker {
    flex: 1 1 200px;
    order: 4;
  }

  .dash__live-meta {
    margin-left: auto;
  }
}

@include media-down(1200px) {
  .dash__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@include media-down($bp-lg) {
  .dash__hero-body {
    grid-template-columns: minmax(0, 1fr);
    gap: 20px;
  }

  .dash__rail {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    padding-left: 0;
    padding-top: 20px;
    border-top: 1px solid var(--warm-line);
    border-left: 0;
  }

  .dash__note {
    grid-column: 1 / -1;
  }

  .dash__grid,
  .dash__grid--even {
    grid-template-columns: minmax(0, 1fr);
  }
}

@include media-down($bp-sm) {
  .dash__stats {
    grid-template-columns: minmax(0, 1fr);
  }

  .dash__rail {
    grid-template-columns: minmax(0, 1fr);
  }

  .dash__range button {
    padding: 5px 10px;
  }

  .dash__livebar {
    padding: 10px 12px;
  }

  .dash__live-meta {
    width: 100%;
    justify-content: space-between;
    gap: 8px;
  }

  .dash__ticker {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .dash__live-dot,
  .panel__tag-dot {
    animation: none;
  }

  .ticker-enter-active,
  .ticker-leave-active {
    transition: none;
  }
}
</style>
