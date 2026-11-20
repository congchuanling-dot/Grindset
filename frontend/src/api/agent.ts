// Agent 对话接口封装。事件比普通对话多:status / tool_call / tool_result / delta / done / error。
import { authFetch, readApiError } from './http'
import { isAbortError, readField, readJson, readSse, readString } from './sse'
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

    if (event === 'tool_call') {
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
    } else if (event === 'error') {
      handlers.onError(readString(payload, 'message') || '对话失败,请重试')
    }
  })
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
