import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { exchangeGithubCode } from '../api/auth'
import { setSession } from '../auth/authStore'
import { useToast } from '../components/toast/ToastProvider'

/**
 * GitHub 登录回调页。
 * 后端在 /api/auth/github/callback 处理完后带一次性 code 跳到本页,
 * 这里用 code 换 access token 并写入登录态。
 */
function AuthCallbackPage() {
  const navigate = useNavigate()
  const toast = useToast()
  // StrictMode 下 effect 会执行两次,一次性 code 只能用一次,这里做防重
  const started = useRef(false)

  useEffect(() => {
    if (started.current) return
    started.current = true

    const params = new URLSearchParams(window.location.search)
    const code = params.get('code')
    const error = params.get('error')

    if (error || !code) {
      toast.error(error || 'GitHub 登录已取消')
      navigate('/login', { replace: true })
      return
    }

    exchangeGithubCode(code)
      .then((result) => {
        setSession({
          userId: result.userId,
          account: result.nickname,
          accessToken: result.accessToken,
        })
        toast.success('登录成功')
        navigate('/', { replace: true })
      })
      .catch((err: unknown) => {
        toast.error(err instanceof Error ? err.message : '登录失败，请稍后重试')
        navigate('/login', { replace: true })
      })
  }, [navigate, toast])

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50">
      <div className="flex flex-col items-center gap-3 text-slate-500">
        <span className="h-7 w-7 animate-spin rounded-full border-2 border-teal-500 border-t-transparent" />
        <p className="text-sm">正在完成 GitHub 登录…</p>
      </div>
    </div>
  )
}

export default AuthCallbackPage
