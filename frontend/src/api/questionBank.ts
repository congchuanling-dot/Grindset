// 题库接口封装:列表筛选、题目详情、提交作答与作答历史。
import { request, unwrapJson } from './http'

export type QuestionStatus = 'TODO' | 'DONE' | 'WRONG'
export type QuestionType = 'BASICS' | 'SCENARIO' | 'PROJECT' | 'CODE'
export type JudgeMode = 'TEXT' | 'CODING'
export type CodeLanguage = 'JAVA' | 'PYTHON'

/** 题单标签展示名；CODETOP 是来源标记，不在列表里重复展示 */
export const QUESTION_TAG_LABELS: Record<string, string> = {
  HIGH_FREQ: '高频',
  HOT100: '热题100',
  JIANZHI: '剑指Offer',
  CODETOP: 'CodeTop',
  BASE: '基础',
}

export function questionTagLabel(tag: string): string {
  return QUESTION_TAG_LABELS[tag] ?? tag
}

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
  tags: string[]
  companies: string[]
  leetcodeNo: number | null
  leetcodeSlug: string | null
  hotScore: number
  lastAskedAt: string | null
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
  seq: number
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
  tags: string[]
  companies: string[]
  leetcodeNo: number | null
  leetcodeSlug: string | null
  hotScore: number
  lastAskedAt: string | null
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

/** 题库列表;summary 始终按用户全量统计,items 按筛选条件分页 */
export async function getQuestions(params: {
  domain?: string
  type?: QuestionType
  difficulty?: number
  status?: QuestionStatus
  keyword?: string
  judgeMode?: JudgeMode
  tag?: string
  company?: string
  sort?: 'hot' | 'default'
  page?: number
  size?: number
} = {}): Promise<QuestionBankList> {
  const search = new URLSearchParams()
  if (params.domain) search.set('domain', params.domain)
  if (params.type) search.set('type', params.type)
  if (params.difficulty) search.set('difficulty', String(params.difficulty))
  if (params.status) search.set('status', params.status)
  if (params.keyword) search.set('keyword', params.keyword)
  if (params.judgeMode) search.set('judgeMode', params.judgeMode)
  if (params.tag) search.set('tag', params.tag)
  if (params.company) search.set('company', params.company)
  if (params.sort) search.set('sort', params.sort)
  if (params.page) search.set('page', String(params.page))
  if (params.size) search.set('size', String(params.size))
  const query = search.toString()

  const res = await request(`/api/questions${query ? `?${query}` : ''}`)
  return unwrapJson<QuestionBankList>(res, '加载题库失败')
}

/** 只取做题统计;judgeMode 分开问答/编程，tag/company 把统计收窄到当前题单 */
export async function getQuestionStats(
  judgeMode?: JudgeMode,
  tag?: string,
  company?: string,
): Promise<QuestionBankSummary> {
  const search = new URLSearchParams()
  if (judgeMode) search.set('judgeMode', judgeMode)
  if (tag) search.set('tag', tag)
  if (company) search.set('company', company)
  const query = search.toString()
  const res = await request(`/api/questions/stats${query ? `?${query}` : ''}`)
  return unwrapJson<QuestionBankSummary>(res, '加载做题统计失败')
}

export async function getQuestionDetail(id: string): Promise<QuestionBankDetail> {
  const res = await request(`/api/questions/${id}`)
  return unwrapJson<QuestionBankDetail>(res, '加载题目失败')
}

export interface QuestionFacet {
  value: string
  count: number
}

export interface QuestionBankFacets {
  domains: QuestionFacet[]
  types: QuestionFacet[]
  tags: QuestionFacet[]
  companies: QuestionFacet[]
}

/** 领域与题型的动态筛选项,计数只统计已上架题目;按 judgeMode 分开 */
export async function getQuestionFacets(judgeMode?: JudgeMode): Promise<QuestionBankFacets> {
  const search = new URLSearchParams()
  if (judgeMode) search.set('judgeMode', judgeMode)
  const query = search.toString()
  const res = await request(`/api/questions/facets${query ? `?${query}` : ''}`)
  return unwrapJson<QuestionBankFacets>(res, '加载筛选项失败')
}

export interface QuestionNeighbors {
  prevId: string | null
  nextId: string | null
}

/** 默认排序下当前题的前后相邻题,用于连续刷题 */
export async function getQuestionNeighbors(id: string): Promise<QuestionNeighbors> {
  const res = await request(`/api/questions/${id}/neighbors`)
  return unwrapJson<QuestionNeighbors>(res, '加载相邻题目失败')
}

/** 随机一题,可排除当前题;judgeMode 限定问答题或编程题 */
export async function getRandomQuestionId(excludeId?: string, judgeMode?: JudgeMode): Promise<string> {
  const search = new URLSearchParams()
  if (excludeId) search.set('excludeId', excludeId)
  if (judgeMode) search.set('judgeMode', judgeMode)
  const query = search.toString()
  const res = await request(`/api/questions/random${query ? `?${query}` : ''}`)
  return unwrapJson<string>(res, '随机选题失败')
}

export async function submitQuestionAnswer(id: string, answer: string): Promise<QuestionAttemptResult> {
  const res = await request(`/api/questions/${id}/attempts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ answer }),
  })
  return unwrapJson<QuestionAttemptResult>(res, '提交作答失败')
}

// ---------- 编程题 ----------

/** 自由运行的结果:只表示程序是否跑完,不判断答案对错 */
export type FreeRunStatus =
  | 'SUCCESS'
  | 'COMPILE_ERROR'
  | 'RUNTIME_ERROR'
  | 'TIME_LIMIT_EXCEEDED'
  | 'SYSTEM_ERROR'

export interface CodingReviewView {
  id: string
  code: string
  score: number
  hitPoints: string[]
  missedPoints: string[]
  comment: string | null
  suggestion: string | null
  createdAt: string | null
}

export interface CodingDetail {
  id: string
  title: string
  content: string
  type: QuestionType
  difficulty: number
  domain: string
  knowledgePoints: string[]
  /** main 骨架 */
  starterCode: Partial<Record<CodeLanguage, string>>
  timeLimitMs: number
  memoryLimitMb: number
  tags: string[]
  companies: string[]
  leetcodeNo: number | null
  leetcodeSlug: string | null
  hotScore: number
  lastAskedAt: string | null
  status: QuestionStatus
}

export interface CodeRunResult {
  runStatus: FreeRunStatus
  stdout: string
  stderr: string
  exitCode: number
  runtimeMs: number
}

export interface CodingReviewResult extends CodeRunResult {
  score: number
  hitPoints: string[]
  missedPoints: string[]
  comment: string | null
  suggestion: string | null
  progressStatus: QuestionStatus
  attemptCount: number
  bestScore: number | null
  createdAt: string | null
}

export async function getCodingDetail(id: string): Promise<CodingDetail> {
  const res = await request(`/api/questions/${id}/coding`)
  return unwrapJson<CodingDetail>(res, '加载编程题失败')
}

export async function runCode(
  id: string,
  language: CodeLanguage,
  code: string,
): Promise<CodeRunResult> {
  const res = await request(`/api/questions/${id}/run`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ language, code }),
  })
  return unwrapJson<CodeRunResult>(res, '运行失败')
}

export async function reviewCode(
  id: string,
  language: CodeLanguage,
  code: string,
): Promise<CodingReviewResult> {
  const res = await request(`/api/questions/${id}/review`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ language, code }),
  })
  return unwrapJson<CodingReviewResult>(res, 'AI 点评失败')
}

export async function getCodingReviews(id: string): Promise<CodingReviewView[]> {
  const res = await request(`/api/questions/${id}/reviews`)
  return unwrapJson<CodingReviewView[]>(res, '加载点评历史失败')
}
