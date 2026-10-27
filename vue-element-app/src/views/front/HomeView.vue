<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'

import type { Article, IconName, MoodLog, MoodOption } from '@/types'
import { listArticles } from '@/api/articles'
import { listMyMoods, saveMood } from '@/api/moods'
import AppIcon from '@/components/AppIcon.vue'
import LazyMount from '@/components/LazyMount.vue'
import { useTypewriter } from '@/composables/useTypewriter'
import { dayAt, isoDate } from '@/utils/date'
import { moodOptions } from '@/data/catalog'

// 呼吸练习位于首屏下方：代码拆成独立 chunk，配合 LazyMount 到视口附近才加载
const BreathingCoach = defineAsyncComponent(() => import('@/components/BreathingCoach.vue'))

interface Feature {
  icon: IconName
  title: string
  text: string
  to: string
  cta: string
  index: string
}

const features: Feature[] = [
  {
    icon: 'chat',
    title: '随时在线的倾听',
    text: '不用预约、不用寒暄。深夜三点，也有人在屏幕这头认真接住你的情绪。',
    to: '/front/chat',
    cta: '开始对话',
    index: '01',
  },
  {
    icon: 'mood',
    title: '每天一次情绪打卡',
    text: '一个分数、几个标签，记录下那些说不清的感受，慢慢看见自己的规律。',
    to: '/front/mood',
    cta: '记录心情',
    index: '02',
  },
  {
    icon: 'book',
    title: '心理科普内容库',
    text: '焦虑、睡眠、关系与自我成长，用可执行的小方法，替代空泛的「别想太多」。',
    to: '/front/articles',
    cta: '去读一读',
    index: '03',
  },
]

const ticker: string[] = [
  '随时在线',
  '不评判',
  '记录仅你可见',
  '深夜也在',
  '温柔追问',
  '可执行的一小步',
  '专业内容支持',
]

// ---- 首页一分钟打卡：与情绪日记共用后端记录 ----
const moodHistory = ref<MoodLog[]>([])
const checkinBusy = ref(false)

const todayMood = computed(
  () => moodHistory.value.find((item) => item.date === isoDate(new Date())) ?? null,
)

const chosenScore = computed(() => todayMood.value?.score ?? null)

const justSaved = ref(false)
const savedScore = ref<number | null>(null)
let savedTimer: number | null = null

async function selectMood(option: MoodOption): Promise<void> {
  if (checkinBusy.value) return
  const date = isoDate(new Date())
  const existing = moodHistory.value.find((item) => item.date === date)
  checkinBusy.value = true

  try {
    const saved = await saveMood({
      date,
      score: option.score,
      tags: existing?.tags ?? [],
      note: existing?.note ?? '',
    })
    moodHistory.value = existing
      ? moodHistory.value.map((item) => (item.date === date ? saved : item))
      : [saved, ...moodHistory.value]

    savedScore.value = option.score
    justSaved.value = true
    if (savedTimer !== null) window.clearTimeout(savedTimer)
    savedTimer = window.setTimeout(() => {
      justSaved.value = false
      savedTimer = null
    }, 3200)
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '情绪记录保存失败，请稍后重试',
      customClass: 'warm-toast',
    })
  } finally {
    checkinBusy.value = false
  }
}

const moodLabelOf = (score: number): string =>
  moodOptions.find((item) => item.score === score)?.label ?? ''
const moodEmojiOf = (score: number): string =>
  moodOptions.find((item) => item.score === score)?.emoji ?? ''

const checkinStatus = computed(() => {
  if (justSaved.value && savedScore.value) {
    return `已记下 · ${moodEmojiOf(savedScore.value)} ${moodLabelOf(savedScore.value)}。这一刻已经被好好收下了。`
  }
  if (todayMood.value) {
    return `今天已记下：${moodEmojiOf(todayMood.value.score)} ${moodLabelOf(todayMood.value.score)} · 点一下可以随时修改`
  }
  return '还没有记录 · 点一个表情，用一分钟照顾自己'
})

const WEEKDAY_LABELS = ['日', '一', '二', '三', '四', '五', '六']

const weekBars = computed(() =>
  Array.from({ length: 7 }, (_, index) => {
    const date = dayAt(index - 6)
    const key = isoDate(date)
    const entry = moodHistory.value.find((item) => item.date === key)
    return {
      key,
      label: WEEKDAY_LABELS[date.getDay()],
      score: entry?.score ?? 0,
      isToday: index === 6,
    }
  }),
)

const weekCount = computed(() => weekBars.value.filter((bar) => bar.score > 0).length)

const streak = computed(() => {
  const dates = new Set(moodHistory.value.map((item) => item.date))
  const cursor = new Date()
  cursor.setHours(12, 0, 0, 0)
  if (!dates.has(isoDate(cursor))) cursor.setDate(cursor.getDate() - 1)

  let count = 0
  while (dates.has(isoDate(cursor)) && count < 3660) {
    count += 1
    cursor.setDate(cursor.getDate() - 1)
  }
  return count
})

onBeforeUnmount(() => {
  if (savedTimer !== null) window.clearTimeout(savedTimer)
})

// ---- 精选阅读：来自后端已发布文章，按阅读量取前三 ----
const featuredArticles = ref<Article[]>([])
const articlesLoading = ref(true)

async function loadFeaturedArticles(): Promise<void> {
  articlesLoading.value = true
  try {
    const articles = await listArticles({ sort: 'popular' })
    featuredArticles.value = articles
      .filter((article) => article.status === '已发布')
      .slice(0, 3)
  } catch {
    featuredArticles.value = []
  } finally {
    articlesLoading.value = false
  }
}

async function loadMoods(): Promise<void> {
  try {
    moodHistory.value = await listMyMoods()
  } catch {
    moodHistory.value = []
  }
}

onMounted(() => {
  void loadFeaturedArticles()
  void loadMoods()
})

// 首屏对话预览：AI 最后一句逐字打出，像真的在回应
const {
  output: typedReply,
  done: replyDone,
} = useTypewriter('「怕做不好」背后，往往是你很想把它做好。我们先从最可控的一小步开始，好吗？', {
  speed: 38,
  startDelay: 1600,
})
</script>

<template>
  <div class="home">
    <!-- ============ HERO ============ -->
    <section class="hero">
      <div class="hero__beam" aria-hidden="true" />

      <div class="hero__ambient" aria-hidden="true">
        <span class="hero__watermark" v-parallax="{ speed: 34 }">倾听</span>
        <span class="hero__orb hero__orb--a" v-parallax="{ speed: 52 }" />
        <span class="hero__orb hero__orb--b" v-parallax="{ speed: -44 }" />
        <span class="hero__orb hero__orb--c" v-parallax="{ speed: 28 }" />
        <span class="hero__scan" />
      </div>

      <div class="container hero__grid">
        <div class="hero__copy">
          <p class="hero__pill">
            <span class="hero__pulse" aria-hidden="true" />
            深夜也在 · 随时可以开始
          </p>

          <h1 class="hero__title">
            <span class="hero__line"><span>把心事</span></span>
            <span class="hero__line"><span>说给<em>懂你</em>的 AI 听</span></span>
          </h1>

          <p class="hero__lede">
            不评判、不说教。它陪你梳理情绪、记录变化，也在你需要专业帮助时，认真提醒你走向真实的人。
          </p>

          <div class="hero__actions">
            <RouterLink to="/front/chat" class="btn btn--primary">
              开始倾诉
              <AppIcon name="arrowRight" :size="17" />
            </RouterLink>
            <RouterLink to="/front/mood" class="btn btn--ghost">
              记录今天的情绪
            </RouterLink>
          </div>

          <ul class="hero__trust">
            <li><AppIcon name="shield" :size="15" /> 记录仅你可见</li>
            <li><AppIcon name="clock" :size="15" /> 全天在线</li>
            <li><AppIcon name="sparkles" :size="15" /> 专业内容支持</li>
          </ul>
        </div>

        <div class="hero__stage">
          <div class="stage__halo" aria-hidden="true" />
          <div class="stage__ring stage__ring--a" aria-hidden="true" />
          <div class="stage__ring stage__ring--b" aria-hidden="true" />

          <div class="preview" v-spotlight v-tilt="{ max: 5 }">
            <header class="preview__head">
              <span class="preview__avatar">心</span>
              <div class="preview__id">
                <strong>心理健康 AI 助手</strong>
                <small><i class="preview__online" />在线 · 仅你可见</small>
              </div>
              <span class="preview__badge">私密会话</span>
            </header>

            <div class="preview__bubbles">
              <p class="bubble bubble--ai bubble--d1">今天听起来有点累，愿意和我说说发生了什么吗？</p>
              <p class="bubble bubble--user bubble--d2">项目快到期了，我总觉得自己做不好。</p>
              <p class="bubble bubble--ai bubble--d3">
                <template v-if="typedReply">
                  {{ typedReply }}
                  <span v-if="!replyDone" class="bubble__caret" aria-hidden="true" />
                </template>
                <span v-else class="bubble__pending" aria-label="AI 正在输入">
                  <i /><i /><i />
                </span>
              </p>
            </div>

            <footer class="preview__chips">
              <span>😕 有点焦虑</span>
              <span>😴 睡眠不足</span>
              <RouterLink to="/front/chat" class="preview__chips-link">
                继续聊 <AppIcon name="arrowRight" :size="13" />
              </RouterLink>
            </footer>
          </div>

          <div class="float-card float-card--mood">
            <span>本周情绪</span>
            <strong>3.6</strong>
            <em>↑ 0.4</em>
          </div>

          <div class="float-card float-card--safe">
            <AppIcon name="shield" :size="15" />
            记录仅你可见
          </div>
        </div>
      </div>

      <a class="hero__scroll" href="#capabilities" aria-label="向下浏览">
        <span class="hero__scroll-line"><i /></span>
        向下浏览
      </a>
    </section>

    <!-- ============ TICKER ============ -->
    <div class="ticker" aria-label="产品特性">
      <div class="ticker__track">
        <span v-for="(item, index) in ticker" :key="`a-${index}`" class="ticker__item">
          {{ item }}<i>✦</i>
        </span>
        <span
          v-for="(item, index) in ticker"
          :key="`b-${index}`"
          class="ticker__item"
          aria-hidden="true"
        >
          {{ item }}<i>✦</i>
        </span>
      </div>
    </div>

    <!-- ============ FEATURES ============ -->
    <section id="capabilities" class="section cv-auto">
      <div class="container">
        <header class="section__head" v-reveal>
          <p class="eyebrow">能做什么</p>
          <h2 class="section-title">情绪需要被安置，<br class="br-wide" />而不是被管理</h2>
          <p class="section-sub">三件小事，构成一个可以持续使用的自我照顾循环。</p>
        </header>

        <div class="features">
          <article
            v-for="(item, index) in features"
            :key="item.title"
            class="feature"
            :class="{ 'feature--lead': index === 0 }"
            v-spotlight
            v-reveal="{ delay: index * 100, variant: index === 0 ? 'depth' : 'right' }"
          >
            <span class="feature__glow" aria-hidden="true" />
            <span class="feature__index" aria-hidden="true">{{ item.index }}</span>
            <span class="feature__icon"><AppIcon :name="item.icon" :size="22" /></span>
            <span v-if="index === 0" class="feature__orbit" aria-hidden="true">
              <i /><i /><i />
            </span>
            <h3>{{ item.title }}</h3>
            <p>{{ item.text }}</p>
            <RouterLink :to="item.to" class="feature__link">
              {{ item.cta }}
              <AppIcon name="arrowRight" :size="15" />
            </RouterLink>
          </article>
        </div>
      </div>
    </section>

    <!-- ============ 现在就试 ============ -->
    <section id="try" class="section section--tools cv-auto">
      <div class="container">
        <header class="section__head section__head--center" v-reveal>
          <p class="eyebrow">现在就试</p>
          <h2 class="section-title">不用等有空，<br class="br-wide" />先给自己一分钟</h2>
          <p class="section-sub">记录一下此刻，或者跟着光晕呼吸。两件很小的事，都能让身体先松一点。</p>
        </header>

        <div class="tools">
          <article class="tool-card checkin" v-spotlight v-reveal="{ variant: 'scale' }">
            <header class="tool-card__head">
              <div>
                <p class="tool-card__eyebrow">今日情绪 · 一分钟打卡</p>
                <h3>此刻的心情，几分？</h3>
              </div>
              <span class="checkin__streak" :class="{ 'is-muted': streak === 0 }">
                <AppIcon name="flame" :size="14" />
                {{ streak > 0 ? `连续 ${streak} 天` : '从今天开始' }}
              </span>
            </header>

            <div class="checkin__moods" role="group" aria-label="选择此刻的情绪">
              <button
                v-for="option in moodOptions"
                :key="option.score"
                type="button"
                class="checkin__mood"
                :class="{ 'is-active': chosenScore === option.score }"
                :aria-pressed="chosenScore === option.score"
                :disabled="checkinBusy"
                @click="selectMood(option)"
              >
                <span class="checkin__emoji" aria-hidden="true">{{ option.emoji }}</span>
                <span class="checkin__label">{{ option.label }}</span>
              </button>
            </div>

            <p class="checkin__status" :class="{ 'is-saved': justSaved }" aria-live="polite">
              <AppIcon :name="justSaved ? 'check' : todayMood ? 'heart' : 'sparkles'" :size="14" />
              {{ checkinStatus }}
            </p>

            <div class="checkin__week">
              <div class="week" aria-hidden="true">
                <div
                  v-for="bar in weekBars"
                  :key="bar.key"
                  class="week__col"
                  :class="{ 'is-today': bar.isToday }"
                >
                  <span class="week__track">
                    <span
                      class="week__bar"
                      :class="{ 'is-empty': !bar.score }"
                      :style="{ '--level': bar.score }"
                    />
                  </span>
                  <span class="week__day">{{ bar.label }}</span>
                </div>
              </div>
              <p class="checkin__week-meta">
                <span class="checkin__week-count">近 7 天记录了 <strong>{{ weekCount }}</strong> 天</span>
                <span class="checkin__week-note">{{ weekCount >= 5 ? '保持得很好' : '不用每天都很好' }}</span>
              </p>
            </div>

            <footer class="checkin__foot">
              <RouterLink to="/front/mood" class="tool-card__link">
                写一条情绪日记
                <AppIcon name="arrowRight" :size="14" />
              </RouterLink>
              <span>已同步到服务器</span>
            </footer>
          </article>

          <article class="tool-card tool-card--breath" v-spotlight v-reveal="{ delay: 90, variant: 'scale' }">
            <header class="tool-card__head">
              <div>
                <p class="tool-card__eyebrow">4-7-8 呼吸 · 约 1 分钟</p>
                <h3>让心跳，慢一点</h3>
              </div>
            </header>

            <LazyMount class="tool-card__breath" :min-height="170">
              <BreathingCoach compact />
            </LazyMount>

            <p class="breath__aside">
              <AppIcon name="leaf" :size="14" />
              吸气 4 秒、屏息 7 秒、呼气 8 秒。三轮为一组；不舒服就停下来，回到自然呼吸。
            </p>
          </article>
        </div>

        <p class="tools__note" v-reveal>
          以上是临床常用的放松方法，是日常陪伴工具，不能替代专业诊断与治疗。
        </p>
      </div>
    </section>

    <!-- ============ 精选阅读 ============ -->
    <section class="section section--reads cv-auto">
      <div class="container">
        <header class="section__head section__head--row" v-reveal>
          <div>
            <p class="eyebrow">精选阅读</p>
            <h2 class="section-title">把「别想太多」，<br class="br-wide" />换成一个具体的方法</h2>
          </div>
          <RouterLink to="/front/articles" class="section__link">
            全部文章
            <AppIcon name="arrowRight" :size="15" />
          </RouterLink>
        </header>

        <p v-if="articlesLoading" class="reads__note">正在加载文章…</p>
        <p v-else-if="!featuredArticles.length" class="reads__note">
          暂时还没有已发布文章，稍后再来看看。
        </p>

        <div v-else class="reads">
          <RouterLink
            v-for="(article, index) in featuredArticles"
            :key="article.id"
            :to="`/front/articles?article=${article.id}`"
            class="read-card"
            v-spotlight
            v-reveal="{ delay: index * 90 }"
          >
            <span class="read-card__cover" :class="`read-card__cover--${article.cover}`">
              <span class="read-card__cat">{{ article.category }}</span>
              <span class="read-card__glyph" aria-hidden="true">{{ article.title.slice(0, 1) }}</span>
              <span class="read-card__ring" aria-hidden="true" />
            </span>

            <div class="read-card__body">
              <h3>{{ article.title }}</h3>
              <p>{{ article.summary }}</p>
              <footer>
                <span>{{ article.minutes }} 分钟 · {{ article.author }}</span>
                <span class="read-card__go">
                  阅读
                  <AppIcon name="arrowRight" :size="14" />
                </span>
              </footer>
            </div>
          </RouterLink>
        </div>
      </div>
    </section>

    <!-- ============ CTA ============ -->
    <section class="section section--cta cv-auto">
      <div class="container">
        <div class="cta" v-reveal="{ variant: 'scale' }">
          <span class="cta__aurora" aria-hidden="true" />
          <span class="cta__grid" aria-hidden="true" />
          <span class="cta__glyph" aria-hidden="true">听</span>
          <div class="cta__copy">
            <p class="eyebrow">今晚，先照顾自己</p>
            <h2>不用等状态好了才开始</h2>
            <p>从一句「我最近有点累」开始，就够了。</p>
          </div>
          <div class="cta__actions">
            <RouterLink to="/front/chat" class="btn btn--primary">
              开始倾诉
              <AppIcon name="arrowRight" :size="17" />
            </RouterLink>
            <RouterLink to="/front/mood" class="btn btn--ghost">写一条情绪日记</RouterLink>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
// ============================================================
// HERO
// ============================================================
.hero {
  position: relative;
  display: flex;
  align-items: center;
  min-height: calc(100svh - 76px);
  padding: 76px 0 104px;
  overflow: hidden;
  isolation: isolate;
}

// 顶部垂直光柱，给首屏一个视觉锚点
.hero__beam {
  position: absolute;
  top: -20%;
  left: 42%;
  width: 1px;
  height: 84%;
  background: linear-gradient(180deg, transparent, var(--fx-neon-soft), transparent);
  box-shadow: 0 0 60px 14px var(--fx-glow-amber);
  opacity: 0.55;
  transform: rotate(14deg);
  pointer-events: none;

  &::after {
    content: '';
    position: absolute;
    top: 18%;
    left: -0.5px;
    width: 2px;
    height: 30%;
    background: linear-gradient(180deg, transparent, var(--fx-neon), transparent);
    opacity: 0.7;
    animation: hero-scan 7s $ease-out infinite;
  }
}

// 背景层：巨字水印 + 三颗景深光球 + 扫描线
.hero__ambient {
  position: absolute;
  inset: 0;
  z-index: -1;
  overflow: hidden;
  pointer-events: none;
}

.hero__watermark {
  position: absolute;
  top: 8%;
  right: -3%;
  display: block;
  color: transparent;
  font-family: $font-display;
  font-size: clamp(220px, 30vw, 460px);
  font-weight: 600;
  line-height: 0.86;
  letter-spacing: -0.06em;
  -webkit-text-stroke: 1px color-mix(in srgb, var(--warm-brand) 12%, transparent);
  opacity: 0.9;
  translate: var(--parallax-x, 0) var(--parallax-y, 0);
  user-select: none;
}

.hero__orb {
  position: absolute;
  display: block;
  border-radius: 50%;
  translate: var(--parallax-x, 0) var(--parallax-y, 0);
  // breathe 动画本身已经让浏览器把光斑提升为合成层，不再常驻 will-change

  &--a {
    top: 12%;
    left: -8%;
    width: 340px;
    height: 340px;
    background: radial-gradient(circle, var(--fx-glow-mint), transparent 68%);
    filter: blur(18px);
    animation: breathe 11s ease-in-out infinite;
  }

  &--b {
    right: 6%;
    bottom: 4%;
    width: 420px;
    height: 420px;
    background: radial-gradient(circle, var(--fx-glow-lilac), transparent 68%);
    filter: blur(26px);
    animation: breathe 15s ease-in-out 1.4s infinite;
  }

  &--c {
    top: 42%;
    left: 46%;
    width: 180px;
    height: 180px;
    background: radial-gradient(circle, var(--fx-glow-amber), transparent 70%);
    filter: blur(14px);
    animation: float-soft 9s ease-in-out infinite;
  }
}

.hero__scan {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 0%, var(--fx-scan) 49.8%, transparent 50.4%);
  background-size: 100% 220%;
  opacity: 0.22;
  animation: fx-scanline 9s linear infinite;
  mask-image: linear-gradient(90deg, transparent, #000 20%, #000 80%, transparent);
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 20%, #000 80%, transparent);
}

@keyframes hero-scan {
  0% {
    transform: translateY(-120%);
    opacity: 0;
  }

  20% {
    opacity: 0.8;
  }

  80% {
    opacity: 0.8;
  }

  100% {
    transform: translateY(240%);
    opacity: 0;
  }
}

.hero__grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1.04fr 0.96fr;
  align-items: center;
  gap: 72px;
}

.hero__copy {
  min-width: 0;
}

// —— 首屏文案入场：一次性编排，总时长控制在 1s 内 ——
@keyframes hero-rise {
  from {
    opacity: 0;
    transform: translateY(20px);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes line-up {
  from {
    transform: translateY(108%);
  }

  to {
    transform: none;
  }
}

.hero__pill,
.hero__lede,
.hero__actions,
.hero__trust {
  animation: hero-rise 0.8s $ease-out-expo both;
}

.hero__pill {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  margin: 0;
  padding: 7px 14px 7px 10px;
  border: 1px solid var(--fx-rim-soft);
  border-radius: $radius-pill;
  background: var(--fx-glass-strong);
  color: var(--warm-text-regular);
  font-size: 11.5px;
  letter-spacing: 0.08em;
  box-shadow:
    inset 0 1px 0 var(--fx-rim),
    0 8px 22px -18px var(--fx-shadow);
  animation-delay: 60ms;
}

.hero__pulse {
  position: relative;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--warm-brand);
  box-shadow: 0 0 0 3px var(--fx-neon-soft);

  &::after {
    content: '';
    position: absolute;
    inset: -3px;
    border: 1px solid var(--fx-neon);
    border-radius: 50%;
    animation: pulse-ring 2.6s $ease-out infinite;
  }
}

.hero__title {
  margin: 28px 0 26px;
  font-family: $font-display;
  font-size: clamp(40px, 4.7vw, 66px);
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: -0.04em;
  color: var(--warm-text);
}

.hero__line {
  display: block;
  overflow: hidden;
  padding-bottom: 0.1em;
  white-space: nowrap;

  > span {
    display: block;
    animation: line-up 1.05s $ease-out-expo both;
  }

  &:nth-child(1) > span {
    animation-delay: 120ms;
  }

  &:nth-child(2) > span {
    animation-delay: 240ms;
  }
}

.hero__title em {
  position: relative;
  font-style: normal;
  background: var(--warm-gradient);
  background-size: 200% auto;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: gradient-pan 7s ease-in-out infinite;
}

.hero__lede {
  max-width: 480px;
  margin: 0 0 34px;
  color: var(--warm-text-muted);
  font-size: 15.5px;
  line-height: 1.95;
  animation-delay: 320ms;
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: $space-3;
  animation-delay: 400ms;
}

// —— 按钮 ——
.btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 52px;
  padding: 0 26px;
  overflow: hidden;
  border-radius: $radius-pill;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.01em;
  transition:
    transform 0.28s $ease-out,
    box-shadow 0.28s $ease-out,
    border-color 0.28s $ease-out,
    background 0.28s $ease-out;

  svg {
    transition: transform 0.28s $ease-out;
  }

  &:hover svg {
    transform: translateX(3px);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }

  &:active {
    transform: translateY(0) scale(0.98);
  }
}

.btn--primary {
  background: linear-gradient(120deg, var(--warm-brand-strong), var(--warm-brand));
  color: var(--warm-on-brand);
  box-shadow:
    0 20px 44px -22px var(--fx-neon-soft),
    0 0 0 1px var(--fx-rim-soft) inset,
    inset 0 1px 0 var(--warm-rim-28);

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: inherit;
    background: var(--fx-aurora);
    opacity: 0;
    transition: opacity 0.3s $ease-out;
    pointer-events: none;
  }

  &::after {
    content: '';
    position: absolute;
    top: -40%;
    left: 0;
    width: 42%;
    height: 180%;
    background: linear-gradient(90deg, transparent, var(--warm-rim-34), transparent);
    transform: translateX(-140%) rotate(8deg);
    opacity: 0;
    pointer-events: none;
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow:
      0 26px 52px -22px var(--fx-neon-soft),
      0 0 30px -6px var(--fx-neon-soft),
      inset 0 1px 0 var(--warm-rim-36);

    &::before {
      opacity: 0.22;
    }

    &::after {
      opacity: 1;
      animation: button-shine 0.85s $ease-out;
    }
  }
}

.btn--ghost {
  border: 1px solid var(--fx-rim-soft);
  background: var(--fx-glass-strong);
  color: var(--warm-text);
  box-shadow: inset 0 1px 0 var(--fx-rim);

  &:hover {
    transform: translateY(-2px);
    border-color: var(--fx-neon-soft);
    background: color-mix(in srgb, var(--warm-brand) 12%, var(--fx-glass-strong));
    box-shadow:
      0 18px 40px -28px var(--fx-neon-soft),
      inset 0 1px 0 var(--fx-rim);
  }
}

.hero__trust {
  display: flex;
  flex-wrap: wrap;
  gap: $space-5;
  margin: 34px 0 0;
  padding: 0;
  list-style: none;
  color: var(--warm-text-muted);
  font-size: 12.5px;
  animation-delay: 480ms;

  li {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  svg {
    color: var(--warm-brand);
    filter: drop-shadow(0 0 6px var(--fx-neon-soft));
  }
}

// —— 首屏舞台 ——
.hero__stage {
  position: relative;
  // 上下留出浮动卡片的呼吸位，避免遮住预览卡里的标签与快捷回复
  padding: 52px 12px;
  perspective: 1200px;
  animation: hero-rise 1s 260ms $ease-out-expo both;

  // 舞台底部的一层透视网格，让预览卡像悬在空间里
  &::after {
    content: '';
    position: absolute;
    right: -10%;
    bottom: -4%;
    left: -10%;
    height: 44%;
    z-index: 0;
    background-image:
      linear-gradient(to right, var(--fx-grid) 1px, transparent 1px),
      linear-gradient(to bottom, var(--fx-grid) 1px, transparent 1px);
    background-size: 48px 48px;
    opacity: 0.55;
    transform: perspective(460px) rotateX(64deg);
    transform-origin: 50% 100%;
    mask-image: radial-gradient(ellipse 60% 100% at 50% 100%, #000 6%, transparent 72%);
    -webkit-mask-image: radial-gradient(ellipse 60% 100% at 50% 100%, #000 6%, transparent 72%);
    pointer-events: none;
  }
}

.stage__halo {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 540px;
  height: 540px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: radial-gradient(circle, var(--fx-glow-mint), var(--fx-glow-amber) 44%, transparent 66%);
  filter: blur(10px);
  animation: breathe 9s ease-in-out infinite;
  pointer-events: none;
}

.stage__ring {
  position: absolute;
  top: 50%;
  left: 50%;
  border-radius: 50%;
  pointer-events: none;
}

.stage__ring--a {
  width: 430px;
  height: 430px;
  margin: -215px 0 0 -215px;
  border: 1px dashed color-mix(in srgb, var(--warm-brand) 28%, transparent);
  animation: spin-slow 44s linear infinite;
}

.stage__ring--b {
  width: 540px;
  height: 540px;
  margin: -270px 0 0 -270px;
  border: 1px solid color-mix(in srgb, var(--warm-lilac) 24%, transparent);
  animation: spin-slow 68s linear reverse infinite;
}

.preview {
  position: relative;
  z-index: 2;
  padding: 22px;
  border: 1px solid var(--fx-rim-soft);
  border-radius: $radius-lg;
  background:
    linear-gradient(165deg, var(--fx-glass-strong), var(--fx-glass)),
    var(--warm-paper);
  box-shadow:
    0 50px 110px -50px var(--fx-shadow),
    0 0 60px -34px var(--fx-neon-soft),
    inset 0 1px 0 var(--fx-rim);

  // 全息描边：沿卡片边缘缓慢流动的极光
  &::before {
    content: '';
    position: absolute;
    inset: -1px;
    z-index: 0;
    padding: 1px;
    border-radius: inherit;
    background: conic-gradient(
      from 120deg,
      transparent 0deg,
      var(--fx-neon) 46deg,
      transparent 120deg,
      transparent 210deg,
      var(--fx-neon-2) 272deg,
      transparent 336deg
    );
    opacity: 0.6;
    -webkit-mask:
      linear-gradient(#000 0 0) content-box,
      linear-gradient(#000 0 0);
    -webkit-mask-composite: xor;
    mask-composite: exclude;
    animation: spin-slow 18s linear infinite;
    pointer-events: none;
  }

  > * {
    position: relative;
    z-index: 1;
  }
}

.preview__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--warm-line);
}

.preview__avatar {
  position: relative;
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  flex: none;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--warm-brand-strong), var(--warm-sage-light));
  color: var(--warm-on-brand);
  font-family: $font-display;
  font-size: 17px;
  font-weight: 700;
  box-shadow: 0 10px 22px -12px rgba(112, 80, 86, 0.7);
}

.preview__id {
  display: grid;
  line-height: 1.3;

  strong {
    color: var(--warm-text);
    font-size: 14px;
  }

  small {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    color: var(--warm-text-muted);
    font-size: 11px;
  }
}

.preview__online {
  position: relative;
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--warm-brand);
  box-shadow: 0 0 0 3px var(--fx-neon-soft);

  &::after {
    content: '';
    position: absolute;
    inset: -2px;
    border: 1px solid var(--fx-neon);
    border-radius: 50%;
    animation: pulse-ring 2.2s $ease-out infinite;
  }
}

.preview__badge {
  margin-left: auto;
  padding: 4px 10px;
  border: 1px solid var(--fx-neon-soft);
  border-radius: $radius-pill;
  background: color-mix(in srgb, var(--warm-brand) 10%, transparent);
  color: var(--warm-brand-deep);
  font-size: 10.5px;
  letter-spacing: 0.06em;
}

.preview__bubbles {
  display: grid;
  gap: 11px;
  padding: 18px 0 16px;
}

@keyframes bubble-in {
  from {
    opacity: 0;
    transform: translateY(10px) scale(0.97);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

.bubble {
  max-width: 88%;
  min-height: 40px;
  margin: 0;
  padding: 11px 14px;
  border-radius: 16px;
  font-size: 13px;
  line-height: 1.75;
  animation: bubble-in 0.6s $ease-out-expo both;
}

.bubble--d1 {
  animation-delay: 700ms;
}

.bubble--d2 {
  animation-delay: 1000ms;
}

.bubble--d3 {
  animation-delay: 1300ms;
}

.bubble--ai {
  justify-self: start;
  border: 1px solid var(--warm-line);
  border-bottom-left-radius: 5px;
  background: var(--fx-glass-strong);
  color: var(--warm-text-regular);
  box-shadow: 0 8px 20px -18px var(--fx-shadow);
}

.bubble--user {
  justify-self: end;
  border-bottom-right-radius: 5px;
  background: linear-gradient(135deg, var(--warm-brand), var(--warm-brand-strong));
  color: var(--warm-on-brand);
  font-weight: 500;
  box-shadow: 0 12px 26px -16px var(--fx-neon-soft);
}

.bubble__caret {
  display: inline-block;
  width: 2px;
  height: 14px;
  margin-left: 2px;
  border-radius: 2px;
  background: var(--warm-brand);
  vertical-align: -2px;
  animation: caret-blink 0.9s steps(1) infinite;
}

.bubble__pending {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 18px;

  i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--warm-text-muted);
    animation: blink 1.2s infinite;

    &:nth-child(2) {
      animation-delay: 0.15s;
    }

    &:nth-child(3) {
      animation-delay: 0.3s;
    }
  }
}

.preview__chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;

  span,
  a {
    padding: 6px 11px;
    border: 1px solid var(--warm-line);
    border-radius: $radius-pill;
    background: var(--warm-paper-72);
    color: var(--warm-text-muted);
    font-size: 11.5px;
    transition:
      border-color 0.2s $ease-out,
      background 0.2s $ease-out,
      color 0.2s $ease-out;
  }

  span:hover {
    border-color: var(--fx-neon-soft);
    color: var(--warm-text-regular);
  }
}

.preview__chips-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-color: var(--fx-neon-soft) !important;
  background: color-mix(in srgb, var(--warm-brand) 10%, transparent);
  color: var(--warm-brand-deep) !important;

  &:hover {
    background: color-mix(in srgb, var(--warm-brand) 18%, transparent);
  }
}

.float-card {
  position: absolute;
  z-index: 3;
  padding: 12px 16px;
  border: 1px solid var(--fx-rim-soft);
  border-radius: $radius-sm;
  background: var(--fx-glass-strong);
  box-shadow:
    0 26px 54px -32px var(--fx-shadow),
    0 0 30px -20px var(--fx-neon-soft),
    inset 0 1px 0 var(--fx-rim);
  font-size: 12px;
  color: var(--warm-text-regular);
}

.float-card--mood {
  top: 0;
  right: -10px;
  display: flex;
  align-items: baseline;
  gap: 6px;
  animation: float-soft 7s ease-in-out infinite;

  span {
    color: var(--warm-text-muted);
  }

  strong {
    font-family: $font-display;
    font-size: 22px;
    font-weight: 600;
    color: var(--warm-brand);
  }

  em {
    color: var(--warm-brand);
    font-size: 11px;
    font-style: normal;
  }
}

.float-card--safe {
  bottom: 0;
  left: -16px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  animation: float-soft 8s ease-in-out 0.8s infinite;

  svg {
    color: var(--warm-brand);
  }
}

.hero__scroll {
  position: absolute;
  bottom: 26px;
  left: 50%;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  transform: translateX(-50%);
  color: var(--warm-text-muted);
  font-size: 10px;
  letter-spacing: 0.24em;
  transition: color 0.2s $ease-out;

  &:hover {
    color: var(--warm-brand);
  }
}

.hero__scroll-line {
  position: relative;
  display: block;
  width: 1px;
  height: 38px;
  overflow: hidden;
  background: var(--warm-line);

  i {
    position: absolute;
    inset: 0 0 auto;
    display: block;
    width: 100%;
    height: 60%;
    background: linear-gradient(180deg, transparent, var(--warm-brand));
    animation: scroll-cue 2.4s $ease-out infinite;
  }
}

// ============================================================
// TICKER
// ============================================================
.ticker {
  overflow: hidden;
  border-block: 1px solid var(--warm-line);
  background: var(--fx-glass-strong);
  mask-image: linear-gradient(90deg, transparent, #000 7%, #000 93%, transparent);
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 7%, #000 93%, transparent);

  &:hover .ticker__track {
    animation-play-state: paused;
  }
}

.ticker__track {
  display: flex;
  width: max-content;
  animation: marquee 42s linear infinite;
}

.ticker__item {
  display: inline-flex;
  align-items: center;
  gap: 20px;
  padding: 17px 28px;
  color: var(--warm-text-muted);
  font-size: 11.5px;
  letter-spacing: 0.2em;
  white-space: nowrap;

  i {
    color: var(--fx-neon);
    font-size: 9px;
    font-style: normal;
    opacity: 0.7;
    text-shadow: 0 0 10px var(--fx-neon-soft);
  }
}

// ============================================================
// 通用区块
// ============================================================
.section {
  padding: 100px 0;
  scroll-margin-top: 104px;

  &--cta {
    padding-top: 40px;
    padding-bottom: 110px;
  }
}

.section__head {
  max-width: 680px;
  margin-bottom: $space-7;
}

.section__head--center {
  max-width: 640px;
  margin-inline: auto;
  text-align: center;

  .eyebrow::before {
    display: none;
  }

  .section-sub {
    margin-inline: auto;
  }
}

.section__head--row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: $space-5;
  max-width: none;
}

.section__link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  flex: none;
  padding: 9px 16px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-pill;
  background: var(--warm-paper-70);
  color: var(--warm-brand-strong);
  font-size: 12.5px;
  font-weight: 600;
  white-space: nowrap;
  transition:
    border-color 0.2s $ease-out,
    background 0.2s $ease-out,
    transform 0.2s $ease-out;

  svg {
    transition: transform 0.3s $ease-out;
  }

  &:hover {
    transform: translateY(-1px);
    border-color: color-mix(in srgb, var(--warm-brand) 40%, transparent);
    background: color-mix(in srgb, var(--warm-brand) 10%, var(--warm-paper-80));

    svg {
      transform: translateX(3px);
    }
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

.br-wide {
  display: none;
}

.features {
  display: grid;
  grid-template-columns: 1.14fr 0.86fr;
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: $space-5;
}

.feature {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 30px 28px 28px;
  overflow: hidden;
  border: 1px solid var(--fx-rim-soft);
  border-radius: $radius-lg;
  background: linear-gradient(160deg, var(--fx-glass-strong), var(--fx-glass));
  box-shadow:
    inset 0 1px 0 var(--fx-rim),
    0 22px 48px -44px var(--fx-shadow);
  transition:
    transform 0.4s $ease-out-expo,
    border-color 0.4s $ease-out,
    box-shadow 0.4s $ease-out;

  &::after {
    content: '';
    position: absolute;
    inset: auto 0 0;
    height: 1px;
    background: linear-gradient(90deg, transparent, var(--fx-neon), transparent);
    opacity: 0;
    transition: opacity 0.4s $ease-out;
  }

  &:hover {
    transform: translateY(-6px);
    border-color: var(--fx-neon-soft);
    box-shadow:
      0 38px 74px -46px var(--fx-shadow),
      0 0 44px -30px var(--fx-neon-soft),
      inset 0 1px 0 var(--fx-rim);

    &::after {
      opacity: 0.8;
    }

    .feature__icon {
      transform: translateY(-3px) rotate(-5deg);
      border-color: var(--fx-neon-soft);
      background: linear-gradient(135deg, var(--fx-glow-mint), var(--fx-glow-amber));
    }

    .feature__orbit {
      opacity: 1;
    }
  }

  h3 {
    margin: 22px 0 10px;
    font-size: 18px;
    font-weight: 600;
    letter-spacing: -0.01em;
  }

  p {
    flex: 1;
    margin: 0 0 24px;
    color: var(--warm-text-muted);
    font-size: 13.5px;
    line-height: 1.85;
  }

  // 文字始终盖在光晕与轨道装饰之上，保证可读性
  > h3,
  > p,
  .feature__link {
    position: relative;
    z-index: 2;
  }
}

// 首卡：不对称布局里的视觉主位
.feature--lead {
  grid-row: span 2;
  justify-content: flex-end;
  padding: 40px 36px 36px;

  h3 {
    max-width: 9em;
    margin-top: 30px;
    font-family: $font-display;
    font-size: clamp(24px, 2.2vw, 32px);
    font-weight: 600;
    line-height: 1.24;
    letter-spacing: -0.03em;
  }

  p {
    max-width: 30em;
    flex: 0;
    font-size: 14px;
    line-height: 1.95;
  }

  .feature__index {
    font-size: 104px;
  }
}

.feature__glow {
  position: absolute;
  top: -28%;
  left: -18%;
  width: 76%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: radial-gradient(circle, var(--fx-glow-mint), transparent 68%);
  opacity: 0.55;
  pointer-events: none;
  transition: opacity 0.5s $ease-out;
}

.feature:hover .feature__glow {
  opacity: 0.9;
}

// 首卡右侧的星座轨道装饰
.feature__orbit {
  position: absolute;
  top: 8%;
  right: -12%;
  width: 62%;
  aspect-ratio: 1;
  border: 1px dashed color-mix(in srgb, var(--warm-brand) 26%, transparent);
  border-radius: 50%;
  opacity: 0.7;
  transition:
    transform 0.6s $ease-out-expo,
    opacity 0.5s $ease-out;
  animation: spin-slow 46s linear infinite;
  pointer-events: none;

  i {
    position: absolute;
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--fx-neon);
    box-shadow: 0 0 12px var(--fx-neon-soft);

    &:nth-child(1) {
      top: 8%;
      left: 34%;
    }

    &:nth-child(2) {
      top: 54%;
      right: -3px;
      width: 5px;
      height: 5px;
      background: var(--fx-neon-2);
    }

    &:nth-child(3) {
      bottom: 6%;
      left: 22%;
      width: 4px;
      height: 4px;
      opacity: 0.7;
    }
  }
}

.feature__index {
  position: absolute;
  top: 14px;
  right: 22px;
  color: color-mix(in srgb, var(--warm-brand) 12%, transparent);
  font-family: $font-display;
  font-size: 68px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.04em;
  pointer-events: none;
}

.feature__icon {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border: 1px solid var(--fx-neon-soft);
  border-radius: 16px;
  background: linear-gradient(135deg, var(--fx-glow-mint), var(--fx-glow-amber));
  color: var(--warm-brand-deep);
  box-shadow:
    inset 0 1px 0 var(--fx-rim),
    0 12px 24px -18px var(--fx-neon-soft);
  transition:
    transform 0.4s $ease-spring,
    background 0.4s $ease-out,
    border-color 0.4s $ease-out;
}

.feature__link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  color: var(--warm-brand-strong);
  font-size: 13px;
  font-weight: 600;
  transition: color 0.2s $ease-out;

  svg {
    transition: transform 0.3s $ease-out;
  }

  &:hover {
    color: var(--warm-apricot);

    svg {
      transform: translateX(4px);
    }
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

// ============================================================
// 现在就试：打卡 + 呼吸
// ============================================================
.section--tools {
  position: relative;
  padding-top: 96px;

  &::before {
    content: '';
    position: absolute;
    inset: 18px 0 0;
    background: radial-gradient(
      58% 72% at 50% 0%,
      color-mix(in srgb, var(--warm-brand) 8%, transparent),
      transparent 72%
    );
    pointer-events: none;
  }

  .container {
    position: relative;
  }
}

.tools {
  display: grid;
  grid-template-columns: 1.08fr 0.92fr;
  gap: $space-5;
  align-items: stretch;
}

.tool-card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 30px;
  overflow: hidden;
  border: 1px solid var(--fx-rim-soft);
  border-radius: $radius-lg;
  background:
    radial-gradient(circle at 92% 0%, var(--fx-glow-mint), transparent 46%),
    linear-gradient(165deg, var(--fx-glass-strong), var(--fx-glass));
  box-shadow:
    inset 0 1px 0 var(--fx-rim),
    0 32px 64px -52px var(--fx-shadow);
  transition:
    border-color 0.35s $ease-out,
    box-shadow 0.35s $ease-out;

  &:hover {
    border-color: var(--fx-neon-soft);
    box-shadow:
      inset 0 1px 0 var(--fx-rim),
      0 32px 64px -52px var(--fx-shadow),
      0 0 42px -30px var(--fx-neon-soft);
  }
}

.tool-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $space-4;
  margin-bottom: 22px;

  h3 {
    margin: 7px 0 0;
    font-family: $font-display;
    font-size: 24px;
    font-weight: 600;
    line-height: 1.3;
    letter-spacing: -0.02em;
  }
}

.tool-card__eyebrow {
  margin: 0;
  color: var(--warm-text-muted);
  font-size: 10.5px;
  letter-spacing: 0.16em;
}

.checkin__streak {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: none;
  margin-top: 2px;
  padding: 6px 11px;
  border: 1px solid color-mix(in srgb, var(--warm-apricot) 28%, transparent);
  border-radius: $radius-pill;
  background: color-mix(in srgb, var(--warm-apricot) 10%, transparent);
  color: var(--warm-apricot);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;

  &.is-muted {
    border-color: var(--warm-line);
    background: var(--warm-paper-70);
    color: var(--warm-text-muted);
  }
}

.checkin__moods {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 6px;
  margin-bottom: 16px;
}

.checkin__mood {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 11px 3px 9px;
  border: 1px solid transparent;
  border-radius: 15px;
  background: transparent;
  color: var(--warm-text-muted);
  font: inherit;
  font-size: 11px;
  cursor: pointer;
  transition:
    transform 0.22s $ease-out,
    background 0.22s $ease-out,
    border-color 0.22s $ease-out,
    color 0.22s $ease-out;

  &:hover {
    transform: translateY(-2px);
    border-color: var(--warm-line);
    background: var(--warm-paper-70);
    color: var(--warm-text);
  }

  &:active {
    transform: translateY(0) scale(0.97);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }

  &.is-active {
    border-color: color-mix(in srgb, var(--warm-brand) 32%, transparent);
    background: color-mix(in srgb, var(--warm-brand) 10%, var(--warm-paper-90));
    color: var(--warm-brand-strong);
    font-weight: 600;

    .checkin__emoji {
      transform: scale(1.08);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--warm-brand) 22%, transparent);
    }
  }
}

.checkin__emoji {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--fx-glass-strong);
  box-shadow: inset 0 0 0 1px var(--warm-line);
  font-size: 22px;
  line-height: 1;
  transition:
    transform 0.28s $ease-spring,
    box-shadow 0.28s $ease-out;
}

.checkin__status {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 20px;
  margin: 0 0 20px;
  color: var(--warm-text-muted);
  font-size: 11.5px;
  line-height: 1.65;

  svg {
    flex: none;
    color: var(--warm-brand);
  }

  &.is-saved {
    color: var(--warm-brand-strong);
    animation: status-pop 0.45s $ease-out;
  }
}

@keyframes status-pop {
  from {
    opacity: 0.2;
    transform: translateY(5px);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

.checkin__week {
  margin-top: auto;
  padding: 16px 18px 13px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-card;
  background: color-mix(in srgb, var(--fx-glass-strong) 82%, transparent);
}

.week {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 5px;
  height: 78px;
}

.week__col {
  display: grid;
  justify-items: center;
  align-content: end;
  gap: 7px;
  height: 100%;
}

.week__track {
  display: flex;
  align-items: flex-end;
  width: 100%;
  height: 56px;
}

.week__bar {
  display: block;
  width: min(18px, 100%);
  height: calc(4px + var(--level, 0) * 9px);
  margin-inline: auto;
  border-radius: 6px 6px 4px 4px;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--warm-brand) 78%, transparent),
    color-mix(in srgb, var(--warm-brand) 32%, transparent)
  );
  transition: height 0.4s $ease-out-expo;

  &.is-empty {
    height: 4px;
    background: var(--warm-line);
  }
}

.week__col.is-today {
  .week__bar {
    background: linear-gradient(180deg, var(--warm-brand-strong), var(--warm-brand));
    box-shadow: 0 8px 16px -10px rgba(112, 80, 86, 0.75);
  }

  .week__day {
    color: var(--warm-brand-strong);
    font-weight: 600;
  }
}

.week__day {
  color: var(--warm-text-muted);
  font-size: 10px;
  letter-spacing: 0.06em;
}

.checkin__week-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $space-3;
  margin: 12px 0 0;
  padding-top: 11px;
  border-top: 1px dashed var(--warm-line);
  color: var(--warm-text-muted);
  font-size: 10.5px;

  strong {
    color: var(--warm-brand-strong);
    font-variant-numeric: tabular-nums;
  }
}

.checkin__week-count {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.checkin__week-note {
  opacity: 0.82;
  white-space: nowrap;
}

.checkin__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $space-3;
  margin-top: 16px;
  color: var(--warm-text-muted);
  font-size: 10.5px;
}

.tool-card__link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--warm-brand-strong);
  font-size: 12.5px;
  font-weight: 600;

  svg {
    transition: transform 0.3s $ease-out;
  }

  &:hover svg {
    transform: translateX(3px);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

.tool-card--breath {
  justify-content: space-between;
  text-align: center;
  background:
    radial-gradient(circle at 50% 46%, var(--fx-glow-mint), transparent 58%),
    linear-gradient(165deg, var(--fx-glass-strong), var(--fx-glass));

  .tool-card__head {
    text-align: left;
  }
}

.tool-card__breath {
  margin: 4px auto 10px;
}

.breath__aside {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 14px 0 0;
  padding: 12px 14px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-sm;
  background: var(--warm-paper-52);
  color: var(--warm-text-muted);
  font-size: 10.5px;
  line-height: 1.75;
  text-align: left;

  svg {
    flex: none;
    margin-top: 2px;
    color: var(--warm-brand);
  }
}

.tools__note {
  margin: 18px 0 0;
  color: var(--warm-text-muted);
  font-size: 10.5px;
  letter-spacing: 0.02em;
  text-align: center;
}

// ============================================================
// 精选阅读
// ============================================================
.section--reads {
  padding-top: 96px;
}

.reads__note {
  margin: 0;
  padding: 26px 0;
  color: var(--warm-text-muted);
  font-size: 13px;
  text-align: center;
}

.reads {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $space-5;
}

.read-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--fx-rim-soft);
  border-radius: $radius-lg;
  background: linear-gradient(168deg, var(--fx-glass-strong), var(--fx-glass));
  box-shadow:
    inset 0 1px 0 var(--fx-rim),
    0 22px 48px -46px var(--fx-shadow);
  transition:
    transform 0.4s $ease-out-expo,
    border-color 0.32s $ease-out,
    box-shadow 0.4s $ease-out;

  &:hover {
    transform: translateY(-6px);
    border-color: var(--fx-neon-soft);
    box-shadow:
      0 38px 76px -50px var(--fx-shadow),
      0 0 44px -30px var(--fx-neon-soft),
      inset 0 1px 0 var(--fx-rim);

    .read-card__glyph {
      transform: translateY(-4px) rotate(-4deg) scale(1.04);
    }

    .read-card__go svg {
      transform: translateX(3px);
    }
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

.read-card__cover {
  position: relative;
  display: block;
  height: 136px;
  overflow: hidden;
  border-bottom: 1px solid var(--warm-line);

  &--teal {
    background:
      radial-gradient(circle at 72% 24%, rgba(136, 104, 110, 0.34), transparent 58%),
      linear-gradient(135deg, rgba(136, 104, 110, 0.16), rgba(217, 169, 79, 0.06));
  }

  &--lilac {
    background:
      radial-gradient(circle at 72% 24%, rgba(196, 143, 154, 0.32), transparent 58%),
      linear-gradient(135deg, rgba(196, 143, 154, 0.18), rgba(136, 104, 110, 0.05));
  }

  &--sand {
    background:
      radial-gradient(circle at 72% 24%, rgba(209, 138, 92, 0.3), transparent 58%),
      linear-gradient(135deg, rgba(209, 138, 92, 0.16), rgba(217, 169, 79, 0.08));
  }
}

.read-card__cat {
  position: absolute;
  top: 14px;
  left: 14px;
  z-index: 1;
  padding: 4px 10px;
  border: 1px solid var(--fx-rim-soft);
  border-radius: $radius-pill;
  background: var(--fx-glass-strong);
  color: var(--warm-text-regular);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.read-card__glyph {
  position: absolute;
  right: 12px;
  bottom: -22px;
  color: color-mix(in srgb, var(--warm-brand) 12%, transparent);
  font-family: $font-display;
  font-size: 122px;
  font-weight: 600;
  line-height: 1;
  -webkit-text-stroke: 1px color-mix(in srgb, var(--warm-brand) 22%, transparent);
  transition: transform 0.5s $ease-out-expo;
  pointer-events: none;
  user-select: none;
}

.read-card__ring {
  position: absolute;
  top: 26px;
  right: 30px;
  width: 92px;
  height: 92px;
  border: 1px dashed color-mix(in srgb, var(--warm-brand) 24%, transparent);
  border-radius: 50%;
}

.read-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 20px 22px 18px;

  h3 {
    display: -webkit-box;
    overflow: hidden;
    margin: 0 0 9px;
    font-size: 17px;
    font-weight: 600;
    line-height: 1.5;
    letter-spacing: -0.01em;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  p {
    display: -webkit-box;
    flex: 1;
    overflow: hidden;
    margin: 0 0 16px;
    color: var(--warm-text-muted);
    font-size: 12.5px;
    line-height: 1.8;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 3;
  }

  footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $space-3;
    padding-top: 13px;
    border-top: 1px dashed var(--warm-line);
    color: var(--warm-text-muted);
    font-size: 10.5px;
  }
}

.read-card__go {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: none;
  color: var(--warm-brand-strong);
  font-weight: 600;
  white-space: nowrap;

  svg {
    transition: transform 0.3s $ease-out;
  }
}

// ============================================================
// CTA
// ============================================================
@property --cta-angle {
  syntax: '<angle>';
  initial-value: 0deg;
  inherits: false;
}

@keyframes cta-spin {
  to {
    --cta-angle: 360deg;
  }
}

.cta {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: $space-6;
  padding: 64px;
  overflow: hidden;
  border: 1px solid rgba(220, 197, 202, 0.2);
  border-radius: $radius-xl;
  background:
    radial-gradient(ellipse 60% 90% at 88% 8%, rgba(214, 181, 188, 0.22), transparent 62%),
    radial-gradient(ellipse 60% 80% at 4% 100%, rgba(219, 170, 180, 0.24), transparent 60%),
    linear-gradient(120deg, #1a1314, #0e0a0b 58%, #151011);
  box-shadow:
    0 50px 100px -60px rgba(0, 0, 0, 0.7),
    0 0 80px -50px rgba(214, 181, 188, 0.5),
    inset 0 1px 0 rgba(248, 241, 242, 0.14);

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    z-index: 1;
    padding: 1px;
    border-radius: inherit;
    background: conic-gradient(
      from var(--cta-angle),
      transparent 0deg,
      rgba(214, 181, 188, 0.9) 62deg,
      transparent 128deg,
      transparent 190deg,
      rgba(220, 197, 202, 0.85) 250deg,
      transparent 316deg,
      transparent 360deg
    );
    -webkit-mask:
      linear-gradient(#000 0 0) content-box,
      linear-gradient(#000 0 0);
    -webkit-mask-composite: xor;
    mask-composite: exclude;
    filter: drop-shadow(0 0 12px rgba(214, 181, 188, 0.28));
    animation: cta-spin 10s linear infinite;
    pointer-events: none;
  }

  .eyebrow {
    color: #dcc7a6;

    &::before {
      background: #d6b5bc;
    }
  }

  h2 {
    color: #f8f3ea;
  }

  p {
    color: rgba(246, 241, 231, 0.78);
  }

  .btn--ghost {
    border-color: rgba(248, 241, 242, 0.18);
    background: rgba(248, 241, 242, 0.06);
    color: #f4efe6;

    &:hover {
      border-color: rgba(214, 181, 188, 0.5);
      background: rgba(214, 181, 188, 0.12);
    }
  }
}

// 日间：CTA 跟随暖光主题，避免整块深夜色和奶油底割裂
html[data-theme='day'] .cta {
  border-color: rgba(112, 80, 86, 0.18);
  background:
    radial-gradient(ellipse 60% 90% at 88% 8%, rgba(136, 104, 110, 0.18), transparent 62%),
    radial-gradient(ellipse 60% 80% at 4% 100%, rgba(209, 138, 92, 0.16), transparent 60%),
    linear-gradient(120deg, #fffdf8, #f7efe2 58%, #f2e8d8);
  box-shadow:
    0 50px 100px -60px rgba(74, 64, 46, 0.3),
    0 0 80px -50px rgba(136, 104, 110, 0.32),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);

  &::before {
    background: conic-gradient(
      from var(--cta-angle),
      transparent 0deg,
      rgba(136, 104, 110, 0.72) 62deg,
      transparent 128deg,
      transparent 190deg,
      rgba(209, 138, 92, 0.62) 250deg,
      transparent 316deg,
      transparent 360deg
    );
    filter: drop-shadow(0 0 12px rgba(136, 104, 110, 0.16));
  }

  .eyebrow {
    color: var(--warm-brand-strong);

    &::before {
      background: var(--warm-brand);
    }
  }

  h2 {
    color: var(--warm-text);
  }

  p {
    color: var(--warm-text-regular);
  }

  .btn--ghost {
    border-color: rgba(112, 80, 86, 0.22);
    background: rgba(255, 253, 248, 0.66);
    color: var(--warm-text);

    &:hover {
      border-color: rgba(112, 80, 86, 0.5);
      background: rgba(136, 104, 110, 0.12);
    }
  }
}

html[data-theme='day'] .cta__grid {
  background-image:
    linear-gradient(to right, rgba(112, 80, 86, 0.1) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(112, 80, 86, 0.1) 1px, transparent 1px);
}

html[data-theme='day'] .cta__glyph {
  -webkit-text-stroke-color: rgba(112, 80, 86, 0.18);
}

html[data-theme='day'] .cta__aurora {
  opacity: 0.16;
}

// 内部流动极光
.cta__aurora {
  position: absolute;
  top: -60%;
  left: -10%;
  width: 70%;
  height: 220%;
  background: var(--fx-aurora);
  opacity: 0.22;
  filter: blur(60px);
  transform: rotate(-12deg);
  animation: cta-flow 18s ease-in-out infinite alternate;
  pointer-events: none;
}

@keyframes cta-flow {
  0% {
    transform: translate3d(-6%, 0, 0) rotate(-12deg) scaleY(0.9);
  }

  100% {
    transform: translate3d(22%, -4%, 0) rotate(-6deg) scaleY(1.1);
  }
}

// 内部透视网格
.cta__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, rgba(214, 181, 188, 0.12) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(214, 181, 188, 0.12) 1px, transparent 1px);
  background-size: 54px 54px;
  opacity: 0.5;
  mask-image: radial-gradient(ellipse 80% 100% at 90% 100%, #000, transparent 70%);
  -webkit-mask-image: radial-gradient(ellipse 80% 100% at 90% 100%, #000, transparent 70%);
  pointer-events: none;
}

.cta__glyph {
  position: absolute;
  right: 24%;
  bottom: -22%;
  color: transparent;
  font-family: $font-display;
  font-size: 260px;
  font-weight: 600;
  line-height: 1;
  -webkit-text-stroke: 1px rgba(214, 181, 188, 0.26);
  pointer-events: none;
  user-select: none;
  animation: glyph-drift 12s ease-in-out infinite;
}

@keyframes glyph-drift {
  0%,
  100% {
    transform: translate3d(0, 0, 0) rotate(0deg);
    opacity: 0.7;
  }

  50% {
    transform: translate3d(-6px, -10px, 0) rotate(-2deg);
    opacity: 1;
  }
}

.cta__copy {
  position: relative;
  z-index: 1;
  max-width: 520px;

  h2 {
    margin: 12px 0 10px;
    font-family: $font-display;
    font-size: clamp(28px, 3.4vw, 44px);
    font-weight: 600;
    letter-spacing: -0.025em;
    line-height: 1.18;
  }

  p {
    margin: 0;
    color: rgba(246, 241, 231, 0.78);
    font-size: 14px;
  }
}

.cta__actions {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: $space-3;
}

// ============================================================
// 响应式
// ============================================================
@include media-down($bp-lg) {
  .hero {
    min-height: auto;
    padding: 64px 0 88px;
  }

  // 单栏后光柱会横穿标题，收掉更干净
  .hero__beam {
    display: none;
  }

  .hero__grid {
    grid-template-columns: 1fr;
    gap: $space-7;
  }

  .hero__copy {
    max-width: 620px;
  }

  .hero__stage {
    width: min(520px, 100%);
    margin-inline: auto;
  }

  .hero__scroll {
    display: none;
  }

  .features {
    grid-template-columns: 1fr 1fr;
    grid-template-rows: none;
  }

  .tools {
    grid-template-columns: 1fr;
    max-width: 720px;
    margin-inline: auto;
  }

  .section__head--row {
    align-items: flex-start;
  }

  .reads {
    grid-template-columns: 1fr;
  }

  .read-card {
    flex-direction: row;
  }

  .read-card__cover {
    width: 216px;
    height: auto;
    min-height: 196px;
    flex: none;
    border-right: 1px solid var(--warm-line);
    border-bottom: 0;
  }

  .cta {
    padding: 44px;
  }

  .cta__glyph {
    font-size: 200px;
  }
}

@include media-down($bp-md) {
  .section {
    padding: 76px 0;

    &--cta {
      padding-bottom: 84px;
    }
  }

  .section__head--row {
    flex-direction: column;
    align-items: flex-start;
    gap: $space-4;
  }

  .br-wide {
    display: inline;
  }
}

@include media-down($bp-sm) {
  .section {
    scroll-margin-top: 152px;
  }

  .hero {
    padding: 44px 0 64px;
  }

  // 水印在窄屏容易压住文字，缩小并移到右上角
  .hero__watermark {
    top: 2%;
    right: -18%;
    font-size: 210px;
    opacity: 0.6;
  }

  .hero__orb--a {
    width: 220px;
    height: 220px;
  }

  .hero__orb--b {
    width: 260px;
    height: 260px;
  }

  .hero__orb--c {
    display: none;
  }

  .hero__title {
    font-size: clamp(32px, 9.4vw, 44px);
  }

  .hero__lede {
    font-size: 14.5px;
  }

  .hero__stage {
    padding: 8px 0;
  }

  .stage__ring {
    display: none;
  }

  .stage__halo {
    width: 380px;
    height: 380px;
  }

  .preview {
    padding: 16px;
  }

  // 小屏空间紧张，浮动卡片会压住预览内容，直接收掉
  .float-card {
    display: none;
  }

  .features {
    grid-template-columns: 1fr;
  }

  // ---- 首页工具卡 ----
  .tools {
    gap: $space-4;
  }

  .tool-card {
    padding: 22px 18px 20px;
  }

  .tool-card__head {
    margin-bottom: 18px;

    h3 {
      font-size: 21px;
    }
  }

  .checkin__streak {
    padding: 5px 9px;
    font-size: 10px;
  }

  .checkin__moods {
    gap: 3px;
  }

  .checkin__mood {
    padding: 9px 1px 7px;
    font-size: 10px;
  }

  .checkin__emoji {
    width: 40px;
    height: 40px;
    font-size: 20px;
  }

  .checkin__status {
    font-size: 11px;
  }

  .checkin__week {
    padding: 14px 12px 11px;
  }

  .checkin__week-note {
    display: none;
  }

  .checkin__foot > span {
    display: none;
  }

  .tools__note {
    padding-inline: 8px;
    line-height: 1.7;
  }

  // ---- 精选阅读 ----
  .read-card {
    flex-direction: column;
  }

  .read-card__cover {
    width: auto;
    height: 126px;
    min-height: 0;
    border-right: 0;
    border-bottom: 1px solid var(--warm-line);
  }

  .read-card__glyph {
    font-size: 108px;
  }

  .read-card__body {
    padding: 18px 18px 16px;
  }

  .ticker__item {
    padding: 14px 22px;
  }

  .cta {
    padding: 32px 22px;
  }

  .cta__actions {
    width: 100%;

    .btn {
      flex: 1;
    }
  }

  .section {
    padding: 60px 0;

    &--cta {
      padding-bottom: 68px;
    }
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero__pill,
  .hero__lede,
  .hero__actions,
  .hero__trust,
  .hero__line > span,
  .preview,
  .bubble,
  .ticker__track,
  .float-card,
  .stage__halo,
  .stage__ring,
  .cta::before,
  .cta__glyph,
  .hero__pulse::after,
  .preview__online::after,
  .hero__scroll-line i,
  .bubble__caret {
    animation: none !important;
  }

  .hero__line > span {
    transform: none;
  }
}
</style>
