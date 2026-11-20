// 对话接口封装。模型列表走普通 JSON,对话走 SSE(fetch + ReadableStream)。
import { authFetch, readApiError, unwrapJson } from './http'
import { isAbortError, readField, readSse } from './sse'

export interface ModelInfo {
  /** 调用厂商时传的模型 ID */
  id: string
  /** 下拉框展示名 */
  displayName: string
}

/** 厂商账户余额(账号级,不是单把 key 的余额) */
export interface BalanceInfo {
  amount: number
  /** 币种,如 CNY / USD */
  currency: string
  /** 最后更新时间,未维护过为 null */
  updatedAt: string | null
  /** MANUAL 手工维护 / AUTO 定时查询 */
  source: string
}

export interface ProviderInfo {
  id: string
  name: string
  models: ModelInfo[]
  /** 没维护过余额时为 null */
  balance: BalanceInfo | null
  /** 该厂商当前可用的 key 数量 */
  keyCount: number
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface StreamHandlers {
  onDelta: (text: string) => void
  onError: (message: string) => void
}

/** 拉取可用模型服务(只含服务端已配置 key 的厂商) */
export async function fetchModels(): Promise<ProviderInfo[]> {
  let res: Response
  try {
    res = await authFetch('/api/chat/models')
  } catch {
    throw new Error('无法连接服务器，请确认后端已启动')
  }
  const list = await unwrapJson<ProviderInfo[] | null>(res, '加载模型列表失败')
  return list ?? []
}

/**
 * 流式对话。增量文本通过 onDelta 回调,业务错误通过 onError 回调。
 * 参数校验失败时后端返回普通 JSON(非 200),这里直接 throw。
 */
export async function streamChat(
  params: { provider: string; model: string; messages: ChatMessage[] },
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  let res: Response
  try {
    res = await authFetch('/api/chat', {
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

  // 参数不合法时后端走统一 JSON 响应,只有正常对话才是 200 + SSE
  if (!res.ok) {
    throw new Error(await readApiError(res, '请求失败'))
  }

  await readSse(res, ({ event, data }) => {
    if (event === 'delta') {
      const text = readField(data, 'text')
      if (text) handlers.onDelta(text)
    } else if (event === 'error') {
      handlers.onError(readField(data, 'message') || '对话失败,请重试')
    }
    // done:流自然结束,不需要额外处理
  })
}
