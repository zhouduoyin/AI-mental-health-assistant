<script setup lang="ts">
withDefaults(
  defineProps<{ eyebrow?: string; title: string; description?: string }>(),
  { eyebrow: '', description: '' },
)
</script>

<template>
  <header class="page-head">
    <div class="page-head__text">
      <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p v-if="description" class="page-head__desc">{{ description }}</p>
    </div>

    <div v-if="$slots.actions" class="page-head__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped lang="scss">
.page-head {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: $space-4;
  margin-bottom: $space-5;
  padding-bottom: 20px;

  // 标题与内容之间用一条向右侧淡出的暖线收口
  &::after {
    content: '';
    position: absolute;
    right: 0;
    bottom: 0;
    left: 0;
    height: 1px;
    background: linear-gradient(90deg, rgba(136, 104, 110, 0.26), rgba(92, 77, 52, 0.06) 46%, transparent);
    pointer-events: none;
  }
}

.page-head__text {
  position: relative;
  min-width: 0;

  > * {
    animation: page-head-in 0.6s $ease-out-expo both;
  }

  .eyebrow {
    animation-delay: 20ms;
  }

  h1 {
    animation-delay: 80ms;
  }

  .page-head__desc {
    animation-delay: 140ms;
  }
}

.page-head__text h1 {
  margin: 8px 0 0;
  background: linear-gradient(96deg, var(--warm-text) 0%, var(--warm-brand-strong) 58%, var(--warm-apricot) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  font-family: $font-display;
  font-size: clamp(26px, 2.5vw, 32px);
  font-weight: $font-weight-semibold;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.page-head__desc {
  max-width: 620px;
  margin: 8px 0 0;
  color: var(--warm-text-muted);
  font-size: 13px;
}

.page-head__actions {
  display: flex;
  flex-wrap: wrap;
  gap: $space-2;
  animation: page-head-in 0.6s $ease-out-expo 0.16s both;
}

@keyframes page-head-in {
  from {
    opacity: 0;
    transform: translate3d(0, 12px, 0);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

@include media-down($bp-sm) {
  .page-head {
    align-items: flex-start;
    padding-bottom: 16px;
  }

  .page-head__actions {
    width: 100%;
  }
}
</style>
