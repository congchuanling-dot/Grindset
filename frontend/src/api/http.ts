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
