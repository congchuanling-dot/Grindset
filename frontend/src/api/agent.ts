// Agent 对话与会话管理接口封装。
// 对话走 SSE(事件比普通对话多),会话列表/详情/重命名/删除走普通 JSON。
import { authFetch, readApiError, unwrapJson } from './http'
import { isAbortError, readField, readJson, readSse, readString } from './sse'

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

/** 侧边栏的会话列表项 */
export interface AgentConversationSummary {
  id: string
  title: string
  messageCount: number
  lastMessageAt: string
  createdAt: string
}

/** 历史消息里已完成的工具调用 */
export interface AgentHistoryStep {
  id: string
  name: string
  label: string
  args: Record<string, unknown>
  ok: boolean
  summary: string
}

/** 历史消息:后端已经把一轮工具调用合并成一个助手气泡 */
export interface AgentHistoryMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  /** 0 正常 / 2 失败 / 3 中断 */
  status: number
  createdAt: string
  steps: AgentHistoryStep[]
}

export interface AgentConversationDetail {
  id: string
  title: string
  messageCount: number
  lastMessageAt: string
  createdAt: string
  messages: AgentHistoryMessage[]
}

export interface AgentStreamHandlers {
  /** 新建会话时后端先回传会话 ID 和占位标题 */
  onConversation?: (id: string, title: string) => void
  /** 阶段提示,如"正在分析你的问题…" */
  onStatus: (text: string) => void
  onToolCall: (step: AgentToolStep) => void
  onToolResult: (id: string, ok: boolean, summary: string) => void
  onDelta: (text: string) => void
  onError: (message: string) => void
  /** 结束时回传会话 ID 和最新标题 */
  onDone?: (id: string, title: string) => void
}

/** 拉取当前用户的会话列表 */
export async function listAgentConversations(): Promise<AgentConversationSummary[]> {
  const res = await request('/api/agent/conversations')
  return unwrapJson<AgentConversationSummary[]>(res, '加载会话列表失败')
}

/** 拉取会话详情,用于刷新恢复和点历史会话 */
export async function getAgentConversation(id: string): Promise<AgentConversationDetail> {
  const res = await request(`/api/agent/conversations/${id}`)
  return unwrapJson<AgentConversationDetail>(res, '加载会话失败')
}

export async function renameAgentConversation(id: string, title: string): Promise<void> {
  const res = await request(`/api/agent/conversations/${id}`, jsonInit('PATCH', { title }))
  await unwrapJson<null>(res, '重命名失败')
}

export async function deleteAgentConversation(id: string): Promise<void> {
  const res = await request(`/api/agent/conversations/${id}`, { method: 'DELETE' })
  await unwrapJson<null>(res, '删除会话失败')
}

/**
 * 流式 Agent 对话。业务错误走 onError,参数错误(非 200)直接 throw。
 * 有 conversationId 时后端从库里读历史,前端只需要传本次提问。
 */
export async function streamAgentChat(
  params: { conversationId?: string; provider: string; model: string; message: string },
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
    throw new Error(await readApiError(res, '请求失败'))
  }

  await readSse(res, ({ event, data }) => {
    // status 只是阶段性提示,不参与结构化数据处理
    if (event === 'status') {
      const text = readField(data, 'text')
      if (text) handlers.onStatus(text)
      return
    }

    const payload = readJson(data)
    if (!payload) return

    if (event === 'conversation') {
      handlers.onConversation?.(
        readString(payload, 'conversationId'),
        readString(payload, 'title'),
      )
    } else if (event === 'tool_call') {
      // 先插一张"运行中"的卡片,等 tool_result 再按 id 改成完成/失败
      handlers.onToolCall({
        id: readString(payload, 'id'),
        name: readString(payload, 'name'),
        label: readString(payload, 'label'),
        args: isRecord(payload.args) ? payload.args : {},
        status: 'running',
      })
    } else if (event === 'tool_result') {
      // 按 id 更新对应卡片,不依赖事件到达顺序
      const id = readString(payload, 'id')
      if (!id) return
      handlers.onToolResult(id, payload.ok === true, readString(payload, 'summary'))
    } else if (event === 'delta') {
      // 最终回答的增量文本
      const text = readString(payload, 'text')
      if (text) handlers.onDelta(text)
    } else if (event === 'done') {
      handlers.onDone?.(readString(payload, 'conversationId'), readString(payload, 'title'))
      return true
    } else if (event === 'error') {
      handlers.onError(readString(payload, 'message') || '对话失败,请重试')
      return true
    }
  })
}

async function request(path: string, init?: RequestInit): Promise<Response> {
  try {
    return await authFetch(path, init)
  } catch {
    throw new Error('无法连接服务器，请确认后端已启动')
  }
}

function jsonInit(method: string, body: unknown): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
