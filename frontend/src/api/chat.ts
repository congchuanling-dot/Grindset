// 对话接口封装。模型列表走普通 JSON,对话走 SSE(fetch + ReadableStream)。

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

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface StreamHandlers {
  onDelta: (text: string) => void
  onError: (message: string) => void
}

/** 拉取可用模型服务(只含服务端已配置 key 的厂商) */
export async function fetchModels(): Promise<ProviderInfo[]> {
  let res: Response
  try {
    res = await fetch('/api/chat/models')
  } catch {
    throw new Error('无法连接服务器，请确认后端已启动')
  }

  let payload: ApiResponse<ProviderInfo[]>
  try {
    payload = (await res.json()) as ApiResponse<ProviderInfo[]>
  } catch {
    throw new Error(`加载模型列表失败（HTTP ${res.status}）`)
  }

  if (!res.ok || payload.code !== 0) {
    throw new Error(payload.message || `加载模型列表失败（HTTP ${res.status}）`)
  }
  return payload.data ?? []
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
    res = await fetch('/api/chat', {
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

      // SSE 以空行分隔事件,可能一次读到多个,也可能读到半个
      let index = buffer.indexOf('\n\n')
      while (index >= 0) {
        handleEvent(buffer.slice(0, index), handlers)
        buffer = buffer.slice(index + 2)
        index = buffer.indexOf('\n\n')
      }
    }
    // 收尾:最后一段可能没有以空行结束
    if (buffer.trim()) {
      handleEvent(buffer, handlers)
    }
  } finally {
    reader.cancel().catch(() => undefined)
  }
}

function handleEvent(chunk: string, handlers: StreamHandlers) {
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
  if (event === 'delta') {
    const text = readField(raw, 'text')
    if (text) handlers.onDelta(text)
  } else if (event === 'error') {
    handlers.onError(readField(raw, 'message') || '对话失败,请重试')
  }
  // done:流自然结束,不需要额外处理
}

/** 取 SSE data 里的某个字符串字段,解析失败返回空串 */
function readField(raw: string, key: string): string {
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>
    const value = parsed[key]
    return typeof value === 'string' ? value : ''
  } catch {
    return ''
  }
}

function isAbortError(error: unknown): boolean {
  return typeof error === 'object' && error !== null && (error as { name?: string }).name === 'AbortError'
}
