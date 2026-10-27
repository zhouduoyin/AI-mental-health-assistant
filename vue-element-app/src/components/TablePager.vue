<script setup lang="ts">
import { computed, watch } from 'vue'

import AppIcon from '@/components/AppIcon.vue'

/**
 * 后台表格分页条：左侧显示区间与总数，右侧页码 + 每页条数。
 * 页码超过 7 页时折叠成「1 … 4 5 6 … 12」的形式。
 */
const props = withDefaults(
  defineProps<{
    total: number
    page: number
    pageSize: number
    pageSizes?: number[]
    unit?: string
  }>(),
  { pageSizes: () => [10, 20, 50], unit: '条' },
)

const emit = defineEmits<{
  'update:page': [value: number]
  'update:pageSize': [value: number]
}>()

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

const start = computed(() => (props.total ? (props.page - 1) * props.pageSize + 1 : 0))
const end = computed(() => Math.min(props.page * props.pageSize, props.total))

const pages = computed<(number | '...')[]>(() => {
  const count = pageCount.value
  const current = props.page

  if (count <= 7) return Array.from({ length: count }, (_, index) => index + 1)

  const list: (number | '...')[] = [1]
  const left = Math.max(2, current - 1)
  const right = Math.min(count - 1, current + 1)

  if (left > 2) list.push('...')
  for (let page = left; page <= right; page += 1) list.push(page)
  if (right < count - 1) list.push('...')
  list.push(count)

  return list
})

function go(page: number): void {
  const next = Math.min(Math.max(page, 1), pageCount.value)
  if (next !== props.page) emit('update:page', next)
}

function changeSize(size: number | string): void {
  const next = Number(size)
  if (!next || Number.isNaN(next)) return

  emit('update:pageSize', next)
  if (props.page > Math.max(1, Math.ceil(props.total / next))) emit('update:page', 1)
}

// 数据变少（例如删除末页记录）时自动回收到最后一页
watch(
  () => [props.total, props.pageSize] as const,
  () => {
    if (props.page > pageCount.value) emit('update:page', pageCount.value)
  },
)
</script>

<template>
  <div class="pager">
    <p class="pager__meta">
      <span class="pager__range">
        <strong>{{ start }}</strong>
        <i>–</i>
        <strong>{{ end }}</strong>
      </span>
      <span class="pager__total">共 {{ total }} {{ unit }}</span>
    </p>

    <div class="pager__tools">
      <label class="pager__size">
        <span>每页</span>
        <el-select
          :model-value="pageSize"
          size="small"
          class="pager__select"
          aria-label="每页条数"
          @update:model-value="changeSize"
        >
          <el-option v-for="size in pageSizes" :key="size" :label="`${size} 条`" :value="size" />
        </el-select>
      </label>

      <nav class="pager__nav" aria-label="表格分页">
        <button
          type="button"
          class="pager__step"
          :disabled="page <= 1"
          aria-label="上一页"
          @click="go(page - 1)"
        >
          <AppIcon name="chevronLeft" :size="15" />
        </button>

        <template v-for="(item, index) in pages" :key="`${item}-${index}`">
          <span v-if="item === '...'" class="pager__gap" aria-hidden="true">…</span>
          <button
            v-else
            type="button"
            class="pager__page"
            :class="{ 'is-active': item === page }"
            :aria-current="item === page ? 'page' : undefined"
            @click="go(item)"
          >
            {{ item }}
          </button>
        </template>

        <button
          type="button"
          class="pager__step"
          :disabled="page >= pageCount"
          aria-label="下一页"
          @click="go(page + 1)"
        >
          <AppIcon name="chevronRight" :size="15" />
        </button>
      </nav>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px 18px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--warm-line);
}

.pager__meta {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 0;
  color: var(--warm-text-muted);
  font-size: 12px;
}

.pager__range {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  color: var(--warm-brand-strong);
  font-family: $font-mono;
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.03em;

  strong {
    font-weight: 600;
  }

  i {
    color: rgba(92, 77, 52, 0.48);
    font-style: normal;
  }
}

.pager__total {
  padding-left: 10px;
  border-left: 1px solid var(--warm-line);
  letter-spacing: 0.04em;
}

.pager__tools {
  display: flex;
  align-items: center;
  gap: 14px;
}

.pager__size {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--warm-text-muted);
  font-size: 11.5px;
  white-space: nowrap;
}

.pager__select {
  width: 100px;
}

.pager__nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pager__page,
.pager__step {
  display: grid;
  place-items: center;
  min-width: 30px;
  height: 30px;
  padding: 0 8px;
  border: 1px solid var(--warm-line);
  border-radius: 9px;
  background: var(--warm-paper-72);
  color: var(--warm-text-regular);
  font-family: $font-mono;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition:
    transform 0.16s $ease-out,
    border-color 0.16s $ease-out,
    background 0.16s $ease-out,
    color 0.16s $ease-out,
    box-shadow 0.16s $ease-out;

  &:hover:not(:disabled):not(.is-active) {
    transform: translateY(-1px);
    border-color: rgba(136, 104, 110, 0.34);
    background: rgba(136, 104, 110, 0.09);
    color: var(--warm-brand-strong);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }

  &:disabled {
    opacity: 0.32;
    cursor: not-allowed;
  }
}

.pager__page.is-active {
  border-color: transparent;
  background: linear-gradient(135deg, var(--warm-brand-strong), var(--warm-sage-light));
  box-shadow:
    0 0 0 1px rgba(112, 80, 86, 0.18),
    0 8px 20px -12px rgba(112, 80, 86, 0.65);
  color: var(--warm-on-brand);
  font-weight: 600;
}

.pager__step {
  color: var(--warm-text-muted);
}

.pager__gap {
  display: grid;
  place-items: center;
  min-width: 18px;
  color: rgba(92, 77, 52, 0.4);
  font-size: 12px;
}

@include media-down($bp-sm) {
  .pager {
    justify-content: center;
  }

  .pager__meta {
    width: 100%;
    justify-content: center;
  }

  .pager__tools {
    flex-wrap: wrap;
    justify-content: center;
  }
}
</style>
