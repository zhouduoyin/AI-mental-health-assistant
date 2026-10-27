<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type { MoodLog } from '@/types'
import { deleteMood, listMyMoods, saveMood } from '@/api/moods'
import AppIcon from '@/components/AppIcon.vue'
import LazyMount from '@/components/LazyMount.vue'
import { useActionGuard } from '@/composables/useActionGuard'
import { dayAt, isoDate } from '@/utils/date'
import { moodOptions, moodTags } from '@/data/catalog'

// 趋势图在首屏下方，单独成 chunk；配合 LazyMount 到视口附近才下载并绘制
const AreaChart = defineAsyncComponent(() => import('@/components/AreaChart.vue'))

// 列表项与首页打卡共用后端同一条记录
type HistoryItem = MoodLog

function startOfWeek(date: Date): Date {
  const start = new Date(date)
  start.setHours(12, 0, 0, 0)
  const weekday = (start.getDay() + 6) % 7 // 周一 = 0
  start.setDate(start.getDate() - weekday)
  return start
}

const shortDate = (iso: string): string => iso.slice(5)

const todayISO = isoDate(new Date())
const yesterdayISO = isoDate(dayAt(-1))

function dateLabel(iso: string): string {
  if (iso === todayISO) return '今天'
  if (iso === yesterdayISO) return '昨天'
  return shortDate(iso)
}

const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function weekdayOf(iso: string): string {
  const date = new Date(`${iso}T12:00:00`)
  return Number.isNaN(date.getTime()) ? '' : WEEKDAY_LABELS[date.getDay()]
}

function relativeDay(iso: string): string {
  if (iso === todayISO) return '今天'
  if (iso === yesterdayISO) return '昨天'
  return ''
}

function fullDate(iso: string): string {
  const [year, month, day] = iso.split('-')
  return `${year} 年 ${Number(month)} 月 ${Number(day)} 日`
}

function recordedAtLabel(value?: number): string {
  if (!value) return '未记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '未记录'
  const pad = (input: number): string => String(input).padStart(2, '0')
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}


// ---- 记录来自后端；首次进入时如果今天已写过，表单自动回填 ----
const history = ref<HistoryItem[]>([])
const loading = ref(true)
const todayEntry = computed(() => history.value.find((item) => item.date === todayISO))

const score = ref(4)
const selectedTags = ref<string[]>(['放松'])
const note = ref('')

async function loadMoods(): Promise<void> {
  loading.value = true
  try {
    history.value = await listMyMoods()
    const today = history.value.find((item) => item.date === todayISO)
    if (today) {
      score.value = today.score
      selectedTags.value = [...today.tags]
      note.value = today.note
    }
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '情绪记录加载失败，请稍后重试',
      customClass: 'warm-toast',
    })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadMoods()
})

const MOOD_COLORS: Record<number, string> = {
  1: '#8a5b68',
  2: 'var(--warm-lilac)',
  3: 'var(--warm-butter)',
  4: '#5aa79b',
  5: '#4f8668',
}

const colorOf = (value: number): string => MOOD_COLORS[value] ?? '#4f8668'

const currentMood = computed(
  () => moodOptions.find((item) => item.score === score.value) ?? moodOptions[2],
)

const orbStyle = computed(() => {
  const color = colorOf(currentMood.value.score)
  return {
    '--mood': color,
    '--mood-soft': `color-mix(in srgb, ${color} 36%, transparent)`,
  }
})

const emojiOf = (value: number): string =>
  moodOptions.find((item) => item.score === Math.round(value))?.emoji ?? '😐'

const labelOf = (value: number): string =>
  moodOptions.find((item) => item.score === Math.round(value))?.label ?? ''

const TAG_LIMIT = 3

function toggleTag(tag: string): void {
  if (selectedTags.value.includes(tag)) {
    selectedTags.value = selectedTags.value.filter((item) => item !== tag)
    return
  }

  if (selectedTags.value.length >= TAG_LIMIT) {
    ElMessage.warning(`最多选择 ${TAG_LIMIT} 个标签`)
    return
  }

  selectedTags.value = [...selectedTags.value, tag]
}

const notePrompts = ['今天最放松的一刻…', '此刻身体的感觉…', '想对明天说的…']

function usePrompt(prompt: string): void {
  const current = note.value.trim()
  if (!current) {
    note.value = prompt
    return
  }
  if (current.includes(prompt)) return
  note.value = `${current}\n${prompt}`.slice(0, 120)
}

async function saveRecord(): Promise<void> {
  const existing = history.value.find((item) => item.date === todayISO)

  try {
    const entry = await saveMood({
      date: todayISO,
      score: score.value,
      tags: [...selectedTags.value],
      note: note.value.trim(),
    })

    history.value = existing
      ? history.value.map((item) => (item.date === todayISO ? entry : item))
      : [entry, ...history.value]

    ElMessage.success({
      message: existing ? '今天的记录已更新' : '今天的情绪已记下',
      customClass: 'warm-toast',
    })
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '保存失败，请稍后重试',
      customClass: 'warm-toast',
    })
  }
}

const { pending: saving, trigger: save } = useActionGuard(saveRecord, { wait: 800 })

// ---- 统计：连续天数、本周打卡、近 7 次均值与高频标签 ----
const sortedHistory = computed(() =>
  [...history.value].sort(
    (a, b) => b.date.localeCompare(a.date) || (b.createdAt ?? b.id) - (a.createdAt ?? a.id),
  ),
)

const recentEntries = computed(() =>
  [...history.value].sort((a, b) => a.date.localeCompare(b.date)).slice(-7),
)

const average = computed(() => {
  const entries = recentEntries.value
  if (!entries.length) return '0.0'
  return (entries.reduce((sum, item) => sum + item.score, 0) / entries.length).toFixed(1)
})

const averageLabel = computed(() => labelOf(Number(average.value)) || '还没有记录')

const weekLabels = computed(() => recentEntries.value.map((item) => shortDate(item.date)))
const weekValues = computed(() => recentEntries.value.map((item) => item.score))

const streak = computed(() => {
  const dates = new Set(history.value.map((item) => item.date))
  const cursor = new Date()
  cursor.setHours(12, 0, 0, 0)

  // 今天还没写时，从昨天开始算，避免一早打开就显示断签
  if (!dates.has(isoDate(cursor))) cursor.setDate(cursor.getDate() - 1)

  let count = 0
  while (dates.has(isoDate(cursor)) && count < 3660) {
    count += 1
    cursor.setDate(cursor.getDate() - 1)
  }
  return count
})

const weekCheckins = computed(() => {
  const start = isoDate(startOfWeek(new Date()))
  const dates = new Set(
    history.value.filter((item) => item.date >= start && item.date <= todayISO).map((item) => item.date),
  )
  return dates.size
})

const topTag = computed(() => {
  const counts = new Map<string, number>()
  for (const item of history.value) {
    for (const tag of item.tags) counts.set(tag, (counts.get(tag) ?? 0) + 1)
  }
  return [...counts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] ?? '还没选过'
})

const insight = computed(() => {
  const entries = recentEntries.value
  if (entries.length < 2) return '再记录几天，就能看见自己的情绪节奏。'

  const first = entries[0]?.score ?? 0
  const last = entries[entries.length - 1]?.score ?? 0
  const diff = last - first

  if (diff >= 0.5) return '最近几次记录在缓慢回暖，继续保持现在的节奏。'
  if (diff <= -0.5) return '最近分数有些走低，允许自己慢一点，必要时找人说说话。'
  return '整体比较平稳。情绪有起伏很正常，不需要每一天都很好。'
})

// ---- 最近四周打卡日历 ----
interface HeatCell {
  key: string
  score: number | null
  future: boolean
  isToday: boolean
  title: string
}

const heatmapCells = computed<HeatCell[]>(() => {
  const scores = new Map(history.value.map((item) => [item.date, item.score]))
  const today = new Date()
  today.setHours(12, 0, 0, 0)
  const start = startOfWeek(today)
  start.setDate(start.getDate() - 21)

  return Array.from({ length: 28 }, (_, index) => {
    const date = new Date(start)
    date.setDate(start.getDate() + index)
    const key = isoDate(date)
    const value = scores.get(key) ?? null
    return {
      key,
      score: value,
      future: date.getTime() > today.getTime(),
      isToday: key === todayISO,
      title: `${key} · ${value ? `${value} 分` : '未记录'}`,
    }
  })
})

function cellStyle(cell: HeatCell): Record<string, string> | undefined {
  if (!cell.score) return undefined
  return {
    background: `color-mix(in srgb, ${colorOf(cell.score)} ${24 + cell.score * 13}%, var(--warm-paper-70))`,
  }
}

// ---- 历史筛选与删除确认 ----
type HistoryFilter = 'all' | 'high' | 'low'

const historyFilter = ref<HistoryFilter>('all')

const filterTabs = computed(() => [
  { key: 'all' as const, label: '全部', count: history.value.length },
  { key: 'high' as const, label: '4–5 分', count: history.value.filter((item) => item.score >= 4).length },
  { key: 'low' as const, label: '1–2 分', count: history.value.filter((item) => item.score <= 2).length },
])

const filteredHistory = computed(() => {
  if (historyFilter.value === 'high') return sortedHistory.value.filter((item) => item.score >= 4)
  if (historyFilter.value === 'low') return sortedHistory.value.filter((item) => item.score <= 2)
  return sortedHistory.value
})

// ---- 历史列表分批渲染：记录可能积累到几百天，先只挂最近的 20 条 ----
const HISTORY_PAGE = 20
const historyLimit = ref(HISTORY_PAGE)

const visibleHistory = computed(() => filteredHistory.value.slice(0, historyLimit.value))
const hasMoreHistory = computed(() => visibleHistory.value.length < filteredHistory.value.length)

function loadMoreHistory(): void {
  if (hasMoreHistory.value) historyLimit.value += HISTORY_PAGE
}

// 切换筛选后从最新的 20 条重新开始
watch(historyFilter, () => {
  historyLimit.value = HISTORY_PAGE
})

// 滚动事件里读布局尺寸，用 rAF 合并，滚动和渲染互不打断
let historyFrame = 0

function onHistoryScroll(event: Event): void {
  if (historyFrame) return
  const element = event.currentTarget as HTMLElement | null

  historyFrame = window.requestAnimationFrame(() => {
    historyFrame = 0
    if (!element || !hasMoreHistory.value) return
    if (element.scrollHeight - element.scrollTop - element.clientHeight < 140) {
      loadMoreHistory()
    }
  })
}

const deleteTarget = ref<HistoryItem | null>(null)

function requestDelete(item: HistoryItem): void {
  deleteTarget.value = item
}

function cancelDelete(): void {
  deleteTarget.value = null
}

function confirmDelete(): void {
  const target = deleteTarget.value
  if (!target) return

  void (async () => {
    try {
      await deleteMood(target.id)
      history.value = history.value.filter((item) => item.id !== target.id)
      deleteTarget.value = null
      ElMessage.success('已删除这条记录')
    } catch (error) {
      ElMessage.error({
        message: error instanceof Error ? error.message : '删除失败，请稍后重试',
        customClass: 'warm-toast',
      })
    }
  })()
}

// ---- 单条记录详情：原生 dialog 自带焦点圈定与 Esc 关闭，不额外引入浮层组件 ----
const detailDialog = ref<HTMLDialogElement | null>(null)
const detailTarget = ref<HistoryItem | null>(null)

const detailEyebrow = computed(() => {
  const item = detailTarget.value
  if (!item) return ''
  return [weekdayOf(item.date), relativeDay(item.date)].filter(Boolean).join(' · ')
})

function openDetail(item: HistoryItem): void {
  detailTarget.value = item
  const dialog = detailDialog.value
  if (!dialog || dialog.open) return
  if (typeof dialog.showModal === 'function') dialog.showModal()
  else dialog.setAttribute('open', '')
}

function closeDetail(): void {
  const dialog = detailDialog.value
  if (!dialog) return
  if (dialog.open && typeof dialog.close === 'function') dialog.close()
  else dialog.removeAttribute('open')
}

// ---- 一分钟呼吸练习：4 秒吸气 · 4 秒屏息 · 6 秒呼气 ----
const BREATH_CYCLE = 14
const BREATH_GOAL = 56
const breathing = ref(false)
const breathElapsed = ref(0)
let breathTimer = 0

const breathPhase = computed(() => {
  const t = breathElapsed.value % BREATH_CYCLE
  if (t < 4) return { key: 'inhale', label: '慢慢吸气', remain: 4 - t }
  if (t < 8) return { key: 'hold', label: '轻轻屏住', remain: 8 - t }
  return { key: 'exhale', label: '缓缓呼气', remain: 14 - t }
})

const breathLeft = computed(() => Math.max(0, BREATH_GOAL - breathElapsed.value))

const breathStatus = computed(() => {
  if (breathing.value) return `${breathPhase.value.label} · ${breathPhase.value.remain}s`
  if (breathElapsed.value >= BREATH_GOAL) return '练习完成，感觉如何？'
  if (breathElapsed.value > 0) return '已暂停，准备好再继续'
  return '准备好，就陪自己呼吸一分钟'
})

const breathButton = computed(() => {
  if (breathing.value) return '结束练习'
  if (breathElapsed.value >= BREATH_GOAL) return '再来一次'
  if (breathElapsed.value > 0) return '继续练习'
  return '开始 1 分钟'
})

function stopBreath(reset = false): void {
  window.clearInterval(breathTimer)
  breathTimer = 0
  breathing.value = false
  if (reset) breathElapsed.value = 0
}

function startBreath(): void {
  if (breathing.value || breathTimer) return
  if (breathElapsed.value >= BREATH_GOAL) breathElapsed.value = 0

  breathing.value = true
  breathTimer = window.setInterval(() => {
    breathElapsed.value += 1
    if (breathElapsed.value >= BREATH_GOAL) {
      stopBreath(false)
      ElMessage.success('完成一次呼吸练习，慢一点也没关系')
    }
  }, 1000)
}

function toggleBreath(): void {
  if (breathing.value) stopBreath(false)
  else startBreath()
}

onBeforeUnmount(() => {
  stopBreath(false)
  if (historyFrame) window.cancelAnimationFrame(historyFrame)
  historyFrame = 0
})
</script>

<template>
  <div class="mood container">
    <header class="mood__head">
      <p class="eyebrow" v-reveal>情绪日记</p>
      <h1 v-reveal="{ delay: 60 }">今天，你的心情几分？</h1>
      <p class="section-sub" v-reveal="{ delay: 120 }">
        没有标准答案。记录本身，就是在照顾自己。
      </p>

      <div class="mood__summary" v-reveal="{ delay: 160 }">
        <div class="mood__summary-item">
          <span class="mood__summary-icon is-flame"><AppIcon name="flame" :size="15" /></span>
          <div>
            <strong>{{ streak }}<small>天</small></strong>
            <span>连续记录</span>
          </div>
        </div>
        <div class="mood__summary-item">
          <span class="mood__summary-icon is-check"><AppIcon name="check" :size="15" /></span>
          <div>
            <strong>{{ weekCheckins }}<small>/ 7</small></strong>
            <span>本周打卡</span>
          </div>
        </div>
        <div class="mood__summary-item">
          <span class="mood__summary-icon is-spark"><AppIcon name="sparkles" :size="15" /></span>
          <div>
            <strong>{{ average }}<small>分</small></strong>
            <span>近 7 次平均</span>
          </div>
        </div>
        <div class="mood__summary-item">
          <span class="mood__summary-icon is-heart"><AppIcon name="heart" :size="15" /></span>
          <div>
            <strong class="is-text">{{ topTag }}</strong>
            <span>最常出现</span>
          </div>
        </div>
      </div>
    </header>

    <div class="mood__grid">
      <div class="mood__main">
        <section class="check-in glass-card" v-reveal="{ delay: 60, variant: 'scale' }">
        <div class="check-in__head">
          <div class="mood-hero" :style="orbStyle">
            <div class="mood-orb" aria-hidden="true">
              <span :key="score" class="mood-orb__emoji">{{ currentMood.emoji }}</span>
              <i class="mood-orb__ring" />
            </div>
            <div class="mood-hero__meta">
              <span class="mood-hero__label">此刻的感受</span>
              <strong>{{ currentMood.label }}</strong>
              <small>{{ score }} / 5 · 已选 {{ selectedTags.length }} 个标签</small>
            </div>
          </div>

          <span class="check-in__state" :class="{ 'is-done': todayEntry }">
            <AppIcon :name="todayEntry ? 'check' : 'clock'" :size="13" />
            {{ todayEntry ? '今天已记录' : '今天还没记录' }}
          </span>
        </div>

        <div class="mood-scale" role="group" aria-label="选择情绪分数">
          <button
            v-for="option in moodOptions"
            :key="option.score"
            type="button"
            class="mood-option"
            :class="{ 'is-active': score === option.score }"
            :style="{ '--option': colorOf(option.score) }"
            :aria-pressed="score === option.score"
            :aria-label="`${option.score} 分，${option.label}`"
            @click="score = option.score"
          >
            <span class="mood-option__emoji">{{ option.emoji }}</span>
            <span class="mood-option__label">{{ option.label }}</span>
          </button>
        </div>

        <div class="check-in__label">
          <h3>相关标签</h3>
          <span>最多 {{ TAG_LIMIT }} 个</span>
        </div>
        <div class="mood-tags">
          <button
            v-for="tag in moodTags"
            :key="tag"
            type="button"
            class="chip"
            :class="{ 'is-active': selectedTags.includes(tag) }"
            :aria-pressed="selectedTags.includes(tag)"
            @click="toggleTag(tag)"
          >
            {{ tag }}
          </button>
        </div>

        <div class="check-in__label">
          <h3>想补充点什么？</h3>
          <span>{{ note.length }} / 120</span>
        </div>
        <el-input
          v-model="note"
          type="textarea"
          :rows="3"
          maxlength="120"
          resize="none"
          placeholder="一句话也可以，比如：今天开会被肯定了。"
        />

        <div class="note-prompts">
          <button
            v-for="prompt in notePrompts"
            :key="prompt"
            type="button"
            @click="usePrompt(prompt)"
          >
            {{ prompt }}
          </button>
        </div>

        <el-button type="primary" size="large" class="check-in__save" :loading="saving" @click="save">
          <AppIcon name="check" :size="16" />
          {{ todayEntry ? '更新今天的记录' : '记录今天' }}
        </el-button>

        <p class="check-in__hint">
          记录会同步到你的账号；同一天再次保存会更新当天记录，仅你自己和后台运营可见。
        </p>
        </section>

        <section class="history glass-card" v-reveal="{ delay: 240 }">
          <header class="history__head">
            <div>
              <h2>历史记录</h2>
              <p>共 {{ history.length }} 天 · 点击记录查看详情</p>
            </div>
          </header>

          <div class="history__filters" role="group" aria-label="筛选历史记录">
            <button
              v-for="tab in filterTabs"
              :key="tab.key"
              type="button"
              :class="{ 'is-active': historyFilter === tab.key }"
              :aria-pressed="historyFilter === tab.key"
              @click="historyFilter = tab.key"
            >
              {{ tab.label }}
              <span>{{ tab.count }}</span>
            </button>
          </div>

          <div v-if="deleteTarget" class="history__confirm">
            <span>删除 {{ dateLabel(deleteTarget.date) }} 的记录？</span>
            <div>
              <button type="button" @click="cancelDelete">保留</button>
              <button type="button" class="is-danger" @click="confirmDelete">删除</button>
            </div>
          </div>

          <div v-if="loading" class="history__skeleton" role="status" aria-label="正在加载历史记录">
            <div v-for="index in 4" :key="index" class="history__skeleton-row" aria-hidden="true">
              <span class="history__skeleton-avatar" />
              <div class="history__skeleton-body">
                <span class="history__skeleton-line history__skeleton-line--title" />
                <span class="history__skeleton-line" />
              </div>
            </div>
          </div>

          <TransitionGroup
            v-else-if="filteredHistory.length"
            name="history"
            tag="ul"
            class="history__list"
            @scroll.passive="onHistoryScroll"
          >
            <li v-for="item in visibleHistory" :key="item.id">
              <div
                class="history__row"
                role="button"
                tabindex="0"
                :aria-label="`查看 ${dateLabel(item.date)} 的情绪详情`"
                @click="openDetail(item)"
                @keydown.enter.prevent="openDetail(item)"
                @keydown.space.prevent="openDetail(item)"
              >
                <span class="history__emoji">{{ emojiOf(item.score) }}</span>
                <div class="history__main">
                  <div class="history__line">
                    <strong>{{ dateLabel(item.date) }} · {{ item.score }} 分</strong>
                    <span class="history__dots" :aria-label="`${item.score} 分（满分 5 分）`">
                      <i
                        v-for="level in 5"
                        :key="level"
                        :class="{ 'is-on': level <= item.score }"
                        :style="{ '--dot': colorOf(item.score) }"
                      />
                    </span>
                    <span class="history__peek">详情</span>
                  </div>
                  <p v-if="item.note">{{ item.note }}</p>
                  <p v-else class="is-muted">（没有写备注）</p>
                  <div class="history__tags">
                    <span v-for="tag in item.tags" :key="tag">{{ tag }}</span>
                  </div>
                </div>
              </div>
              <button
                type="button"
                class="history__delete"
                :aria-label="`删除 ${dateLabel(item.date)} 的记录`"
                @click.stop="requestDelete(item)"
              >
                <AppIcon name="close" :size="13" />
              </button>
            </li>

            <li v-if="hasMoreHistory" key="__more" class="history__more">
              <button type="button" @click="loadMoreHistory">
                加载更早的记录（还有 {{ filteredHistory.length - visibleHistory.length }} 天）
              </button>
            </li>
          </TransitionGroup>

          <div v-else class="mood-empty">
            <AppIcon name="leaf" :size="20" />
            <p>{{ history.length ? '这个筛选下还没有记录。' : '还没有历史记录，从今天开始吧。' }}</p>
          </div>
        </section>

        <dialog
          ref="detailDialog"
          class="mood-detail"
          aria-labelledby="mood-detail-title"
          @close="detailTarget = null"
          @click.self="closeDetail"
        >
          <article
            v-if="detailTarget"
            class="mood-detail__card"
            :style="{ '--mood': colorOf(detailTarget.score) }"
          >
            <header class="mood-detail__head">
              <div>
                <p class="mood-detail__eyebrow">{{ detailEyebrow }}</p>
                <h2 id="mood-detail-title">{{ fullDate(detailTarget.date) }}</h2>
              </div>
              <button type="button" class="mood-detail__close" aria-label="关闭详情" @click="closeDetail">
                <AppIcon name="close" :size="14" />
              </button>
            </header>

            <div class="mood-detail__hero">
              <span class="mood-detail__emoji" aria-hidden="true">{{ emojiOf(detailTarget.score) }}</span>
              <div class="mood-detail__mood">
                <strong>{{ labelOf(detailTarget.score) }}</strong>
                <span
                  class="mood-detail__dots"
                  :aria-label="`${detailTarget.score} 分（满分 5 分）`"
                >
                  <i
                    v-for="level in 5"
                    :key="level"
                    :class="{ 'is-on': level <= detailTarget.score }"
                  />
                </span>
              </div>
              <span class="mood-detail__score">
                {{ detailTarget.score }}<small>/ 5</small>
              </span>
            </div>

            <dl class="mood-detail__stats">
              <div>
                <dt>心情指数</dt>
                <dd>
                  {{ Number.isFinite(detailTarget.moodIndex) ? `${detailTarget.moodIndex} / 100` : '—' }}
                </dd>
              </div>
              <div>
                <dt>记录时间</dt>
                <dd>{{ recordedAtLabel(detailTarget.createdAt) }}</dd>
              </div>
            </dl>

            <section class="mood-detail__block">
              <h3>那天的标签</h3>
              <div v-if="detailTarget.tags.length" class="mood-detail__tags">
                <span v-for="tag in detailTarget.tags" :key="tag">{{ tag }}</span>
              </div>
              <p v-else class="mood-detail__empty">那天没有选择标签。</p>
            </section>

            <section class="mood-detail__block">
              <h3>想说的话</h3>
              <blockquote v-if="detailTarget.note" class="mood-detail__note">
                {{ detailTarget.note }}
              </blockquote>
              <p v-else class="mood-detail__empty">那天没有留下备注。</p>
            </section>

            <footer class="mood-detail__foot">
              <span>
                {{
                  todayEntry && todayEntry.id === detailTarget.id
                    ? '这是今天的记录，可以在上方继续更新'
                    : '这条记录已保存在你的账号里'
                }}
              </span>
              <button type="button" class="mood-detail__btn" @click="closeDetail">知道了</button>
            </footer>
          </article>
        </dialog>
      </div>

      <aside class="mood__side">
        <section class="trend glass-card" v-reveal="{ delay: 120 }">
          <header class="trend__head">
            <div>
              <h2>最近 7 次记录</h2>
              <p>平均 {{ average }} 分 · 最高 {{ Math.max(...weekValues, 0) }} 分</p>
            </div>
            <span class="trend__badge">{{ emojiOf(Number(average)) }} {{ averageLabel }}</span>
          </header>

          <LazyMount v-if="weekValues.length" :min-height="182">
            <AreaChart
              :labels="weekLabels"
              :values="weekValues"
              :height="182"
              color="var(--warm-brand)"
              suffix=" 分"
              panel
            />
          </LazyMount>
          <div v-else class="mood-empty">
            <AppIcon name="mood" :size="20" />
            <p>还没有记录，完成第一次打卡后这里会出现趋势。</p>
          </div>

          <p class="trend__insight">
            <AppIcon name="sparkles" :size="14" />
            {{ insight }}
          </p>
        </section>

        <section
          class="breath glass-card"
          :class="breathing ? `is-${breathPhase.key}` : ''"
          v-reveal="{ delay: 160 }"
        >
          <div class="breath__head">
            <div>
              <h2>给自己一分钟</h2>
              <p>4 秒吸气 · 4 秒屏息 · 6 秒呼气</p>
            </div>
            <span class="breath__time">{{ breathing ? `${breathLeft}s` : '60s' }}</span>
          </div>

          <div class="breath__stage">
            <div class="breath__orb" aria-hidden="true">
              <span>{{ breathing ? breathPhase.remain : '·' }}</span>
              <i class="breath__ring" />
              <i class="breath__ring is-late" />
            </div>
            <p class="breath__status" aria-live="polite">{{ breathStatus }}</p>
          </div>

          <button type="button" class="breath__btn" :class="{ 'is-running': breathing }" @click="toggleBreath">
            {{ breathButton }}
          </button>
        </section>

        <section class="heat glass-card" v-reveal="{ delay: 200 }">
          <header class="heat__head">
            <div>
              <h2>打卡日历</h2>
              <p>最近四周 · 颜色越深，分数越高</p>
            </div>
            <span class="heat__total">{{ history.length }} 条记录</span>
          </header>

          <div class="heat__weekdays" aria-hidden="true">
            <span v-for="day in ['一', '二', '三', '四', '五', '六', '日']" :key="day">{{ day }}</span>
          </div>
          <div class="heat__grid" role="img" aria-label="最近四周的情绪打卡日历">
            <span
              v-for="cell in heatmapCells"
              :key="cell.key"
              class="heat__cell"
              :class="{
                'is-empty': !cell.score,
                'is-future': cell.future,
                'is-today': cell.isToday,
              }"
              :style="cellStyle(cell)"
              :title="cell.title"
            />
          </div>

          <div class="heat__legend">
            <span>低</span>
            <i v-for="level in 5" :key="level" :style="{ background: colorOf(level) }" />
            <span>高</span>
            <span class="heat__legend-gap">空白 = 未记录</span>
          </div>
        </section>

      </aside>
    </div>
  </div>
</template>

<style scoped lang="scss">
.mood {
  padding: 54px 0 88px;
}

.mood__head {
  max-width: 720px;
  margin-bottom: $space-6;

  h1 {
    margin: 12px 0 10px;
    font-size: clamp(30px, 3.6vw, 42px);
    font-weight: 600;
    letter-spacing: -0.03em;
  }
}

.mood__summary {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
}

.mood__summary-item {
  display: flex;
  flex: 1 1 150px;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid var(--warm-line);
  border-radius: 14px;
  background: var(--warm-paper-72);
  box-shadow: inset 0 1px 0 var(--warm-rim-60);

  strong {
    display: block;
    color: var(--warm-text);
    font-family: $font-display;
    font-size: 20px;
    font-weight: 600;
    line-height: 1.2;
    font-variant-numeric: tabular-nums;

    small {
      margin-left: 2px;
      color: var(--warm-text-muted);
      font-family: $font-body;
      font-size: 10.5px;
      font-weight: 400;
    }

    &.is-text {
      font-size: 15px;
      line-height: 1.5;
    }
  }

  span:last-child {
    color: var(--warm-text-muted);
    font-size: 10.5px;
    letter-spacing: 0.06em;
  }
}

.mood__summary-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 10px;

  &.is-flame {
    background: color-mix(in srgb, var(--warm-apricot) 14%, transparent);
    color: var(--warm-apricot);
  }

  &.is-check {
    background: color-mix(in srgb, var(--warm-brand) 12%, transparent);
    color: var(--warm-brand-strong);
  }

  &.is-spark {
    background: color-mix(in srgb, var(--warm-butter) 16%, transparent);
    color: var(--warm-butter);
  }

  &.is-heart {
    background: color-mix(in srgb, var(--warm-rose) 14%, transparent);
    color: var(--warm-rose);
  }
}

.mood__grid {
  display: grid;
  grid-template-columns: 1.04fr 0.96fr;
  align-items: start;
  gap: $space-5;
}

.mood__main {
  display: grid;
  gap: $space-5;
  min-width: 0;
}

.mood__side {
  display: grid;
  gap: $space-5;
}

.glass-card {
  padding: 26px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-lg;
  background: linear-gradient(160deg, var(--warm-paper), var(--warm-paper-alt));
  box-shadow:
    0 32px 72px -54px var(--warm-shadow),
    inset 0 1px 0 var(--warm-paper-85);
}

// ---- 打卡表单 ----
.check-in__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--warm-line);
}

.mood-hero {
  display: flex;
  align-items: center;
  gap: 20px;
  min-width: 0;
}

.mood-orb {
  position: relative;
  display: grid;
  flex: none;
  place-items: center;
  width: 92px;
  height: 92px;
  border-radius: 50%;
  background:
    radial-gradient(circle at 30% 24%, rgba(255, 255, 255, 0.66), transparent 40%),
    radial-gradient(circle at 64% 72%, var(--mood), transparent 72%),
    radial-gradient(circle at 50% 50%, var(--mood-soft), transparent 78%);
  box-shadow:
    0 18px 38px -22px var(--mood-soft),
    inset 0 0 26px -12px rgba(255, 255, 255, 0.8);
  transition: background 0.4s $ease-out;
  animation: orb-breathe 6.5s ease-in-out infinite;
}

.mood-orb__emoji {
  font-size: 40px;
  line-height: 1;
  filter: drop-shadow(0 6px 12px rgba(74, 64, 46, 0.18));
  animation: orb-pop 0.5s $ease-out-expo both;
}

.mood-orb__ring {
  position: absolute;
  inset: -8px;
  border: 1px solid var(--mood-soft);
  border-radius: 50%;
  animation: breathe 6.5s ease-in-out infinite;
}

.mood-hero__meta {
  display: grid;
  gap: 3px;
  min-width: 0;

  strong {
    color: var(--warm-text);
    font-family: $font-display;
    font-size: 23px;
    font-weight: 600;
    letter-spacing: -0.01em;
  }

  small {
    color: var(--warm-text-muted);
    font-size: 12px;
    font-variant-numeric: tabular-nums;
  }
}

.mood-hero__label {
  color: var(--warm-text-muted);
  font-size: 11px;
  letter-spacing: 0.16em;
}

.check-in__state {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-pill;
  background: var(--warm-paper-70);
  color: var(--warm-text-muted);
  font-size: 10.5px;
  white-space: nowrap;

  &.is-done {
    border-color: color-mix(in srgb, var(--warm-brand) 28%, transparent);
    background: color-mix(in srgb, var(--warm-brand) 10%, transparent);
    color: var(--warm-brand-strong);
  }
}

.check-in {
  h3 {
    margin: 0;
    color: var(--warm-text-muted);
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.14em;
  }
}

.check-in__label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 24px 0 12px;

  span {
    color: var(--warm-text-muted);
    font-size: 10.5px;
    font-variant-numeric: tabular-nums;
  }
}

.mood-scale {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
  margin-top: 20px;
}

.mood-option {
  display: grid;
  justify-items: center;
  gap: 6px;
  padding: 13px 4px 11px;
  border: 1px solid var(--warm-line);
  border-radius: 14px;
  background: var(--warm-paper-72);
  color: var(--warm-text-muted);
  cursor: pointer;
  transition:
    transform 0.25s $ease-out-expo,
    border-color 0.25s $ease-out,
    background 0.25s $ease-out,
    box-shadow 0.25s $ease-out;

  &:hover {
    transform: translateY(-3px);
    border-color: var(--option);
    color: var(--warm-text);

    .mood-option__emoji {
      transform: scale(1.14);
    }
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }

  &.is-active {
    border-color: var(--option);
    background: color-mix(in srgb, var(--option) 10%, var(--warm-paper));
    color: var(--warm-text);
    box-shadow:
      0 12px 24px -18px var(--option),
      inset 0 1px 0 var(--warm-paper-85);

    .mood-option__emoji {
      transform: scale(1.18);
    }

    .mood-option__label {
      font-weight: 600;
    }
  }
}

.mood-option__emoji {
  font-size: 25px;
  line-height: 1;
  transition: transform 0.28s $ease-out-expo;
}

.mood-option__label {
  font-size: 11px;
  white-space: nowrap;
}

.mood-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.note-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;

  button {
    padding: 4px 9px;
    border: 1px dashed var(--warm-line);
    border-radius: $radius-pill;
    background: transparent;
    color: var(--warm-text-muted);
    font-size: 10.5px;
    cursor: pointer;
    transition:
      color 0.2s $ease-out,
      border-color 0.2s $ease-out,
      background 0.2s $ease-out;

    &:hover {
      border-color: color-mix(in srgb, var(--warm-brand) 36%, transparent);
      background: color-mix(in srgb, var(--warm-brand) 7%, transparent);
      color: var(--warm-brand-strong);
    }

    &:focus-visible {
      @include focus-ring($warm-brand);
    }
  }
}

.check-in__save {
  width: 100%;
  margin-top: 22px;
  gap: 6px;
}

.check-in__hint {
  margin: 10px 0 0;
  color: var(--warm-text-muted);
  font-size: 10.5px;
  line-height: 1.7;
  text-align: center;
}

// ---- 趋势卡 ----
.trend__head,
.breath__head,
.heat__head,
.history__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $space-3;

  h2 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
  }

  p {
    margin: 3px 0 0;
    color: var(--warm-text-muted);
    font-size: 11.5px;
  }
}

.trend__head {
  margin-bottom: $space-4;
}

.trend__badge {
  flex: none;
  padding: 5px 11px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 26%, transparent);
  border-radius: $radius-pill;
  background: color-mix(in srgb, var(--warm-brand) 10%, transparent);
  color: var(--warm-brand-strong);
  font-size: 11.5px;
  white-space: nowrap;
}

.trend__insight {
  display: flex;
  gap: 7px;
  margin: 14px 0 0;
  padding-top: 12px;
  border-top: 1px dashed var(--warm-line);
  color: var(--warm-text-muted);
  font-size: 11.5px;
  line-height: 1.7;

  svg {
    flex: none;
    margin-top: 2px;
    color: var(--warm-brand-strong);
  }
}

// ---- 呼吸练习 ----
.breath {
  overflow: hidden;
  background:
    radial-gradient(120% 120% at 80% 0%, color-mix(in srgb, var(--warm-brand) 9%, transparent), transparent 62%),
    linear-gradient(160deg, var(--warm-paper), var(--warm-paper-alt));
}

.breath__time {
  flex: none;
  color: var(--warm-brand-strong);
  font-family: $font-mono;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.breath__stage {
  display: grid;
  place-items: center;
  gap: 10px;
  padding: 18px 0 14px;
}

.breath__orb {
  position: relative;
  display: grid;
  place-items: center;
  width: 96px;
  height: 96px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 26%, transparent);
  border-radius: 50%;
  background:
    radial-gradient(circle at 36% 30%, rgba(255, 255, 255, 0.6), transparent 46%),
    radial-gradient(circle, color-mix(in srgb, var(--warm-brand) 52%, transparent), transparent 72%);
  box-shadow:
    0 0 0 1px color-mix(in srgb, var(--warm-brand) 18%, transparent),
    0 18px 40px -24px var(--warm-glow),
    inset 0 0 24px -10px color-mix(in srgb, var(--warm-brand) 55%, transparent);
  color: var(--warm-brand-strong);
  font-family: $font-display;
  font-size: 24px;
  font-variant-numeric: tabular-nums;
  transform: scale(0.78);
  transition: transform 0.8s $ease-out;

  span {
    position: relative;
    z-index: 1;
  }
}

.breath__ring {
  position: absolute;
  inset: -10px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 24%, transparent);
  border-radius: 50%;
  opacity: 0.7;

  &.is-late {
    inset: -22px;
    opacity: 0.35;
  }
}

.breath.is-inhale .breath__orb {
  transform: scale(1);
  transition-duration: 4s;
  transition-timing-function: linear;
}

.breath.is-hold .breath__orb {
  transform: scale(1);
  transition-duration: 0.4s;
}

.breath.is-exhale .breath__orb {
  transform: scale(0.72);
  transition-duration: 6s;
  transition-timing-function: linear;
}

.breath__status {
  margin: 0;
  color: var(--warm-text-muted);
  font-size: 11.5px;
  letter-spacing: 0.04em;
  text-align: center;
}

.breath__btn {
  width: 100%;
  padding: 9px 16px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 30%, transparent);
  border-radius: $radius-pill;
  background: color-mix(in srgb, var(--warm-brand) 9%, transparent);
  color: var(--warm-brand-strong);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background 0.24s $ease-out,
    color 0.24s $ease-out,
    border-color 0.24s $ease-out;

  &:hover {
    background: color-mix(in srgb, var(--warm-brand) 16%, transparent);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }

  &.is-running {
    border-color: var(--warm-brand);
    background: var(--warm-brand);
    color: var(--warm-on-brand);
  }
}

// ---- 打卡日历 ----
.heat__total {
  flex: none;
  color: var(--warm-text-muted);
  font-size: 10.5px;
  font-variant-numeric: tabular-nums;
}

.heat__weekdays,
.heat__grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 6px;
}

.heat__weekdays {
  margin: 16px 0 6px;
  color: var(--warm-text-muted);
  font-size: 9.5px;
  text-align: center;
}

.heat__cell {
  display: block;
  aspect-ratio: 1;
  border-radius: 7px;
  border: 1px solid transparent;
  background: var(--warm-line);
  transition:
    transform 0.2s $ease-out-expo,
    box-shadow 0.2s $ease-out;

  &:hover {
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--warm-brand) 28%, transparent);
    transform: scale(1.12);
  }

  &.is-empty {
    border-color: var(--warm-line);
    background: var(--warm-paper-60);
  }

  &.is-future {
    border-color: transparent;
    background: transparent;
    opacity: 0.3;
  }

  &.is-today {
    border-color: var(--warm-brand);
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--warm-brand) 16%, transparent);
  }
}

.heat__legend {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 14px;
  color: var(--warm-text-muted);
  font-size: 9.5px;

  i {
    width: 11px;
    height: 11px;
    border-radius: 3px;
    opacity: 0.85;
  }

  &-gap {
    margin-left: auto;
    letter-spacing: 0.03em;
  }
}

// ---- 历史记录 ----
.history__filters {
  display: flex;
  gap: 4px;
  margin: 16px 0 12px;
  padding: 3px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-pill;
  background: var(--warm-paper-60);

  button {
    display: inline-flex;
    flex: 1;
    align-items: center;
    justify-content: center;
    gap: 5px;
    padding: 5px 8px;
    border: 0;
    border-radius: $radius-pill;
    background: transparent;
    color: var(--warm-text-muted);
    font-size: 11px;
    cursor: pointer;
    transition:
      color 0.2s $ease-out,
      background 0.2s $ease-out;

    span {
      font-size: 9.5px;
      opacity: 0.7;
      font-variant-numeric: tabular-nums;
    }

    &:hover {
      color: var(--warm-text);
    }

    &:focus-visible {
      @include focus-ring($warm-brand);
    }

    &.is-active {
      background: var(--warm-brand-soft);
      color: var(--warm-brand-strong);
      font-weight: 600;
      box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--warm-brand) 20%, transparent);
    }
  }
}

.history__confirm {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
  padding: 8px 10px;
  border: 1px solid color-mix(in srgb, var(--warm-apricot) 30%, transparent);
  border-radius: 10px;
  background: color-mix(in srgb, var(--warm-apricot) 8%, transparent);
  color: var(--warm-text-regular);
  font-size: 11px;

  div {
    display: flex;
    gap: 6px;
  }

  button {
    padding: 3px 9px;
    border: 1px solid var(--warm-line);
    border-radius: $radius-pill;
    background: var(--warm-paper-80);
    color: var(--warm-text-muted);
    font-size: 10.5px;
    cursor: pointer;

    &.is-danger {
      border-color: color-mix(in srgb, var(--warm-apricot) 40%, transparent);
      background: color-mix(in srgb, var(--warm-apricot) 14%, transparent);
      color: var(--warm-apricot);
      font-weight: 600;
    }

    &:focus-visible {
      @include focus-ring($warm-brand);
    }
  }
}

.history__list {
  position: relative;
  display: grid;
  max-height: 420px;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
  mask-image: linear-gradient(180deg, #000 calc(100% - 18px), transparent);
  -webkit-mask-image: linear-gradient(180deg, #000 calc(100% - 18px), transparent);

  li {
    position: relative;
    display: flex;
    gap: 10px;
    padding: 12px 24px 12px 0;
    // 屏幕外的历史行跳过布局与绘制；auto 会记住渲染过的真实高度，滚动条不跳
    content-visibility: auto;
    contain-intrinsic-size: auto 92px;

    & + li {
      border-top: 1px dashed var(--warm-line);
    }

    &:hover .history__delete {
      opacity: 1;
    }
  }
}

// 整条记录可点击：悬停时日期和表情轻轻亮一下，右侧给出「详情」提示
.history__row {
  display: flex;
  flex: 1;
  gap: 10px;
  min-width: 0;
  border-radius: 10px;
  cursor: pointer;

  &:hover {
    .history__line strong {
      color: var(--warm-brand-strong);
    }

    .history__emoji {
      transform: scale(1.08) rotate(-4deg);
    }

    .history__peek {
      opacity: 1;
    }
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

.history__peek {
  margin-left: auto;
  color: var(--warm-brand-strong);
  font-size: 9.5px;
  letter-spacing: 0.08em;
  opacity: 0.55;
  transition: opacity 0.2s $ease-out;
}

.history__more {
  justify-content: center;
  padding: 14px 0;

  button {
    padding: 7px 16px;
    border: 1px solid var(--warm-line);
    border-radius: $radius-pill;
    background: var(--warm-paper-52);
    color: var(--warm-text-muted);
    font: inherit;
    font-size: 11.5px;
    cursor: pointer;
    transition:
      color 0.2s $ease-out,
      border-color 0.2s $ease-out;

    &:hover {
      border-color: var(--warm-brand);
      color: var(--warm-text);
    }

    &:focus-visible {
      @include focus-ring($warm-brand);
    }
  }
}

// 首屏加载历史记录时的骨架，避免出现「空列表 → 突然出现」的跳动
.history__skeleton {
  display: grid;
  gap: 12px;
  padding: 6px 0;
}

.history__skeleton-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.history__skeleton-avatar {
  width: 26px;
  height: 26px;
  flex: none;
  border-radius: 50%;
  background: var(--warm-paper-40);
}

.history__skeleton-body {
  flex: 1;
  min-width: 0;
}

.history__skeleton-line {
  display: block;
  width: 66%;
  height: 9px;
  margin-top: 8px;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--warm-paper-40), var(--warm-paper-70), var(--warm-paper-40));
  background-size: 200% 100%;
  animation: history-shimmer 1.4s linear infinite;
}

.history__skeleton-line--title {
  width: 42%;
  height: 11px;
  margin-top: 0;
}

@keyframes history-shimmer {
  from {
    background-position: 200% 0;
  }

  to {
    background-position: -200% 0;
  }
}

.history__emoji {
  flex: none;
  font-size: 19px;
  line-height: 1.5;
  transition: transform 0.24s $ease-out-expo;
}

.history__main {
  min-width: 0;

  p {
    margin: 3px 0 6px;
    color: var(--warm-text-muted);
    font-size: 12px;
    line-height: 1.65;

    &.is-muted {
      opacity: 0.6;
    }
  }
}

.history__line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;

  strong {
    color: var(--warm-text);
    font-size: 12.5px;
    font-weight: 600;
    transition: color 0.2s $ease-out;
  }
}

.history__dots {
  display: inline-flex;
  gap: 2px;

  i {
    width: 4px;
    height: 4px;
    border-radius: 50%;
    background: var(--warm-line);

    &.is-on {
      background: var(--dot);
    }
  }
}

.history__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;

  span {
    padding: 2px 8px;
    border: 1px solid var(--warm-line);
    border-radius: $radius-pill;
    background: var(--warm-paper-78);
    color: var(--warm-text-muted);
    font-size: 10.5px;
  }
}

.history__delete {
  position: absolute;
  top: 10px;
  right: 0;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 50%;
  background: var(--warm-paper-80);
  color: var(--warm-text-muted);
  opacity: 0;
  cursor: pointer;
  transition:
    opacity 0.2s $ease-out,
    color 0.2s $ease-out,
    background 0.2s $ease-out;

  &:hover,
  &:focus-visible {
    opacity: 1;
    background: color-mix(in srgb, var(--warm-apricot) 12%, transparent);
    color: var(--warm-apricot);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

// ---- 记录详情弹层 ----
// 沿用站内统一的暖纸卡片、圆角、字体与情绪色 token，日夜主题自动跟随
:global(html:has(dialog.mood-detail[open])) {
  overflow: hidden;
}

.mood-detail {
  width: min(468px, calc(100vw - 32px));
  max-width: none;
  max-height: none;
  margin: auto;
  padding: 0;
  overflow: visible;
  border: 0;
  background: transparent;
  color: var(--warm-text);

  &::backdrop {
    background: rgba(28, 20, 24, 0.46);
    backdrop-filter: blur(5px);
    animation: mood-detail-fade 0.28s $ease-out;
  }

  &[open] .mood-detail__card {
    animation: mood-detail-in 0.34s $ease-out-expo both;
  }
}

.mood-detail__card {
  position: relative;
  max-height: min(86vh, 700px);
  padding: 24px 24px 18px;
  overflow-y: auto;
  border: 1px solid var(--warm-line);
  border-radius: 20px;
  background:
    radial-gradient(
      130% 76% at 88% -10%,
      color-mix(in srgb, var(--mood) 18%, transparent),
      transparent 62%
    ),
    linear-gradient(160deg, var(--warm-paper), var(--warm-paper-alt));
  box-shadow:
    0 40px 90px -52px var(--warm-shadow),
    inset 0 1px 0 var(--warm-paper-85);
  scrollbar-width: thin;
  scrollbar-color: rgba(136, 104, 110, 0.28) transparent;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    right: 24px;
    left: 24px;
    height: 1px;
    background: linear-gradient(90deg, transparent, var(--warm-brand), var(--mood), transparent);
    opacity: 0.65;
    pointer-events: none;
  }
}

.mood-detail__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;

  h2 {
    margin: 5px 0 0;
    font-family: $font-display;
    font-size: 19px;
    font-weight: 600;
    letter-spacing: -0.01em;
  }
}

.mood-detail__eyebrow {
  margin: 0;
  color: var(--warm-text-muted);
  font-size: 10.5px;
  letter-spacing: 0.14em;
}

.mood-detail__close {
  display: grid;
  flex: none;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid var(--warm-line);
  border-radius: 50%;
  background: var(--warm-paper-70);
  color: var(--warm-text-muted);
  cursor: pointer;
  transition:
    color 0.2s $ease-out,
    background 0.2s $ease-out,
    border-color 0.2s $ease-out;

  &:hover {
    border-color: var(--warm-brand);
    background: var(--warm-paper-85);
    color: var(--warm-text);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

.mood-detail__hero {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 18px;
  padding: 16px;
  border: 1px solid color-mix(in srgb, var(--mood) 26%, transparent);
  border-radius: 16px;
  background:
    radial-gradient(
      120% 130% at 12% 10%,
      color-mix(in srgb, var(--mood) 14%, transparent),
      transparent 62%
    ),
    var(--warm-paper-60);
}

.mood-detail__emoji {
  flex: none;
  font-size: 42px;
  line-height: 1;
  filter: drop-shadow(0 8px 14px rgba(74, 64, 46, 0.16));
}

.mood-detail__mood {
  display: grid;
  gap: 6px;
  min-width: 0;

  strong {
    font-family: $font-display;
    font-size: 20px;
    font-weight: 600;
  }
}

.mood-detail__dots {
  display: inline-flex;
  gap: 4px;

  i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--warm-line);

    &.is-on {
      background: var(--mood);
    }
  }
}

.mood-detail__score {
  margin-left: auto;
  color: var(--mood);
  font-family: $font-display;
  font-size: 28px;
  font-weight: 600;
  line-height: 1;
  font-variant-numeric: tabular-nums;

  small {
    margin-left: 3px;
    color: var(--warm-text-muted);
    font-family: $font-body;
    font-size: 11px;
    font-weight: 400;
  }
}

.mood-detail__stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 12px 0 0;

  > div {
    min-width: 0;
    padding: 10px 12px;
    border: 1px solid var(--warm-line);
    border-radius: 12px;
    background: var(--warm-paper-60);
  }

  dt {
    margin-bottom: 4px;
    color: var(--warm-text-muted);
    font-size: 9.5px;
    letter-spacing: 0.1em;
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

.mood-detail__block {
  margin-top: 18px;

  h3 {
    margin: 0 0 8px;
    color: var(--warm-text-muted);
    font-size: 9.5px;
    font-weight: 600;
    letter-spacing: 0.16em;
  }
}

.mood-detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;

  span {
    padding: 3px 9px;
    border: 1px solid color-mix(in srgb, var(--mood) 24%, var(--warm-line));
    border-radius: $radius-pill;
    background: color-mix(in srgb, var(--mood) 8%, transparent);
    color: var(--warm-text-regular);
    font-size: 10.5px;
  }
}

.mood-detail__note {
  margin: 0;
  padding: 12px 14px;
  border-left: 2px solid var(--mood);
  border-radius: 0 12px 12px 0;
  background: var(--warm-paper-60);
  color: var(--warm-text-regular);
  font-size: 12.5px;
  font-style: normal;
  line-height: 1.85;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.mood-detail__empty {
  margin: 0;
  color: var(--warm-text-muted);
  font-size: 11.5px;
  opacity: 0.8;
}

.mood-detail__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px dashed var(--warm-line);

  span {
    color: var(--warm-text-muted);
    font-size: 10.5px;
    line-height: 1.6;
  }
}

.mood-detail__btn {
  flex: none;
  padding: 7px 18px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 40%, transparent);
  border-radius: $radius-pill;
  background: var(--warm-brand);
  color: var(--warm-on-brand);
  font: inherit;
  font-size: 11.5px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background 0.2s $ease-out,
    border-color 0.2s $ease-out,
    transform 0.2s $ease-out;

  &:hover {
    background: var(--warm-brand-strong);
  }

  &:active {
    transform: translateY(1px);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

@keyframes mood-detail-in {
  from {
    opacity: 0;
    transform: translateY(14px) scale(0.97);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes mood-detail-fade {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

.mood-empty {
  display: grid;
  place-items: center;
  gap: 8px;
  padding: 26px 16px;
  border: 1px dashed var(--warm-line);
  border-radius: 14px;
  color: var(--warm-text-muted);
  text-align: center;

  svg {
    color: var(--warm-brand);
    opacity: 0.8;
  }

  p {
    margin: 0;
    font-size: 11.5px;
    line-height: 1.7;
  }
}

.history-enter-active {
  transition:
    opacity 0.45s $ease-out-expo,
    transform 0.45s $ease-out-expo;
}

.history-enter-from {
  opacity: 0;
  transform: translateY(-12px);
}

.history-leave-active {
  transition:
    opacity 0.2s $ease-in,
    transform 0.2s $ease-in;
}

.history-leave-to {
  opacity: 0;
  transform: translateX(10px);
}

.history-move {
  transition: transform 0.45s $ease-out-expo;
}

@keyframes orb-breathe {
  0%,
  100% {
    transform: translateY(0) scale(1);
  }

  50% {
    transform: translateY(-4px) scale(1.03);
  }
}

@keyframes orb-pop {
  from {
    opacity: 0;
    transform: scale(0.6) rotate(-8deg);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

@include media-down($bp-lg) {
  .mood__grid {
    grid-template-columns: 1fr;
  }
}

@include media-down($bp-sm) {
  .mood {
    padding: 34px 0 60px;
  }

  .mood__summary-item {
    flex: 1 1 44%;
  }

  .mood-scale {
    gap: 6px;
  }

  .mood-option {
    padding: 10px 2px;
  }

  .mood-option__label {
    font-size: 10px;
  }

  .mood-option__emoji {
    font-size: 21px;
  }

  .glass-card {
    padding: 20px;
  }

  .check-in__head {
    flex-direction: column;
  }

  .mood-hero {
    gap: 14px;
  }

  .mood-orb {
    width: 72px;
    height: 72px;
  }

  .mood-orb__emoji {
    font-size: 32px;
  }

  .mood-hero__meta strong {
    font-size: 20px;
  }

  .history__list {
    max-height: none;
  }

  .history__delete {
    opacity: 1;
  }

  .mood-detail__card {
    padding: 20px 18px 16px;
  }

  .mood-detail__hero {
    gap: 12px;
    padding: 14px;
  }

  .mood-detail__emoji {
    font-size: 34px;
  }

  .mood-detail__score {
    font-size: 24px;
  }

  .mood-detail__foot {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
    text-align: center;
  }

  .mood-detail__btn {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .mood-orb,
  .mood-orb__emoji,
  .mood-orb__ring {
    animation: none !important;
  }

  .breath__orb {
    transition: none;
  }

  .history__skeleton-line {
    animation: none;
  }

  .mood-detail[open] .mood-detail__card,
  .mood-detail::backdrop {
    animation: none;
  }
}
</style>
