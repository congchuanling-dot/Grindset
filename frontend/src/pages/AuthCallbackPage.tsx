import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { exchangeGithubCode, exchangeGoogleCode } from '../api/auth'
import { setSession } from '../auth/authStore'
import { useToast } from '../components/toast/ToastProvider'
import { errMessage } from '../utils/error'

/**
 * 第三方登录回调页。
 * 后端处理完授权后带 provider 与一次性 code 跳到本页,
 * 这里按 provider 调对应的 exchange 接口换 access token 并写入登录态。
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
    const provider = params.get('provider') ?? 'github'
    const code = params.get('code')
    const error = params.get('error')

    if (error || !code) {
      toast.error(error || '登录已取消')
      navigate('/login', { replace: true })
      return
    }

    const exchange = provider === 'google' ? exchangeGoogleCode : exchangeGithubCode
    exchange(code)
      .then((result) => {
        setSession({
          userId: result.userId,
          // OAuth 登录后端只返回昵称,没有单独的邮箱字段
          account: result.nickname,
          nickname: result.nickname,
          accessToken: result.accessToken,
        })
        toast.success('登录成功')
        navigate('/', { replace: true })
      })
      .catch((err: unknown) => {
        toast.error(errMessage(err, '登录失败，请稍后重试'))
        navigate('/login', { replace: true })
      })
  }, [navigate, toast])

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50">
      <div className="flex flex-col items-center gap-3 text-slate-500">
        <span className="h-7 w-7 animate-spin rounded-full border-2 border-brand-500 border-t-transparent" />
        <p className="text-sm">正在完成登录…</p>
      </div>
    </div>
  )
}

export default AuthCallbackPage
