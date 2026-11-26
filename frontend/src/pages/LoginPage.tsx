import { Eye, EyeOff } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { githubAuthorize, googleAuthorize, login, register } from '../api/auth'
import { setSession } from '../auth/authStore'
import { useToast } from '../components/toast/ToastProvider'

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
  const toast = useToast()

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
      // 注册成功即建号,直接当已登录处理;后端同时下发 access token 和 refresh Cookie
      const result = isLogin
        ? await login(trimmedAccount, password)
        : await register(trimmedAccount, password)
      setSession({
        userId: result.userId,
        account: trimmedAccount,
        nickname: result.nickname,
        accessToken: result.accessToken,
      })
      toast.success(isLogin ? '登录成功' : '注册成功，已自动登录')
      navigate('/')
    } catch (err) {
      // 后端已把错误文案统一成中文(400/401/409 等),直接展示
      setError(err instanceof Error ? err.message : '请求失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="press-scope min-h-screen bg-slate-50">
      <div className="mx-auto flex min-h-screen w-full max-w-6xl flex-col lg:flex-row">
        {/* 左侧:说明与三步流程 */}
        <section className="flex flex-1 flex-col justify-between px-6 pb-10 pt-10 lg:border-r lg:border-slate-200 lg:px-12 lg:py-14">
          <div>
            <div className="flex items-center gap-2">
              <span aria-hidden className="h-2.5 w-2.5 bg-slate-900" />
              <span className="text-[13px] font-medium tracking-tight text-slate-900">
                面试复习闭环
              </span>
            </div>

            <h1 className="mt-16 max-w-xl text-[clamp(34px,4.6vw,56px)] font-semibold leading-[1.08] tracking-[-0.03em] text-slate-900">
              把每一次模拟面试，
              <br />
              变成可复用的答案。
            </h1>
            <p className="mt-6 max-w-md text-sm leading-7 text-slate-500">
              上传简历，AI 面试官按岗位逐题追问；结束后给出评分与复盘，再直接进入题库巩固薄弱点。
            </p>
          </div>

          {/* 产品本身就是三步顺序,编号在这里是信息而不是装饰 */}
          <ol className="mt-14 grid gap-6 border-t border-slate-200 pt-6 sm:grid-cols-3 sm:gap-0">
            {[
              { no: '01', title: '上传简历', desc: '解析成结构化经历，作为提问依据。' },
              { no: '02', title: '模拟面试', desc: '按岗位与轮次提问，最多三次追问。' },
              { no: '03', title: '复盘刷题', desc: '拿到评分与建议，回到题库补短板。' },
            ].map((step, index) => (
              <li
                key={step.no}
                className={index > 0 ? 'sm:border-l sm:border-slate-200 sm:pl-6' : 'sm:pr-6'}
              >
                <span className="inline-block bg-marker px-1.5 py-0.5 text-[11px] font-medium tabular-nums text-marker-ink">
                  {step.no}
                </span>
                <span className="mt-1 block text-sm font-medium text-slate-900">{step.title}</span>
                <span className="mt-1 block text-xs leading-5 text-slate-500">{step.desc}</span>
              </li>
            ))}
          </ol>
        </section>

        {/* 右侧:登录表单 */}
        <section className="flex w-full items-center px-6 pb-12 pt-2 lg:w-[420px] lg:px-10 lg:py-14">
          <div className="mx-auto w-full max-w-md lg:mx-0 lg:max-w-none">
            {/* 登录 / 注册 切换标签 */}
            <div className="flex gap-6 border-b border-slate-200">
          <button
            type="button"
            onClick={() => {
              setMode('login')
              setError('')
            }}
            className={`-mb-px border-b-2 pb-3 text-sm font-medium transition ${
              isLogin
                ? 'border-slate-900 text-slate-900'
                : 'border-transparent text-slate-500 hover:text-slate-900'
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
            className={`-mb-px border-b-2 pb-3 text-sm font-medium transition ${
              !isLogin
                ? 'border-slate-900 text-slate-900'
                : 'border-transparent text-slate-500 hover:text-slate-900'
            }`}
          >
            注册
          </button>
        </div>

        <form onSubmit={handleSubmit} noValidate className="mt-7 flex flex-col gap-5">
          {/* 邮箱输入框(受控):账号统一用邮箱,以后接邮箱验证码 */}
          <div>
            <label htmlFor="email" className="mb-1.5 block text-[13px] font-medium text-slate-600">
              邮箱
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              value={account}
              onChange={(e) => setAccount(e.target.value)}
              placeholder="you@example.com"
              className="w-full border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800 outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-marker/50"
            />
          </div>

          {/* 密码输入框(受控)+ 眼睛显隐 */}
          <div>
            <label className="mb-1.5 block text-[13px] font-medium text-slate-600">
              密码
            </label>
            <div className="relative">
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="8-16 位，含字母和数字"
                className="w-full border border-slate-300 bg-white px-3 py-2.5 pr-10 text-sm text-slate-800 outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-marker/50"
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
            className="mt-2 w-full bg-slate-900 py-3 text-sm font-medium text-white transition hover:bg-black disabled:cursor-not-allowed disabled:opacity-60"
          >
            {loading ? '处理中…' : isLogin ? '登录' : '注册'}
          </button>
        </form>

        {/* 分隔线 */}
        <div className="my-6 flex items-center gap-3 text-xs text-slate-400">
          <span className="h-px flex-1 bg-slate-200" />
          或
          <span className="h-px flex-1 bg-slate-200" />
        </div>

        {/* 第三方登录:GitHub / Google 均已接入 */}
        <div className="flex flex-col gap-2">
          <button
            type="button"
            onClick={githubAuthorize}
            className="flex items-center justify-center gap-2 border border-slate-300 bg-white py-2.5 text-sm font-medium text-slate-600 transition hover:border-slate-900 hover:text-slate-900"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 .5C5.73.5.5 5.73.5 12c0 5.08 3.29 9.39 7.86 10.91.58.1.79-.25.79-.56v-2c-3.2.7-3.88-1.54-3.88-1.54-.53-1.34-1.29-1.7-1.29-1.7-1.05-.72.08-.7.08-.7 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.7 1.26 3.36.96.1-.75.4-1.26.73-1.55-2.55-.29-5.24-1.28-5.24-5.69 0-1.26.45-2.29 1.19-3.1-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.18 1.18a11 11 0 0 1 5.8 0c2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.12 3.05.74.81 1.18 1.84 1.18 3.1 0 4.42-2.69 5.39-5.25 5.68.41.36.78 1.06.78 2.14v3.17c0 .31.21.67.8.56A11.51 11.51 0 0 0 23.5 12C23.5 5.73 18.27.5 12 .5Z" />
            </svg>
            使用 GitHub 登录
          </button>
          <button
            type="button"
            onClick={googleAuthorize}
            className="flex items-center justify-center gap-2 border border-slate-300 bg-white py-2.5 text-sm font-medium text-slate-600 transition hover:border-slate-900 hover:text-slate-900"
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
        </section>
      </div>
    </div>
  )
}

export default LoginPage
