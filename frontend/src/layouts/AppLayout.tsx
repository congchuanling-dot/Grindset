import {
  FileUp,
  History,
  Library,
  LogOut,
  MessageSquare,
  Mic,
} from 'lucide-react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { logout } from '../api/auth'
import { getSession, setSession } from '../auth/authStore'

// 侧边栏栏目。除 AI 助手外都是占位,后续逐个接功能
const navItems = [
  { to: '/', label: 'AI 助手', icon: MessageSquare, end: true },
  { to: '/resume', label: '上传简历', icon: FileUp },
  { to: '/questions', label: '题库', icon: Library },
  { to: '/interview', label: 'AI 面试', icon: Mic },
  { to: '/records', label: '面试记录', icon: History },
]

// 登录后所有页面共用的外壳:左侧边栏 + 右侧内容区。
// 窄屏时侧边栏收成图标条,>=md 展开显示文字。
function AppLayout() {
  const navigate = useNavigate()
  const session = getSession()

  async function handleLogout() {
    try {
      // 后端删 Redis 里的 refresh token 并清 Cookie
      await logout()
    } catch {
      // 后端不可用也要保证本地退出
    } finally {
      setSession(null)
      navigate('/login', { replace: true })
    }
  }

  return (
    <div className="flex h-screen overflow-hidden bg-white">
      <aside className="flex w-16 shrink-0 flex-col border-r border-slate-200 bg-slate-50 md:w-60">
        <div className="flex h-14 items-center justify-center gap-2 md:justify-start md:px-4">
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-teal-600 text-sm font-bold text-white">
            G
          </span>
          <span className="hidden text-sm font-semibold text-slate-800 md:inline">
            面试复习闭环
          </span>
        </div>

        <nav className="flex-1 space-y-1 px-2 py-2">
          {navItems.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              title={label}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition ${
                  isActive
                    ? 'bg-teal-50 font-medium text-teal-700'
                    : 'text-slate-600 hover:bg-slate-200/60'
                }`
              }
            >
              <Icon size={18} className="shrink-0" />
              <span className="hidden md:inline">{label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-slate-200 p-2 md:p-3">
          <div className="hidden truncate px-2 pb-2 text-xs text-slate-400 md:block">
            {session?.account}
          </div>
          <button
            type="button"
            onClick={handleLogout}
            title="退出登录"
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm text-slate-500 transition hover:bg-slate-200/60 hover:text-slate-700"
          >
            <LogOut size={18} className="shrink-0" />
            <span className="hidden md:inline">退出登录</span>
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <Outlet />
      </div>
    </div>
  )
}

export default AppLayout
