// 管理接口封装:管理员登录 + 模型单价维护。鉴权走 X-Admin-Token,与用户 JWT 无关。
import { readApiError, unwrapJson } from './http'

const TOKEN_KEY = 'grindset.adminToken'

/** 管理会话失效,前端收到后回到登录页 */
export class AdminAuthError extends Error {}

/** 管理会话只放 sessionStorage,关掉标签页即失效 */
export function getAdminToken(): string {
  return sessionStorage.getItem(TOKEN_KEY) ?? ''
}

export function saveAdminToken(token: string): void {
  if (token) {
    sessionStorage.setItem(TOKEN_KEY, token)
  } else {
    sessionStorage.removeItem(TOKEN_KEY)
  }
}

function adminHeaders(): HeadersInit {
  return { 'X-Admin-Token': getAdminToken() }
}

/** 401/403 统一当成管理会话失效,由页面跳回登录态 */
async function unwrapAdmin<T>(res: Response, fallback: string): Promise<T> {
  if (res.status === 401 || res.status === 403) {
    throw new AdminAuthError(await readApiError(res, '管理登录已失效，请重新登录'))
  }
  return unwrapJson<T>(res, fallback)
}

/** 管理员登录:只有后端配置的固定账号能通过,成功返回会话令牌 */
export async function adminLogin(account: string, password: string): Promise<string> {
  const res = await fetch('/api/admin/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, password }),
  })
  const data = await unwrapAdmin<{ token: string }>(res, '登录失败')
  return data.token
}

/** 退出登录:顺手让后端删掉 Redis 会话 */
export async function adminLogout(): Promise<void> {
  try {
    await fetch('/api/admin/logout', { method: 'POST', headers: adminHeaders() })
  } catch {
    // 后端不可用也要保证前端退出
  }
}

/** 单价单位统一为「元/百万 token」,与厂商官网口径一致 */
export interface AdminModelPrice {
  id: string
  providerId: string
  channelName: string
  modelId: string
  displayName: string
  enabled: boolean
  inputPrice: number
  cachedInputPrice: number
  outputPrice: number
}

export async function listModelPrices(): Promise<AdminModelPrice[]> {
  const res = await fetch('/api/admin/llm/models', { headers: adminHeaders() })
  return unwrapAdmin<AdminModelPrice[]>(res, '加载模型价格失败')
}

export async function updateModelPrices(
  id: string,
  prices: { inputPrice: number; cachedInputPrice: number; outputPrice: number },
): Promise<void> {
  const res = await fetch(`/api/admin/llm/models/${id}/prices`, {
    method: 'PUT',
    headers: { ...adminHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify(prices),
  })
  await unwrapAdmin<null>(res, '保存单价失败')
}

/** 渠道余额信息(管理页只看展示字段) */
export interface AdminChannelBalance {
  id: number
  providerId: string
  name: string
  enabled: boolean
  balance: number | null
  currency: string
  source: string
  balanceUpdatedAt: string | null
}

export async function listChannelBalances(): Promise<AdminChannelBalance[]> {
  const res = await fetch('/api/admin/llm/channels', { headers: adminHeaders() })
  return unwrapAdmin<AdminChannelBalance[]>(res, '加载余额失败')
}

/** 立即向厂商查询一次余额(目前仅 DeepSeek 支持) */
export async function refreshChannelBalance(
  id: number,
): Promise<{ balance: number | null; currency: string; updatedAt: string | null }> {
  const res = await fetch(`/api/admin/llm/channels/${id}/balance/refresh`, {
    method: 'POST',
    headers: adminHeaders(),
  })
  return unwrapAdmin(res, '刷新余额失败')
}
