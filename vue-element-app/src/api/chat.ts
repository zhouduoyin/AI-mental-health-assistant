import { ApiError, API_BASE, apiJson, readErrorDetail } from './http'

/** 检索命中的一条知识库片段 */
export interface Reference {
  chunkId: string
  docId: number
  title: string
  chunkIndex: number
  score: number
  text: string
}

/** 左侧对话记录列表项 */
export interface ConversationSummary {
  id: string
  title: string
  preview: string
  /** 毫秒时间戳 */
  updatedAt: number
  messageCount: number
}

export interface ServerMessage {
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
}

export interface StreamMeta {
  conversationId: string
  references: Reference[]
}

export interface DonePayload {
  conversationId: string
  messageId: number
}

export interface StreamHandlers {
  onMeta?: (meta: StreamMeta) => void
  onDelta?: (text: string) => void
  onDone?: (payload: DonePayload) => void
}

interface RawConversation {
  id: string
  title: string
  preview: string
  updatedAt: string
  messageCount: number
}

async function conversations(): Promise<ConversationSummary[]> {
  const raw = await apiJson<RawConversation[]>('/api/chat/conversations')
  return raw.map((item) => ({
    id: item.id,
    title: item.title,
    preview: item.preview,
    updatedAt: Date.parse(item.updatedAt) || 0,
    messageCount: item.messageCount,
  }))
}

async function history(conversationId: string): Promise<ServerMessage[]> {
  return apiJson<ServerMessage[]>(`/api/chat/${encodeURIComponent(conversationId)}/messages`)
}

async function remove(conversationId: string): Promise<void> {
  await apiJson<void>(`/api/chat/${encodeURIComponent(conversationId)}`, { method: 'DELETE' })
}

async function stream(
  payload: { conversationId: string | null; question: string },
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  return streamRequest(
    '/api/chat/stream',
    payload,
    handlers,
    signal,
  )
}

async function regenerate(
  conversationId: string,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  return streamRequest(
    `/api/chat/${encodeURIComponent(conversationId)}/regenerate/stream`,
    null,
    handlers,
    signal,
  )
}

async function streamRequest(
  path: string,
  body: object | null,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
      body: body ? JSON.stringify(body) : undefined,
      signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError('无法连接后端服务（默认 http://localhost:8080），请确认后端已启动')
  }

  if (!response.ok) {
    if (response.status === 502 || response.status === 503 || response.status === 504) {
      throw new ApiError(
        `后端服务不可用（HTTP ${response.status}）。请确认后端已在 ${API_BASE || 'http://localhost:8080'} 启动，然后重试。`,
        response.status,
      )
    }
    const detail = await readErrorDetail(response)
    throw new ApiError(detail || `请求失败（HTTP ${response.status}）`, response.status)
  }
  if (!response.body) {
    throw new ApiError('当前浏览器不支持流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const chunk = buffer.slice(0, boundary).replace(/\r/g, '')
        buffer = buffer.slice(boundary + 2)
        dispatch(chunk, handlers)
        boundary = buffer.indexOf('\n\n')
      }
    }
    if (buffer.trim()) {
      dispatch(buffer.replace(/\r/g, ''), handlers)
    }
  } catch (error) {
    await reader.cancel().catch(() => undefined)
    throw error
  } finally {
    reader.releaseLock()
  }
}

function dispatch(raw: string, handlers: StreamHandlers): void {
  let event = ''
  const dataLines: string[] = []
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }
  if (!event || dataLines.length === 0) return

  let payload: Record<string, unknown>
  try {
    payload = JSON.parse(dataLines.join('\n')) as Record<string, unknown>
  } catch {
    return
  }

  if (event === 'meta') {
    handlers.onMeta?.(payload as unknown as StreamMeta)
  } else if (event === 'delta') {
    handlers.onDelta?.(String(payload.text ?? ''))
  } else if (event === 'done') {
    handlers.onDone?.(payload as unknown as DonePayload)
  } else if (event === 'error') {
    throw new ApiError(String(payload.detail ?? 'AI 服务返回错误'))
  }
}

export const chatApi = { conversations, history, remove, stream, regenerate }
