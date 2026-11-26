import {
  FileUp,
  Gauge,
  History,
  Library,
  LogOut,
  MessageSquare,
  Mic,
  Wallet,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { logout } from '../api/auth'
import { getWallet, type WalletInfo } from '../api/wallet'
import { getSession, setSession, type Session } from '../auth/authStore'
import ProfileDialog from '../components/profile/ProfileDialog'

// 侧边栏栏目
const navItems = [
  { to: '/', label: 'AI 助手', icon: MessageSquare, end: true },
  { to: '/resume', label: '上传简历', icon: FileUp },
  { to: '/questions', label: '题库', icon: Library },
  { to: '/interview', label: 'AI 面试', icon: Mic },
  { to: '/records', label: '面试记录', icon: History },
  { to: '/wallet', label: '我的钱包', icon: Wallet },
]

/** 余额展示保留 4 位小数;没加载到就显示 -- */
function formatBalance(value: number | undefined): string {
  return value === undefined ? '--' : `¥${value.toFixed(4)}`
}

// 登录后所有页面共用的外壳:左侧边栏 + 右侧内容区。
// 窄屏时侧边栏收成图标条,>=md 展开显示文字。
function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  // 登录态放本地 state,改昵称后能立即重渲染;同时同步 authStore
  const [session, setLocalSession] = useState<Session | null>(getSession())
  const [wallet, setWallet] = useState<WalletInfo | null>(null)
  const [profileOpen, setProfileOpen] = useState(false)

  // 路由切换时刷新余额:AI 调用花掉的钱,切个页面就能看到
  useEffect(() => {
    let cancelled = false
    getWallet()
      .then((data) => {
        if (!cancelled) setWallet(data)
      })
      .catch(() => {
        // 余额不是关键路径,失败就保持 --,不打扰用户
      })
    return () => {
      cancelled = true
    }
  }, [location.pathname])

  async function handleLogout() {
    try {
      // 后端删 Redis 里的 refresh token 并清 Cookie
      await logout()
    } catch {
      // 后端不可用也要保证本地退出
    } finally {
      setSession(null)
      setLocalSession(null)
      navigate('/login', { replace: true })
    }
  }

  function handleProfileSaved(nickname: string) {
    const current = getSession()
    if (!current) {
      return
    }
    const next = { ...current, nickname }
    setSession(next)
    setLocalSession(next)
  }

  const displayName = session?.nickname || session?.account || '未登录'
  // OAuth 登录没有单独邮箱,account 与 nickname 相同时不重复展示
  const showEmail = Boolean(session?.account && session.account !== session.nickname)

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

        {/* 侧边栏底部:用量统计 + 账号信息 + 退出 */}
        <div className="border-t border-slate-200 px-2 pt-2">
          <NavLink
            to="/usage"
            title="Token 用量"
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition ${
                isActive
                  ? 'bg-teal-50 font-medium text-teal-700'
                  : 'text-slate-600 hover:bg-slate-200/60'
              }`
            }
          >
            <Gauge size={18} className="shrink-0" />
            <span className="hidden md:inline">Token 用量</span>
          </NavLink>
        </div>

        <div className="border-t border-slate-200 p-2 md:p-3">
          {/* 点击用户块打开改昵称弹窗 */}
          <button
            type="button"
            onClick={() => setProfileOpen(true)}
            title="编辑昵称"
            className="flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left transition hover:bg-slate-200/60"
          >
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-teal-600 text-sm font-semibold text-white">
              {displayName.slice(0, 1).toUpperCase()}
            </span>
            <span className="hidden min-w-0 flex-1 md:block">
              <span className="block truncate text-sm font-medium text-slate-700">
                {displayName}
              </span>
              {showEmail && (
                <span className="block truncate text-xs text-slate-400">{session?.account}</span>
              )}
            </span>
          </button>

          <div className="mt-1 flex items-center justify-between rounded-lg bg-white px-3 py-2 text-xs text-slate-500">
            <span className="hidden md:inline">余额</span>
            <span className="font-medium text-slate-700">{formatBalance(wallet?.balance)}</span>
          </div>

          <button
            type="button"
            onClick={handleLogout}
            title="退出登录"
            className="mt-1 flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm text-slate-500 transition hover:bg-slate-200/60 hover:text-slate-700"
          >
            <LogOut size={18} className="shrink-0" />
            <span className="hidden md:inline">退出登录</span>
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <Outlet />
      </div>

      {profileOpen && (
        <ProfileDialog
          initialNickname={session?.nickname ?? ''}
          onClose={() => setProfileOpen(false)}
          onSaved={handleProfileSaved}
        />
      )}
    </div>
  )
}

export default AppLayout
