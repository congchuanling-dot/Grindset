// 登录状态的唯一来源。后端目前只返回 userId,邮箱由登录时前端记下,仅用于顶栏展示。
// 它和 access token 一样只存内存,刷新页面即退出登录。
export interface Session {
  userId: string
  account: string
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
