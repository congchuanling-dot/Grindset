// SSE 读取与事件解析。chat / agent / interview 三条流共用这一套。

export interface SseEvent {
  event: string
  data: string
}

/**
 * 逐块读取 SSE 响应,按空行切出事件后回调。
 * onEvent 返回 true 表示让调用方提前结束读取(例如收到 done / error)。
 */
export async function readSse(
  res: Response,
  onEvent: (event: SseEvent) => boolean | void,
): Promise<void> {
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
        if (dispatch(buffer.slice(0, index), onEvent)) {
          await reader.cancel().catch(() => undefined)
          return
        }
        buffer = buffer.slice(index + 2)
        index = buffer.indexOf('\n\n')
      }
    }
    // 收尾:最后一段可能没有以空行结束
    if (buffer.trim()) {
      dispatch(buffer, onEvent)
    }
  } finally {
    reader.cancel().catch(() => undefined)
  }
}

function dispatch(chunk: string, onEvent: (event: SseEvent) => boolean | void): boolean {
  let event = 'message'
  const dataLines: string[] = []

  for (const line of chunk.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }
  if (dataLines.length === 0) return false

  return onEvent({ event, data: dataLines.join('\n') }) === true
}

/** 把 SSE data 解析成对象,失败或不是对象时返回 null */
export function readJson(raw: string): Record<string, unknown> | null {
  try {
    const parsed = JSON.parse(raw) as unknown
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return null
    }
    return parsed as Record<string, unknown>
  } catch {
    return null
  }
}

/** 取对象里的字符串字段,缺失或类型不对返回空串 */
export function readString(payload: Record<string, unknown>, key: string): string {
  const value = payload[key]
  return typeof value === 'string' ? value : ''
}

/** 取 SSE data 里的字符串字段 */
export function readField(raw: string, key: string): string {
  const payload = readJson(raw)
  return payload ? readString(payload, key) : ''
}

export function isAbortError(error: unknown): boolean {
  return typeof error === 'object' && error !== null && (error as { name?: string }).name === 'AbortError'
}
