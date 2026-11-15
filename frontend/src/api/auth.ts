// 认证接口封装。后端统一返回 {code, message, data},code=0 才算成功。

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface AuthResult {
  /** 雪花 ID,后端按字符串下发 */
  userId: string
}

async function post<T>(path: string, body: unknown): Promise<T> {
  let res: Response
  try {
    res = await fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
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

/** 注册账号,成功返回新用户 ID(后端会直接建号,等同已登录) */
export function register(account: string, password: string): Promise<AuthResult> {
  return post<AuthResult>('/api/auth/register', { account, password })
}

/** 账号密码登录 */
export function login(account: string, password: string): Promise<AuthResult> {
  return post<AuthResult>('/api/auth/login', { account, password })
}
