// 登录状态的唯一来源。MVP 先用内存变量占位,
// 等后端接口做好后,把 token 换成后端返回的 access token。
let accessToken: string | null = null

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function isLoggedIn(): boolean {
  return accessToken !== null
}
