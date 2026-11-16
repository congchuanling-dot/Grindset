// 错题本接口封装:列表、详情、单题重练。
import { authFetch } from './http'

export type WrongStatus = 'PENDING' | 'MASTERED'

export interface WrongQuestionSummary {
  total: number
  pendingCount: number
  masteredCount: number
  dueCount: number
}

export interface WrongQuestionItem {
  id: string
  questionPreview: string
  knowledgePoints: string[]
  originalScore: number | null
  status: WrongStatus
  retryCount: number
  bestScore: number | null
  lastScore: number | null
  lastRetryAt: string | null
  nextReviewAt: string | null
  createdAt: string | null
  sessionId: string | null
}

export interface WrongQuestionList {
  summary: WrongQuestionSummary
  filteredTotal: number
  items: WrongQuestionItem[]
}

export interface WrongQuestionRetry {
  id: string
  answer: string
  score: number
  comment: string | null
  suggestion: string | null
  referenceAnswer: string | null
  createdAt: string | null
}

export interface WrongQuestionDetail {
  id: string
  questionContent: string
  questionContext: string | null
  knowledgePoints: string[]
  originalScore: number | null
  originalComment: string | null
  originalSuggestion: string | null
  originalFeedback: string | null
  originalAnswer: string | null
  status: WrongStatus
  retryCount: number
  bestScore: number | null
  lastScore: number | null
  nextReviewAt: string | null
  createdAt: string | null
  sessionId: string | null
  position: string | null
  round: string | null
  retries: WrongQuestionRetry[]
}

export interface RetryResult {
  wrongId: string
  score: number
  comment: string | null
  suggestion: string | null
  referenceAnswer: string
  status: WrongStatus
  retryCount: number
  bestScore: number | null
  nextReviewAt: string | null
}

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

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

/** 错题列表;summary 始终按用户全量统计,items 按筛选条件过滤 */
export async function getWrongQuestions(params: {
  status?: WrongStatus
  kpId?: string
  kp?: string
  sessionId?: string
  limit?: number
} = {}): Promise<WrongQuestionList> {
  const search = new URLSearchParams()
  if (params.status) search.set('status', params.status)
  if (params.kpId) search.set('kpId', params.kpId)
  if (params.kp) search.set('kp', params.kp)
  if (params.sessionId) search.set('sessionId', params.sessionId)
  if (params.limit) search.set('limit', String(params.limit))
  const query = search.toString()

  const res = await request(`/api/wrong-questions${query ? `?${query}` : ''}`)
  return unwrap<WrongQuestionList>(res, '加载错题本失败')
}

export async function getWrongQuestionDetail(id: string): Promise<WrongQuestionDetail> {
  const res = await request(`/api/wrong-questions/${id}`)
  return unwrap<WrongQuestionDetail>(res, '加载错题详情失败')
}

export async function retryWrongQuestion(id: string, answer: string): Promise<RetryResult> {
  const res = await request(`/api/wrong-questions/${id}/retry`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ answer }),
  })
  return unwrap<RetryResult>(res, '提交重练失败')
}
