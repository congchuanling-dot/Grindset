// 登录状态的唯一来源。后端目前只返回 userId(JWT 下一步接入),
// 所以先用内存里的 userId 判登录态;它和 access token 一样只存内存,刷新页面即退出。
let userId: string | null = null

export function getUserId(): string | null {
  return userId
}

export function setUserId(id: string | null): void {
  userId = id
}

export function isLoggedIn(): boolean {
  return userId !== null
}
