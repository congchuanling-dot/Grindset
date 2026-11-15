import { Eye, EyeOff } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { login, register } from '../api/auth'
import { setSession } from '../auth/authStore'

// 与后端 AuthService 保持一致的规则:邮箱格式 + 8~16 位字母数字密码
const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/

function validateInput(email: string, password: string): string {
  if (!email || !password) return '邮箱和密码都要填'
  if (!EMAIL_PATTERN.test(email)) return '请输入正确的邮箱地址'
  if (password.length < 8 || password.length > 16) return '密码长度需为 8-16 位'
  if (!/[A-Za-z]/.test(password) || !/[0-9]/.test(password)) return '密码必须同时包含字母和数字'
  return ''
}

function LoginPage() {
  const navigate = useNavigate()

  // ── 表单状态:每个都用 useState 管理 ──
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [account, setAccount] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const isLogin = mode === 'login'

  // 提交:走真实接口,后端返回 userId 后写入登录态并进首页
  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault() // 阻止表单默认的刷新页面行为
    setError('')

    const trimmedAccount = account.trim()
    const message = validateInput(trimmedAccount, password)
    if (message) {
      setError(message)
      return
    }

    setLoading(true)
    try {
      // 注册成功即建号,直接当已登录处理
      const result = isLogin
        ? await login(trimmedAccount, password)
        : await register(trimmedAccount, password)
      setSession({ userId: result.userId, account: trimmedAccount })
      navigate('/')
    } catch (err) {
      // 后端已把错误文案统一成中文(400/401/409 等),直接展示
      setError(err instanceof Error ? err.message : '请求失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm rounded-2xl bg-white p-8 shadow-lg">
        {/* 标题 */}
        <div className="mb-6 text-center">
          <h1 className="text-2xl font-bold text-slate-800">面试复习闭环</h1>
          <p className="mt-1 text-sm text-slate-500">越练越准的个性化面试复习</p>
        </div>

        {/* 登录 / 注册 切换标签 */}
        <div className="mb-6 flex rounded-lg bg-slate-100 p-1">
          <button
            type="button"
            onClick={() => {
              setMode('login')
              setError('')
            }}
            className={`flex-1 rounded-md py-1.5 text-sm font-medium transition ${
              isLogin ? 'bg-white text-teal-600 shadow' : 'text-slate-500'
            }`}
          >
            登录
          </button>
          <button
            type="button"
            onClick={() => {
              setMode('register')
              setError('')
            }}
            className={`flex-1 rounded-md py-1.5 text-sm font-medium transition ${
              !isLogin ? 'bg-white text-teal-600 shadow' : 'text-slate-500'
            }`}
          >
            注册
          </button>
        </div>

        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
          {/* 邮箱输入框(受控):账号统一用邮箱,以后接邮箱验证码 */}
          <div>
            <label htmlFor="email" className="mb-1 block text-sm font-medium text-slate-700">
              邮箱
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              value={account}
              onChange={(e) => setAccount(e.target.value)}
              placeholder="you@example.com"
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-800 outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
            />
          </div>

          {/* 密码输入框(受控)+ 眼睛显隐 */}
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">
              密码
            </label>
            <div className="relative">
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="8-16 位，含字母和数字"
                className="w-full rounded-lg border border-slate-300 px-3 py-2 pr-10 text-slate-800 outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
              />
              <button
                type="button"
                onClick={() => setShowPassword((v) => !v)}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
              >
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
          </div>

          {/* 错误提示:只有 error 有内容时才显示 */}
          {error && <p className="text-sm text-red-500">{error}</p>}

          {/* 主按钮:提交时转 loading 并禁用 */}
          <button
            type="submit"
            disabled={loading}
            className="mt-2 rounded-lg bg-teal-500 py-2 font-medium text-white transition hover:bg-teal-600 disabled:opacity-60"
          >
            {loading ? '处理中…' : isLogin ? '登录' : '注册'}
          </button>
        </form>

        {/* 分隔线 */}
        <div className="my-5 flex items-center gap-3 text-xs text-slate-400">
          <span className="h-px flex-1 bg-slate-200" />
          或
          <span className="h-px flex-1 bg-slate-200" />
        </div>

        {/* 第三方登录(MVP 占位,暂时禁用) */}
        <div className="flex flex-col gap-2">
          <button
            type="button"
            disabled
            title="即将支持"
            className="flex items-center justify-center gap-2 rounded-lg border border-slate-300 py-2 text-sm font-medium text-slate-600 disabled:opacity-50"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 .5C5.73.5.5 5.73.5 12c0 5.08 3.29 9.39 7.86 10.91.58.1.79-.25.79-.56v-2c-3.2.7-3.88-1.54-3.88-1.54-.53-1.34-1.29-1.7-1.29-1.7-1.05-.72.08-.7.08-.7 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.7 1.26 3.36.96.1-.75.4-1.26.73-1.55-2.55-.29-5.24-1.28-5.24-5.69 0-1.26.45-2.29 1.19-3.1-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.18 1.18a11 11 0 0 1 5.8 0c2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.12 3.05.74.81 1.18 1.84 1.18 3.1 0 4.42-2.69 5.39-5.25 5.68.41.36.78 1.06.78 2.14v3.17c0 .31.21.67.8.56A11.51 11.51 0 0 0 23.5 12C23.5 5.73 18.27.5 12 .5Z" />
            </svg>
            使用 GitHub 登录
          </button>
          <button
            type="button"
            disabled
            title="即将支持"
            className="flex items-center justify-center gap-2 rounded-lg border border-slate-300 py-2 text-sm font-medium text-slate-600 disabled:opacity-50"
          >
            <svg width="18" height="18" viewBox="0 0 24 24">
              <path fill="#4285F4" d="M23.5 12.3c0-.8-.1-1.6-.2-2.3H12v4.5h6.5a5.6 5.6 0 0 1-2.4 3.6v3h3.9c2.3-2.1 3.5-5.2 3.5-8.8Z" />
              <path fill="#34A853" d="M12 24c3.2 0 6-1.1 8-3l-3.9-3a7.2 7.2 0 0 1-10.7-3.8h-4v3.1A12 12 0 0 0 12 24Z" />
              <path fill="#FBBC05" d="M5.3 14.2a7.2 7.2 0 0 1 0-4.6V6.5h-4a12 12 0 0 0 0 11l4-3.3Z" />
              <path fill="#EA4335" d="M12 4.8c1.8 0 3.3.6 4.6 1.8l3.4-3.4A12 12 0 0 0 1.3 6.5l4 3.1A7.2 7.2 0 0 1 12 4.8Z" />
            </svg>
            使用 Google 登录
          </button>
        </div>
      </div>
    </div>
  )
}

export default LoginPage
