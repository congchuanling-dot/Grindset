// Agent 对话接口封装。事件比普通对话多:status / tool_call / tool_result / delta / done / error。
import { authFetch } from './http'
import type { ChatMessage } from './chat'

/** 一次工具调用的前端展示状态 */
export interface AgentToolStep {
  id: string
  name: string
  label: string
  args: Record<string, unknown>
  status: 'running' | 'done' | 'failed'
  /** 工具执行完成后的一行结论 */
  summary?: string
}

export interface AgentStreamHandlers {
  /** 阶段提示,如"正在分析你的问题…" */
  onStatus: (text: string) => void
  onToolCall: (step: AgentToolStep) => void
  onToolResult: (id: string, ok: boolean, summary: string) => void
  onDelta: (text: string) => void
  onError: (message: string) => void
}

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/**
 * 流式 Agent 对话。业务错误走 onError,参数错误(非 200)直接 throw。
 */
export async function streamAgentChat(
  params: { provider: string; model: string; messages: ChatMessage[] },
  handlers: AgentStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  let res: Response
  try {
    res = await authFetch('/api/agent/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
      body: JSON.stringify(params),
      signal,
    })
  } catch (error) {
    if (isAbortError(error)) throw error
    throw new Error('无法连接服务器，请确认后端已启动')
  }

  // 鉴权/参数问题走统一 JSON 响应,只有正常对话才是 200 + SSE
  if (!res.ok) {
    let message = `请求失败（HTTP ${res.status}）`
    try {
      const payload = (await res.json()) as ApiResponse<null>
      if (payload?.message) message = payload.message
    } catch {
      // 响应体不是 JSON,用默认文案
    }
    throw new Error(message)
  }

  if (!res.body) {
    throw new Error('当前浏览器不支持流式响应')
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n')

      let index = buffer.indexOf('\n\n')
      while (index >= 0) {
        handleEvent(buffer.slice(0, index), handlers)
        buffer = buffer.slice(index + 2)
        index = buffer.indexOf('\n\n')
      }
    }
    if (buffer.trim()) {
      handleEvent(buffer, handlers)
    }
  } finally {
    reader.cancel().catch(() => undefined)
  }
}

function handleEvent(chunk: string, handlers: AgentStreamHandlers) {
  let event = 'message'
  const dataLines: string[] = []

  for (const line of chunk.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }
  if (dataLines.length === 0) return

  const raw = dataLines.join('\n')
  if (raw === '{}') {
    // done 的空载荷
    return
  }

  let payload: Record<string, unknown>
  try {
    payload = JSON.parse(raw) as Record<string, unknown>
  } catch {
    return
  }

  if (event === 'status') {
    const text = readString(payload, 'text')
    if (text) handlers.onStatus(text)
  } else if (event === 'tool_call') {
    handlers.onToolCall({
      id: readString(payload, 'id'),
      name: readString(payload, 'name'),
      label: readString(payload, 'label'),
      args: isRecord(payload.args) ? payload.args : {},
      status: 'running',
    })
  } else if (event === 'tool_result') {
    const id = readString(payload, 'id')
    if (!id) return
    handlers.onToolResult(id, payload.ok === true, readString(payload, 'summary'))
  } else if (event === 'delta') {
    const text = readString(payload, 'text')
    if (text) handlers.onDelta(text)
  } else if (event === 'error') {
    handlers.onError(readString(payload, 'message') || '对话失败,请重试')
  }
}

function readString(payload: Record<string, unknown>, key: string): string {
  const value = payload[key]
  return typeof value === 'string' ? value : ''
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isAbortError(error: unknown): boolean {
  return typeof error === 'object' && error !== null && (error as { name?: string }).name === 'AbortError'
}
