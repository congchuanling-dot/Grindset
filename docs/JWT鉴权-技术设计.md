# JWT 鉴权 · 技术设计文档

> 版本:v1.0
> 作者:从传领
> 日期:2026-11-16
> 关联:[面试复习闭环-PRD](面试复习闭环-PRD.md) 的 F-13(用户系统)、[登录模块-技术设计](登录模块-技术设计.md)
> 一句话:在现有账号密码登录之上,补一套**短期 access JWT + 长期 refresh 随机串**的无状态鉴权,支持自动续期、刷新页面恢复登录态和主动登出。

---

## 1. 范围

### 1.1 本次实现

| 能力 | 说明 |
|---|---|
| access JWT | HS256/384/512 由密钥长度自动选择,默认 30 分钟,前端只存内存 |
| refresh token | 32 字节随机串,Redis 存储,默认 7 天,HttpOnly Cookie |
| 令牌轮换 | 每次刷新消费旧 refresh 并签发新的一对,旧 token 立即失效 |
| 登出 | 删除 Redis 中的 refresh 并清除 Cookie |
| 当前用户 | `GET /api/auth/me` 返回 userId / nickname / email |
| 前端自动续期 | 401 时单飞刷新一次并重试原请求 |
| 刷新页面恢复 | 内存 access 丢失后,用 refresh Cookie 自动换新令牌 |
| 接口保护 | 除白名单外所有接口要求登录 |

### 1.2 明确不做

- OAuth(GitHub / Google)登录、邮箱验证码。
- 登录/刷新接口限流(接 PRD F-15 时再做)。
- refresh token 重放检测(当前靠“消费即失效”的轮换机制)。
- access token 即时吊销(登出后已签发的 access 最长再活 30 分钟)。
- 多设备会话管理、登出所有设备。
- 跨站部署的完整 CORS 配置(本地走 Vite 代理,同源)。

---

## 2. 总体设计

| | access token | refresh token |
|---|---|---|
| 形态 | JWT | 32 字节随机串(Base64URL),不是 JWT |
| 有效期 | 30 分钟(`access-ttl-minutes`) | 7 天(`refresh-ttl-days`) |
| 存储位置 | 前端内存(authStore) | Redis + 浏览器 HttpOnly Cookie |
| 传输方式 | `Authorization: Bearer <token>` | 浏览器自动携带,JS 不可读 |
| 校验方式 | 验签 + 过期时间,不查库 | 查 Redis 并原子删除 |
| 能否主动失效 | 否,到期自然失效 | 能,删除 Redis key 立即失效 |

### 2.1 登录/注册

```
客户端 POST /api/auth/login
  → 校验账号密码
  → 签发 access JWT
  → 生成 refresh 随机串,Redis 存 auth:refresh:{sha256} → userId
  → 响应体返回 accessToken
  → Set-Cookie: refresh_token=...; HttpOnly; Path=/api/auth; Max-Age=7d
```

### 2.2 普通业务请求

```
客户端带 Authorization: Bearer <access>
  → JwtAuthFilter 验签
  → 把 LoginUser(userId) 放进 SecurityContext
  → 业务接口直接取 userId
```

### 2.3 access 过期

```
业务请求返回 401
  → 前端 authFetch 捕获
  → 单飞调用 POST /api/auth/refresh(浏览器自动带 Cookie)
  → 后端消费旧 refresh,签发新的一对 token
  → 前端更新内存 access,重试原请求一次
```

### 2.4 刷新页面

```
内存 session 丢失
  → RequireAuth 调 ensureRefreshed()
  → POST /api/auth/refresh 成功 → 恢复登录态
  → 失败 → 跳转 /login
```

### 2.5 登出

```
POST /api/auth/logout
  → 删除 Redis 里的 refresh
  → Set-Cookie: refresh_token=; Max-Age=0
  → 前端清空内存 session,跳登录页
```

---

## 3. 存储设计

### 3.1 Redis

| key | value | TTL | 说明 |
|---|---|---|---|
| `auth:refresh:{sha256(token)}` | `userId` | `refresh-ttl-days` 天 | 只存哈希,Redis 泄露也无法直接使用原始 token |

- 消费使用 Redis `GETDEL`(`StringRedisTemplate.opsForValue().getAndDelete`),保证同一个 refresh 只能成功换一次。
- 登出直接 `DEL`。
- 未引入额外表结构,复用现有 `users` / `user_identities`。

### 3.2 Cookie

| 属性 | 值 | 说明 |
|---|---|---|
| name | `refresh_token` | |
| HttpOnly | true | JS 读不到,防 XSS 窃取 |
| Secure | 配置项,默认 false | 本地 http 必须 false;线上 https 设 true |
| SameSite | 配置项,默认 `Lax` | 同站部署用 Lax;跨站用 None + Secure |
| Path | `/api/auth` | 只在认证接口上携带,减少暴露面 |
| Max-Age | `refresh-ttl-days` 天 | 与 Redis TTL 对齐 |

---

## 4. 接口设计

统一前缀 `/api/auth`。

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/register` | 公开 | 注册并直接签发双 token |
| POST | `/login` | 公开 | 登录并签发双 token |
| POST | `/refresh` | Cookie | 消费旧 refresh,轮换新的一对 token |
| POST | `/logout` | Cookie | 删除 refresh,清 Cookie |
| GET | `/me` | access token | 返回当前用户资料 |

### 4.1 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "userId": "380526153426800640",
    "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
    "expiresIn": 1800,
    "nickname": "you@example.com"
  }
}
```

- `userId` 按字符串下发,避免雪花 ID 在 JS 端精度丢失。
- `expiresIn` 单位为秒。

### 4.2 失败约定

| HTTP | code | 场景 |
|---|---|---|
| 400 | 400 | 入参格式错误、邮箱/密码不合规 |
| 401 | 401 | 账号或密码错误、access 无效/过期、refresh 无效或已使用 |
| 403 | 403 | 账号被禁用 |
| 409 | 409 | 邮箱已注册 |
| 503 | 503 | Redis 不可用,会话服务暂时不可用 |

未登录/过期时,Spring Security 的 401/403 响应也保持统一 `{code,message,data}` 结构。

---

## 5. 后端实现

### 5.1 类与职责

| 类 | 职责 |
|---|---|
| `config/JwtProperties` | 读取 `app.jwt.*`:密钥、两个 TTL、Cookie 属性 |
| `security/JwtService` | 签发/解析 access JWT;启动时校验密钥长度 |
| `security/RefreshTokenService` | 生成随机 refresh、Redis 存储、GETDEL 消费、吊销 |
| `security/TokenService` | 组合操作:`issue` / `rotate` / `revoke` |
| `security/LoginUser` | SecurityContext 中的当前用户,只带 userId |
| `security/JwtAuthFilter` | 解析 Bearer,验签成功写入 SecurityContext |
| `security/RestAuthenticationEntryPoint` | 统一 401 JSON |
| `security/RestAccessDeniedHandler` | 统一 403 JSON |
| `config/SecurityConfig` | 无状态鉴权、白名单、注册过滤器 |
| `controller/AuthController` | 登录/注册/刷新/登出/me,写 refresh Cookie |
| `service/AuthService` | 账号密码校验、注册、`requireActiveUser` |
| `dto/AuthResult` / `dto/UserInfo` | 令牌响应 / 当前用户资料 |

### 5.2 access JWT

- 载荷:`sub=userId`、`typ=access`、`iat`、`exp`。
- 签名密钥由 `app.jwt.secret` 派生,长度必须 ≥ 32 字节,否则应用启动失败。
- 解析允许 60 秒时钟偏差。
- 校验 `typ`,防止 refresh 或其他 JWT 被当成 access 使用。

### 5.3 refresh token

- `SecureRandom` 生成 32 字节,Base64URL 无填充编码。
- Redis key 使用 SHA-256 十六进制,不存原文。
- 刷新时先 `GETDEL` 旧 token,再签发新的一对,实现轮换。
- 刷新失败时控制器会顺带清除浏览器里的失效 Cookie。

### 5.4 安全过滤链

```
OPTIONS /**                          → 放行(预检)
/api/health, /error                  → 放行
/api/auth/login|register|refresh|logout → 放行
/api/admin/**                        → 放行(继续由 X-Admin-Token 自校验)
其余接口                              → 必须登录
```

- `SessionCreationPolicy.STATELESS`,关闭 formLogin / httpBasic / CSRF。
- `JwtAuthFilter` 不抛异常:无效 token 只清空上下文,公开接口继续执行,受保护接口由 EntryPoint 返回 401。
- `JwtAuthFilter` 直接 `new` 进过滤器链,不声明为 Spring Bean,避免被 Servlet 容器重复注册。

### 5.5 管理接口兼容

`/api/admin/**` 仍走原有的 `X-Admin-Token` 校验,不纳入 JWT,避免现有后台调用被破坏。

---

## 6. 前端实现

### 6.1 状态

`auth/authStore.ts` 的 Session:

```ts
export interface Session {
  userId: string
  account: string
  accessToken: string
}
```

- 只存内存,不写 localStorage。
- 刷新页面后由 refresh Cookie 恢复。

### 6.2 统一请求层

`api/http.ts` 提供 `authFetch`:

1. 从 session 取 access token,写入 `Authorization: Bearer`。
2. 始终 `credentials: 'include'`,让浏览器带 refresh Cookie。
3. 收到 401 → 调 `ensureRefreshed()`。
4. 刷新成功 → 重试原请求一次。
5. 刷新失败 → 清空 session 并跳登录页。

`ensureRefreshed` 使用模块级 Promise 做**单飞**:

- 并发多个 401 只发一次 `/api/auth/refresh`。
- 同时覆盖 React StrictMode 开发环境下的重复 effect。
- refresh 自己用裸 `fetch`,不能走 `authFetch`,否则会递归。

### 6.3 页面接入

| 位置 | 改动 |
|---|---|
| `pages/LoginPage.tsx` | 登录/注册成功后保存 `accessToken` |
| `auth/RequireAuth.tsx` | 无 token 时先调 `ensureRefreshed`,加载中显示 spinner,失败跳登录 |
| `layouts/AppLayout.tsx` | 退出登录先调后端 `/logout`,再清本地 session |
| `api/chat.ts` | 模型列表与 SSE 对话改用 `authFetch` |

### 6.4 请求时序

```
业务请求 ──401──→ ensureRefreshed ──成功──→ 重试一次 ──→ 正常返回
                        │
                        └──失败──→ 清 session ──→ /login
```

---

## 7. 安全设计

| 风险 | 措施 |
|---|---|
| 密钥泄露 | 生产通过 `JWT_SECRET` 注入,长度 ≥ 32 字节,代码只保留开发默认值 |
| XSS 窃取令牌 | access 只在内存;refresh 为 HttpOnly Cookie |
| CSRF | Cookie `SameSite=Lax`;跨站部署时用 `SameSite=None; Secure` 并配合 CORS 凭证 |
| refresh 重放 | 消费即删除(GETDEL),每次刷新轮换 |
| access 被盗 | 有效期短;登出只保证 refresh 失效,access 最长再活 30 分钟 |
| 账号枚举 | 登录失败统一提示“账号或密码错误” |
| 管理接口裸奔 | 继续要求 `X-Admin-Token`,未配置时管理接口停用 |
| 日志泄露 | 不打印 token、Cookie、密码等敏感值 |

---

## 8. 测试与验收

### 8.1 后端接口

```powershell
# 健康检查
curl.exe -i http://localhost:8081/api/health

# 未登录访问受保护接口 -> 401
curl.exe -i http://localhost:8081/api/auth/me

# 注册/登录,记录响应里的 accessToken 与 Set-Cookie
curl.exe -i -X POST http://localhost:8081/api/auth/register `
  -H "Content-Type: application/json" `
  -d '{\"account\":\"you@example.com\",\"password\":\"abc12345\"}'

# 带 token 访问
curl.exe -i http://localhost:8081/api/auth/me -H "Authorization: Bearer <accessToken>"

# 刷新(Cookie 使用登录响应中的 refresh_token)
curl.exe -i -X POST http://localhost:8081/api/auth/refresh `
  -H "Cookie: refresh_token=<cookie值>"

# 同一个 refresh 再用一次 -> 401(验证轮换)
curl.exe -i -X POST http://localhost:8081/api/auth/refresh `
  -H "Cookie: refresh_token=<旧cookie值>"

# 登出 -> 清 Cookie;再用旧 refresh -> 401
curl.exe -i -X POST http://localhost:8081/api/auth/logout `
  -H "Cookie: refresh_token=<cookie值>"
```

已验证结果:

| 场景 | 结果 |
|---|---|
| 健康检查 | 200 |
| 注册 | 200,返回 accessToken + Set-Cookie |
| 带 access 访问 `/me` | 200,返回当前用户 |
| 不带 access 访问 `/me` | 401 |
| refresh | 200,Cookie 已轮换 |
| 复用旧 refresh | 401 |
| logout | 200 |
| 登出后再 refresh | 401 |

### 8.2 浏览器验收

1. 登录后刷新页面,仍保持登录态。
2. access 过期后继续操作,自动刷新并重试成功(可临时把 TTL 改为 1 分钟验证)。
3. 退出登录后刷新页面,回到登录页。
4. 未登录直接访问 `/resume`,跳转 `/login`。
5. 登录后 AI 助手模型列表与对话正常。

---

## 9. 配置项

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:dev-only-secret-change-me-please-make-it-long-enough-32bytes}
    access-ttl-minutes: 30
    refresh-ttl-days: 7
    cookie-secure: ${JWT_COOKIE_SECURE:false}
    cookie-same-site: ${JWT_COOKIE_SAME_SITE:Lax}
```

| 环境 | 建议 |
|---|---|
| 本地开发 | `cookie-secure=false`,`cookie-same-site=Lax`,前端走 Vite 代理 |
| 同域生产(https) | `cookie-secure=true`,建议 `Lax` |
| 前后端不同站 | `cookie-secure=true`,`cookie-same-site=None`,并配置 CORS 允许具体来源 + 凭证 |

---

## 10. 后续扩展

| 能力 | 做法 |
|---|---|
| 登录限流 | Redis 令牌桶,按账号 + IP 限次(PRD F-15) |
| 即时吊销 access | 给 JWT 加 `jti`,Redis 黑名单,登出时写入直至过期 |
| refresh 重放检测 | Redis 记录已用 token 的短 TTL 指纹,检测到复用则吊销整条会话链 |
| 多设备管理 | 以 userId 维护 refresh 集合,支持列出/踢出设备 |
| 跨域部署 | 增加 `CorsConfigurationSource`,允许来源从配置读取并开启 `allowCredentials` |

---

*本文档与代码同步维护。核心原则:access 短期无状态、refresh 长期可吊销,前端内存 + HttpOnly Cookie 分工,不把长期凭证暴露给 JS。*
