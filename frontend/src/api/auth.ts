// 认证接口封装。后端统一返回 {code, message, data},code=0 才算成功。
// access token 在响应体,refresh token 由后端写入 HttpOnly Cookie,前端无需处理。
import { authFetch, unwrapJson, type ApiResponse } from './http'

export interface AuthResult {
  /** 雪花 ID,后端按字符串下发 */
  userId: string
  accessToken: string
  /** access token 有效期(秒) */
  expiresIn: number
  nickname: string
}

/** 当前用户资料 */
export interface UserInfo {
  userId: string
  nickname: string
  email: string | null
}

/** 统一请求:始终携带 Cookie,登录/刷新/登出都依赖它 */
async function request<T>(path: string, init: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(path, { ...init, credentials: 'include' })
  } catch {
    throw new Error('无法连接服务器，请确认后端已启动')
  }

  let payload: ApiResponse<T>
  try {
    payload = (await res.json()) as ApiResponse<T>
  } catch {
    throw new Error(`请求失败（HTTP ${res.status}）`)
  }

  // HTTP 状态码与业务 code 已对齐,任一不对都按失败处理
  if (!res.ok || payload.code !== 0) {
    throw new Error(payload.message || `请求失败（HTTP ${res.status}）`)
  }
  return payload.data
}

function post<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

/** 注册账号,成功返回令牌(后端会直接建号,等同已登录) */
export function register(account: string, password: string): Promise<AuthResult> {
  return post<AuthResult>('/api/auth/register', { account, password })
}

/** 账号密码登录 */
export function login(account: string, password: string): Promise<AuthResult> {
  return post<AuthResult>('/api/auth/login', { account, password })
}

/** 用 HttpOnly Cookie 里的 refresh token 换一对新令牌 */
export function refresh(): Promise<AuthResult> {
  return post<AuthResult>('/api/auth/refresh')
}

/** 登出:后端删 Redis 里的 refresh 并清 Cookie */
export function logout(): Promise<void> {
  return post<void>('/api/auth/logout')
}

/** 修改昵称,返回最新资料。需要带 access token,所以走 authFetch */
export async function updateProfile(nickname: string): Promise<UserInfo> {
  const res = await authFetch('/api/auth/profile', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nickname }),
  })
  return unwrapJson<UserInfo>(res, '保存昵称失败')
}

/** 跳转 GitHub 授权页:整页跳转,不经过 fetch */
export function githubAuthorize(): void {
  window.location.href = '/api/auth/github/authorize'
}

/** GitHub 回调后用一次性 code 换本系统令牌 */
export function exchangeGithubCode(code: string): Promise<AuthResult> {
  return post<AuthResult>('/api/auth/github/exchange', { code })
}

/** 跳转 Google 授权页:整页跳转,不经过 fetch */
export function googleAuthorize(): void {
  window.location.href = '/api/auth/google/authorize'
}

/** Google 回调后用一次性 code 换本系统令牌 */
export function exchangeGoogleCode(code: string): Promise<AuthResult> {
  return post<AuthResult>('/api/auth/google/exchange', { code })
}
