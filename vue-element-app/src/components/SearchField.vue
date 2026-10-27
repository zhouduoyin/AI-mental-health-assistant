<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

import { debounce } from '@/utils/timing'

/**
 * 带防抖的搜索框：输入即时回显，对外只按 wait 毫秒发一次变更，
 * 避免每敲一个字都触发列表过滤与重新渲染。
 */
const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    wait?: number
  }>(),
  { placeholder: '搜索', wait: 300 },
)

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const inner = ref(props.modelValue)

const flush = debounce((value: string): void => {
  emit('update:modelValue', value)
}, props.wait)

watch(inner, (value) => flush(value))

// 父组件重置筛选条件时，同步回内部值
watch(
  () => props.modelValue,
  (value) => {
    if (value !== inner.value) inner.value = value
  },
)

// 点清除按钮时立即生效，不用再等防抖
function handleClear(): void {
  flush.cancel()
  emit('update:modelValue', '')
}

onBeforeUnmount(flush.cancel)
</script>

<template>
  <el-input
    v-model="inner"
    :placeholder="placeholder"
    clearable
    @clear="handleClear"
  />
</template>
