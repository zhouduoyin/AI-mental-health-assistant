<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { TableInstance } from 'element-plus'

import type { Article, ArticleCategory, ArticleDraft, ArticleStatus } from '@/types'
import type { BatchResult } from '@/api/batch'
import {
  batchChangeArticleStatus,
  batchDeleteArticles,
  changeArticleStatus,
  createArticle,
  deleteArticle,
  listAdminArticles,
  toArticlePayload,
  updateArticle,
} from '@/api/articles'
import AppIcon from '@/components/AppIcon.vue'
import PageHeader from '@/components/PageHeader.vue'
import PanelCard from '@/components/PanelCard.vue'
import SearchField from '@/components/SearchField.vue'
import TablePager from '@/components/TablePager.vue'
import { useActionGuard } from '@/composables/useActionGuard'
import { useAuth } from '@/composables/useAuth'
import { articleCategories } from '@/data/catalog'

const { user } = useAuth()
const author = computed(() => user.value?.name ?? '林医生')

const rows = ref<Article[]>([])
const loading = ref(true)
const selected = ref<Article[]>([])
const batchBusy = ref(false)
const tableRef = ref<TableInstance>()

async function loadRows(): Promise<void> {
  loading.value = true
  try {
    rows.value = await listAdminArticles()
    selected.value = []
    tableRef.value?.clearSelection()
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '文章列表加载失败，请稍后重试',
      customClass: 'warm-toast',
    })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadRows()
})

const keyword = ref('')
const category = ref<ArticleCategory | ''>('')
const status = ref<ArticleStatus | ''>('')

const dialog = ref(false)
const editing = ref<Article | null>(null)
const form = reactive<ArticleDraft>({
  title: '',
  category: '焦虑',
  summary: '',
  content: '',
  status: '草稿',
})

const filtered = computed<Article[]>(() =>
  rows.value.filter((row) => {
    if (keyword.value && !row.title.includes(keyword.value.trim())) return false
    if (category.value && row.category !== category.value) return false
    if (status.value && row.status !== status.value) return false
    return true
  }),
)

// 表格分页：筛选条件变化时回到第一页
const page = ref(1)
const pageSize = ref(20)
const paged = computed(() =>
  filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
)

watch([keyword, category, status], () => {
  page.value = 1
})

const dialogTitle = computed(() => (editing.value ? '编辑文章' : '新建文章'))

function openCreate(): void {
  editing.value = null
  Object.assign(form, { title: '', category: '焦虑', summary: '', content: '', status: '草稿' })
  dialog.value = true
}

function openEdit(row: Article): void {
  editing.value = row
  Object.assign(form, {
    title: row.title,
    category: row.category,
    summary: row.summary,
    content: row.content.join('\n\n'),
    status: row.status,
  })
  dialog.value = true
}

async function saveArticle(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请先填写文章标题')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('正文至少需要一段内容')
    return
  }

  const payload = toArticlePayload(form, author.value, editing.value?.cover)

  try {
    if (editing.value) {
      const updated = await updateArticle(editing.value.id, payload)
      rows.value = rows.value.map((item) => (item.id === updated.id ? updated : item))
      ElMessage.success('文章已更新')
    } else {
      const created = await createArticle(payload)
      rows.value = [created, ...rows.value]
      page.value = 1
      ElMessage.success('文章已创建')
    }
    dialog.value = false
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '保存失败，请稍后重试',
      customClass: 'warm-toast',
    })
  }
}

async function onlineArticle(row: Article): Promise<void> {
  if (row.status === '已发布') return
  try {
    const updated = await changeArticleStatus(row.id, '已发布')
    rows.value = rows.value.map((item) => (item.id === updated.id ? updated : item))
    ElMessage.success('已上线，前台可以查看')
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '上线失败，请稍后重试',
      customClass: 'warm-toast',
    })
  }
}

async function offlineArticle(row: Article): Promise<void> {
  if (row.status !== '已发布') return
  try {
    const updated = await changeArticleStatus(row.id, '草稿')
    rows.value = rows.value.map((item) => (item.id === updated.id ? updated : item))
    ElMessage.success('已下线，前台不再展示')
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '下线失败，请稍后重试',
      customClass: 'warm-toast',
    })
  }
}

function removeArticle(row: Article): void {
  ElMessageBox.confirm(`确定删除《${row.title}》吗？删除后不可恢复。`, '删除文章', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    customClass: 'warm-message-box',
  })
    .then(async () => {
      try {
        await deleteArticle(row.id)
        rows.value = rows.value.filter((item) => item.id !== row.id)
        ElMessage.success('已删除')
      } catch (error) {
        ElMessage.error({
          message: error instanceof Error ? error.message : '删除失败，请稍后重试',
          customClass: 'warm-toast',
        })
      }
    })
    .catch(() => {})
}

// ---- 批量操作 ----
function onSelectionChange(selection: Article[]): void {
  selected.value = selection
}

function clearSelection(): void {
  selected.value = []
  tableRef.value?.clearSelection()
}

function batchMessage(result: BatchResult, action: string): void {
  const firstFailure = result.items.find((item) => !item.success)
  const suffix = firstFailure?.message ? `；首个失败原因：${firstFailure.message}` : ''
  if (result.failed > 0) {
    ElMessage.warning({
      message: `${action}完成：成功 ${result.succeeded} 篇，失败 ${result.failed} 篇${suffix}`,
      duration: 6000,
      customClass: 'warm-toast',
    })
  } else {
    ElMessage.success({
      message: `${action}成功，共 ${result.succeeded} 篇`,
      customClass: 'warm-toast',
    })
  }
}

async function confirmBatch(message: string, title: string): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: '继续',
      cancelButtonText: '取消',
      customClass: 'warm-message-box',
    })
    return true
  } catch {
    return false
  }
}

async function batchSetStatus(nextStatus: ArticleStatus): Promise<void> {
  if (!selected.value.length) return
  const action = nextStatus === '已发布' ? '批量发布' : '批量下线'
  if (nextStatus === '草稿') {
    const confirmed = await confirmBatch(
      `将下线选中的 ${selected.value.length} 篇文章，前台将不再展示。确定继续吗？`,
      '批量下线文章',
    )
    if (!confirmed) return
  }

  batchBusy.value = true
  try {
    const result = await batchChangeArticleStatus(
      selected.value.map((row) => row.id),
      nextStatus,
    )
    batchMessage(result, action)
    await loadRows()
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : `${action}失败，请稍后重试`,
      customClass: 'warm-toast',
    })
  } finally {
    batchBusy.value = false
  }
}

async function batchRemove(): Promise<void> {
  if (!selected.value.length) return
  const confirmed = await confirmBatch(
    `确定删除选中的 ${selected.value.length} 篇文章吗？删除后不可恢复。`,
    '批量删除文章',
  )
  if (!confirmed) return

  batchBusy.value = true
  try {
    const result = await batchDeleteArticles(selected.value.map((row) => row.id))
    batchMessage(result, '批量删除')
    await loadRows()
  } catch (error) {
    ElMessage.error({
      message: error instanceof Error ? error.message : '批量删除失败，请稍后重试',
      customClass: 'warm-toast',
    })
  } finally {
    batchBusy.value = false
  }
}

// 按钮统一加防连点：保存 / 上线 / 下线 / 删除都只响应第一次点击
const { pending: saving, trigger: save } = useActionGuard(saveArticle, { wait: 600 })
const { trigger: online } = useActionGuard(onlineArticle, { wait: 400 })
const { trigger: offline } = useActionGuard(offlineArticle, { wait: 400 })
const { trigger: remove } = useActionGuard(removeArticle, { wait: 500 })
</script>

<template>
  <div class="articles-admin">
    <PageHeader eyebrow="Content" title="知识文章">
      <template #actions>
        <el-button type="primary" @click="openCreate">
          <AppIcon name="plus" :size="15" />
          新建文章
        </el-button>
      </template>
    </PageHeader>

    <div class="filters">
      <span class="filters__label">
        <AppIcon name="filter" :size="14" />
        筛选
      </span>
      <SearchField v-model="keyword" class="filters__search" placeholder="搜索标题" />
      <el-select v-model="category" placeholder="全部分类" clearable class="filters__select">
        <el-option v-for="item in articleCategories" :key="item" :label="item" :value="item" />
      </el-select>
      <el-select v-model="status" placeholder="全部状态" clearable class="filters__select">
        <el-option label="已发布" value="已发布" />
        <el-option label="草稿" value="草稿" />
      </el-select>
      <span class="filters__count">
        {{ loading ? '加载中…' : `共 ${filtered.length} 篇` }}
      </span>
    </div>

    <div v-if="selected.length" class="batch-bar" role="region" aria-label="批量操作">
      <span class="batch-bar__count">已选 {{ selected.length }} 篇文章</span>
      <el-button
        size="small"
        type="primary"
        plain
        :loading="batchBusy"
        :disabled="batchBusy"
        @click="batchSetStatus('已发布')"
      >
        批量发布
      </el-button>
      <el-button
        size="small"
        :loading="batchBusy"
        :disabled="batchBusy"
        @click="batchSetStatus('草稿')"
      >
        批量下线
      </el-button>
      <el-button
        size="small"
        type="danger"
        plain
        :loading="batchBusy"
        :disabled="batchBusy"
        @click="batchRemove"
      >
        批量删除
      </el-button>
      <el-button size="small" text :disabled="batchBusy" @click="clearSelection">
        取消选择
      </el-button>
    </div>

    <PanelCard class="table-panel" title="内容列表" description="点击行内操作即可编辑、上线/下线或删除文章">
      <div class="table-shell">
        <div class="table-wrap">
          <el-table
            ref="tableRef"
            :data="paged"
            row-key="id"
            height="100%"
            class="table"
            @selection-change="onSelectionChange"
          >
            <el-table-column type="selection" width="46" reserve-selection />
            <el-table-column label="文章标题" min-width="320" align="center">
              <template #default="{ row }">
                <span class="cell-title">{{ row.title }}</span>
              </template>
            </el-table-column>
            <el-table-column label="分类" min-width="108" align="center">
              <template #default="{ row }">
                <span class="cat-pill">{{ row.category }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="author" label="作者" min-width="110" align="center" />
            <el-table-column label="阅读量" min-width="100" align="center">
              <template #default="{ row }">
                <span class="cell-views">{{ row.views.toLocaleString('zh-CN') }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" min-width="120" align="center">
              <template #default="{ row }">
                <span class="status-pill" :class="row.status === '已发布' ? 'is-live' : 'is-draft'">
                  <i aria-hidden="true" />
                  {{ row.status }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="发布时间" min-width="132" align="center">
              <template #default="{ row }">
                <span :class="{ 'cell-muted': !row.publishedAt }">{{ row.publishedAt || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="168" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEdit(row as Article)">
                  编辑
                </el-button>
                <el-button
                  v-if="row.status === '已发布'"
                  link
                  type="primary"
                  size="small"
                  title="下线后前台不再展示"
                  @click="offline(row as Article)"
                >
                  下线
                </el-button>
                <el-button
                  v-else
                  link
                  type="primary"
                  size="small"
                  title="上线后前台可以查看"
                  @click="online(row as Article)"
                >
                  上线
                </el-button>
                <el-button link type="danger" size="small" @click="remove(row as Article)">
                  删除
                </el-button>
              </template>
            </el-table-column>

            <template #empty>
              <el-empty description="没有匹配的文章，换个关键词或新建一篇" />
            </template>
          </el-table>
        </div>

        <TablePager
          v-if="filtered.length"
          v-model:page="page"
          v-model:page-size="pageSize"
          :total="filtered.length"
          unit="篇"
        />
      </div>
    </PanelCard>

    <el-dialog v-model="dialog" :title="dialogTitle" width="min(640px, 94vw)" class="admin-dialog">
      <el-form label-width="72px" label-position="left">
        <el-form-item label="标题">
          <el-input
            v-model="form.title"
            maxlength="40"
            show-word-limit
            placeholder="例如：焦虑发作时试试 4-7-8 呼吸法"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" class="form-select">
            <el-option v-for="item in articleCategories" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="摘要">
          <el-input
            v-model="form.summary"
            type="textarea"
            :rows="2"
            maxlength="80"
            show-word-limit
            placeholder="一句话说明这篇文章解决什么问题"
          />
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="form.content" type="textarea" :rows="6" placeholder="用空行分隔段落" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" class="form-select">
            <el-option label="草稿" value="草稿" />
            <el-option label="已发布" value="已发布" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.articles-admin {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.table-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.table-shell {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.table-wrap {
  flex: 1;
  min-height: 0;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: $space-3;
  margin-bottom: $space-4;
  padding: 12px 14px;
  border: 1px solid var(--warm-line);
  border-radius: 14px;
  background:
    linear-gradient(120deg, var(--warm-paper-92), var(--warm-paper-80));
  box-shadow:
    inset 0 1px 0 var(--warm-paper-90),
    0 18px 40px -38px rgba(74, 64, 46, 0.4);
  backdrop-filter: blur(12px);
}

.filters__label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding-right: 6px;
  color: var(--warm-brand-strong);
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.08em;

  svg {
    filter: drop-shadow(0 1px 3px rgba(136, 104, 110, 0.16));
  }
}

.filters__search {
  width: 220px;
}

.filters__select {
  width: 132px;
}

.filters__count {
  margin-left: auto;
  padding: 4px 12px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 28%, transparent);
  border-radius: $radius-pill;
  background: color-mix(in srgb, var(--warm-brand) 12%, transparent);
  color: var(--warm-brand-strong);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
}

.batch-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: $space-4;
  padding: 10px 14px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 32%, transparent);
  border-radius: 12px;
  background: color-mix(in srgb, var(--warm-brand) 9%, var(--warm-paper-88));
  box-shadow: inset 0 1px 0 var(--warm-paper-90);
}

.batch-bar__count {
  margin-right: auto;
  color: var(--warm-brand-strong);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.table {
  width: 100%;
}

.cell-title {
  display: block;
  color: var(--warm-text);
  font-size: 13.5px;
  font-weight: 500;
  transition: color 0.24s $ease-out;
  @include truncate;
}

.el-table__row:hover .cell-title {
  color: var(--warm-brand-strong);
}

.cell-views {
  color: var(--warm-text-regular);
  font-family: $font-mono;
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
}

.cell-muted {
  color: var(--warm-text-muted);
}

.cat-pill {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border: 1px solid rgba(196, 143, 154, 0.3);
  border-radius: $radius-pill;
  background: rgba(196, 143, 154, 0.12);
  color: #88686e;
  font-size: 11.5px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border: 1px solid transparent;
  border-radius: $radius-pill;
  font-size: 11.5px;

  i {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: currentColor;
  }

  &.is-live {
    border-color: rgba(136, 104, 110, 0.26);
    background: rgba(136, 104, 110, 0.1);
    color: var(--warm-brand-strong);

    i {
      box-shadow: 0 0 0 3px rgba(136, 104, 110, 0.14);
    }
  }

  &.is-draft {
    border-color: var(--warm-line);
    background: var(--warm-paper-68);
    color: var(--warm-text-muted);
  }
}

// 表格里的链接按钮：常态更亮，hover 带辉光
.table :deep(.el-button.is-link) {
  font-weight: 500;
}

.form-select {
  width: 100%;
}

@include media-down($bp-md) {
  // 窄屏空间有限：表格保留固定可视高度，页面允许滚动，避免只剩一行
  .articles-admin {
    height: auto;
    min-height: 100%;
  }

  .table-panel,
  .table-shell,
  .table-wrap {
    flex: none;
  }

  .table-wrap {
    height: min(420px, 54vh);
  }

  .filters {
    padding: 12px;
  }

  .filters__label {
    width: 100%;
  }

  .filters__search,
  .filters__select {
    width: 100%;
  }

  .filters__count {
    margin-left: 0;
  }
}
</style>
