<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import AppIcon from '@/components/AppIcon.vue'
import BreathingCoach from '@/components/BreathingCoach.vue'
import { chatApi, type ConversationSummary, type Reference } from '@/api/chat'
import type { IconName, MoodOption } from '@/types'
import { readJSON, writeJSON } from '@/utils/storage'

type Role = 'user' | 'ai'

interface ChatMsg {
  id: string
  role: Role
  text: string
  time: string
  suggestions?: string[]
  references?: Reference[]
  feedback?: 'up' | 'down'
}

interface Starter {
  icon: IconName
  title: string
  hint: string
  prompt: string
}

const MOOD_KEY = 'xinli.chat.mood'

const sessions = ref<ConversationSummary[]>([])
const sessionsLoading = ref(true)
const activeId = ref<string | null>(null)
const messages = ref<ChatMsg[]>([])
const draft = ref('')
const isTyping = ref(false)
const railOpen = ref(false)
const breathOpen = ref(false)
const isNarrow = ref(false)
const bodyRef = ref<HTMLElement | null>(null)
const showJump = ref(false)
const speakingId = ref<string | null>(null)

let controller: AbortController | null = null

const EMPTY_SESSION: ConversationSummary = {
  id: '',
  title: '新的对话',
  preview: '',
  updatedAt: 0,
  messageCount: 0,
}

const activeSession = computed<ConversationSummary>(
  () => sessions.value.find((session) => session.id === activeId.value) ?? EMPTY_SESSION,
)

const sortedSessions = computed(() =>
  [...sessions.value].sort((a, b) => b.updatedAt - a.updatedAt),
)

const isFresh = computed(() => messages.value.length === 0)

// 等待首个 token 时先显示打字指示器，避免出现空气泡
const visibleMessages = computed(() =>
  messages.value.filter(
    (message) => !(isTyping.value && message.role === 'ai' && !message.text),
  ),
)

const lastAiId = computed(() => {
  for (let i = messages.value.length - 1; i >= 0; i -= 1) {
    if (messages.value[i]?.role === 'ai') return messages.value[i]?.id ?? null
  }
  return null
})

const canRegenerate = computed(() => {
  if (isTyping.value || !activeId.value) return false
  const last = messages.value[messages.value.length - 1]
  return last?.role === 'ai' && !!last.text.trim()
})

const starters: Starter[] = [
  {
    icon: 'moon',
    title: '最近睡不好',
    hint: '入睡难、容易醒',
    prompt: '最近总是睡不着，躺下脑子停不下来，怎么办？',
  },
  {
    icon: 'wind',
    title: '总是很焦虑',
    hint: '心慌、停不下来',
    prompt: '我最近总是很焦虑，心里发慌，有什么办法能缓解吗？',
  },
  {
    icon: 'heart',
    title: '关系让我很累',
    hint: '边界与拒绝',
    prompt: '我在关系里总是不好意思拒绝别人，觉得很累，该怎么建立边界？',
  },
  {
    icon: 'leaf',
    title: '情绪很低落',
    hint: '提不起劲',
    prompt: '最近情绪很低落，什么都不想做，该怎么照顾自己？',
  },
]

const quickPrompts = ['我有点焦虑', '晚上睡不着', '如何拒绝别人', '什么时候该看医生']

const moodOptions: MoodOption[] = [
  { score: 1, emoji: '😔', label: '很低落', color: '#8a5b68' },
  { score: 2, emoji: '😟', label: '有点糟', color: '#b78490' },
  { score: 3, emoji: '😐', label: '一般', color: '#b5884d' },
  { score: 4, emoji: '🙂', label: '还不错', color: '#5aa79b' },
  { score: 5, emoji: '😊', label: '挺好', color: '#4f8668' },
]

const moodPicked = ref<number | null>(readJSON<number | null>(MOOD_KEY, null))

function pickMood(option: MoodOption): void {
  moodPicked.value = option.score
  writeJSON(MOOD_KEY, option.score)
}

function formatTime(input: number | string | Date): string {
  const date = input instanceof Date ? input : new Date(input)
  if (Number.isNaN(date.getTime())) return ''
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${hour}:${minute}`
}

function timeLabel(timestamp: number): string {
  if (!timestamp) return ''
  const diff = Date.now() - timestamp
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  const date = new Date(timestamp)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function previewOf(session: ConversationSummary): string {
  return session.preview || '还没有消息，点开继续聊'
}

async function refreshSessions(): Promise<void> {
  sessionsLoading.value = true
  try {
    sessions.value = await chatApi.conversations()
  } catch {
    // 后端未启动时保持空列表，发送消息时会给出明确提示
  } finally {
    sessionsLoading.value = false
  }
}

async function loadMessages(conversationId: string): Promise<void> {
  try {
    const history = await chatApi.history(conversationId)
    messages.value = history.map((message, index) => ({
      id: `${conversationId}-${index}`,
      role: message.role === 'USER' ? 'user' : 'ai',
      text: message.content,
      time: formatTime(message.createdAt),
    }))
  } catch (error) {
    ElMessage.error(errorText(error, '加载对话失败'))
  }
}

function newSession(): void {
  stopStreaming()
  activeId.value = null
  messages.value = []
  draft.value = ''
  showJump.value = false
  railOpen.value = false
}

async function selectSession(conversationId: string): Promise<void> {
  if (conversationId === activeId.value) {
    railOpen.value = false
    return
  }
  stopStreaming()
  activeId.value = conversationId
  railOpen.value = false
  messages.value = []
  await loadMessages(conversationId)
  scrollToBottom(true)
}

async function removeSession(session: ConversationSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除「${session.title}」这段对话？删除后无法恢复。`, '删除对话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await chatApi.remove(session.id)
    sessions.value = sessions.value.filter((item) => item.id !== session.id)
    if (activeId.value === session.id) {
      newSession()
    }
    ElMessage.success('对话已删除')
  } catch (error) {
    ElMessage.error(errorText(error, '删除失败'))
  }
}

function stopStreaming(): void {
  if (controller) {
    controller.abort()
    controller = null
  }
  isTyping.value = false
}

function send(preset?: string): void {
  const text = (preset ?? draft.value).trim()
  if (!text) return
  if (isTyping.value) {
    ElMessage.info('上一条还在回复中，稍等一下')
    return
  }
  if (preset === undefined) {
    draft.value = ''
  }

  const now = Date.now()
  const userMessage: ChatMsg = { id: `u-${now}`, role: 'user', text, time: formatTime(now) }
  const aiMessage = reactive<ChatMsg>({ id: `a-${now}`, role: 'ai', text: '', time: '' })
  messages.value.push(userMessage, aiMessage)
  isTyping.value = true
  scrollToBottom(true)
  void streamAnswer(text, aiMessage)
}

async function streamAnswer(question: string, aiMessage: ChatMsg): Promise<void> {
  const requestController = new AbortController()
  controller = requestController
  streaming = true
  let conversationId = activeId.value

  try {
    await chatApi.stream(
      { conversationId, question },
      {
        onMeta(meta) {
          conversationId = meta.conversationId
          activeId.value = meta.conversationId
          aiMessage.references = meta.references
        },
        onDelta(text) {
          if (isTyping.value) isTyping.value = false
          aiMessage.text += text
          scrollToBottom()
        },
      },
      requestController.signal,
    )

    aiMessage.time = formatTime(Date.now())
    aiMessage.suggestions = suggestionsFor(aiMessage.references)
    if (!aiMessage.text.trim()) {
      aiMessage.text = '抱歉，这次没有收到有效回复，请再试一次。'
    }
    await refreshSessions()
  } catch (error) {
    if (isAbort(error)) {
      if (!aiMessage.text.trim()) removeMessage(aiMessage.id)
      await refreshSessions()
      return
    }
    const detail = errorText(error, '网络连接失败')
    if (!aiMessage.text.trim()) {
      aiMessage.text = '抱歉，这次没能连上 AI 服务。'
    }
    aiMessage.text += `\n\n（${detail}）`
    await refreshSessions()
    ElMessage.error(detail)
  } finally {
    if (controller === requestController) {
      controller = null
      streaming = false
    }
    isTyping.value = false
    scrollToBottom()
  }
}

async function regenerate(): Promise<void> {
  if (!activeId.value || isTyping.value) return
  const last = messages.value[messages.value.length - 1]
  if (!last || last.role !== 'ai' || !last.text.trim()) return

  const conversationId = activeId.value
  messages.value.pop()
  const aiMessage = reactive<ChatMsg>({ id: `a-${Date.now()}`, role: 'ai', text: '', time: '' })
  messages.value.push(aiMessage)
  isTyping.value = true
  scrollToBottom(true)

  const requestController = new AbortController()
  controller = requestController
  streaming = true

  try {
    await chatApi.regenerate(
      conversationId,
      {
        onMeta(meta) {
          aiMessage.references = meta.references
        },
        onDelta(text) {
          if (isTyping.value) isTyping.value = false
          aiMessage.text += text
          scrollToBottom()
        },
      },
      requestController.signal,
    )
    aiMessage.time = formatTime(Date.now())
    aiMessage.suggestions = suggestionsFor(aiMessage.references)
    await refreshSessions()
  } catch (error) {
    if (isAbort(error)) {
      removeMessage(aiMessage.id)
      return
    }
    ElMessage.error(errorText(error, '重新生成失败'))
    await loadMessages(conversationId)
  } finally {
    if (controller === requestController) {
      controller = null
      streaming = false
    }
    isTyping.value = false
    scrollToBottom()
  }
}

function suggestionsFor(references?: Reference[]): string[] | undefined {
  const titles = [...new Set((references ?? []).map((item) => item.title).filter(Boolean))].slice(0, 2)
  if (titles.length === 0) return undefined
  return titles.map((title) => `关于「${title}」，还能具体说说吗？`)
}

function removeMessage(id: string): void {
  messages.value = messages.value.filter((message) => message.id !== id)
}

async function copyMessage(message: ChatMsg): Promise<void> {
  try {
    await navigator.clipboard.writeText(message.text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择文字')
  }
}

function speak(message: ChatMsg): void {
  const synth = window.speechSynthesis
  if (!synth) {
    ElMessage.warning('当前浏览器不支持语音朗读')
    return
  }
  if (speakingId.value === message.id) {
    synth.cancel()
    speakingId.value = null
    return
  }
  synth.cancel()
  const utterance = new SpeechSynthesisUtterance(message.text)
  utterance.lang = 'zh-CN'
  utterance.rate = 1
  utterance.onend = () => {
    if (speakingId.value === message.id) speakingId.value = null
  }
  utterance.onerror = () => {
    if (speakingId.value === message.id) speakingId.value = null
  }
  speakingId.value = message.id
  synth.speak(utterance)
}

function rate(message: ChatMsg, feedback: 'up' | 'down'): void {
  message.feedback = message.feedback === feedback ? undefined : feedback
}

function exportSession(): void {
  const title = activeSession.value.title || '对话记录'
  const body = messages.value
    .filter((message) => message.text.trim())
    .map((message) => `**${message.role === 'user' ? '我' : 'AI 助手'}：**\n\n${message.text}\n`)
    .join('\n')
  const content = [
    `# ${title}`,
    '',
    `> 导出自心理健康 AI 助手 · ${new Date().toLocaleString('zh-CN')}`,
    '',
    body,
    '---',
    '本内容由 AI 生成，仅供参考，不能替代专业诊断与治疗。',
  ].join('\n')
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${title.replace(/[\\/:*?"<>|]/g, '_')}.md`
  link.click()
  URL.revokeObjectURL(url)
}

// 流式回复每个 token 都会触发一次滚动请求：按帧合并，避免每来一个 token
// 就 nextTick + 读一次 scrollHeight，把主线程塞满布局计算。
let scrollFrame = 0
let scrollForce = false
let stateFrame = 0
// 是否跟随最新一行。流式期间保持为 true；用户主动向上翻后暂停，回到底部附近再恢复
let followLatest = true
// isTyping 在第一个 token 到达时就会关掉，这里单独记录“正在流式接收”
let streaming = false

function scrollToBottom(force = false): void {
  if (force) followLatest = true
  scrollForce = scrollForce || force
  if (scrollFrame) return

  scrollFrame = window.requestAnimationFrame(() => {
    void nextTick(() => {
      scrollFrame = 0
      const element = bodyRef.value
      if (!element) return
      const distance = element.scrollHeight - element.scrollTop - element.clientHeight
      // 回复还在增长时始终贴住底部；临时关掉 CSS smooth，
      // 否则平滑滚动永远追不上 token 的输出速度，看起来就像“停在半路”。
      if (scrollForce || (streaming && followLatest)) {
        element.style.scrollBehavior = 'auto'
        element.scrollTop = element.scrollHeight
        element.style.scrollBehavior = ''
      } else if (followLatest && distance < 160) {
        element.scrollTop = element.scrollHeight
      }
      scrollForce = false
      updateScrollState()
    })
  })
}

function updateScrollState(): void {
  const element = bodyRef.value
  if (!element) return
  const distance = element.scrollHeight - element.scrollTop - element.clientHeight
  showJump.value = distance > 180
  // 离底部 80px 以内视为“正在看最新内容”，否则认为用户主动上翻，暂停跟随
  followLatest = distance < 80
}

function onBodyScroll(): void {
  if (stateFrame) return
  stateFrame = window.requestAnimationFrame(() => {
    stateFrame = 0
    updateScrollState()
  })
}

function isAbort(error: unknown): boolean {
  return !!error && typeof error === 'object' && (error as { name?: string }).name === 'AbortError'
}

function errorText(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'message' in error) {
    const message = (error as { message?: unknown }).message
    if (typeof message === 'string' && message.trim()) return message
  }
  return fallback
}

function syncNarrow(): void {
  isNarrow.value = window.innerWidth < 1180
}

onMounted(() => {
  syncNarrow()
  window.addEventListener('resize', syncNarrow)
  void refreshSessions()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncNarrow)
  if (scrollFrame) window.cancelAnimationFrame(scrollFrame)
  if (stateFrame) window.cancelAnimationFrame(stateFrame)
  scrollFrame = 0
  stateFrame = 0
  stopStreaming()
  window.speechSynthesis?.cancel()
})

</script>

<template>
  <div class="chat container">
    <header class="chat__head">
      <button
        class="chat__rail-btn"
        type="button"
        aria-label="打开对话记录"
        @click="railOpen = true"
      >
        <AppIcon name="menu" :size="18" />
      </button>

      <span class="chat__avatar">
        心
        <i class="chat__avatar-ping" aria-hidden="true" />
      </span>
      <div class="chat__id">
        <h1 class="chat__name">心理健康 AI 助手</h1>
        <small><i class="chat__dot" />在线 · 仅你可见</small>
      </div>

      <div class="chat__tools">
        <button
          class="chat__tool chat__tool--breath"
          type="button"
          aria-label="打开呼吸练习"
          @click="breathOpen = true"
        >
          <AppIcon name="wind" :size="15" />
          <span>呼吸</span>
        </button>
        <button class="chat__tool" type="button" aria-label="导出当前对话" @click="exportSession">
          <AppIcon name="download" :size="15" />
          <span>导出</span>
        </button>
        <button
          class="chat__tool chat__tool--primary"
          type="button"
          aria-label="新建对话"
          @click="newSession"
        >
          <AppIcon name="plus" :size="15" />
          <span>新对话</span>
        </button>
      </div>
    </header>

    <el-alert
      class="chat__safety"
      type="info"
      :closable="false"
      show-icon
      title="心理健康 AI 助手是 AI 陪伴工具，不能替代专业诊断与治疗。紧急情况请拨打 120 / 110 或心理援助热线 12356。"
    />

    <div class="chat__layout">
      <Teleport to="body" :disabled="!isNarrow">
        <Transition name="rail-mask">
          <div v-if="railOpen && isNarrow" class="chat__scrim" aria-hidden="true" @click="railOpen = false" />
        </Transition>

        <aside class="chat__rail" :class="{ 'is-open': railOpen }" aria-label="对话记录">
          <div class="rail__head">
            <div class="rail__heading">
              <p class="rail__eyebrow">History</p>
              <h2 class="rail__title">
                对话记录
                <em>{{ sessions.length }}</em>
              </h2>
            </div>
            <button class="rail__new" type="button" @click="newSession">
              <AppIcon name="plus" :size="14" />
              新建
            </button>
            <button class="rail__close" type="button" aria-label="关闭对话记录" @click="railOpen = false">
              <AppIcon name="close" :size="16" />
            </button>
          </div>

          <div class="rail__list" :aria-busy="sessionsLoading">
            <div
              v-if="sessionsLoading && !sortedSessions.length"
              class="rail__skeleton"
              role="status"
              aria-label="正在加载对话记录"
            >
              <div v-for="index in 3" :key="index" class="rail__skeleton-row" aria-hidden="true">
                <span class="rail__skeleton-line rail__skeleton-line--title" />
                <span class="rail__skeleton-line rail__skeleton-line--preview" />
              </div>
            </div>

            <template v-else>
              <div
                v-for="session in sortedSessions"
                :key="session.id"
                class="session"
                :class="{ 'is-active': session.id === activeId }"
                role="button"
                tabindex="0"
                :aria-current="session.id === activeId ? 'true' : 'false'"
                @click="selectSession(session.id)"
                @keydown.enter.prevent="selectSession(session.id)"
                @keydown.space.prevent="selectSession(session.id)"
              >
                <span class="session__top">
                  <strong>{{ session.title }}</strong>
                  <em>{{ timeLabel(session.updatedAt) }}</em>
                </span>
                <span class="session__preview">{{ previewOf(session) }}</span>
                <button
                  class="session__delete"
                  type="button"
                  aria-label="删除这段对话"
                  title="删除对话"
                  @click.stop="removeSession(session)"
                >
                  <AppIcon name="close" :size="12" />
                </button>
              </div>

              <p v-if="!sortedSessions.length" class="rail__empty">
                还没有对话记录，点右上角「新建」开始。
              </p>
            </template>
          </div>

          <div class="rail__care">
            <AppIcon name="sparkles" :size="14" />
            <p>不用急着说清楚，慢慢来。你随时可以停在这里。</p>
          </div>

          <div class="rail__foot">
            <AppIcon name="shield" :size="14" />
            <span>对话保存在服务器，刷新后仍可继续。</span>
          </div>
        </aside>
      </Teleport>

      <section class="chat__panel">
        <div class="chat__console">
          <div class="console__bar">
            <span class="console__session" :title="activeSession.title">
              <AppIcon name="clock" :size="13" />
              {{ activeSession.title }}
            </span>
            <span class="console__meta">{{ messages.length }} 条消息 · 已保存到服务器</span>
          </div>

          <div
            ref="bodyRef"
            class="chat__body"
            :class="{ 'is-fresh': isFresh && !isTyping }"
            @scroll="onBodyScroll"
          >
            <TransitionGroup name="msg" tag="div" class="chat__stream" aria-live="polite">
              <div
                v-for="message in visibleMessages"
                :key="message.id"
                class="msg"
                :class="`msg--${message.role}`"
              >
                <span v-if="message.role === 'ai'" class="msg__avatar">心</span>
                <div class="msg__content">
                  <p class="msg__bubble">{{ message.text }}</p>

                  <div v-if="message.suggestions?.length" class="suggestions">
                    <span class="suggestions__label">可以接着聊</span>
                    <button
                      v-for="suggestion in message.suggestions"
                      :key="suggestion"
                      type="button"
                      class="suggestion"
                      @click="send(suggestion)"
                    >
                      {{ suggestion }}
                    </button>
                  </div>

                  <div v-if="message.references?.length" class="refs">
                    <span class="refs__label">
                      <AppIcon name="book" :size="12" />
                      知识库依据
                    </span>
                    <span
                      v-for="reference in message.references"
                      :key="reference.chunkId"
                      class="refs__item"
                      :title="reference.text"
                    >
                      {{ reference.title }}
                    </span>
                  </div>

                  <div class="msg__tools">
                    <span class="msg__time">{{ message.time }}</span>
                    <button
                      type="button"
                      class="msg__tool msg__tool--icon"
                      aria-label="复制这条消息"
                      @click="copyMessage(message)"
                    >
                      <AppIcon name="copy" :size="12" />
                      <span>复制</span>
                    </button>
                    <template v-if="message.role === 'ai'">
                      <button
                        type="button"
                        class="msg__tool msg__tool--icon"
                        :class="{ 'is-on': speakingId === message.id }"
                        :aria-label="speakingId === message.id ? '停止朗读' : '朗读这条回复'"
                        @click="speak(message)"
                      >
                        <AppIcon name="volume" :size="12" />
                        <span>{{ speakingId === message.id ? '停止' : '朗读' }}</span>
                      </button>
                      <button
                        v-if="message.id === lastAiId && canRegenerate"
                        type="button"
                        class="msg__tool msg__tool--icon"
                        aria-label="重新生成这条回复"
                        @click="regenerate"
                      >
                        <AppIcon name="refresh" :size="12" />
                        <span>重新生成</span>
                      </button>
                      <span class="msg__tool-sep" aria-hidden="true" />
                      <button
                        type="button"
                        class="msg__tool"
                        :class="{ 'is-on': message.feedback === 'up' }"
                        aria-label="这条回复有帮助"
                        :aria-pressed="message.feedback === 'up'"
                        @click="rate(message, 'up')"
                      >
                        <AppIcon name="thumbUp" :size="12" />
                        <span>有用</span>
                      </button>
                      <button
                        type="button"
                        class="msg__tool"
                        :class="{ 'is-on': message.feedback === 'down' }"
                        aria-label="这条回复不太准"
                        :aria-pressed="message.feedback === 'down'"
                        @click="rate(message, 'down')"
                      >
                        <AppIcon name="thumbDown" :size="12" />
                        <span>不太准</span>
                      </button>
                    </template>
                  </div>
                </div>
              </div>
            </TransitionGroup>

            <Transition name="starter">
              <div v-if="isFresh && !isTyping" class="starters">
                <p class="starters__label">不知道从哪开始？选一个，或者直接说。</p>
                <div class="starters__grid">
                  <button
                    v-for="starter in starters"
                    :key="starter.title"
                    type="button"
                    class="starter"
                    @click="send(starter.prompt)"
                  >
                    <span class="starter__icon">
                      <AppIcon :name="starter.icon" :size="17" />
                    </span>
                    <span class="starter__text">
                      <strong>{{ starter.title }}</strong>
                      <small>{{ starter.hint }}</small>
                    </span>
                    <AppIcon class="starter__arrow" name="arrowRight" :size="14" />
                  </button>
                </div>
              </div>
            </Transition>

            <Transition name="starter">
              <div v-if="isTyping" class="msg msg--ai msg--typing">
                <span class="msg__avatar">心</span>
                <div class="msg__content">
                  <p class="msg__bubble msg__bubble--typing" aria-label="AI 正在输入">
                    <i /><i /><i />
                  </p>
                </div>
              </div>
            </Transition>
          </div>

          <Transition name="jump">
            <button
              v-if="showJump"
              type="button"
              class="chat__jump"
              aria-label="回到最新消息"
              @click="scrollToBottom(true)"
            >
              <AppIcon name="chevronRight" :size="15" />
            </button>
          </Transition>

          <div class="chat__quick">
            <span class="chat__quick-label">可以直接说</span>
            <button
              v-for="prompt in quickPrompts"
              :key="prompt"
              type="button"
              class="chat__quick-chip"
              @click="send(prompt)"
            >
              {{ prompt }}
            </button>
          </div>

          <form class="composer" @submit.prevent="send()">
            <div class="composer__field">
              <el-input
                v-model="draft"
                class="composer__input"
                type="textarea"
                maxlength="500"
                :autosize="{ minRows: 2, maxRows: 5 }"
                resize="none"
                placeholder="说点什么…（Enter 发送，Shift + Enter 换行）"
                @keydown.enter.exact.prevent="send()"
              />
              <div class="composer__meta">
                <span>Enter 发送 · Shift + Enter 换行</span>
                <span class="composer__count" :class="{ 'is-near': draft.length > 450 }">
                  {{ draft.length }}/500
                </span>
              </div>
            </div>
            <button
              class="composer__send"
              type="submit"
              :disabled="!draft.trim() || isTyping"
              aria-label="发送消息"
            >
              <AppIcon name="send" :size="18" />
            </button>
          </form>
        </div>
      </section>

      <aside class="chat__side">
        <section class="side-card side-card--breath">
          <p class="side-card__eyebrow">片刻练习</p>
          <h3 class="side-card__title">4-7-8 呼吸</h3>
          <BreathingCoach compact />
        </section>

        <section class="side-card">
          <p class="side-card__eyebrow">今天的心情</p>
          <h3 class="side-card__title">此刻更接近哪一种？</h3>
          <div class="mood-row">
            <button
              v-for="option in moodOptions"
              :key="option.score"
              type="button"
              class="mood"
              :class="{ 'is-on': moodPicked === option.score }"
              :aria-pressed="moodPicked === option.score"
              :title="option.label"
              @click="pickMood(option)"
            >
              <span class="mood__emoji">{{ option.emoji }}</span>
              <small>{{ option.label }}</small>
            </button>
          </div>
          <p v-if="moodPicked" class="side-card__foot">
            已记下今天的心情。
            <RouterLink to="/front/mood">去情绪日记 →</RouterLink>
          </p>
          <p v-else class="side-card__foot">随手点一个，只保存在本机。</p>
        </section>

        <section class="side-card side-card--safety">
          <p class="side-card__eyebrow">需要更及时的帮助</p>
          <h3 class="side-card__title">如果情绪让你难以承受</h3>
          <p class="side-card__text">你不需要一个人扛。联系身边可信赖的人，或拨打：</p>
          <ul class="hotline">
            <li><strong>12356</strong><span>心理援助热线</span></li>
            <li><strong>120 / 110</strong><span>紧急求助</span></li>
          </ul>
        </section>
      </aside>
    </div>

    <p class="chat__note">
      AI 回复会检索心理健康知识库后生成，并保存到服务器；内容仅供参考，不能替代专业诊断与治疗。
    </p>

    <el-dialog v-model="breathOpen" title="4-7-8 呼吸练习" width="min(420px, 92vw)" class="admin-dialog">
      <BreathingCoach />
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.chat {
  width: min(100% - 48px, 1240px);
  padding: 44px 0 64px;
  // 对话正文高度：左侧历史栏按同一基准定高，才能和右侧卡片一样 sticky 住
  --chat-body-h: clamp(360px, calc(100vh - 400px), 560px);
}

// ---- 顶部身份 + 工具 ----
.chat__head {
  display: flex;
  align-items: center;
  gap: $space-3;
  margin-bottom: $space-4;
}

.chat__rail-btn {
  display: none;
  place-items: center;
  width: 38px;
  height: 38px;
  flex: none;
  border: 1px solid var(--warm-line);
  border-radius: 50%;
  background: var(--warm-paper-65);
  color: var(--warm-text-regular);
  cursor: pointer;
  transition:
    border-color 0.2s $ease-out,
    background 0.2s $ease-out,
    color 0.2s $ease-out;

  &:hover {
    border-color: var(--warm-line-strong);
    color: var(--warm-brand-strong);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

.chat__avatar {
  position: relative;
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  flex: none;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--warm-brand-strong), var(--warm-sage-light));
  color: var(--warm-on-brand);
  font-family: $font-display;
  font-size: 18px;
  font-weight: 700;
  box-shadow: 0 12px 26px -16px rgba(112, 80, 86, 0.7);
}

.chat__avatar-ping {
  position: absolute;
  right: 1px;
  bottom: 1px;
  width: 11px;
  height: 11px;
  border: 2px solid var(--warm-paper);
  border-radius: 50%;
  background: var(--warm-brand);

  &::after {
    content: '';
    position: absolute;
    inset: -3px;
    border: 1px solid rgba(136, 104, 110, 0.5);
    border-radius: 50%;
    animation: pulse-ring 2.4s $ease-out infinite;
  }
}

.chat__id {
  display: grid;
  min-width: 0;
  flex: 1;
  line-height: 1.3;

  small {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: var(--warm-text-muted);
    font-size: 11.5px;
  }
}

.chat__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--warm-brand);
  box-shadow: 0 0 0 3px rgba(136, 104, 110, 0.14);
}

.chat__name {
  margin: 0;
  overflow: hidden;
  color: var(--warm-text);
  font-family: $font-display;
  font-size: 19px;
  font-weight: 600;
  letter-spacing: -0.01em;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.chat__tools {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.chat__tool {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 12px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-pill;
  background: var(--warm-paper-65);
  color: var(--warm-text-regular);
  font: inherit;
  font-size: 12px;
  line-height: 1.2;
  white-space: nowrap;
  cursor: pointer;
  transition:
    transform 0.2s $ease-out,
    border-color 0.2s $ease-out,
    background 0.2s $ease-out,
    color 0.2s $ease-out;

  &:hover {
    transform: translateY(-1px);
    border-color: var(--warm-line-strong);
    color: var(--warm-brand-strong);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

.chat__tool--primary {
  border-color: color-mix(in srgb, var(--warm-brand) 38%, transparent);
  background: color-mix(in srgb, var(--warm-brand) 14%, var(--warm-paper-80));
  color: var(--warm-brand-strong);
  font-weight: 600;
}

.chat__tool--breath {
  display: none;
}

.chat__safety {
  margin-bottom: $space-4;
  border-radius: $radius-sm;

  :deep(.el-alert__title) {
    color: var(--warm-text-muted);
    font-size: 12px;
    line-height: 1.7;
  }

  :deep(.el-alert__icon) {
    color: var(--warm-brand);
  }
}

// ---- 三栏 ----
.chat__layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr) 278px;
  gap: 16px;
  align-items: stretch;
}

// ---- 左：对话历史 ----
.chat__rail {
  position: sticky;
  top: 96px;
  display: flex;
  flex-direction: column;
  // 比中间对话区矮一截，给 sticky 留出滑动距离；会话列表在内部滚动
  height: calc(var(--chat-body-h) + 96px);
  align-self: start;
  min-height: 0;
  padding: 16px 12px 12px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-lg;
  background: linear-gradient(170deg, var(--warm-paper-90), var(--warm-paper-72));
  box-shadow: 0 30px 70px -60px var(--warm-shadow);
}

.rail__head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 0 4px 12px;
}

.rail__heading {
  min-width: 0;
  flex: 1;
}

.rail__eyebrow {
  margin: 0;
  color: var(--warm-brand);
  font-family: $font-mono;
  font-size: 9px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.rail__title {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 2px 0 0;
  font-family: $font-display;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--warm-text);

  em {
    display: inline-grid;
    place-items: center;
    min-width: 19px;
    height: 19px;
    padding-inline: 5px;
    border-radius: $radius-pill;
    background: color-mix(in srgb, var(--warm-brand) 14%, transparent);
    color: var(--warm-brand-strong);
    font-family: $font-mono;
    font-size: 10px;
    font-style: normal;
  }
}

.rail__new {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 5px 9px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 30%, transparent);
  border-radius: $radius-pill;
  background: color-mix(in srgb, var(--warm-brand) 10%, var(--warm-paper-70));
  color: var(--warm-brand-strong);
  font: inherit;
  font-size: 10.5px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background 0.2s $ease-out,
    transform 0.2s $ease-out;

  &:hover {
    transform: translateY(-1px);
    background: color-mix(in srgb, var(--warm-brand) 16%, var(--warm-paper-80));
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

.rail__close {
  display: none;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--warm-line);
  border-radius: 50%;
  background: var(--warm-paper-70);
  color: var(--warm-text-muted);
  cursor: pointer;

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

.rail__list {
  display: grid;
  flex: 1;
  align-content: start;
  gap: 4px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
  scrollbar-width: thin;
  scrollbar-color: rgba(136, 104, 110, 0.28) transparent;
}

.rail__skeleton {
  display: grid;
  gap: 4px;
  padding: 2px 0;
}

.rail__skeleton-row {
  display: grid;
  gap: 8px;
  padding: 12px 11px;
  border: 1px solid var(--warm-line);
  border-radius: 13px;
  background: var(--warm-paper-40);
}

.rail__skeleton-line {
  height: 10px;
  border-radius: 999px;
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--warm-brand) 8%, transparent) 0%,
    color-mix(in srgb, var(--warm-brand) 20%, transparent) 50%,
    color-mix(in srgb, var(--warm-brand) 8%, transparent) 100%
  );
  background-size: 200% 100%;
  animation: rail-shimmer 1.4s ease-in-out infinite;
}

.rail__skeleton-line--title {
  width: 64%;
}

.rail__skeleton-line--preview {
  width: 88%;
  height: 8px;
}

.rail__empty {
  margin: 16px 10px;
  color: var(--warm-text-muted);
  font-size: 12px;
  line-height: 1.8;
  text-align: center;
}

@keyframes rail-shimmer {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .rail__skeleton-line {
    animation: none;
  }
}

.session {
  position: relative;
  display: grid;
  gap: 2px;
  padding: 9px 28px 9px 11px;
  border: 1px solid transparent;
  border-radius: 13px;
  text-align: left;
  cursor: pointer;
  // 会话会随使用不断累积，屏幕外的行跳过布局与绘制；auto 记住真实高度
  content-visibility: auto;
  contain-intrinsic-size: auto 64px;
  transition:
    border-color 0.2s $ease-out,
    background 0.2s $ease-out,
    transform 0.2s $ease-out;

  &:hover,
  &:focus-visible {
    border-color: var(--warm-line);
    background: var(--warm-paper-70);
    outline: none;
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }

  &.is-active {
    border-color: color-mix(in srgb, var(--warm-brand) 32%, transparent);
    background: color-mix(in srgb, var(--warm-brand) 11%, var(--warm-paper-80));
    box-shadow: inset 0 1px 0 var(--warm-paper-90);
  }
}

.session__top {
  display: flex;
  align-items: baseline;
  gap: 6px;

  strong {
    flex: 1;
    min-width: 0;
    color: var(--warm-text-regular);
    font-size: 12.5px;
    font-weight: 600;
    @include truncate;
  }

  em {
    flex: none;
    color: var(--warm-text-muted);
    font-family: $font-mono;
    font-size: 9.5px;
    font-style: normal;
  }
}

.session.is-active .session__top strong {
  color: var(--warm-brand-strong);
}

.session__preview {
  color: var(--warm-text-muted);
  font-size: 10.5px;
  line-height: 1.55;
  @include truncate;
}

.session__delete {
  position: absolute;
  top: 50%;
  right: 5px;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  transform: translateY(-50%);
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--warm-text-muted);
  opacity: 0;
  cursor: pointer;
  transition:
    opacity 0.18s $ease-out,
    background 0.18s $ease-out,
    color 0.18s $ease-out;

  &:hover {
    background: rgba(207, 138, 131, 0.16);
    color: #b85c4c;
  }

  &:focus-visible {
    opacity: 1;
    @include focus-ring($warm-brand);
  }
}

.session:hover .session__delete,
.session:focus-within .session__delete {
  opacity: 1;
}

.rail__care {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 10px;
  padding: 11px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 18%, transparent);
  border-radius: 13px;
  background: color-mix(in srgb, var(--warm-brand) 7%, var(--warm-paper-60));

  svg {
    flex: none;
    margin-top: 1px;
    color: var(--warm-apricot);
  }

  p {
    margin: 0;
    color: var(--warm-text-muted);
    font-size: 10.5px;
    line-height: 1.7;
  }
}

.rail__foot {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-top: 10px;
  padding: 10px 6px 2px;
  border-top: 1px solid var(--warm-line);
  color: var(--warm-text-muted);
  font-size: 10px;
  line-height: 1.65;

  svg {
    flex: none;
    margin-top: 2px;
    color: var(--warm-brand);
  }
}

// ---- 中：对话主体 ----
.chat__console {
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--warm-line);
  border-radius: $radius-lg;
  background: linear-gradient(165deg, var(--warm-paper-95), var(--warm-paper-90));
  box-shadow:
    0 44px 88px -60px var(--warm-shadow),
    inset 0 1px 0 var(--warm-paper-90);
}

.console__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 18px;
  border-bottom: 1px solid var(--warm-line);
  background: var(--warm-paper-60);
  color: var(--warm-text-muted);
  font-size: 10.5px;
}

.console__session {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: var(--warm-text-regular);
  font-size: 11.5px;
  @include truncate;

  svg {
    flex: none;
    color: var(--warm-brand);
  }
}

.console__meta {
  flex: none;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}

.chat__body {
  position: relative;
  height: var(--chat-body-h);
  padding: 22px 20px 16px;
  overflow-y: auto;
  background:
    radial-gradient(ellipse 80% 58% at 50% 0%, rgba(136, 104, 110, 0.08), transparent 70%),
    var(--warm-paper-60);
  scroll-behavior: smooth;
  scrollbar-width: thin;
  scrollbar-color: rgba(136, 104, 110, 0.28) transparent;
}

// 新会话时内容不多，垂直居中更像一个安静的欢迎页
@media (min-width: 769px) {
  .chat__body.is-fresh {
    display: flex;
    flex-direction: column;
    justify-content: center;
  }
}

.chat__stream {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.msg {
  display: flex;
  align-items: flex-end;
  gap: 9px;
}

.msg--user {
  justify-content: flex-end;
}

.msg__avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--warm-brand-strong), var(--warm-sage-light));
  color: var(--warm-on-brand);
  font-family: $font-display;
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 8px 18px -12px rgba(112, 80, 86, 0.6);
}

.msg__content {
  display: grid;
  gap: 5px;
  max-width: 78%;
}

.msg--user .msg__content {
  justify-items: end;
}

.msg__bubble {
  margin: 0;
  padding: 12px 15px;
  border: 1px solid var(--warm-line);
  border-radius: 17px 17px 17px 5px;
  background: var(--warm-paper-94);
  color: var(--warm-text-regular);
  font-size: 13.5px;
  line-height: 1.78;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  box-shadow: 0 10px 26px -22px var(--warm-shadow);
}

.msg--user .msg__bubble {
  border-color: transparent;
  border-radius: 17px 17px 5px 17px;
  background: linear-gradient(135deg, var(--warm-brand), var(--warm-brand-strong));
  color: var(--warm-on-brand);
  font-weight: 500;
  box-shadow: 0 14px 30px -18px rgba(112, 80, 86, 0.65);
}

.msg__time {
  margin-right: 2px;
  color: var(--warm-text-muted);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}

.msg__tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1px;
  min-height: 20px;
  opacity: 0;
  transform: translateY(-2px);
  transition:
    opacity 0.18s $ease-out,
    transform 0.18s $ease-out;
}

.msg:hover .msg__tools,
.msg:focus-within .msg__tools {
  opacity: 1;
  transform: none;
}

.msg--user .msg__tools {
  justify-content: flex-end;
}

.msg__tool {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 6px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--warm-text-muted);
  font: inherit;
  font-size: 10px;
  line-height: 1.2;
  cursor: pointer;
  transition:
    background 0.18s $ease-out,
    color 0.18s $ease-out;

  &:hover {
    background: color-mix(in srgb, var(--warm-brand) 12%, transparent);
    color: var(--warm-brand-strong);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }

  &.is-on {
    background: color-mix(in srgb, var(--warm-brand) 14%, transparent);
    color: var(--warm-brand-strong);
  }
}

.msg__tool--icon {
  padding: 3px 5px;
}

.msg__tool-sep {
  width: 1px;
  height: 10px;
  margin: 0 3px;
  background: var(--warm-line);
}

// 可以接着聊的快捷短句
.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 1px;
}

.suggestions__label {
  width: 100%;
  color: var(--warm-text-muted);
  font-size: 9.5px;
  letter-spacing: 0.08em;
}

.suggestion {
  padding: 5px 10px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 26%, transparent);
  border-radius: $radius-pill;
  background: color-mix(in srgb, var(--warm-brand) 7%, var(--warm-paper-80));
  color: var(--warm-brand-strong);
  font: inherit;
  font-size: 11px;
  line-height: 1.4;
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.18s $ease-out,
    border-color 0.18s $ease-out,
    background 0.18s $ease-out;

  &:hover {
    transform: translateY(-1px);
    border-color: color-mix(in srgb, var(--warm-brand) 46%, transparent);
    background: color-mix(in srgb, var(--warm-brand) 13%, var(--warm-paper-85));
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

// 新会话的话题入口
.starters {
  margin-top: 18px;
  padding: 14px;
  border: 1px dashed color-mix(in srgb, var(--warm-brand) 24%, transparent);
  border-radius: 16px;
  background: var(--warm-paper-50);
}

.starters__label {
  margin: 0 0 10px;
  color: var(--warm-text-muted);
  font-size: 10.5px;
  letter-spacing: 0.04em;
}

.starters__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.starter {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 11px;
  border: 1px solid var(--warm-line);
  border-radius: 12px;
  background: var(--warm-paper-80);
  color: var(--warm-text-regular);
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.2s $ease-out,
    border-color 0.2s $ease-out,
    background 0.2s $ease-out,
    box-shadow 0.2s $ease-out;

  &:hover {
    transform: translateY(-2px);
    border-color: color-mix(in srgb, var(--warm-brand) 38%, transparent);
    background: color-mix(in srgb, var(--warm-brand) 7%, var(--warm-paper-90));
    box-shadow: 0 16px 30px -26px var(--warm-shadow);

    .starter__arrow {
      opacity: 0.9;
      transform: translateX(2px);
    }
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

.starter__icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex: none;
  border-radius: 10px;
  background: color-mix(in srgb, var(--warm-brand) 11%, transparent);
  color: var(--warm-brand-strong);
}

.starter__text {
  display: grid;
  min-width: 0;
  flex: 1;
  line-height: 1.35;

  strong {
    color: var(--warm-text);
    font-size: 12px;
    font-weight: 600;
  }

  small {
    margin-top: 2px;
    color: var(--warm-text-muted);
    font-size: 9.5px;
    @include truncate;
  }
}

.starter__arrow {
  flex: none;
  color: var(--warm-brand);
  opacity: 0.35;
  transition:
    opacity 0.2s $ease-out,
    transform 0.2s $ease-out;
}

.msg__bubble--typing {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 15px 17px;

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

// ---- 滚动到底部 ----
.chat__jump {
  position: absolute;
  right: 18px;
  bottom: 174px;
  z-index: 3;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--warm-line);
  border-radius: 50%;
  background: var(--warm-paper);
  color: var(--warm-brand-strong);
  box-shadow: 0 12px 26px -16px var(--warm-shadow);
  cursor: pointer;
  transition:
    transform 0.2s $ease-out,
    border-color 0.2s $ease-out;

  svg {
    transform: rotate(90deg);
  }

  &:hover {
    transform: translateY(-2px);
    border-color: var(--warm-line-strong);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

// ---- 快捷话题 / 输入 ----
.chat__quick {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  padding: 12px 18px;
  border-top: 1px solid var(--warm-line);
  background: var(--warm-paper-50);
}

.chat__quick-label {
  margin-right: 2px;
  color: var(--warm-text-muted);
  font-size: 10.5px;
  letter-spacing: 0.1em;
}

.chat__quick-chip {
  padding: 5px 11px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-pill;
  background: var(--warm-paper-80);
  color: var(--warm-text-regular);
  font: inherit;
  font-size: 11.5px;
  cursor: pointer;
  transition:
    transform 0.2s $ease-out,
    border-color 0.2s $ease-out,
    background 0.2s $ease-out,
    color 0.2s $ease-out;

  &:hover {
    transform: translateY(-2px);
    border-color: color-mix(in srgb, var(--warm-brand) 40%, transparent);
    background: color-mix(in srgb, var(--warm-brand) 10%, var(--warm-paper-80));
    color: var(--warm-brand-strong);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

.composer {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 18px 14px;
  border-top: 1px solid var(--warm-line);
  background: var(--warm-paper-60);
}

.composer__field {
  flex: 1;
  min-width: 0;
  padding: 3px 10px 0;
  border: 1px solid transparent;
  border-radius: 14px;
  background: var(--warm-paper-70);
  transition:
    border-color 0.2s $ease-out,
    background 0.2s $ease-out,
    box-shadow 0.2s $ease-out;

  &:focus-within {
    border-color: color-mix(in srgb, var(--warm-brand) 40%, transparent);
    background: var(--warm-paper-90);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--warm-brand) 10%, transparent);
  }
}

.composer__input {
  :deep(.el-textarea__inner) {
    padding: 5px 2px;
    background: transparent;
    box-shadow: none;
    font-size: 13.5px;
    line-height: 1.7;
  }

  :deep(.el-textarea__inner:focus) {
    box-shadow: none;
  }
}

.composer__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 2px 4px;
  color: var(--warm-text-muted);
  font-size: 9.5px;
  line-height: 1.4;
}

.composer__count {
  flex: none;
  font-family: $font-mono;
  font-variant-numeric: tabular-nums;

  &.is-near {
    color: var(--warm-apricot);
  }
}

.composer__send {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  flex: none;
  border: 0;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--warm-brand-strong), var(--warm-brand));
  color: var(--warm-on-brand);
  cursor: pointer;
  box-shadow: 0 14px 26px -16px rgba(112, 80, 86, 0.7);
  transition:
    transform 0.22s $ease-out,
    opacity 0.22s $ease-out,
    box-shadow 0.22s $ease-out;

  &:not(:disabled):hover {
    transform: translateY(-2px) scale(1.03);
    box-shadow: 0 20px 34px -16px rgba(112, 80, 86, 0.8);
  }

  &:disabled {
    opacity: 0.42;
    cursor: not-allowed;
    box-shadow: none;
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

// ---- 右：陪伴工具 ----
.chat__side {
  position: sticky;
  top: 96px;
  display: grid;
  gap: 12px;
  align-self: start;
  // 和左侧「对话记录」一样吸顶；三张卡片在栏内一起滚动，避免最下面一张被视口截断
  max-height: calc(100vh - 112px);
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 4px;
  scrollbar-width: thin;
  scrollbar-color: rgba(136, 104, 110, 0.28) transparent;
}

.side-card {
  padding: 16px;
  border: 1px solid var(--warm-line);
  border-radius: 18px;
  background: linear-gradient(165deg, var(--warm-paper-92), var(--warm-paper-75));
  box-shadow: 0 24px 60px -52px var(--warm-shadow);
}

.side-card--breath {
  text-align: center;
}

.side-card__eyebrow {
  margin: 0 0 3px;
  color: var(--warm-brand);
  font-family: $font-mono;
  font-size: 9px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.side-card__title {
  margin: 0 0 12px;
  color: var(--warm-text);
  font-family: $font-display;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.side-card__text {
  margin: 0;
  color: var(--warm-text-muted);
  font-size: 11px;
  line-height: 1.75;
}

.side-card__foot {
  margin: 10px 0 0;
  color: var(--warm-text-muted);
  font-size: 10px;
  line-height: 1.7;

  a {
    color: var(--warm-brand-strong);
    font-weight: 600;
    text-decoration: underline;
    text-underline-offset: 3px;

    &:focus-visible {
      @include focus-ring($warm-brand);
    }
  }
}

.mood-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 2px;
}

.mood {
  display: grid;
  justify-items: center;
  gap: 3px;
  padding: 7px 1px 6px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  transition:
    transform 0.18s $ease-out,
    border-color 0.18s $ease-out,
    background 0.18s $ease-out;

  &:hover {
    transform: translateY(-1px);
    background: color-mix(in srgb, var(--warm-brand) 9%, transparent);
  }

  &.is-on {
    border-color: color-mix(in srgb, var(--warm-brand) 34%, transparent);
    background: color-mix(in srgb, var(--warm-brand) 13%, transparent);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }

  small {
    color: var(--warm-text-muted);
    font-size: 8.5px;
    line-height: 1.2;
    white-space: nowrap;
    transform: scale(0.94);
  }

  &.is-on small {
    color: var(--warm-brand-strong);
    font-weight: 600;
  }
}

.mood__emoji {
  font-size: 19px;
  line-height: 1;
  filter: grayscale(0.12);
}

.hotline {
  display: grid;
  gap: 0;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;

  li {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 8px;
    padding: 7px 0;
    border-top: 1px dashed var(--warm-line);
  }

  strong {
    color: var(--warm-brand-strong);
    font-family: $font-mono;
    font-size: 12px;
    letter-spacing: 0.02em;
  }

  span {
    color: var(--warm-text-muted);
    font-size: 10px;
  }
}

.chat__note {
  margin: 14px 0 0;
  color: var(--warm-text-muted);
  font-size: 11px;
  text-align: center;
}

// ---- 动效 ----
.msg-enter-active {
  transition:
    opacity 0.3s $ease-out-expo,
    transform 0.3s $ease-out-expo;
}

.msg-leave-active {
  transition:
    opacity 0.18s $ease-in,
    transform 0.18s $ease-in;
}

.msg-enter-from {
  opacity: 0;
  transform: translateY(12px) scale(0.97);
}

.msg-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.starter-enter-active {
  transition:
    opacity 0.3s $ease-out,
    transform 0.3s $ease-out-expo;
}

.starter-leave-active {
  transition: opacity 0.18s $ease-in;
}

.starter-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.starter-leave-to {
  opacity: 0;
}

.jump-enter-active,
.jump-leave-active {
  transition:
    opacity 0.18s $ease-out,
    transform 0.18s $ease-out;
}

.jump-enter-from,
.jump-leave-to {
  opacity: 0;
  transform: translateY(6px) scale(0.94);
}

.rail-mask-enter-active,
.rail-mask-leave-active {
  transition: opacity 0.22s $ease-out;
}

.rail-mask-enter-from,
.rail-mask-leave-to {
  opacity: 0;
}

// ---- 响应式 ----
@media (max-width: 1199px) {
  .chat__layout {
    grid-template-columns: 232px minmax(0, 1fr);
  }

  .chat__side {
    display: none;
  }

  .chat__tool--breath {
    display: inline-flex;
  }
}

@include media-down($bp-lg) {
  .chat {
    padding-top: 28px;
  }

  .chat__layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .chat__rail-btn {
    display: inline-grid;
  }

  .chat__rail {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 70;
    width: min(320px, 86vw);
    max-height: none;
    border-radius: 0 $radius-lg $radius-lg 0;
    transform: translateX(-104%);
    box-shadow: 24px 0 60px -30px var(--warm-shadow);
    transition: transform 0.34s $ease-out-expo;
    pointer-events: none;

    &.is-open {
      transform: none;
      pointer-events: auto;
    }
  }

  .rail__close {
    display: inline-grid;
  }

  .chat__scrim {
    position: fixed;
    inset: 0;
    z-index: 65;
    background: var(--warm-overlay);
  }
}

@include media-down($bp-md) {
  .chat {
    padding: 22px 0 44px;
  }

  .chat__body {
    height: 56vh;
    min-height: 320px;
    padding: 18px 14px 14px;
  }

  .chat__quick {
    padding: 10px 14px;
  }

  .composer {
    padding: 10px 14px 12px;
  }

  .chat__jump {
    bottom: 168px;
    right: 14px;
  }
}

@include media-down($bp-sm) {
  .chat__head {
    gap: 8px;
  }

  .chat__rail-btn {
    width: 34px;
    height: 34px;
  }

  .chat__avatar {
    width: 42px;
    height: 42px;
    font-size: 16px;
  }

  .chat__name {
    font-size: 15.5px;
  }

  .chat__tools {
    gap: 6px;
  }

  .chat__tool {
    padding: 7px 9px;
  }

  .chat__tool span {
    display: none;
  }

  .msg__content {
    max-width: 86%;
  }

  .starters {
    padding: 12px;
  }

  .starters__grid {
    grid-template-columns: 1fr;
  }

  .chat__quick-label {
    width: 100%;
  }

  .msg__tools {
    opacity: 0.75;
    transform: none;
  }

  // 窄屏放不下所有文案：复制/朗读/重新生成只留图标，反馈按钮保留文字
  .msg__tool--icon {
    padding: 6px;

    span {
      display: none;
    }
  }
}

.refs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 2px;

  &__label {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 11px;
    letter-spacing: 0.04em;
    color: var(--warm-text-muted);
  }

  &__item {
    max-width: 260px;
    padding: 3px 9px;
    overflow: hidden;
    border: 1px dashed var(--warm-line);
    border-radius: 999px;
    background: var(--warm-paper-94);
    color: var(--warm-text-regular);
    font-size: 11px;
    line-height: 1.5;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

@media (max-width: 480px) {
  .chat__avatar {
    display: none;
  }
}

@media (hover: none) {
  .session__delete {
    opacity: 0.55;
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat__body {
    scroll-behavior: auto;
  }

  .msg-enter-active,
  .starter-enter-active,
  .jump-enter-active,
  .rail-mask-enter-active {
    transition-duration: 0.01ms;
  }
}
</style>
