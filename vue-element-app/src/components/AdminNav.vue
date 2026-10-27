<script setup lang="ts">
import { useRoute } from 'vue-router'

import AppIcon from '@/components/AppIcon.vue'
import { adminNav } from '@/data/nav'

/**
 * 后台导航：侧栏与移动端抽屉共用同一份菜单与高亮逻辑。
 * 抽屉里监听 select 关闭自身，侧栏里不传监听即可。
 * collapsed = true 时交给 el-menu 折叠，只显示图标 + 悬浮提示。
 */
defineProps<{ ariaLabel?: string; collapsed?: boolean }>()
const emit = defineEmits<{ select: [] }>()

const route = useRoute()
</script>

<template>
  <nav class="admin__nav" :class="{ 'admin__nav--collapsed': collapsed }" :aria-label="ariaLabel ?? '后台导航'">
    <el-menu
      :default-active="route.path"
      :collapse="collapsed"
      :collapse-transition="false"
      router
      class="admin__menu"
      @select="emit('select')"
    >
      <el-menu-item v-for="item in adminNav" :key="item.to" :index="item.to" :title="item.label">
        <AppIcon :name="item.icon" :size="18" />
        <span>{{ item.label }}</span>
      </el-menu-item>
    </el-menu>
  </nav>
</template>

<style scoped lang="scss">
// 暖纸侧栏里的菜单主题：只改 Element Plus 变量与 item 形状，不碰内部结构类
.admin__nav--collapsed .admin__menu {
  padding: 0;
}

.admin__menu {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--warm-text-regular);
  --el-menu-active-color: var(--warm-brand-strong);
  --el-menu-hover-bg-color: color-mix(in srgb, var(--warm-brand) 8%, transparent);

  padding: 0 2px;
  border-right: none;

  :deep(.el-menu-item) {
    position: relative;
    height: 44px;
    margin: 4px 0;
    padding: 0 12px;
    gap: 10px;
    border-radius: 11px;
    color: var(--warm-text-regular);
    font-size: 14px;
    font-weight: 500;
    transition:
      background $duration-fast $ease-out,
      color $duration-fast $ease-out,
      transform $duration-fast $ease-spring;

    svg {
      color: var(--warm-text-muted);
      transition: color $duration-fast $ease-out;
    }

    &:hover {
      transform: translateX(2px);
      color: var(--warm-text);

      svg {
        color: var(--warm-brand-strong);
      }
    }

    &.is-active {
      background: linear-gradient(
        90deg,
        color-mix(in srgb, var(--warm-brand) 16%, transparent),
        color-mix(in srgb, var(--warm-brand) 4%, transparent)
      );
      box-shadow:
        inset 0 0 0 1px color-mix(in srgb, var(--warm-brand) 22%, transparent),
        0 10px 22px -18px color-mix(in srgb, var(--warm-brand) 60%, transparent);
      color: var(--warm-brand-strong);
      font-weight: 600;

      // 左侧绿色指示条：比整块高亮更克制
      &::before {
        content: '';
        position: absolute;
        top: 50%;
        left: 0;
        width: 2px;
        height: 18px;
        border-radius: $radius-pill;
        background: var(--warm-brand);
        transform: translateY(-50%);
      }

      svg {
        color: var(--warm-brand-strong);
      }
    }
  }
}

.admin__nav--collapsed .admin__menu :deep(.el-menu-item.is-active)::before {
  display: none;
}
</style>
