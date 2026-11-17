// 题库接口封装:列表筛选、题目详情、提交作答与作答历史。
import { authFetch } from './http'

export type QuestionStatus = 'TODO' | 'DONE' | 'WRONG'
export type QuestionType = 'BASICS' | 'SCENARIO' | 'PROJECT' | 'CODE'
export type JudgeMode = 'TEXT' | 'CODING'
export type CodeLanguage = 'JAVA' | 'PYTHON'
export type CodingStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'COMPILE_ERROR'
  | 'RUNTIME_ERROR'
  | 'TIME_LIMIT_EXCEEDED'
  | 'SYSTEM_ERROR'

export interface QuestionBankSummary {
  total: number
  done: number
  wrong: number
  todo: number
  totalAttempts: number
}

export interface QuestionBankItem {
  id: string
  title: string
  preview: string
  type: QuestionType
  judgeMode: JudgeMode
  difficulty: number
  domain: string
  knowledgePoints: string[]
  status: QuestionStatus
  attemptCount: number
  bestScore: number | null
  lastScore: number | null
  lastAttemptAt: string | null
}

export interface QuestionBankList {
  summary: QuestionBankSummary
  filteredTotal: number
  page: number
  size: number
  items: QuestionBankItem[]
}

export interface QuestionAttemptView {
  id: string
  answer: string
  score: number
  hitPoints: string[]
  missedPoints: string[]
  comment: string | null
  suggestion: string | null
  createdAt: string | null
}

export interface QuestionBankDetail {
  id: string
  title: string
  content: string
  type: QuestionType
  judgeMode: JudgeMode
  difficulty: number
  domain: string
  knowledgePoints: string[]
  keyPoints: string[]
  referenceAnswer: string | null
  status: QuestionStatus
  attemptCount: number
  bestScore: number | null
  lastScore: number | null
  lastAttemptAt: string | null
  attempts: QuestionAttemptView[]
}

export interface QuestionAttemptResult {
  attemptId: string
  score: number
  hitPoints: string[]
  missedPoints: string[]
  comment: string | null
  suggestion: string | null
  referenceAnswer: string | null
  keyPoints: string[]
  status: QuestionStatus
  attemptCount: number
  bestScore: number | null
  createdAt: string | null
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

/** 题库列表;summary 始终按用户全量统计,items 按筛选条件分页 */
export async function getQuestions(params: {
  domain?: string
  difficulty?: number
  status?: QuestionStatus
  keyword?: string
  page?: number
  size?: number
} = {}): Promise<QuestionBankList> {
  const search = new URLSearchParams()
  if (params.domain) search.set('domain', params.domain)
  if (params.difficulty) search.set('difficulty', String(params.difficulty))
  if (params.status) search.set('status', params.status)
  if (params.keyword) search.set('keyword', params.keyword)
  if (params.page) search.set('page', String(params.page))
  if (params.size) search.set('size', String(params.size))
  const query = search.toString()

  const res = await request(`/api/questions${query ? `?${query}` : ''}`)
  return unwrap<QuestionBankList>(res, '加载题库失败')
}

export async function getQuestionDetail(id: string): Promise<QuestionBankDetail> {
  const res = await request(`/api/questions/${id}`)
  return unwrap<QuestionBankDetail>(res, '加载题目失败')
}

export async function submitQuestionAnswer(id: string, answer: string): Promise<QuestionAttemptResult> {
  const res = await request(`/api/questions/${id}/attempts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ answer }),
  })
  return unwrap<QuestionAttemptResult>(res, '提交作答失败')
}

// ---------- 编程题 ----------

export interface CodingSampleCase {
  input: string
  expectedOutput: string
  sort: number
}

export interface CodingDetail {
  id: string
  title: string
  content: string
  type: QuestionType
  difficulty: number
  domain: string
  knowledgePoints: string[]
  starterCode: Partial<Record<CodeLanguage, string>>
  timeLimitMs: number
  memoryLimitMb: number
  samples: CodingSampleCase[]
  status: QuestionStatus
  submissionCount: number
  accepted: boolean
}

export interface CodeCaseResult {
  index: number
  input: string
  expectedOutput: string
  actualOutput: string
  passed: boolean
  message: string
}

export interface CodeRunResult {
  status: CodingStatus
  passed: number
  total: number
  runtimeMs: number
  errorMessage: string | null
  cases: CodeCaseResult[]
}

export interface CodingSubmission {
  id: string
  questionId: string
  language: CodeLanguage
  code: string
  status: CodingStatus
  passed: number
  total: number
  runtimeMs: number | null
  errorMessage: string | null
  createdAt: string | null
  finishedAt: string | null
}

export async function getCodingDetail(id: string): Promise<CodingDetail> {
  const res = await request(`/api/questions/${id}/coding`)
  return unwrap<CodingDetail>(res, '加载编程题失败')
}

export async function runCode(id: string, language: CodeLanguage, code: string): Promise<CodeRunResult> {
  const res = await request(`/api/questions/${id}/run`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ language, code }),
  })
  return unwrap<CodeRunResult>(res, '运行失败')
}

export async function submitCode(id: string, language: CodeLanguage, code: string): Promise<CodingSubmission> {
  const res = await request(`/api/questions/${id}/submissions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ language, code }),
  })
  return unwrap<CodingSubmission>(res, '提交失败')
}

export async function getSubmission(submissionId: string): Promise<CodingSubmission> {
  const res = await request(`/api/questions/submissions/${submissionId}`)
  return unwrap<CodingSubmission>(res, '查询判题结果失败')
}

export async function getCodingSubmissions(id: string): Promise<CodingSubmission[]> {
  const res = await request(`/api/questions/${id}/submissions`)
  return unwrap<CodingSubmission[]>(res, '加载提交历史失败')
}
