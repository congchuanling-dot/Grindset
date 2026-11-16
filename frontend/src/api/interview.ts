// AI 面试接口封装:普通请求走 JSON,出题走 SSE 流。
import { authFetch } from './http'

export interface InterviewSummary {
  id: string
  resumeFileName: string
  position: string
  style: string
  round: string | null
  questionCount: number
  completedQuestions: number
  status: number
  overallScore: number | null
  createdAt: string
}

export interface InterviewMessage {
  id: string
  seq: number
  role: 'INTERVIEWER' | 'USER'
  type: 'QUESTION' | 'FOLLOW_UP' | 'ANSWER' | 'FEEDBACK'
  content: string
  createdAt: string
}

export interface InterviewQuestion {
  id: string
  seq: number
  status: number
  followUpCount: number
  score: number | null
  knowledgePoints: string[]
  comment: string | null
  suggestion: string | null
  messages: InterviewMessage[]
}

export interface InterviewReport {
  overallScore: number
  dimensionScores: Record<string, number>
  weakPoints: string[]
  highlights: string[]
  advice: string[]
}

export interface InterviewDetail {
  id: string
  resumeId: string
  resumeFileName: string
  position: string
  style: string
  round: string | null
  focus: string[]
  questionCount: number
  completedQuestions: number
  status: number
  overallScore: number | null
  createdAt: string
  questions: InterviewQuestion[]
  report: InterviewReport | null
}

export interface AnswerResult {
  feedback: string | null
  followUpQuestion: string | null
  questionFinished: boolean
  evaluation: {
    score: number
    knowledgePoints: string[]
    comment: string | null
    suggestion: string | null
  } | null
  nextAction: 'ANSWER_FOLLOW_UP' | 'NEXT_QUESTION' | 'REPORT_READY'
  completedQuestions: number
  questionCount: number
  /** 本题被收录为错题时的 ID,未收录为 null */
  wrongQuestionId: string | null
}

export interface CreateInterviewPayload {
  resumeId: string
  position: string
  style: string
  round: string
  focus: string[]
  questionCount: number
}

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 统一处理非流式响应:非 2xx 或业务 code 非 0 都抛出可读错误 */
async function unwrap<T>(res: Response, fallback: string): Promise<T> {
  let payload: ApiResponse<T> | null = null
  try {
    payload = (await res.json()) as ApiResponse<T>
  } catch {
    // 响应体不是 JSON,用兜底文案
  }
  if (!res.ok || !payload || payload.code !== 0) {
    throw new Error(payload?.message || `${fallback}（HTTP ${res.status}）`)
  }
  return payload.data
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

export async function createInterview(payload: CreateInterviewPayload): Promise<InterviewDetail> {
  const res = await request('/api/interviews', jsonInit('POST', payload))
  return unwrap<InterviewDetail>(res, '创建面试失败')
}

/** 面试记录列表,可按简历 id 过滤(多个简历取并集) */
export async function listInterviews(resumeIds?: string[]): Promise<InterviewSummary[]> {
  const params = new URLSearchParams()
  for (const id of resumeIds ?? []) params.append('resumeIds', id)
  const query = params.toString()
  const res = await request(`/api/interviews${query ? `?${query}` : ''}`)
  return unwrap<InterviewSummary[]>(res, '加载面试记录失败')
}

export async function getInterview(id: string): Promise<InterviewDetail> {
  const res = await request(`/api/interviews/${id}`)
  return unwrap<InterviewDetail>(res, '加载面试详情失败')
}

export async function deleteInterview(id: string): Promise<void> {
  const res = await request(`/api/interviews/${id}`, { method: 'DELETE' })
  await unwrap<null>(res, '删除面试记录失败')
}

export async function submitAnswer(sessionId: string, content: string): Promise<AnswerResult> {
  const res = await request(`/api/interviews/${sessionId}/answers`, jsonInit('POST', { content }))
  return unwrap<AnswerResult>(res, '提交回答失败')
}

export async function finishInterview(sessionId: string): Promise<InterviewReport> {
  const res = await request(`/api/interviews/${sessionId}/finish`, { method: 'POST' })
  return unwrap<InterviewReport>(res, '生成复盘报告失败')
}

export interface QuestionStreamHandlers {
  onDelta: (text: string) => void
  onError: (message: string) => void
  onDone?: (payload: { questionId: string; seq: number }) => void
}

/** 生成下一道主问题。增量文本通过 onDelta,结束通过 onDone 带回题目 id */
export async function streamNextQuestion(
  sessionId: string,
  handlers: QuestionStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  let res: Response
  try {
    res = await authFetch(`/api/interviews/${sessionId}/questions`, {
      method: 'POST',
      headers: { Accept: 'text/event-stream' },
      signal,
    })
  } catch (error) {
    if (isAbortError(error)) throw error
    throw new Error('无法连接服务器，请确认后端已启动')
  }

  if (!res.ok) {
    let message = `生成题目失败（HTTP ${res.status}）`
    try {
      const payload = (await res.json()) as ApiResponse<unknown>
      if (payload?.message) message = payload.message
    } catch {
      // 忽略非 JSON 错误体
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
        const stop = handleEvent(buffer.slice(0, index), handlers)
        buffer = buffer.slice(index + 2)
        if (stop) {
          // 收到 done / error 就主动结束读取:不等服务端关闭连接,
          // 否则连接未及时终止时输入框会一直停在“出题中”
          await reader.cancel().catch(() => undefined)
          return
        }
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

/** 处理一个 SSE 事件块;返回 true 表示流已结束,调用方应停止读取 */
function handleEvent(chunk: string, handlers: QuestionStreamHandlers): boolean {
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

  const raw = dataLines.join('\n')
  if (event === 'delta') {
    const text = readField(raw, 'text')
    if (text) handlers.onDelta(text)
    return false
  }
  if (event === 'error') {
    handlers.onError(readField(raw, 'message') || '生成题目失败，请重试')
    return true
  }
  if (event === 'done') {
    const questionId = readField(raw, 'questionId')
    const seq = Number(readField(raw, 'seq'))
    if (questionId) handlers.onDone?.({ questionId, seq: Number.isFinite(seq) ? seq : 0 })
    return true
  }
  return false
}

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
