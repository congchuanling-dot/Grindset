// 统一请求层:自动带 access token;401 时单飞刷新并重试一次。
import { getSession, setSession } from '../auth/authStore'
import { refresh } from './auth'

/** 同一时刻只允许一个刷新请求,避免并发 401 把 refresh token 轮换两次 */
let refreshing: Promise<boolean> | null = null

/** 用 refresh Cookie 恢复登录态;成功返回 true,失败清空 session 返回 false */
export function ensureRefreshed(): Promise<boolean> {
  if (!refreshing) {
    refreshing = refresh()
      .then((result) => {
        const current = getSession()
        setSession({
          userId: result.userId,
          account: current?.account ?? result.nickname,
          nickname: result.nickname,
          accessToken: result.accessToken,
        })
        return true
      })
      .catch(() => {
        setSession(null)
        return false
      })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

async function send(input: RequestInfo | URL, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers)
  const token = getSession()?.accessToken
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  // credentials: include 让浏览器带上 refresh Cookie;FormData 不要手动设 Content-Type
  return fetch(input, { ...init, headers, credentials: 'include' })
}

/**
 * 带鉴权的 fetch:401 时先刷新再重试原请求一次。
 * SSE 请求同样适用,重试发生在读取流之前。
 */
export async function authFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const response = await send(input, init)
  if (response.status !== 401) {
    return response
  }

  const refreshed = await ensureRefreshed()
  if (!refreshed) {
    if (window.location.pathname !== '/login') {
      window.location.assign('/login')
    }
    return response
  }
  return send(input, init)
}

/** 后端统一响应体 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 解析非流式响应:非 2xx 或业务 code 非 0 都抛出可读错误 */
export async function unwrapJson<T>(res: Response, fallback: string): Promise<T> {
  let payload: ApiResponse<T> | null = null
  try {
    // 错误响应体里通常有更准确的 message,先解析再判断,不要只看 HTTP 状态
    payload = (await res.json()) as ApiResponse<T>
  } catch {
    // 响应体不是 JSON,用兜底文案
  }
  // HTTP 状态和业务 code 任一失败都算失败
  if (!res.ok || !payload || payload.code !== 0) {
    throw new Error(payload?.message || `${fallback}（HTTP ${res.status}）`)
  }
  return payload.data
}

/** 从失败响应里取业务错误文案,取不到就用兜底 */
export async function readApiError(res: Response, fallback: string): Promise<string> {
  try {
    const payload = (await res.json()) as ApiResponse<unknown>
    return payload?.message || `${fallback}（HTTP ${res.status}）`
  } catch {
    // 错误分支不能再抛异常,否则会盖掉原始错误
    return `${fallback}（HTTP ${res.status}）`
  }
}
