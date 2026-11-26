import { useEffect, useState, type ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { ensureRefreshed } from '../api/http'
import { isLoggedIn } from './authStore'

type State = 'loading' | 'authed' | 'guest'

// 把需要登录才能看的页面包在 <RequireAuth> 里。
// 刷新页面后内存 token 丢失,先尝试用 refresh Cookie 恢复,再决定放行还是跳登录。
function RequireAuth({ children }: { children: ReactNode }) {
  const [state, setState] = useState<State>(isLoggedIn() ? 'authed' : 'loading')

  useEffect(() => {
    if (state !== 'loading') return
    let alive = true
    ensureRefreshed().then((ok) => {
      if (alive) setState(ok ? 'authed' : 'guest')
    })
    return () => {
      alive = false
    }
  }, [state])

  if (state === 'loading') {
    return (
      <div className="flex h-screen items-center justify-center bg-slate-50">
        <span className="h-6 w-6 animate-spin rounded-full border-2 border-brand-500 border-t-transparent" />
      </div>
    )
  }
  if (state === 'guest') {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
}

export default RequireAuth
