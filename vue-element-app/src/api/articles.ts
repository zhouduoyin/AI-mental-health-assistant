import type { Article, ArticleCategory, ArticleStatus } from '@/types'

import type { BatchResult } from './batch'
import { apiJson } from './http'

export interface ArticleQuery {
  category?: string
  keyword?: string
  sort?: 'latest' | 'popular' | 'shortest'
}

export interface ArticlePayload {
  title: string
  category: ArticleCategory
  summary: string
  content: string[]
  status: ArticleStatus
  cover?: string
  author?: string
}

function queryString(params: ArticleQuery): string {
  const search = new URLSearchParams()
  if (params.category) search.set('category', params.category)
  if (params.keyword) search.set('keyword', params.keyword)
  if (params.sort) search.set('sort', params.sort)
  const text = search.toString()
  return text ? `?${text}` : ''
}

/** 前台只返回已发布文章。 */
export function listArticles(params: ArticleQuery = {}): Promise<Article[]> {
  return apiJson<Article[]>(`/api/articles${queryString(params)}`)
}

/** 打开详情时后端会把阅读量 +1。 */
export function getArticle(id: number): Promise<Article> {
  return apiJson<Article>(`/api/articles/${id}`)
}

export function listAdminArticles(): Promise<Article[]> {
  return apiJson<Article[]>('/api/admin/articles')
}

export function createArticle(payload: ArticlePayload): Promise<Article> {
  return apiJson<Article>('/api/admin/articles', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function updateArticle(id: number, payload: ArticlePayload): Promise<Article> {
  return apiJson<Article>(`/api/admin/articles/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function changeArticleStatus(id: number, status: ArticleStatus): Promise<Article> {
  return apiJson<Article>(`/api/admin/articles/${id}/status`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status }),
  })
}

export function deleteArticle(id: number): Promise<void> {
  return apiJson<void>(`/api/admin/articles/${id}`, { method: 'DELETE' })
}

/** 批量上线 / 下线文章。 */
export function batchChangeArticleStatus(ids: number[], status: ArticleStatus): Promise<BatchResult> {
  return apiJson<BatchResult>('/api/admin/articles/batch/status', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ids, status }),
  })
}

/** 批量删除文章。 */
export function batchDeleteArticles(ids: number[]): Promise<BatchResult> {
  return apiJson<BatchResult>('/api/admin/articles/batch/delete', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ids }),
  })
}

/**
 * 后台表单里的正文是纯文本（空行分段），发送前统一转成段落数组。
 */
export function toArticlePayload(
  draft: {
    title: string
    category: ArticleCategory
    summary: string
    content: string
    status: ArticleStatus
  },
  author?: string,
  cover?: string,
): ArticlePayload {
  return {
    title: draft.title.trim(),
    category: draft.category,
    summary: draft.summary.trim(),
    content: draft.content
      .split(/\n{2,}/)
      .map((text) => text.trim())
      .filter(Boolean),
    status: draft.status,
    author,
    cover,
  }
}
