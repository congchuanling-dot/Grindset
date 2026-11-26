// AI 面试接口封装:普通请求走 JSON,出题走 SSE 流。
import { authFetch, readApiError, unwrapJson } from './http'
import { isAbortError, readField, readSse } from './sse'

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
}

export interface CreateInterviewPayload {
  resumeId: string
  position: string
  style: string
  round: string
  focus: string[]
  questionCount: number
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
  return unwrapJson<InterviewDetail>(res, '创建面试失败')
}

/** 面试记录列表,可按简历 id 过滤(多个简历取并集) */
export async function listInterviews(resumeIds?: string[]): Promise<InterviewSummary[]> {
  const params = new URLSearchParams()
  for (const id of resumeIds ?? []) params.append('resumeIds', id)
  const query = params.toString()
  const res = await request(`/api/interviews${query ? `?${query}` : ''}`)
  return unwrapJson<InterviewSummary[]>(res, '加载面试记录失败')
}

export async function getInterview(id: string): Promise<InterviewDetail> {
  const res = await request(`/api/interviews/${id}`)
  return unwrapJson<InterviewDetail>(res, '加载面试详情失败')
}

export async function deleteInterview(id: string): Promise<void> {
  const res = await request(`/api/interviews/${id}`, { method: 'DELETE' })
  await unwrapJson<null>(res, '删除面试记录失败')
}

export async function submitAnswer(sessionId: string, content: string): Promise<AnswerResult> {
  const res = await request(`/api/interviews/${sessionId}/answers`, jsonInit('POST', { content }))
  return unwrapJson<AnswerResult>(res, '提交回答失败')
}

/** 生成复盘报告；没有完成任何题目时后端只结束会话，返回 null */
export async function finishInterview(sessionId: string): Promise<InterviewReport | null> {
  const res = await request(`/api/interviews/${sessionId}/finish`, { method: 'POST' })
  return unwrapJson<InterviewReport | null>(res, '生成复盘报告失败')
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

  // 参数/权限错误是普通 JSON,只有正常出题才是 200 + SSE
  if (!res.ok) {
    throw new Error(await readApiError(res, '生成题目失败'))
  }

  await readSse(res, ({ event, data }) => {
    if (event === 'delta') {
      const text = readField(data, 'text')
      if (text) handlers.onDelta(text)
    } else if (event === 'error') {
      handlers.onError(readField(data, 'message') || '生成题目失败，请重试')
      return true
    } else if (event === 'done') {
      // done 带 questionId / seq,前端拿到就能直接渲染,不用再查一次详情
      const questionId = readField(data, 'questionId')
      const seq = Number(readField(data, 'seq'))
      if (questionId) handlers.onDone?.({ questionId, seq: Number.isFinite(seq) ? seq : 0 })
      return true
    }
  })
}
