<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'

import AppIcon from '@/components/AppIcon.vue'

/**
 * 4-7-8 呼吸练习。纯前端计时器 + CSS 动画：
 * - 光晕用 19s 的 keyframes 循环，暂停时 animation-play-state 同步暂停；
 * - 文字倒计时由 setInterval 驱动，和光晕节奏一一对应。
 */
const props = withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })

const phases = [
  { key: 'inhale', label: '吸气', hint: '用鼻子慢慢吸气，让肩膀放松。', seconds: 4 },
  { key: 'hold', label: '屏息', hint: '轻轻停住，不用用力。', seconds: 7 },
  { key: 'exhale', label: '呼气', hint: '用嘴缓缓呼气，像叹一口气。', seconds: 8 },
] as const

const phaseIndex = ref(0)
const remaining = ref<number>(phases[0].seconds)
const started = ref(false)
const running = ref(false)
const rounds = ref(0)

let timer: ReturnType<typeof setInterval> | null = null

const phase = computed(() => phases[phaseIndex.value])
const phaseLabel = computed(() => (started.value ? phase.value.label : '准备'))
const hint = computed(() => (started.value ? phase.value.hint : '跟着光晕的节奏呼吸，三轮之后会慢慢平静下来。'))

function clearTimer(): void {
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
}

function tick(): void {
  remaining.value -= 1
  if (remaining.value > 0) return

  const next = (phaseIndex.value + 1) % phases.length
  if (next === 0) rounds.value += 1
  phaseIndex.value = next
  remaining.value = phases[next].seconds
}

function resume(): void {
  clearTimer()
  running.value = true
  timer = setInterval(tick, 1000)
}

function start(): void {
  started.value = true
  phaseIndex.value = 0
  remaining.value = phases[0].seconds
  rounds.value = 0
  resume()
}

function pause(): void {
  running.value = false
  clearTimer()
}

function reset(): void {
  clearTimer()
  running.value = false
  started.value = false
  phaseIndex.value = 0
  remaining.value = phases[0].seconds
  rounds.value = 0
}

function toggle(): void {
  if (!started.value) start()
  else if (running.value) pause()
  else resume()
}

onBeforeUnmount(clearTimer)
</script>

<template>
  <div class="breath" :class="{ 'breath--compact': props.compact }">
    <div class="breath__stage">
      <span
        class="breath__halo"
        :class="{ 'is-active': started, 'is-running': running }"
        aria-hidden="true"
      />
      <span class="breath__core">
        <strong>{{ started ? remaining : 4 }}</strong>
        <small>{{ phaseLabel }}</small>
      </span>
    </div>

    <p class="breath__hint">{{ hint }}</p>

    <div class="breath__meta">
      <span>{{ started ? `第 ${rounds + 1} 轮 · 4-7-8` : '4 秒吸气 · 7 秒屏息 · 8 秒呼气' }}</span>
      <button v-if="started" type="button" class="breath__reset" @click="reset">重置</button>
    </div>

    <button type="button" class="breath__btn" @click="toggle">
      <AppIcon :name="running ? 'pause' : 'play'" :size="15" />
      {{ running ? '暂停' : started ? '继续' : '开始练习' }}
    </button>
  </div>
</template>

<style scoped lang="scss">
.breath {
  --orb-halo: 156px;
  --orb-core: 96px;

  display: grid;
  justify-items: center;
  gap: 10px;
  text-align: center;
}

.breath--compact {
  --orb-halo: 134px;
  --orb-core: 86px;

  gap: 8px;
}

.breath__stage {
  position: relative;
  display: grid;
  place-items: center;
  width: var(--orb-halo);
  height: var(--orb-halo);
}

.breath__halo {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background:
    radial-gradient(circle at 38% 32%, color-mix(in srgb, var(--warm-brand) 34%, transparent), transparent 62%),
    radial-gradient(circle at 68% 72%, color-mix(in srgb, var(--warm-apricot) 26%, transparent), transparent 60%),
    radial-gradient(circle, color-mix(in srgb, var(--warm-sage-light) 20%, transparent), transparent 72%);
  box-shadow:
    0 0 0 1px color-mix(in srgb, var(--warm-brand) 12%, transparent),
    0 18px 44px -26px var(--warm-glow);
  transform: scale(0.72);
  opacity: 0.85;
  transition:
    transform 0.8s $ease-out,
    opacity 0.3s $ease-out;

  &.is-active {
    animation: breath-478 19s linear infinite;
    animation-play-state: paused;
  }

  &.is-running {
    animation-play-state: running;
  }
}

// 4s 吸气放大 → 11s 屏息保持 → 19s 呼气缩小
@keyframes breath-478 {
  0% {
    transform: scale(0.72);
  }

  21.05% {
    transform: scale(1);
  }

  57.9% {
    transform: scale(1);
  }

  100% {
    transform: scale(0.72);
  }
}

.breath__core {
  position: relative;
  z-index: 1;
  display: grid;
  place-content: center;
  width: var(--orb-core);
  height: var(--orb-core);
  border: 1px solid color-mix(in srgb, var(--warm-brand) 18%, transparent);
  border-radius: 50%;
  background: var(--warm-paper-90);
  box-shadow:
    inset 0 1px 0 var(--warm-paper-92),
    0 12px 30px -22px var(--warm-shadow);

  strong {
    color: var(--warm-brand-strong);
    font-family: $font-display;
    font-size: 28px;
    font-weight: 600;
    line-height: 1.1;
    font-variant-numeric: tabular-nums;
  }

  small {
    margin-top: 2px;
    color: var(--warm-text-muted);
    font-size: 10.5px;
    letter-spacing: 0.16em;
  }
}

.breath--compact .breath__core strong {
  font-size: 24px;
}

.breath__hint {
  max-width: 240px;
  margin: 0;
  color: var(--warm-text-muted);
  font-size: 11.5px;
  line-height: 1.75;
}

.breath__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 18px;
  color: var(--warm-text-muted);
  font-size: 10.5px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
}

.breath__reset {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--warm-brand-strong);
  font: inherit;
  font-size: 10.5px;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

.breath__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-width: 132px;
  padding: 9px 18px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 30%, transparent);
  border-radius: $radius-pill;
  background: color-mix(in srgb, var(--warm-brand) 12%, var(--warm-paper-90));
  color: var(--warm-brand-strong);
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition:
    transform 0.2s $ease-out,
    border-color 0.2s $ease-out,
    background 0.2s $ease-out;

  &:hover {
    transform: translateY(-1px);
    border-color: color-mix(in srgb, var(--warm-brand) 48%, transparent);
    background: color-mix(in srgb, var(--warm-brand) 18%, var(--warm-paper-90));
  }

  &:active {
    transform: translateY(0);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }
}

@media (prefers-reduced-motion: reduce) {
  .breath__halo,
  .breath__halo.is-active {
    animation: none !important;
    transform: scale(0.88);
  }
}
</style>
