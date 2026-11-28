// AI 面试接口封装:普通请求走 JSON,出题与编程交流走 SSE 流。
import { authFetch, readApiError, unwrapJson } from './http'
import type { CodeLanguage } from './questionBank'
import { isAbortError, readField, readSse } from './sse'

export interface InterviewSummary {
  id: string
  resumeFileName: string
  position: string
  company: string | null
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
  type: 'QUESTION' | 'FOLLOW_UP' | 'ANSWER' | 'FEEDBACK' | 'CODING_CHAT'
  content: string
  createdAt: string
}

export type InterviewQuestionType = 'VERBAL' | 'CODING'

export interface InterviewQuestion {
  id: string
  seq: number
  type: InterviewQuestionType
  status: number
  followUpCount: number
  score: number | null
  knowledgePoints: string[]
  competency: string | null
  jdGap: string | null
  comment: string | null
  suggestion: string | null
  codingQuestionId: string | null
  codingTitle: string | null
  judgeStatus: string | null
  passed: number | null
  total: number | null
  codingSubmissionId: string | null
  deadlineAt: string | null
  messages: InterviewMessage[]
}

export interface InterviewCodingReport {
  title: string | null
  status: string | null
  exitCode: number | null
  stdout: string | null
  stderr: string | null
  runtimeMs: number | null
  score: number | null
  comment: string | null
}

export interface InterviewWeakness {
  kpKey: string
  kpName: string
  domain: string | null
  dimension: string
  dimensionLabel: string
  severity: number
  evidence: string | null
  advice: string | null
  questionSeq: number | null
  questionId: string | null
}

export interface InterviewReport {
  overallScore: number
  dimensionScores: Record<string, number>
  weakPoints: string[]
  highlights: string[]
  advice: string[]
  jdMatchScore?: number | null
  jdCoverage?: JdCoverage[]
  jdGaps?: string[]
  coding?: InterviewCodingReport | null
  weaknesses?: InterviewWeakness[]
}

export interface JdCoverage {
  name: string
  weight: number
  score: number | null
  evidence: string | null
}

export interface JdCompetency {
  name: string
  weight: number
  expectation: string | null
}

export interface JdProfile {
  title: string | null
  company: string | null
  level: string | null
  mustHave: string[]
  niceToHave: string[]
  responsibilities: string[]
  competencies: JdCompetency[]
  resumeGaps: string[]
}

export interface InterviewPlanItem {
  type: string
  label: string
  count: number
}

export interface InterviewPlanCompetency {
  name: string
  weight: number
  source: string
}

export interface InterviewPlan {
  mode: string
  round: string
  durationMin: number
  questionCount: number
  mix: InterviewPlanItem[]
  competencies: InterviewPlanCompetency[]
  style: string
  rationale: string[]
  warnings: string[]
}

export interface InterviewPlanPreview {
  position: string
  jdProfile: JdProfile | null
  plan: InterviewPlan
}

export interface InterviewDetail {
  id: string
  resumeId: string
  resumeFileName: string
  position: string
  company: string | null
  jobLevel: string | null
  jdEnabled: boolean
  jdProfile: JdProfile | null
  style: string
  round: string | null
  focus: string[]
  questionCount: number
  codingEnabled: boolean
  codingTimeLimitSec: number
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
  nextAction: 'ANSWER_FOLLOW_UP' | 'NEXT_QUESTION' | 'CODING_READY' | 'REPORT_READY'
  completedQuestions: number
  questionCount: number
}

export interface CreateInterviewPayload {
  resumeId: string
  position: string
  company?: string
  jobLevel?: string
  jdText?: string
  jdProfile?: JdProfile
  style: string
  round: string
  focus: string[]
  questionCount: number
  codingEnabled?: boolean
  codingTimeLimitSec?: number
}

export interface InterviewPlanPayload {
  resumeId: string
  mode: string
  position?: string
  company?: string
  jobLevel?: string
  jdText?: string
  style: string
  round: string
  durationMin: number
  questionCount: number
  focus: string[]
  codingEnabled: boolean
  codingTimeLimitSec: number
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

/** 创建前预览:解析 JD 并生成推荐计划,不创建会话 */
export async function previewInterviewPlan(payload: InterviewPlanPayload): Promise<InterviewPlanPreview> {
  const res = await request('/api/interviews/plan', jsonInit('POST', payload))
  return unwrapJson<InterviewPlanPreview>(res, '生成面试计划失败')
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

export interface InterviewRunResult {
  status: string
  stdout: string
  stderr: string
  exitCode: number
  runtimeMs: number
  ranAt: string | null
}

export interface InterviewCodingView {
  interviewQuestionId: string | null
  seq: number
  skipped: boolean
  codingQuestionId: string | null
  title: string | null
  content: string | null
  difficulty: number
  domain: string | null
  knowledgePoints: string[]
  intro: string | null
  deadlineAt: string | null
  deadlineAtEpochMs: number | null
  starterCode: Partial<Record<CodeLanguage, string>>
  codeDraft: string | null
  codeLanguage: CodeLanguage | null
  latestRun: InterviewRunResult | null
}

export interface InterviewCodingResult {
  status: string
  exitCode: number
  stdout: string
  stderr: string
  runtimeMs: number
  score: number
  comment: string | null
  suggestion: string | null
  nextAction: 'REPORT_READY'
  completedQuestions: number
  questionCount: number
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

// ---------- 手撕代码 ----------

export async function startInterviewCoding(sessionId: string): Promise<InterviewCodingView> {
  const res = await request(`/api/interviews/${sessionId}/coding/start`, { method: 'POST' })
  return unwrapJson<InterviewCodingView>(res, '开始手撕代码失败')
}

export async function runInterviewCode(
  sessionId: string,
  language: CodeLanguage,
  code: string,
): Promise<InterviewRunResult> {
  const res = await request(`/api/interviews/${sessionId}/coding/run`, jsonInit('POST', {
    language,
    code,
  }))
  return unwrapJson<InterviewRunResult>(res, '运行失败')
}

export async function completeInterviewCoding(sessionId: string): Promise<InterviewCodingResult> {
  const res = await request(`/api/interviews/${sessionId}/coding/complete`, { method: 'POST' })
  return unwrapJson<InterviewCodingResult>(res, '完成本题失败')
}

export async function saveInterviewCodingDraft(
  sessionId: string,
  language: CodeLanguage,
  code: string,
): Promise<void> {
  const res = await request(`/api/interviews/${sessionId}/coding/draft`, jsonInit('POST', {
    language,
    code,
  }))
  await unwrapJson<null>(res, '保存代码草稿失败')
}

export interface CodingChatHandlers {
  onDelta: (text: string) => void
  onError: (message: string) => void
  onDone?: () => void
}

/** 编程阶段问面试官;带上当前代码,面试官才能基于最新实现回答 */
export async function streamCodingChat(
  sessionId: string,
  payload: { message: string; code: string; language: CodeLanguage },
  handlers: CodingChatHandlers,
  signal?: AbortSignal,
): Promise<void> {
  let res: Response
  try {
    res = await authFetch(`/api/interviews/${sessionId}/coding/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify(payload),
      signal,
    })
  } catch (error) {
    if (isAbortError(error)) throw error
    throw new Error('无法连接服务器，请确认后端已启动')
  }

  if (!res.ok) {
    throw new Error(await readApiError(res, '面试官回复失败'))
  }

  await readSse(res, ({ event, data }) => {
    if (event === 'delta') {
      const text = readField(data, 'text')
      if (text) handlers.onDelta(text)
    } else if (event === 'error') {
      handlers.onError(readField(data, 'message') || '面试官回复失败，请重试')
      return true
    } else if (event === 'done') {
      handlers.onDone?.()
      return true
    }
  })
}
