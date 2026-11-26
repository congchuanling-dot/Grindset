import {
  AlertCircle,
  CheckCircle2,
  Loader2,
  LockKeyhole,
  LogOut,
  RefreshCw,
  Save,
  Tags,
  Wallet,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  adminLogin,
  adminLogout,
  AdminAuthError,
  getAdminToken,
  listChannelBalances,
  listModelPrices,
  refreshChannelBalance,
  saveAdminToken,
  updateModelPrices,
  type AdminChannelBalance,
  type AdminModelPrice,
} from '../api/adminPricing'
import { errMessage } from '../utils/error'

interface Draft {
  input: string
  cached: string
  output: string
}

function draftOf(model: AdminModelPrice): Draft {
  return {
    input: String(model.inputPrice),
    cached: String(model.cachedInputPrice),
    output: String(model.outputPrice),
  }
}

/**
 * 独立管理后台:模型价格维护。
 * 不套用户侧边栏、不依赖普通用户登录,只认后端配置的管理员账号。
 */
function AdminPricingPage() {
  const [token, setToken] = useState(getAdminToken())
  const [account, setAccount] = useState('')
  const [password, setPassword] = useState('')
  const [models, setModels] = useState<AdminModelPrice[]>([])
  const [drafts, setDrafts] = useState<Record<string, Draft>>({})
  const [channels, setChannels] = useState<AdminChannelBalance[]>([])
  const [loading, setLoading] = useState(false)
  const [loadingChannels, setLoadingChannels] = useState(false)
  const [refreshingId, setRefreshingId] = useState<number | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [savingId, setSavingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  /** 管理会话失效时清掉令牌,回到登录页 */
  function resetToLogin(message: string) {
    saveAdminToken('')
    setToken('')
    setModels([])
    setDrafts({})
    setChannels([])
    setError(message)
  }

  async function load() {
    setLoading(true)
    setLoadingChannels(true)
    setError(null)

    // 价格和余额互不依赖,一个失败不影响另一个展示
    const [modelResult, channelResult] = await Promise.allSettled([
      listModelPrices(),
      listChannelBalances(),
    ])

    if (modelResult.status === 'fulfilled') {
      const list = modelResult.value
      setModels(list)
      setDrafts(Object.fromEntries(list.map((item) => [item.id, draftOf(item)])))
    }
    if (channelResult.status === 'fulfilled') {
      setChannels(channelResult.value)
    }

    const rejected = [modelResult, channelResult].find(
      (result): result is PromiseRejectedResult => result.status === 'rejected',
    )
    if (rejected) {
      if (rejected.reason instanceof AdminAuthError) {
        resetToLogin(rejected.reason.message)
      } else {
        setError(errMessage(rejected.reason, '加载管理数据失败'))
      }
    }

    setLoading(false)
    setLoadingChannels(false)
  }

  useEffect(() => {
    if (token) {
      void load()
    }
    // 首次进入按已保存的会话加载
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    const name = account.trim()
    if (!name || !password) {
      setError('账号和密码都要填')
      return
    }
    setSubmitting(true)
    try {
      const session = await adminLogin(name, password)
      saveAdminToken(session)
      setToken(session)
      setPassword('')
      await load()
    } catch (e) {
      setError(errMessage(e, '登录失败'))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleLogout() {
    await adminLogout()
    resetToLogin('已退出管理后台')
    setNotice(null)
  }

  function handleChange(id: string, field: keyof Draft, value: string) {
    setNotice(null)
    setDrafts((prev) => ({ ...prev, [id]: { ...prev[id], [field]: value } }))
  }

  async function handleSave(model: AdminModelPrice) {
    const draft = drafts[model.id]
    if (!draft) return
    const input = Number(draft.input)
    const cached = Number(draft.cached)
    const output = Number(draft.output)
    if (
      !Number.isFinite(input) ||
      !Number.isFinite(cached) ||
      !Number.isFinite(output) ||
      input < 0 ||
      cached < 0 ||
      output < 0
    ) {
      setError('单价必须是非负数字')
      return
    }

    setSavingId(model.id)
    setError(null)
    try {
      await updateModelPrices(model.id, {
        inputPrice: input,
        cachedInputPrice: cached,
        outputPrice: output,
      })
      setModels((prev) =>
        prev.map((item) =>
          item.id === model.id
            ? { ...item, inputPrice: input, cachedInputPrice: cached, outputPrice: output }
            : item,
        ),
      )
      setNotice(`已保存 ${model.modelId} 的单价，后续调用立即生效`)
    } catch (e) {
      if (e instanceof AdminAuthError) {
        resetToLogin(e.message)
      } else {
        setError(errMessage(e, '保存单价失败'))
      }
    } finally {
      setSavingId(null)
    }
  }

  async function handleRefreshBalance(channel: AdminChannelBalance) {
    setRefreshingId(channel.id)
    setError(null)
    try {
      const data = await refreshChannelBalance(channel.id)
      setChannels((prev) =>
        prev.map((item) =>
          item.id === channel.id
            ? {
                ...item,
                balance: data.balance,
                currency: data.currency,
                source: 'MANUAL',
                balanceUpdatedAt: data.updatedAt,
              }
            : item,
        ),
      )
      setNotice(`${channel.name} 余额已刷新`)
    } catch (e) {
      if (e instanceof AdminAuthError) {
        resetToLogin(e.message)
      } else {
        setError(errMessage(e, '刷新余额失败'))
      }
    } finally {
      setRefreshingId(null)
    }
  }

  // ---------------- 未登录:独立登录页 ----------------
  if (!token) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
        <div className="w-full max-w-sm rounded-2xl bg-white p-8 shadow-lg">
          <div className="mb-6 text-center">
            <span className="mx-auto flex h-11 w-11 items-center justify-center rounded-xl bg-brand-600 text-white">
              <LockKeyhole size={20} />
            </span>
            <h1 className="mt-3 text-xl font-bold text-slate-800">管理后台</h1>
            <p className="mt-1 text-xs text-slate-500">仅限管理员账号登录</p>
          </div>

          {error && (
            <div className="mb-4 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
              <AlertCircle size={14} className="mt-0.5 shrink-0" />
              <span className="flex-1">{error}</span>
            </div>
          )}

          <form onSubmit={handleLogin} className="flex flex-col gap-3">
            <div>
              <label htmlFor="admin-account" className="mb-1 block text-xs font-medium text-slate-700">
                管理员邮箱
              </label>
              <input
                id="admin-account"
                type="email"
                autoComplete="username"
                value={account}
                onChange={(e) => setAccount(e.target.value)}
                placeholder="admin@example.com"
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-800 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
              />
            </div>
            <div>
              <label htmlFor="admin-password" className="mb-1 block text-xs font-medium text-slate-700">
                密码
              </label>
              <input
                id="admin-password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="请输入密码"
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-800 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
              />
            </div>
            <button
              type="submit"
              disabled={submitting}
              className="mt-1 flex items-center justify-center gap-2 rounded-lg bg-brand-600 py-2 text-sm font-medium text-white transition hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {submitting && <Loader2 size={14} className="animate-spin" />}
              登录
            </button>
          </form>

          <div className="mt-5 text-center text-xs">
            <Link to="/" className="text-slate-400 hover:text-slate-600">
              返回应用
            </Link>
          </div>
        </div>
      </div>
    )
  }

  // ---------------- 已登录:价格管理 ----------------
  return (
    <div className="min-h-screen bg-slate-100">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-4 md:px-8">
          <div>
            <h1 className="flex items-center gap-2 text-base font-semibold text-slate-800">
              <Tags size={18} className="text-brand-600" />
              模型价格管理
            </h1>
            <p className="mt-1 text-xs text-slate-500">
              单位：元 / 百万 token。保存后立即影响后续调用的成本计算，历史记录保留原单价快照。
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => void load()}
              className="flex items-center gap-1 rounded-lg border border-slate-200 px-3 py-1.5 text-xs text-slate-600 transition hover:bg-slate-50"
            >
              <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
              刷新
            </button>
            <button
              type="button"
              onClick={() => void handleLogout()}
              className="flex items-center gap-1 rounded-lg border border-slate-200 px-3 py-1.5 text-xs text-slate-600 transition hover:bg-slate-50"
            >
              <LogOut size={14} />
              退出
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl space-y-4 px-4 py-4 md:px-8 md:py-6">
        {error && (
          <div className="flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
            <AlertCircle size={16} className="mt-0.5 shrink-0" />
            <span className="flex-1">{error}</span>
            <button
              type="button"
              className="text-xs text-rose-500 hover:text-rose-700"
              onClick={() => setError(null)}
            >
              关闭
            </button>
          </div>
        )}
        {notice && (
          <div className="flex items-start gap-2 rounded-lg border border-brand-200 bg-brand-50 px-3 py-2 text-sm text-brand-700">
            <CheckCircle2 size={16} className="mt-0.5 shrink-0" />
            <span className="flex-1">{notice}</span>
            <button
              type="button"
              className="text-xs text-brand-600 hover:text-brand-800"
              onClick={() => setNotice(null)}
            >
              关闭
            </button>
          </div>
        )}

        {/* 各渠道余额:DeepSeek 由后端定时自动刷新 */}
        <section className="rounded-xl border border-slate-200 bg-white p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-800">
              <Wallet size={15} className="text-brand-600" />
              账户余额
            </h2>
            <span className="text-[11px] text-slate-400">
              DeepSeek 每 5 分钟自动查询，其余厂商手工维护
            </span>
          </div>

          {loadingChannels ? (
            <div className="mt-3 flex items-center gap-2 text-xs text-slate-400">
              <Loader2 size={13} className="animate-spin" />
              加载余额…
            </div>
          ) : channels.length === 0 ? (
            <p className="mt-3 text-xs text-slate-400">还没有配置渠道。</p>
          ) : (
            <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {channels.map((channel) => (
                <div
                  key={channel.id}
                  className="rounded-lg border border-slate-100 bg-slate-50/60 p-3"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-slate-600">{channel.name}</span>
                    {!channel.enabled && (
                      <span className="rounded bg-slate-200 px-1 py-0.5 text-[10px] text-slate-500">
                        已停用
                      </span>
                    )}
                  </div>
                  <div className="mt-1 text-xl font-semibold text-slate-800">
                    {formatBalance(channel)}
                  </div>
                  <div className="mt-1 flex items-center justify-between gap-2 text-[11px] text-slate-400">
                    <span>
                      {channel.balanceUpdatedAt ? `${channel.balanceUpdatedAt} 更新` : '暂无更新记录'}
                    </span>
                    {channel.providerId === 'deepseek' && (
                      <button
                        type="button"
                        disabled={refreshingId === channel.id}
                        onClick={() => void handleRefreshBalance(channel)}
                        className="flex items-center gap-1 text-brand-600 transition enabled:hover:text-brand-700 disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        <RefreshCw
                          size={11}
                          className={refreshingId === channel.id ? 'animate-spin' : ''}
                        />
                        刷新
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="rounded-xl border border-slate-200 bg-white">
          <div className="flex items-center justify-between px-4 pt-4">
            <h2 className="text-sm font-semibold text-slate-800">模型单价</h2>
            <span className="text-xs text-slate-400">共 {models.length} 个模型</span>
          </div>

          {loading ? (
            <div className="flex items-center gap-2 px-4 py-10 text-xs text-slate-400">
              <Loader2 size={14} className="animate-spin" />
              加载中…
            </div>
          ) : models.length === 0 ? (
            <p className="px-4 py-10 text-center text-xs text-slate-400">没有可维护的模型。</p>
          ) : (
            <div className="mt-2 overflow-x-auto">
              <table className="min-w-full text-left text-xs">
                <thead className="bg-slate-50 text-slate-400">
                  <tr>
                    <th className="px-4 py-2 font-medium">厂商</th>
                    <th className="px-4 py-2 font-medium">模型</th>
                    <th className="px-4 py-2 font-medium">输入单价</th>
                    <th className="px-4 py-2 font-medium">缓存命中单价</th>
                    <th className="px-4 py-2 font-medium">输出单价</th>
                    <th className="px-4 py-2 text-right font-medium">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {models.map((model) => {
                    const draft = drafts[model.id] ?? draftOf(model)
                    return (
                      <tr key={model.id} className="border-t border-slate-100">
                        <td className="whitespace-nowrap px-4 py-2 text-slate-600">
                          {model.channelName}
                          <span className="ml-1 text-slate-400">{model.providerId}</span>
                        </td>
                        <td className="whitespace-nowrap px-4 py-2">
                          <span className="font-medium text-slate-700">{model.displayName}</span>
                          <span className="ml-1 text-slate-400">{model.modelId}</span>
                          {!model.enabled && (
                            <span className="ml-1 rounded bg-slate-100 px-1 py-0.5 text-[10px] text-slate-500">
                              已下架
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-2">
                          <PriceInput
                            value={draft.input}
                            onChange={(value) => handleChange(model.id, 'input', value)}
                          />
                        </td>
                        <td className="px-4 py-2">
                          <PriceInput
                            value={draft.cached}
                            onChange={(value) => handleChange(model.id, 'cached', value)}
                          />
                        </td>
                        <td className="px-4 py-2">
                          <PriceInput
                            value={draft.output}
                            onChange={(value) => handleChange(model.id, 'output', value)}
                          />
                        </td>
                        <td className="whitespace-nowrap px-4 py-2 text-right">
                          <button
                            type="button"
                            disabled={savingId === model.id}
                            onClick={() => void handleSave(model)}
                            className="inline-flex items-center gap-1 rounded-lg border border-brand-200 bg-brand-50 px-2 py-1 text-xs text-brand-700 transition enabled:hover:bg-brand-100 disabled:cursor-not-allowed disabled:opacity-50"
                          >
                            {savingId === model.id ? (
                              <Loader2 size={12} className="animate-spin" />
                            ) : (
                              <Save size={12} />
                            )}
                            保存
                          </button>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}

          <p className="border-t border-slate-100 px-4 py-3 text-[11px] leading-5 text-slate-400">
            缓存命中单价填 0 时按输入原价计；免费模型（如 glm-4-flash）三项都填 0 即可。
          </p>
        </section>
      </main>
    </div>
  )
}

function formatBalance(channel: AdminChannelBalance): string {
  if (channel.balance == null) {
    return '—'
  }
  const symbol = channel.currency === 'USD' ? '$' : '¥'
  return `${symbol}${channel.balance.toFixed(2)}`
}

function PriceInput({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return (
    <input
      type="number"
      min="0"
      step="0.0001"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="w-24 rounded-lg border border-slate-200 px-2 py-1 text-right text-xs text-slate-700 outline-none focus:border-brand-400"
    />
  )
}

export default AdminPricingPage
