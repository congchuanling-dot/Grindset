// 登录状态的唯一来源。access token 与 userId 都只存内存,刷新页面后靠 refresh Cookie 恢复。
export interface Session {
  userId: string
  account: string
  /** 短期 access token,请求头 Authorization: Bearer 用 */
  accessToken: string
}

let session: Session | null = null

export function getSession(): Session | null {
  return session
}

export function setSession(next: Session | null): void {
  session = next
}

export function isLoggedIn(): boolean {
  return session !== null
}
