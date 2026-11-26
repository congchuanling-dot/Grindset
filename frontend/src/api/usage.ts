// Token 用量接口封装:汇总统计 + 调用明细分页。
import { authFetch, unwrapJson } from './http'

export interface BizUsage {
  bizType: string
  label: string
  calls: number
  inputTokens: number
  outputTokens: number
  cost: number
}

export interface ModelUsage {
  providerId: string
  model: string
  calls: number
  inputTokens: number
  outputTokens: number
  cost: number
}

export interface DailyUsage {
  date: string
  inputTokens: number
  outputTokens: number
  cost: number
}

export interface UsageSummary {
  calls: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  cost: number
  byBizType: BizUsage[]
  byModel: ModelUsage[]
  daily: DailyUsage[]
}

export interface UsageRecord {
  id: string
  bizType: string
  bizLabel: string
  providerId: string
  model: string
  inputTokens: number
  outputTokens: number
  cachedTokens: number
  estimated: boolean
  cost: number
  createdAt: string
}

export interface UsageRecordPage {
  items: UsageRecord[]
  total: number
  page: number
  size: number
}

/** 汇总统计;days 支持 1 / 7 / 30,后端会夹到 1~90 */
export async function getUsageSummary(days: number): Promise<UsageSummary> {
  const res = await authFetch(`/api/usage/summary?days=${days}`)
  return unwrapJson<UsageSummary>(res, '加载用量统计失败')
}

/** 调用明细分页 */
export async function listUsageRecords(page: number, size = 20): Promise<UsageRecordPage> {
  const res = await authFetch(`/api/usage/records?page=${page}&size=${size}`)
  return unwrapJson<UsageRecordPage>(res, '加载用量明细失败')
}
