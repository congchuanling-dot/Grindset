import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { isLoggedIn } from './authStore'

// 把需要登录才能看的页面包在 <RequireAuth> 里。
// 没登录就重定向到 /login,登录了就正常渲染 children。
function RequireAuth({ children }: { children: ReactNode }) {
  if (!isLoggedIn()) {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
}

export default RequireAuth
