<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { Article, ArticleCategory } from '@/types'
import { getArticle, listArticles } from '@/api/articles'
import AppIcon from '@/components/AppIcon.vue'
import { readJSON, writeJSON } from '@/utils/storage'

type CategoryFilter = ArticleCategory | '全部'
type SortKey = 'latest' | 'popular' | 'shortest'

interface Marks {
  bookmarks: number[]
  read: number[]
}

const STORAGE_KEY = 'mha-article-marks'

function loadMarks(): Marks {
  const stored = readJSON<Partial<Marks> | null>(STORAGE_KEY, null)
  const asIds = (value: unknown): number[] =>
    Array.isArray(value) ? value.filter((id): id is number => typeof id === 'number') : []
  return { bookmarks: asIds(stored?.bookmarks), read: asIds(stored?.read) }
}

const marks = ref<Marks>(loadMarks())

const bookmarkSet = computed(() => new Set(marks.value.bookmarks))
const readSet = computed(() => new Set(marks.value.read))

const published = ref<Article[]>([])
const loading = ref(true)

const featured = computed<Article | null>(
  () => [...published.value].sort((a, b) => b.views - a.views)[0] ?? null,
)

async function loadArticles(): Promise<void> {
  loading.value = true
  try {
    published.value = await listArticles({ sort: 'latest' })
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '文章加载失败，请稍后重试',
      customClass: 'warm-toast',
    })
  } finally {
    loading.value = false
  }
}

// ---- 筛选、搜索与排序 ----
const categories: CategoryFilter[] = ['全部', '焦虑', '睡眠', '关系', '自我成长']
const activeCategory = ref<CategoryFilter>('全部')
const keyword = ref('')
const sortKey = ref<SortKey>('latest')
const bookmarkOnly = ref(false)

const sortOptions: Array<{ key: SortKey; label: string }> = [
  { key: 'latest', label: '最新' },
  { key: 'popular', label: '最热' },
  { key: 'shortest', label: '最短' },
]

const counts = computed<Record<string, number>>(() => {
  const result: Record<string, number> = { 全部: published.value.length }
  for (const category of categories.slice(1)) result[category] = 0
  for (const article of published.value) {
    result[article.category] = (result[article.category] ?? 0) + 1
  }
  return result
})

const list = computed<Article[]>(() => {
  const key = keyword.value.trim().toLowerCase()

  const filtered = published.value.filter((article) => {
    if (activeCategory.value !== '全部' && article.category !== activeCategory.value) return false
    if (bookmarkOnly.value && !bookmarkSet.value.has(article.id)) return false
    if (!key) return true
    return [article.title, article.summary, article.author, article.category]
      .join(' ')
      .toLowerCase()
      .includes(key)
  })

  return filtered.sort((a, b) => {
    if (sortKey.value === 'popular') return b.views - a.views
    if (sortKey.value === 'shortest') return a.minutes - b.minutes || b.views - a.views
    return b.publishedAt.localeCompare(a.publishedAt)
  })
})

const isDefaultView = computed(
  () =>
    activeCategory.value === '全部' &&
    !keyword.value.trim() &&
    !bookmarkOnly.value,
)

const showFeatured = computed(() => isDefaultView.value && featured.value !== null)

const gridList = computed(() => {
  if (!showFeatured.value || !featured.value) return list.value
  return list.value.filter((article) => article.id !== featured.value?.id)
})

const emptyHint = computed(() => {
  if (bookmarkOnly.value) return '还没有收藏的文章。读到喜欢的，点一下书签就好。'
  if (keyword.value.trim()) return `没有找到和「${keyword.value.trim()}」相关的文章。`
  return '这个分类下暂时没有文章。'
})

function resetFilters(): void {
  activeCategory.value = '全部'
  keyword.value = ''
  bookmarkOnly.value = false
  sortKey.value = 'latest'
}

// ---- 收藏 / 已读，写进 localStorage ----
function persistMarks(): void {
  writeJSON(STORAGE_KEY, marks.value)
}

function isBookmarked(id: number): boolean {
  return bookmarkSet.value.has(id)
}

function isRead(id: number): boolean {
  return readSet.value.has(id)
}

function toggleBookmark(article: Article): void {
  const bookmarks = new Set(marks.value.bookmarks)
  if (bookmarks.has(article.id)) bookmarks.delete(article.id)
  else bookmarks.add(article.id)

  marks.value = { ...marks.value, bookmarks: [...bookmarks] }
  persistMarks()
}

function markRead(id: number): void {
  if (readSet.value.has(id)) return
  marks.value = { ...marks.value, read: [...marks.value.read, id] }
  persistMarks()
}

// ---- 阅读弹窗 ----
const selected = ref<Article | null>(null)
const dialog = ref(false)
const readerRef = ref<HTMLElement | null>(null)
const progress = ref(0)
let scrollable = false

const related = computed<Article[]>(() => {
  const current = selected.value
  if (!current) return []
  return published.value
    .filter((article) => article.id !== current.id)
    .sort((a, b) => {
      const sameA = a.category === current.category ? 1 : 0
      const sameB = b.category === current.category ? 1 : 0
      return sameB - sameA || b.views - a.views
    })
    .slice(0, 3)
})

/** 从正文里提炼三条可带走的小提示，省去额外维护一份数据 */
const takeaways = computed<string[]>(() => {
  const body = selected.value?.content ?? []
  const source = body.length > 1 ? body.slice(1) : body
  return source.slice(0, 3).map((paragraph) => {
    const sentence = paragraph.split(/[。！？]/)[0] ?? paragraph
    return `${sentence}。`
  })
})

function syncReader(): void {
  const el = readerRef.value
  if (!el) return
  scrollable = el.scrollHeight > el.clientHeight + 4
  progress.value = scrollable ? 0 : 100
  if (scrollable) el.scrollTop = 0
}

function onReaderScroll(event: Event): void {
  const el = event.currentTarget as HTMLElement
  const max = el.scrollHeight - el.clientHeight
  progress.value = max <= 0 ? 100 : Math.min(100, Math.max(0, (el.scrollTop / max) * 100))
}

function showArticle(article: Article): void {
  selected.value = article
  dialog.value = true
  progress.value = 0
  void nextTick(syncReader)
}

/** 打开文章时向后端拉取最新详情，阅读量由后端 +1。 */
async function open(article: Article): Promise<void> {
  showArticle(article)
  try {
    const fresh = await getArticle(article.id)
    selected.value = fresh
    published.value = published.value.map((item) => (item.id === fresh.id ? fresh : item))
    await nextTick()
    syncReader()
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '文章详情加载失败',
      customClass: 'warm-toast',
    })
  }
}

function finishReading(): void {
  const current = selected.value
  if (!current) return

  const firstTime = !isRead(current.id)
  markRead(current.id)
  ElMessage.success(firstTime ? '已标记为读完了' : '这篇已经读过啦')
}

function onDialogOpened(): void {
  syncReader()
}

function onDialogClosed(): void {
  selected.value = null
  progress.value = 0

  // 从首页「精选阅读」带着 ?article=id 进来时，关掉弹窗就清掉参数
  if (route.query.article) {
    const query = { ...route.query }
    delete query.article
    void router.replace({ path: route.path, query })
  }
}

// 首页精选阅读直接打开对应文章
const route = useRoute()
const router = useRouter()

onMounted(() => {
  void (async () => {
    await loadArticles()

    const id = Number(route.query.article)
    if (!Number.isFinite(id) || id <= 0) return
    const target = published.value.find((article) => article.id === id)
    if (target) {
      await open(target)
      return
    }
    // 列表里没有（例如刚发布、排序变动），直接按 id 尝试取详情
    try {
      showArticle(await getArticle(id))
    } catch {
      ElMessage.warning('这篇文章不存在或已下线')
    }
  })()
})
</script>

<template>
  <div class="articles container">
    <header class="articles__head">
      <div class="articles__intro">
        <p class="eyebrow" v-reveal>知识库</p>
        <h1 v-reveal="{ delay: 60 }">把心理学，<br class="articles__br" />翻译成日常语言</h1>
        <p class="section-sub" v-reveal="{ delay: 120 }">
          不贩卖焦虑，也不承诺速效。每篇都能带走一个可执行的小方法。
        </p>

        <div class="articles__meta" v-reveal="{ delay: 160 }">
          <span><strong>{{ published.length }}</strong> 篇科普</span>
          <span><strong>{{ readSet.size }}</strong> 篇已读</span>
          <span><strong>{{ bookmarkSet.size }}</strong> 篇收藏</span>
        </div>
      </div>

      <el-input
        v-model="keyword"
        class="articles__search"
        placeholder="搜索标题、摘要或作者"
        clearable
      >
        <template #prefix>
          <AppIcon name="search" :size="14" />
        </template>
      </el-input>
    </header>

    <!-- 本周精选：默认视图才有，避免和筛选结果重复 -->
    <section v-if="showFeatured && featured" class="feature" v-reveal="{ delay: 180 }">
      <div class="feature__body">
        <span class="feature__badge">
          <AppIcon name="sparkles" :size="13" />
          本周精选
        </span>
        <h2>{{ featured.title }}</h2>
        <p>{{ featured.summary }}</p>

        <div class="feature__meta">
          <span>{{ featured.author }}</span>
          <i />
          <span>{{ featured.category }}</span>
          <i />
          <span>{{ featured.minutes }} 分钟</span>
          <i />
          <span>{{ featured.views.toLocaleString('zh-CN') }} 次阅读</span>
        </div>

        <div class="feature__actions">
          <el-button type="primary" size="large" @click="open(featured)">
            开始阅读
            <AppIcon name="arrowRight" :size="15" />
          </el-button>
          <button
            type="button"
            class="feature__save"
            :class="{ 'is-saved': isBookmarked(featured.id) }"
            :aria-pressed="isBookmarked(featured.id)"
            @click="toggleBookmark(featured)"
          >
            <AppIcon name="bookmark" :size="15" />
            {{ isBookmarked(featured.id) ? '已收藏' : '收藏' }}
          </button>
        </div>
      </div>

      <button
        type="button"
        class="feature__cover"
        :class="`feature__cover--${featured.cover}`"
        :aria-label="`阅读《${featured.title}》`"
        @click="open(featured)"
      >
        <span class="feature__glyph" aria-hidden="true">{{ featured.title.slice(0, 1) }}</span>
        <span class="feature__ring" aria-hidden="true" />
        <span class="feature__swirl" aria-hidden="true" />
        <span class="feature__mark">Vol. {{ String(featured.id).padStart(2, '0') }}</span>
        <span class="feature__engrave" aria-hidden="true">READ · SLOWLY</span>
      </button>
    </section>

    <div class="articles__toolbar" v-reveal="{ delay: 160 }">
      <div class="articles__filters" role="group" aria-label="按主题筛选">
        <button
          v-for="category in categories"
          :key="category"
          type="button"
          class="chip articles__chip"
          :class="{ 'is-active': activeCategory === category }"
          :aria-pressed="activeCategory === category"
          @click="activeCategory = category"
        >
          {{ category }}
          <span class="articles__count">{{ counts[category] }}</span>
        </button>
      </div>

      <div class="articles__tools">
        <button
          type="button"
          class="articles__saved"
          :class="{ 'is-active': bookmarkOnly }"
          :aria-pressed="bookmarkOnly"
          @click="bookmarkOnly = !bookmarkOnly"
        >
          <AppIcon name="bookmark" :size="14" />
          只看收藏
          <span>{{ bookmarkSet.size }}</span>
        </button>

        <div class="articles__sort" role="group" aria-label="排序方式">
          <button
            v-for="option in sortOptions"
            :key="option.key"
            type="button"
            :class="{ 'is-active': sortKey === option.key }"
            :aria-pressed="sortKey === option.key"
            @click="sortKey = option.key"
          >
            {{ option.label }}
          </button>
        </div>
      </div>
    </div>

    <p class="articles__result">
      共 {{ list.length }} 篇
      <span v-if="keyword.trim()">· 关键词「{{ keyword.trim() }}」</span>
      <span v-if="bookmarkOnly">· 只看收藏</span>
    </p>

    <div
      v-if="loading"
      class="articles__grid articles__skeleton"
      role="status"
      aria-busy="true"
      aria-label="正在加载文章"
    >
      <div v-for="index in 6" :key="index" class="article-skeleton" aria-hidden="true">
        <span class="article-skeleton__cover" />
        <span class="article-skeleton__line article-skeleton__line--title" />
        <span class="article-skeleton__line article-skeleton__line--text" />
        <span class="article-skeleton__line article-skeleton__line--meta" />
      </div>
    </div>

    <TransitionGroup v-else-if="gridList.length" name="article" tag="div" class="articles__grid">
      <article
        v-for="(article, index) in gridList"
        :key="article.id"
        class="article"
        tabindex="0"
        v-spotlight
        :style="{ '--stagger': `${index * 55}ms` }"
        @click="open(article)"
        @keydown.enter.self="open(article)"
        @keydown.space.prevent.self="open(article)"
      >
        <div class="article__cover" :class="`article__cover--${article.cover}`">
          <span class="article__cat">{{ article.category }}</span>
          <span class="article__glyph" aria-hidden="true">{{ article.title.slice(0, 1) }}</span>
          <span class="article__ring" aria-hidden="true" />
          <span class="article__minutes">{{ article.minutes }} 分钟</span>
          <span v-if="isRead(article.id)" class="article__read">已读</span>

          <button
            type="button"
            class="article__save"
            :class="{ 'is-saved': isBookmarked(article.id) }"
            :aria-label="isBookmarked(article.id) ? '取消收藏' : '收藏这篇文章'"
            :aria-pressed="isBookmarked(article.id)"
            @click.stop="toggleBookmark(article)"
            @keydown.stop
          >
            <AppIcon name="bookmark" :size="14" />
          </button>
        </div>

        <div class="article__body">
          <h2>{{ article.title }}</h2>
          <p>{{ article.summary }}</p>

          <footer>
            <span>{{ article.author }} · {{ article.views.toLocaleString('zh-CN') }} 次阅读</span>
            <span class="article__go">
              {{ isRead(article.id) ? '再读' : '阅读' }}
              <AppIcon name="arrowRight" :size="14" />
            </span>
          </footer>
        </div>
      </article>
    </TransitionGroup>

    <div v-else class="articles__empty">
      <span class="articles__empty-icon">
        <AppIcon :name="bookmarkOnly ? 'bookmark' : 'leaf'" :size="22" />
      </span>
      <h3>{{ bookmarkOnly ? '收藏夹还是空的' : '没有找到文章' }}</h3>
      <p>{{ emptyHint }}</p>
      <button type="button" @click="resetFilters">看看全部文章</button>
    </div>

  <el-dialog
    v-model="dialog"
    width="min(760px, 94vw)"
    class="article-dialog"
    :show-close="true"
    destroy-on-close
    @opened="onDialogOpened"
    @closed="onDialogClosed"
  >
    <template #header>
      <div v-if="selected" class="reader-head">
        <div class="reader-head__top">
          <span class="reader-head__cat">{{ selected.category }}</span>
          <span class="reader-head__time">{{ selected.minutes }} 分钟阅读</span>
          <span v-if="isRead(selected.id)" class="reader-head__done">
            <AppIcon name="check" :size="12" />
            已读
          </span>
        </div>
        <h2>{{ selected.title }}</h2>
        <p class="reader-head__summary">{{ selected.summary }}</p>
        <div class="reader-head__meta">
          <span>{{ selected.author }}</span>
          <i />
          <span>{{ selected.views.toLocaleString('zh-CN') }} 次阅读</span>
          <i />
          <span>{{ selected.updated }}</span>
        </div>
      </div>
    </template>

    <div v-if="selected" class="reader">
      <div class="reader__progress" aria-hidden="true">
        <i :style="{ '--progress': progress / 100 }" />
      </div>

      <div ref="readerRef" class="reader__scroll" @scroll.passive="onReaderScroll">
        <p v-for="(paragraph, index) in selected.content" :key="index">{{ paragraph }}</p>

        <section class="reader__takeaways">
          <h3>
            <AppIcon name="sparkles" :size="14" />
            三句话回顾
          </h3>
          <ol>
            <li v-for="(item, index) in takeaways" :key="index">
              <span>{{ index + 1 }}</span>
              {{ item }}
            </li>
          </ol>
        </section>

        <p class="reader__note">
          本文为心理科普内容，不构成医疗建议。若困扰持续两周以上并影响生活，请寻求专业帮助。
        </p>

        <section v-if="related.length" class="reader__related">
          <h3>继续读一点</h3>
          <div class="reader__related-list">
            <button
              v-for="item in related"
              :key="item.id"
              type="button"
              @click="open(item)"
            >
              <span class="reader__related-cat">{{ item.category }}</span>
              <strong>{{ item.title }}</strong>
              <small>{{ item.minutes }} 分钟 · {{ item.views.toLocaleString('zh-CN') }} 次阅读</small>
            </button>
          </div>
        </section>
      </div>

      <footer class="reader__actions">
        <button
          type="button"
          class="reader__save"
          :class="{ 'is-saved': isBookmarked(selected.id) }"
          :aria-pressed="isBookmarked(selected.id)"
          @click="toggleBookmark(selected)"
        >
          <AppIcon name="bookmark" :size="15" />
          {{ isBookmarked(selected.id) ? '已收藏' : '收藏这篇' }}
        </button>

        <el-button type="primary" :disabled="isRead(selected.id)" @click="finishReading">
          <AppIcon name="check" :size="15" />
          {{ isRead(selected.id) ? '已读完' : '读完了' }}
        </el-button>
      </footer>
    </div>
  </el-dialog>
  </div>
</template>

<style lang="scss">
// 弹窗 teleport 到 body 外，样式挂在 .article-dialog 下，避免污染全局
.article-dialog {
  overflow: hidden;
  border-radius: $radius-lg;
  background: var(--warm-paper);

  .el-dialog__header {
    padding: 0;
    margin: 0;
  }

  .el-dialog__headerbtn {
    top: 14px;
    right: 14px;
    z-index: 5;
    display: grid;
    place-items: center;
    width: 32px;
    height: 32px;
    border: 1px solid var(--warm-line);
    border-radius: 50%;
    background: var(--warm-paper-80);

    .el-dialog__close {
      color: var(--warm-text-muted);
    }

    &:hover {
      background: var(--warm-paper-95);

      .el-dialog__close {
        color: var(--warm-text);
      }
    }
  }

  .el-dialog__body {
    padding: 0;
  }
}

.reader-head {
  padding: 26px 30px 20px;
  padding-right: 62px;
  border-bottom: 1px solid var(--warm-line);
  background:
    radial-gradient(120% 140% at 0% 0%, color-mix(in srgb, var(--warm-brand) 9%, transparent), transparent 55%),
    linear-gradient(160deg, var(--warm-paper), var(--warm-paper-alt));

  h2 {
    margin: 10px 0 8px;
    color: var(--warm-text);
    font-family: $font-display;
    font-size: clamp(20px, 2.6vw, 26px);
    font-weight: 600;
    line-height: 1.45;
    letter-spacing: -0.01em;
  }
}

.reader-head__top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.reader-head__cat {
  padding: 3px 9px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 26%, transparent);
  border-radius: $radius-pill;
  background: color-mix(in srgb, var(--warm-brand) 10%, transparent);
  color: var(--warm-brand-strong);
  font-size: 10.5px;
  font-weight: 600;
}

.reader-head__time,
.reader-head__meta {
  color: var(--warm-text-muted);
  font-size: 11px;
}

.reader-head__done {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 8px;
  border-radius: $radius-pill;
  background: var(--warm-line);
  color: var(--warm-text-muted);
  font-size: 10px;
}

.reader-head__summary {
  margin: 0;
  color: var(--warm-text-muted);
  font-size: 12.5px;
  line-height: 1.7;
}

.reader-head__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 9px;
  margin-top: 12px;

  i {
    width: 3px;
    height: 3px;
    border-radius: 50%;
    background: var(--warm-text-muted);
    opacity: 0.5;
  }
}

.reader__progress {
  height: 2px;
  background: var(--warm-line);

  i {
    display: block;
    height: 100%;
    border-radius: 2px;
    background: linear-gradient(90deg, var(--warm-brand), var(--warm-brand-deep));
    transform: scaleX(var(--progress, 0));
    transform-origin: left;
    transition: transform 0.12s linear;
  }
}

.reader__scroll {
  max-height: min(52vh, 480px);
  padding: 24px 30px 10px;
  overflow-y: auto;
  overscroll-behavior: contain;

  > p {
    margin: 0 0 15px;
    color: var(--warm-text-regular);
    font-size: 14px;
    line-height: 1.95;

    &:first-of-type::first-letter {
      float: left;
      padding: 3px 7px 0 0;
      color: var(--warm-brand-strong);
      font-family: $font-display;
      font-size: 40px;
      line-height: 0.92;
    }
  }
}

.reader__takeaways {
  margin: 22px 0 18px;
  padding: 16px 18px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 18%, transparent);
  border-radius: 14px;
  background:
    radial-gradient(100% 120% at 100% 0%, color-mix(in srgb, var(--warm-butter) 10%, transparent), transparent 60%),
    var(--warm-paper-60);

  h3 {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0 0 10px;
    color: var(--warm-text);
    font-size: 12.5px;
    font-weight: 600;

    svg {
      color: var(--warm-brand-strong);
    }
  }

  ol {
    display: grid;
    gap: 8px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  li {
    display: flex;
    gap: 9px;
    color: var(--warm-text-regular);
    font-size: 12.5px;
    line-height: 1.7;

    span {
      display: grid;
      flex: none;
      place-items: center;
      width: 18px;
      height: 18px;
      margin-top: 2px;
      border-radius: 50%;
      background: color-mix(in srgb, var(--warm-brand) 14%, transparent);
      color: var(--warm-brand-strong);
      font-family: $font-mono;
      font-size: 9.5px;
    }
  }
}

.reader__note {
  margin: 0 0 18px !important;
  padding: 12px 14px;
  border: 1px dashed var(--warm-line);
  border-radius: 12px;
  color: var(--warm-text-muted) !important;
  font-size: 11.5px !important;
  line-height: 1.75 !important;
}

.reader__related {
  padding-top: 4px;

  h3 {
    margin: 0 0 10px;
    color: var(--warm-text-muted);
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.12em;
  }
}

.reader__related-list {
  display: grid;
  gap: 7px;

  button {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr);
    align-items: center;
    gap: 3px 10px;
    padding: 10px 12px;
    border: 1px solid var(--warm-line);
    border-radius: 11px;
    background: var(--warm-paper-60);
    text-align: left;
    cursor: pointer;
    transition:
      border-color 0.2s $ease-out,
      background 0.2s $ease-out,
      transform 0.2s $ease-out-expo;

    &:hover {
      border-color: color-mix(in srgb, var(--warm-brand) 30%, transparent);
      background: var(--warm-paper-85);
      transform: translateX(3px);
    }

    &:focus-visible {
      @include focus-ring($warm-brand);
    }

    strong {
      grid-column: 2;
      overflow: hidden;
      color: var(--warm-text);
      font-size: 12.5px;
      font-weight: 600;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    small {
      grid-column: 2;
      color: var(--warm-text-muted);
      font-size: 10.5px;
    }
  }
}

.reader__related-cat {
  grid-row: span 2;
  padding: 2px 7px;
  border-radius: $radius-pill;
  background: color-mix(in srgb, var(--warm-brand) 10%, transparent);
  color: var(--warm-brand-strong);
  font-size: 9.5px;
  white-space: nowrap;
}

.reader__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 30px;
  border-top: 1px solid var(--warm-line);
  background: var(--warm-paper-85);
}

.reader__save {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 13px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-pill;
  background: var(--warm-paper-70);
  color: var(--warm-text-muted);
  font-size: 11.5px;
  cursor: pointer;
  transition:
    color 0.2s $ease-out,
    border-color 0.2s $ease-out,
    background 0.2s $ease-out;

  &:hover {
    border-color: color-mix(in srgb, var(--warm-brand) 32%, transparent);
    color: var(--warm-brand-strong);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }

  &.is-saved {
    border-color: color-mix(in srgb, var(--warm-brand) 34%, transparent);
    background: color-mix(in srgb, var(--warm-brand) 10%, transparent);
    color: var(--warm-brand-strong);
  }
}

@media (max-width: 640px) {
  .reader-head {
    padding: 22px 18px 16px;
    padding-right: 54px;
  }

  .reader__scroll {
    max-height: 54vh;
    padding: 20px 18px 8px;
  }

  .reader__actions {
    padding: 12px 18px;
  }

  .reader__related-list button {
    grid-template-columns: minmax(0, 1fr);

    strong,
    small {
      grid-column: 1;
    }
  }

  .reader__related-cat {
    grid-row: auto;
    justify-self: start;
  }
}
</style>

<style scoped lang="scss">
.articles {
  padding: 54px 0 88px;
}

.articles__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: $space-5;

  h1 {
    margin: 12px 0 0;
    font-size: clamp(30px, 3.6vw, 42px);
    font-weight: 600;
    letter-spacing: -0.03em;
    line-height: 1.24;
  }
}

.articles__br {
  display: none;
}

.articles__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 18px;
  color: var(--warm-text-muted);
  font-size: 11.5px;

  strong {
    margin-right: 2px;
    color: var(--warm-text);
    font-family: $font-display;
    font-size: 16px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }
}

.articles__search {
  width: 286px;
}

// ---- 本周精选 ----
.feature {
  display: grid;
  grid-template-columns: minmax(0, 1.06fr) minmax(0, 0.94fr);
  min-height: 288px;
  margin-top: 30px;
  overflow: hidden;
  border: 1px solid var(--warm-line);
  border-radius: 28px;
  background:
    radial-gradient(120% 130% at 4% 0%, color-mix(in srgb, var(--warm-brand) 8%, transparent), transparent 58%),
    linear-gradient(150deg, var(--warm-paper), var(--warm-paper-alt));
  box-shadow:
    inset 0 1px 0 var(--warm-rim-60),
    0 34px 80px -58px var(--warm-shadow);
}

.feature__body {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  padding: clamp(24px, 3.2vw, 40px);

  h2 {
    max-width: 17ch;
    margin: 14px 0 10px;
    color: var(--warm-text);
    font-family: $font-display;
    font-size: clamp(23px, 2.7vw, 32px);
    font-weight: 600;
    line-height: 1.35;
    letter-spacing: -0.02em;
  }

  > p {
    max-width: 46ch;
    margin: 0;
    color: var(--warm-text-muted);
    font-size: 13px;
    line-height: 1.8;
  }
}

.feature__badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border: 1px solid color-mix(in srgb, var(--warm-butter) 32%, transparent);
  border-radius: $radius-pill;
  background: color-mix(in srgb, var(--warm-butter) 12%, transparent);
  color: var(--warm-butter);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.feature__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  color: var(--warm-text-muted);
  font-size: 11px;

  i {
    width: 3px;
    height: 3px;
    border-radius: 50%;
    background: var(--warm-text-muted);
    opacity: 0.5;
  }
}

.feature__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 24px;

  .el-button {
    gap: 6px;
  }
}

.feature__save {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-sm;
  background: var(--warm-paper-70);
  color: var(--warm-text-muted);
  font-size: 12.5px;
  cursor: pointer;
  transition:
    color 0.2s $ease-out,
    border-color 0.2s $ease-out,
    background 0.2s $ease-out;

  &:hover {
    border-color: color-mix(in srgb, var(--warm-brand) 32%, transparent);
    color: var(--warm-brand-strong);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }

  &.is-saved {
    border-color: color-mix(in srgb, var(--warm-brand) 34%, transparent);
    background: color-mix(in srgb, var(--warm-brand) 10%, transparent);
    color: var(--warm-brand-strong);
  }
}

.feature__cover {
  position: relative;
  display: grid;
  place-items: center;
  min-height: 220px;
  overflow: hidden;
  border: 0;
  padding: 0;
  cursor: pointer;
  isolation: isolate;

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, transparent 52%, rgba(74, 64, 46, 0.14));
    pointer-events: none;
  }

  &--teal {
    background:
      radial-gradient(circle at 24% 18%, rgba(255, 255, 255, 0.72), transparent 52%),
      linear-gradient(140deg, #f0dfe2, #d6aeb6 58%, #bd8691);
  }

  &--lilac {
    background:
      radial-gradient(circle at 24% 18%, rgba(255, 255, 255, 0.72), transparent 52%),
      linear-gradient(140deg, #f4e4e7, #e4c0c7 58%, #d6a5af);
  }

  &--sand {
    background:
      radial-gradient(circle at 24% 18%, rgba(255, 255, 255, 0.72), transparent 52%),
      linear-gradient(140deg, #f9e9d4, #eacfa9 58%, #ddb982);
  }

  &:hover .feature__glyph {
    transform: translateY(-6px) scale(1.04) rotate(-2deg);
  }

  &:hover .feature__ring {
    transform: scale(1.12) rotate(18deg);
    opacity: 0.8;
  }

  &:hover .feature__swirl {
    transform: rotate(24deg) scale(1.06);
  }

  &:focus-visible {
    @include focus-ring($warm-brand-strong);
  }
}

.feature__glyph {
  position: relative;
  z-index: 1;
  color: rgba(255, 255, 255, 0.64);
  font-family: $font-display;
  font-size: clamp(132px, 15vw, 210px);
  font-weight: 600;
  line-height: 1;
  text-shadow: 0 18px 50px rgba(74, 64, 46, 0.18);
  transition: transform 0.7s $ease-out-expo;
}

.feature__ring {
  position: absolute;
  right: -42px;
  bottom: -66px;
  width: 200px;
  height: 200px;
  border: 1px solid rgba(255, 255, 255, 0.48);
  border-radius: 50%;
  box-shadow: 0 0 0 26px rgba(255, 255, 255, 0.12);
  transition:
    transform 0.9s $ease-out-expo,
    opacity 0.4s $ease-out;
}

.feature__swirl {
  position: absolute;
  top: -44px;
  left: -30px;
  width: 148px;
  height: 148px;
  border: 1px dashed rgba(255, 255, 255, 0.5);
  border-radius: 50%;
  transition: transform 1.1s $ease-out-expo;
}

.feature__mark {
  position: absolute;
  top: 16px;
  left: 18px;
  z-index: 2;
  color: rgba(44, 58, 48, 0.6);
  font-family: $font-mono;
  font-size: 10px;
  letter-spacing: 0.16em;
}

.feature__engrave {
  position: absolute;
  right: 20px;
  bottom: 18px;
  z-index: 2;
  color: rgba(44, 58, 48, 0.42);
  font-family: $font-mono;
  font-size: 9px;
  letter-spacing: 0.3em;
  writing-mode: vertical-rl;
}

// ---- 工具栏 ----
.articles__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 30px 0 0;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--warm-line);
}

.articles__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.articles__chip {
  gap: 7px;
}

.articles__count {
  display: inline-grid;
  place-items: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: $radius-pill;
  background: var(--warm-paper-70);
  color: var(--warm-text-muted);
  font-size: 10.5px;
  font-variant-numeric: tabular-nums;
  transition:
    background 0.2s $ease-out,
    color 0.2s $ease-out;
}

.chip.is-active .articles__count {
  background: var(--warm-rim-22);
  color: var(--warm-on-brand);
}

.articles__tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.articles__saved {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 11px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-pill;
  background: var(--warm-paper-70);
  color: var(--warm-text-muted);
  font-size: 11px;
  cursor: pointer;
  transition:
    color 0.2s $ease-out,
    border-color 0.2s $ease-out,
    background 0.2s $ease-out;

  span {
    padding: 1px 6px;
    border-radius: $radius-pill;
    background: var(--warm-paper-70);
    font-size: 9.5px;
    font-variant-numeric: tabular-nums;
  }

  &:hover {
    border-color: color-mix(in srgb, var(--warm-brand) 32%, transparent);
    color: var(--warm-brand-strong);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }

  &.is-active {
    border-color: color-mix(in srgb, var(--warm-brand) 40%, transparent);
    background: var(--warm-brand-soft);
    color: var(--warm-brand-strong);
    font-weight: 600;

    span {
      background: color-mix(in srgb, var(--warm-brand) 16%, transparent);
    }
  }
}

.articles__sort {
  display: flex;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-pill;
  background: var(--warm-paper-60);

  button {
    padding: 4px 11px;
    border: 0;
    border-radius: $radius-pill;
    background: transparent;
    color: var(--warm-text-muted);
    font-size: 11px;
    cursor: pointer;
    transition:
      color 0.2s $ease-out,
      background 0.2s $ease-out;

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

.articles__result {
  margin: 12px 0 18px;
  color: var(--warm-text-muted);
  font-size: 11px;
  letter-spacing: 0.04em;

  span {
    margin-left: 4px;
  }
}

.articles__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: $space-5;
}

.articles__skeleton {
  @media (max-width: 900px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @media (max-width: 620px) {
    grid-template-columns: 1fr;
  }
}

.article-skeleton {
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
  padding-bottom: 18px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-lg;
  background: var(--warm-paper-50);
}

.article-skeleton__cover {
  aspect-ratio: 16 / 9;
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--warm-brand) 6%, transparent) 0%,
    color-mix(in srgb, var(--warm-brand) 14%, transparent) 50%,
    color-mix(in srgb, var(--warm-brand) 6%, transparent) 100%
  );
  background-size: 200% 100%;
  animation: article-shimmer 1.4s ease-in-out infinite;
}

.article-skeleton__line {
  height: 10px;
  margin-inline: 18px;
  border-radius: 999px;
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--warm-brand) 7%, transparent) 0%,
    color-mix(in srgb, var(--warm-brand) 16%, transparent) 50%,
    color-mix(in srgb, var(--warm-brand) 7%, transparent) 100%
  );
  background-size: 200% 100%;
  animation: article-shimmer 1.4s ease-in-out infinite;
}

.article-skeleton__line--title {
  width: 62%;
  height: 14px;
  margin-top: 2px;
}

.article-skeleton__line--text {
  width: 82%;
}

.article-skeleton__line--meta {
  width: 44%;
  height: 8px;
  margin-top: 8px;
}

@keyframes article-shimmer {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .article-skeleton__cover,
  .article-skeleton__line {
    animation: none;
  }
}

// ---- 文章卡片 ----
.article {
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--warm-line);
  border-radius: $radius-lg;
  background: linear-gradient(160deg, var(--warm-paper-95), var(--warm-paper-85));
  box-shadow:
    inset 0 1px 0 var(--warm-paper-90),
    0 20px 46px -44px var(--warm-shadow);
  cursor: pointer;
  animation: article-in 0.7s $ease-out-expo both;
  animation-delay: var(--stagger, 0ms);
  transition:
    transform 0.4s $ease-out-expo,
    box-shadow 0.4s $ease-out,
    border-color 0.4s $ease-out;

  &:hover {
    transform: translateY(-6px);
    border-color: color-mix(in srgb, var(--warm-brand) 34%, transparent);
    box-shadow:
      0 36px 76px -46px var(--warm-shadow),
      0 0 44px -32px var(--warm-glow),
      inset 0 1px 0 var(--warm-paper-95);

    .article__ring {
      transform: scale(1.12) rotate(14deg);
      opacity: 0.8;
    }

    .article__glyph {
      transform: translateY(-4px) scale(1.04);
    }

    .article__go svg {
      transform: translateX(4px);
    }
  }

  &:focus-visible {
    border-color: color-mix(in srgb, var(--warm-brand) 50%, transparent);
    @include focus-ring($warm-brand);
  }

  h2 {
    margin: 0 0 8px;
    color: var(--warm-text);
    font-size: 16px;
    font-weight: 600;
    line-height: 1.5;
  }

  p {
    flex: 1;
    margin: 0 0 16px;
    color: var(--warm-text-muted);
    font-size: 12.5px;
    line-height: 1.75;
    @include line-clamp(2);
  }

  footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    color: var(--warm-text-muted);
    font-size: 10.5px;
  }
}

.article__cover {
  position: relative;
  height: 132px;
  overflow: hidden;

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, transparent 42%, rgba(74, 64, 46, 0.12));
    pointer-events: none;
  }

  &--teal {
    background:
      radial-gradient(circle at 22% 18%, rgba(255, 255, 255, 0.7), transparent 56%),
      linear-gradient(135deg, #f0dfe2, #d8b4bb);
  }

  &--lilac {
    background:
      radial-gradient(circle at 22% 18%, rgba(255, 255, 255, 0.7), transparent 56%),
      linear-gradient(135deg, #f4e4e7, #e3c1c8);
  }

  &--sand {
    background:
      radial-gradient(circle at 22% 18%, rgba(255, 255, 255, 0.7), transparent 56%),
      linear-gradient(135deg, #f9e9d4, #eacfa9);
  }
}

.article__cat,
.article__minutes,
.article__read {
  position: absolute;
  z-index: 2;
  padding: 4px 10px;
  border: 1px solid rgba(255, 255, 255, 0.55);
  border-radius: $radius-pill;
  background: var(--warm-paper-72);
  color: var(--warm-text);
  font-size: 10.5px;
}

.article__cat {
  top: 12px;
  left: 12px;
  color: var(--warm-brand-strong);
  font-weight: 600;
}

.article__minutes {
  bottom: 11px;
  left: 12px;
  color: var(--warm-text-regular);
}

.article__read {
  right: 12px;
  bottom: 11px;
  border-color: color-mix(in srgb, var(--warm-brand) 26%, transparent);
  color: var(--warm-brand-strong);
}

.article__glyph {
  position: absolute;
  right: 16px;
  bottom: -18px;
  z-index: 1;
  color: rgba(255, 255, 255, 0.55);
  font-family: $font-display;
  font-size: 108px;
  font-weight: 600;
  line-height: 1;
  transition: transform 0.5s $ease-out-expo;
}

.article__ring {
  position: absolute;
  left: -34px;
  bottom: -54px;
  z-index: 1;
  width: 128px;
  height: 128px;
  border: 1px solid rgba(255, 255, 255, 0.48);
  border-radius: 50%;
  box-shadow: 0 0 0 18px rgba(255, 255, 255, 0.12);
  opacity: 0.7;
  transition:
    transform 0.8s $ease-out-expo,
    opacity 0.4s $ease-out;
}

.article__save {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 3;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 50%;
  background: var(--warm-paper-72);
  color: var(--warm-text-muted);
  cursor: pointer;
  transition:
    color 0.2s $ease-out,
    background 0.2s $ease-out,
    transform 0.2s $ease-spring;

  &:hover {
    background: var(--warm-paper-95);
    color: var(--warm-brand-strong);
    transform: scale(1.08);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }

  &.is-saved {
    border-color: color-mix(in srgb, var(--warm-brand) 36%, transparent);
    background: var(--warm-brand-soft);
    color: var(--warm-brand-strong);
  }
}

.article__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 18px 20px 20px;
}

.article__go {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 4px;
  color: var(--warm-brand-strong);
  font-weight: 600;
  opacity: 0;
  transform: translateX(-4px);
  transition:
    opacity 0.3s $ease-out,
    transform 0.3s $ease-out;

  svg {
    transition: transform 0.3s $ease-out;
  }
}

.article:hover .article__go,
.article:focus-visible .article__go {
  opacity: 1;
  transform: none;
}

// ---- 空状态 ----
.articles__empty {
  display: grid;
  place-items: center;
  gap: 8px;
  padding: 64px 24px;
  border: 1px dashed var(--warm-line);
  border-radius: $radius-lg;
  background: var(--warm-paper-50);
  text-align: center;

  h3 {
    margin: 6px 0 0;
    color: var(--warm-text);
    font-size: 15px;
    font-weight: 600;
  }

  p {
    max-width: 34ch;
    margin: 0;
    color: var(--warm-text-muted);
    font-size: 12px;
    line-height: 1.7;
  }

  button {
    margin-top: 10px;
    padding: 8px 18px;
    border: 1px solid color-mix(in srgb, var(--warm-brand) 30%, transparent);
    border-radius: $radius-pill;
    background: color-mix(in srgb, var(--warm-brand) 9%, transparent);
    color: var(--warm-brand-strong);
    font-size: 12px;
    cursor: pointer;
    transition: background 0.2s $ease-out;

    &:hover {
      background: color-mix(in srgb, var(--warm-brand) 16%, transparent);
    }

    &:focus-visible {
      @include focus-ring($warm-brand);
    }
  }
}

.articles__empty-icon {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 14px;
  background: var(--warm-brand-soft);
  color: var(--warm-brand-strong);
}

@keyframes article-in {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.97);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

.article-enter-active {
  transition:
    opacity 0.45s $ease-out-expo,
    transform 0.45s $ease-out-expo;
}

.article-leave-active {
  // 入场 animation 的 fill: both 会覆盖离场过渡
  animation: none;
  transition:
    opacity 0.18s $ease-in,
    transform 0.18s $ease-in;
}

.article-enter-from {
  opacity: 0;
  transform: translateY(16px) scale(0.97);
}

.article-leave-to {
  opacity: 0;
  transform: scale(0.97);
}

.article-move {
  transition: transform 0.45s $ease-out-expo;
}

@include media-down($bp-lg) {
  .feature {
    grid-template-columns: minmax(0, 1fr) minmax(0, 0.8fr);
  }

  .articles__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@include media-down($bp-md) {
  .feature {
    grid-template-columns: 1fr;
  }

  .feature__cover {
    order: -1;
    min-height: 196px;
  }

  .feature__glyph {
    font-size: 138px;
  }
}

@include media-down($bp-sm) {
  .articles {
    padding: 34px 0 60px;
  }

  .articles__search {
    width: 100%;
  }

  .articles__br {
    display: inline;
  }

  .articles__grid {
    grid-template-columns: 1fr;
  }

  .articles__tools {
    width: 100%;
    margin-left: 0;
    justify-content: space-between;
  }

  .articles__result {
    width: 100%;
    margin-left: 0;
  }

  .article__go {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .article {
    animation: none !important;
  }

  .feature__glyph,
  .feature__ring,
  .feature__swirl {
    transition: none;
  }
}
</style>
