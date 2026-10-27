import { apiJson } from './http'

export interface KnowledgeDocument {
  id: number
  title: string
  source: string
  chunkCount: number
  createdAt: string
}

export interface DocumentDetail extends KnowledgeDocument {
  content: string
}

export interface MetaInfo {
  vectorStore: string
  chatModel: string
  embeddingModel: string
  documents: number
  chunks: number
  conversations: number
}

export interface CreateDocumentPayload {
  title: string
  source?: string
  content: string
}

export async function listDocuments(): Promise<KnowledgeDocument[]> {
  return apiJson<KnowledgeDocument[]>('/api/documents')
}

export async function getDocument(id: number): Promise<DocumentDetail> {
  return apiJson<DocumentDetail>(`/api/documents/${id}`)
}

export async function createDocument(payload: CreateDocumentPayload): Promise<KnowledgeDocument> {
  return apiJson<KnowledgeDocument>('/api/documents', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export async function uploadDocument(file: File, title?: string): Promise<KnowledgeDocument> {
  const form = new FormData()
  form.append('file', file)
  if (title?.trim()) form.append('title', title.trim())
  return apiJson<KnowledgeDocument>('/api/documents/upload', { method: 'POST', body: form })
}

export async function deleteDocument(id: number): Promise<void> {
  await apiJson<void>(`/api/documents/${id}`, { method: 'DELETE' })
}

export async function fetchMeta(): Promise<MetaInfo> {
  return apiJson<MetaInfo>('/api/meta')
}
