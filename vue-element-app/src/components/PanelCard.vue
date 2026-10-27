<script setup lang="ts">
/**
 * 后台通用面板：白底卡片 + 标题/描述 + 右侧操作区。
 * 样式复用全局 `.panel` / `.panel__head`，页面只负责内容与操作按钮。
 */
withDefaults(defineProps<{ title?: string; description?: string }>(), {
  title: '',
  description: '',
})
</script>

<template>
  <section class="panel" v-spotlight>
    <header v-if="title || $slots.actions" class="panel__head">
      <div>
        <h2 v-if="title">{{ title }}</h2>
        <p v-if="description">{{ description }}</p>
      </div>
      <slot name="actions" />
    </header>

    <slot />
  </section>
</template>

<style scoped lang="scss">
.panel {
  position: relative;
  overflow: hidden;
  transition:
    border-color 0.35s $ease-out,
    box-shadow 0.35s $ease-out;

  // 顶部一条柔光高光，让纸面像被日光扫到边缘
  &::after {
    content: '';
    position: absolute;
    top: 0;
    left: 8%;
    right: 8%;
    height: 1px;
    background: linear-gradient(90deg, transparent, rgba(136, 104, 110, 0.32), rgba(209, 138, 92, 0.22), transparent);
    opacity: 0.7;
    pointer-events: none;
  }

  &:hover {
    border-color: rgba(136, 104, 110, 0.24);
    box-shadow:
      0 34px 76px -54px rgba(74, 64, 46, 0.42),
      0 0 44px -36px rgba(136, 104, 110, 0.42);
  }
}

.panel__head {
  position: relative;
  z-index: 1;

  h2 {
    display: flex;
    align-items: center;
    gap: 10px;

    &::before {
      content: '';
      width: 3px;
      height: 16px;
      border-radius: $radius-pill;
      background: linear-gradient(180deg, var(--warm-brand), rgba(136, 104, 110, 0.16));
    }
  }
}
</style>
