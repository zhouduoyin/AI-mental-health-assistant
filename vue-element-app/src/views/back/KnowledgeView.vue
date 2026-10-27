<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import AppIcon from '@/components/AppIcon.vue'
import PageHeader from '@/components/PageHeader.vue'
import PanelCard from '@/components/PanelCard.vue'
import SearchField from '@/components/SearchField.vue'
import {
  createDocument,
  deleteDocument,
  fetchMeta,
  getDocument,
  listDocuments,
  uploadDocument,
  type DocumentDetail,
  type KnowledgeDocument,
  type MetaInfo,
} from '@/api/knowledge'

const documents = ref<KnowledgeDocument[]>([])
const meta = ref<MetaInfo | null>(null)
const loading = ref(false)
const keyword = ref('')

const filtered = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  if (!key) return documents.value
  return documents.value.filter((doc) =>
    `${doc.title} ${doc.source}`.toLowerCase().includes(key),
  )
})

const createOpen = ref(false)
const createForm = reactive({ title: '', source: '心理健康知识库', content: '' })
const creating = ref(false)

const uploadOpen = ref(false)
const uploadTitle = ref('')
const uploadFile = ref<File | null>(null)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<DocumentDetail | null>(null)

async function load(): Promise<void> {
  loading.value = true
  try {
    const [docs, info] = await Promise.all([listDocuments(), fetchMeta()])
    documents.value = docs
    meta.value = info
  } catch (error) {
    ElMessage.error(errorText(error, '加载知识库失败，请确认后端已启动'))
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  Object.assign(createForm, { title: '', source: '心理健康知识库', content: '' })
  createOpen.value = true
}

async function submitCreate(): Promise<void> {
  if (!createForm.title.trim()) {
    ElMessage.warning('请填写文档标题')
    return
  }
  if (!createForm.content.trim()) {
    ElMessage.warning('请填写文档正文')
    return
  }
  creating.value = true
  try {
    await createDocument({
      title: createForm.title.trim(),
      source: createForm.source.trim(),
      content: createForm.content,
    })
    ElMessage.success('文档已切块并写入向量库')
    createOpen.value = false
    await load()
  } catch (error) {
    ElMessage.error(errorText(error, '保存失败'))
  } finally {
    creating.value = false
  }
}

function openUpload(): void {
  uploadTitle.value = ''
  uploadFile.value = null
  uploadOpen.value = true
}

function pickFile(): void {
  fileInput.value?.click()
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  if (file && !/\.(txt|md|markdown)$/i.test(file.name)) {
    ElMessage.warning('仅支持 .txt / .md / .markdown 纯文本文件')
    input.value = ''
    return
  }
  uploadFile.value = file
  if (file && !uploadTitle.value) {
    uploadTitle.value = file.name.replace(/\.[^.]+$/, '')
  }
}

async function submitUpload(): Promise<void> {
  if (!uploadFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  uploading.value = true
  try {
    await uploadDocument(uploadFile.value, uploadTitle.value)
    ElMessage.success('上传成功，已切块并写入向量库')
    uploadOpen.value = false
    await load()
  } catch (error) {
    ElMessage.error(errorText(error, '上传失败'))
  } finally {
    uploading.value = false
  }
}

async function openDetail(row: KnowledgeDocument): Promise<void> {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getDocument(row.id)
  } catch (error) {
    ElMessage.error(errorText(error, '加载文档失败'))
  } finally {
    detailLoading.value = false
  }
}

async function remove(row: KnowledgeDocument): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除《${row.title}》？对应的 ${row.chunkCount} 个向量块也会一起删除。`,
      '删除知识文档',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteDocument(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    ElMessage.error(errorText(error, '删除失败'))
  }
}

function prettyDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}

function errorText(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'message' in error) {
    const message = (error as { message?: unknown }).message
    if (typeof message === 'string' && message.trim()) return message
  }
  return fallback
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="knowledge-admin">
    <PageHeader eyebrow="RAG" title="知识库">
      <template #actions>
        <el-button @click="openUpload">
          <AppIcon name="download" :size="15" />
          上传文档
        </el-button>
        <el-button type="primary" @click="openCreate">
          <AppIcon name="plus" :size="15" />
          新建文档
        </el-button>
      </template>
    </PageHeader>

    <div class="knowledge-stats">
      <article class="stat">
        <span class="stat__label">文档</span>
        <strong>{{ meta?.documents ?? documents.length }}</strong>
        <small>篇</small>
      </article>
      <article class="stat">
        <span class="stat__label">向量块</span>
        <strong>{{ meta?.chunks ?? 0 }}</strong>
        <small>chunks</small>
      </article>
      <article class="stat">
        <span class="stat__label">对话</span>
        <strong>{{ meta?.conversations ?? 0 }}</strong>
        <small>段</small>
      </article>
      <article class="stat stat--wide">
        <span class="stat__label">当前引擎</span>
        <strong class="stat__engine">{{ meta?.vectorStore ?? '—' }}</strong>
        <small>{{ meta?.embeddingModel ?? '' }}</small>
      </article>
    </div>

    <div class="filters">
      <span class="filters__label">
        <AppIcon name="filter" :size="14" />
        筛选
      </span>
      <SearchField v-model="keyword" class="filters__search" placeholder="搜索标题或来源" />
      <span class="filters__count">共 {{ filtered.length }} 篇</span>
      <el-button text :loading="loading" @click="load">刷新</el-button>
    </div>

    <PanelCard class="table-panel" title="文档列表" description="查看切片数量，或删除后重新上传">
      <div class="table-wrap">
        <el-table v-loading="loading" :data="filtered" height="100%" class="table">
          <el-table-column label="标题" min-width="280">
            <template #default="{ row }">
              <button class="doc-title" type="button" @click="openDetail(row as KnowledgeDocument)">
                {{ row.title }}
              </button>
            </template>
          </el-table-column>
          <el-table-column prop="source" label="来源" min-width="170" align="center" />
          <el-table-column label="向量块" min-width="100" align="center">
            <template #default="{ row }">
              <span class="chunk-pill">{{ row.chunkCount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="入库时间" min-width="180" align="center">
            <template #default="{ row }">
              <span class="cell-muted">{{ prettyDate(row.createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openDetail(row as KnowledgeDocument)">
                查看
              </el-button>
              <el-button link type="danger" size="small" @click="remove(row as KnowledgeDocument)">
                删除
              </el-button>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty description="知识库还是空的，先新建或上传一篇文档吧" />
          </template>
        </el-table>
      </div>
    </PanelCard>

    <el-dialog v-model="createOpen" title="新建知识文档" width="min(680px, 94vw)" class="admin-dialog">
      <el-form label-width="72px" label-position="left">
        <el-form-item label="标题">
          <el-input v-model="createForm.title" maxlength="80" show-word-limit placeholder="例如：焦虑来临时，身体在发生什么" />
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="createForm.source" maxlength="80" placeholder="可留空，例如：心理健康知识库" />
        </el-form-item>
        <el-form-item label="正文">
          <el-input
            v-model="createForm.content"
            type="textarea"
            :rows="12"
            maxlength="20000"
            show-word-limit
            placeholder="粘贴纯文本内容，系统会自动切块并生成向量。"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">保存并入库</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="uploadOpen" title="上传纯文本文档" width="min(520px, 94vw)" class="admin-dialog">
      <div class="upload-box">
        <input ref="fileInput" class="upload-file" type="file" accept=".txt,.md,.markdown" @change="onFileChange" />
        <button class="upload-drop" type="button" @click="pickFile">
          <AppIcon name="download" :size="22" />
          <strong>{{ uploadFile ? uploadFile.name : '选择一个 .txt / .md 文件' }}</strong>
          <small>上传后自动切块、生成向量，并立即参与检索</small>
        </button>
        <el-form label-width="64px" label-position="left" class="upload-form">
          <el-form-item label="标题">
            <el-input v-model="uploadTitle" maxlength="80" placeholder="不填则使用文件名" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="uploadOpen = false">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!uploadFile" @click="submitUpload">
          上传并入库
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailOpen" :title="detail?.title ?? '文档详情'" width="min(720px, 94vw)" class="admin-dialog">
      <div v-loading="detailLoading" class="doc-detail">
        <div class="doc-detail__meta">
          <span>来源：{{ detail?.source || '—' }}</span>
          <span>向量块：{{ detail?.chunkCount ?? 0 }}</span>
          <span>入库：{{ detail ? prettyDate(detail.createdAt) : '—' }}</span>
        </div>
        <pre class="doc-detail__content">{{ detail?.content || '' }}</pre>
      </div>
      <template #footer>
        <el-button @click="detailOpen = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.knowledge-admin {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.knowledge-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: $space-3;
  margin-bottom: $space-4;
}

.stat {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 14px 16px;
  border: 1px solid var(--warm-line);
  border-radius: 14px;
  background: linear-gradient(120deg, var(--warm-paper-92), var(--warm-paper-80));

  &__label {
    color: var(--warm-text-muted);
    font-size: 12px;
  }

  strong {
    color: var(--warm-brand-strong);
    font-size: 22px;
    font-weight: 700;
  }

  small {
    color: var(--warm-text-muted);
    font-size: 11px;
  }

  &--wide {
    flex-wrap: wrap;
  }

  &__engine {
    font-size: 15px !important;
  }
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
  background: linear-gradient(120deg, var(--warm-paper-92), var(--warm-paper-80));
}

.filters__label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--warm-brand-strong);
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.filters__search {
  width: 240px;
}

.filters__count {
  margin-left: auto;
  padding: 4px 12px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 28%, transparent);
  border-radius: 999px;
  color: var(--warm-brand-strong);
  font-size: 12px;
}

.table-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.table-wrap {
  flex: 1;
  min-height: 360px;
}

.doc-title {
  padding: 0;
  border: 0;
  background: none;
  color: var(--warm-text-regular);
  font: inherit;
  text-align: left;
  cursor: pointer;

  &:hover {
    color: var(--warm-brand-strong);
    text-decoration: underline;
  }
}

.chunk-pill {
  display: inline-flex;
  min-width: 28px;
  justify-content: center;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--warm-brand-soft);
  color: var(--warm-brand-strong);
  font-size: 12px;
  font-weight: 600;
}

.upload-box {
  display: flex;
  flex-direction: column;
  gap: $space-3;
}

.upload-file {
  display: none;
}

.upload-drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 26px 18px;
  border: 1px dashed var(--warm-line-strong);
  border-radius: 14px;
  background: var(--warm-paper-80);
  color: var(--warm-text-regular);
  cursor: pointer;

  &:hover {
    border-color: var(--warm-brand);
    background: var(--warm-brand-soft);
  }

  strong {
    font-size: 14px;
  }

  small {
    color: var(--warm-text-muted);
    font-size: 12px;
  }
}

.upload-form {
  margin-top: 4px;
}

.doc-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 12px;
  color: var(--warm-text-muted);
  font-size: 12px;
}

.doc-detail__content {
  max-height: 52vh;
  margin: 0;
  padding: 14px 16px;
  overflow: auto;
  border: 1px solid var(--warm-line);
  border-radius: 12px;
  background: var(--warm-paper-80);
  color: var(--warm-text-regular);
  font-family: inherit;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
}

@media (max-width: 1180px) {
  .knowledge-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
