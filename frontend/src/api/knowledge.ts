// 掌握度画像接口封装
import { authFetch } from './http'

export interface KnowledgeSummary {
  total: number
  weakCount: number
  learningCount: number
  masteredCount: number
  averageMastery: number
}

export type KnowledgeLevel = 'WEAK' | 'LEARNING' | 'MASTERED'
export type KnowledgeTrend = 'NEW' | 'UP' | 'DOWN' | 'FLAT'

export interface KnowledgeState {
  knowledgePointId: string
  name: string
  domain: string
  mastery: number
  prevMastery: number | null
  level: KnowledgeLevel
  trend: KnowledgeTrend
  practiceCount: number
  correctCount: number
  lastScore: number | null
  lastPracticedAt: string | null
}

export interface KnowledgeProfile {
  summary: KnowledgeSummary
  items: KnowledgeState[]
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

/** 查询掌握度画像;level 可选 WEAK/LEARNING/MASTERED,domain 为领域名称 */
export async function getKnowledgeProfile(params: {
  level?: string
  domain?: string
  limit?: number
} = {}): Promise<KnowledgeProfile> {
  const search = new URLSearchParams()
  if (params.level) search.set('level', params.level)
  if (params.domain) search.set('domain', params.domain)
  if (params.limit) search.set('limit', String(params.limit))
  const query = search.toString()

  let res: Response
  try {
    res = await authFetch(`/api/knowledge/profile${query ? `?${query}` : ''}`)
  } catch {
    throw new Error('无法连接服务器，请确认后端已启动')
  }
  return unwrap<KnowledgeProfile>(res, '加载掌握度画像失败')
}
