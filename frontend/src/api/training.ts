// 弱项训练 v2 接口封装。
import { jsonInit, request, unwrapJson } from './http'

export type TrainingStage = 'BASELINE' | 'DAILY' | 'RETEST' | 'MISTAKE'

export interface DimensionView {
  code: string
  label: string
  mastery: number
  delta7d: number
}

export interface GrowthPoint {
  date: string
  mastery: number
}

export interface TrainingWeaknessItem {
  kpKey: string
  kpName: string
  dimension: string
  dimensionLabel: string
  mastery: number
  level: string
  evidence: string | null
  nextReviewAt: string | null
  eliminated: boolean
}

export interface TrainingMistake {
  id: string
  kpKey: string
  kpName: string
  dimension: string
  dimensionLabel: string
  score: number
  status: number
  reviewCount: number
  nextReviewAt: string | null
  createdAt: string | null
  title: string | null
  questionId: string | null
  question: string | null
  missedPoints: string[]
  comment: string | null
  suggestion: string | null
}

export interface TrainingDashboard {
  readiness: number
  readinessDelta7d: number
  hasBaseline: boolean
  dimensions: DimensionView[]
  growth: GrowthPoint[]
  weaknesses: TrainingWeaknessItem[]
  dueCount: number
  mistakeCount: number
  activePlan: {
    id: string
    title: string
    stage: string
    completedCount: number
    itemCount: number
  } | null
  recentMistakes: TrainingMistake[]
}

export interface TrainingGoal {
  kpKey: string
  kpName: string
  dimension: string
  beforeMastery: number
  severity: number
}

export interface SimilarQuestion {
  questionId: string
  title: string
  domain: string | null
  difficulty: number | null
}

export interface ScoringPoint {
  point: string
  weight: number
  required: boolean
  acceptable: string | null
}

export interface TrainingItem {
  id: string
  seq: number
  role: 'PRACTICE' | 'RETEST'
  kpKey: string
  kpName: string
  dimension: string
  dimensionLabel: string
  itemType: 'TEXT' | 'CODING'
  source: 'BANK' | 'AI'
  questionId: string | null
  title: string
  content: string
  keyPoints: string[]
  difficulty: number | null
  status: number
  score: number | null
  hitPoints: string[]
  missedPoints: string[]
  comment: string | null
  suggestion: string | null
  summary: string | null
  detailedFeedback: string | null
  improvements: string[]
  referenceAnswer: string | null
  questionType: string | null
  scoringPoints: ScoringPoint[]
  finishedAt: string | null
  similarQuestions: SimilarQuestion[]
  mistakeId: string | null
}

export interface MasteryDelta {
  kpKey: string
  kpName: string
  dimension: string
  before: number
  after: number
  delta: number
  level: string
  nextReviewAt: string | null
  eliminated: boolean
}

export interface DimensionDelta {
  dimension: string
  label: string
  before: number
  after: number
  delta: number
}

export interface TrainingSessionDetail {
  id: string
  title: string
  sourceType: string
  stage: TrainingStage | string
  status: number
  itemCount: number
  completedCount: number
  accuracy: number
  avgScore: number | null
  createdAt: string | null
  expiresAt: string | null
  goals: TrainingGoal[]
  items: TrainingItem[]
  result: MasteryDelta[]
  dimensionResult: DimensionDelta[]
}

export interface TrainingSessionSummary {
  id: string
  title: string
  sourceType: string
  stage: string
  status: number
  itemCount: number
  completedCount: number
  createdAt: string | null
  expiresAt: string | null
}

export interface TrainingAnswerResult {
  itemId: string
  score: number
  hitPoints: string[]
  missedPoints: string[]
  comment: string | null
  suggestion: string | null
  alreadyCompleted: boolean
  mastery: MasteryDelta | null
  dimension: DimensionDelta | null
  planCompleted: boolean
  completedCount: number
  itemCount: number
  nextDifficulty: number
  similarQuestions: SimilarQuestion[]
  mistakeId: string | null
  eliminated: boolean
  summary: string | null
  detailedFeedback: string | null
  improvements: string[]
  referenceAnswer: string | null
  questionType: string | null
  scoringPoints: ScoringPoint[]
}

export async function getTrainingDashboard(): Promise<TrainingDashboard> {
  const res = await request('/api/training/dashboard')
  return unwrapJson<TrainingDashboard>(res, '加载训练仪表盘失败')
}

export async function createTrainingSession(
  stage: TrainingStage,
  sourceId?: string,
): Promise<TrainingSessionDetail> {
  const res = await request('/api/training/sessions', jsonInit('POST', { stage, sourceId }))
  return unwrapJson<TrainingSessionDetail>(res, '创建训练失败')
}

export async function getTrainingSession(id: string): Promise<TrainingSessionDetail> {
  const res = await request(`/api/training/sessions/${id}`)
  return unwrapJson<TrainingSessionDetail>(res, '加载训练失败')
}

export async function listTrainingSessions(status?: number): Promise<TrainingSessionSummary[]> {
  const query = status === undefined ? '' : `?status=${status}`
  const res = await request(`/api/training/sessions${query}`)
  return unwrapJson<TrainingSessionSummary[]>(res, '加载训练列表失败')
}

export async function submitTrainingAnswer(
  sessionId: string,
  itemId: string,
  answer: string,
): Promise<TrainingAnswerResult> {
  const res = await request(`/api/training/sessions/${sessionId}/items/${itemId}/answer`,
    jsonInit('POST', { answer }))
  return unwrapJson<TrainingAnswerResult>(res, '提交训练作答失败')
}

export async function abandonTrainingSession(sessionId: string): Promise<void> {
  const res = await request(`/api/training/sessions/${sessionId}/abandon`, { method: 'POST' })
  await unwrapJson<null>(res, '放弃训练失败')
}

export async function getTrainingMistakes(status?: number): Promise<TrainingMistake[]> {
  const query = status === undefined ? '' : `?status=${status}`
  const res = await request(`/api/training/mistakes${query}`)
  return unwrapJson<TrainingMistake[]>(res, '加载错题本失败')
}

export async function retrainMistakes(): Promise<TrainingSessionDetail> {
  const res = await request('/api/training/mistakes/retrain', { method: 'POST' })
  return unwrapJson<TrainingSessionDetail>(res, '创建错题重练失败')
}
