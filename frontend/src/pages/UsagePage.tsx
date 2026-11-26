import {
  Activity,
  AlertCircle,
  ArrowDownToLine,
  ArrowUpFromLine,
  ChevronLeft,
  ChevronRight,
  Coins,
  Gauge,
  Loader2,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import {
  getUsageSummary,
  listUsageRecords,
  type UsageRecordPage,
  type UsageSummary,
} from '../api/usage'
import { errMessage } from '../utils/error'

/** 时间范围选项,与后端 days 参数对应 */
const RANGES = [
  { days: 1, label: '今日' },
  { days: 7, label: '近 7 天' },
  { days: 30, label: '近 30 天' },
]

const PAGE_SIZE = 20

function formatNumber(value: number): string {
  return value.toLocaleString('zh-CN')
}

function formatCost(value: number): string {
  if (!value) return '¥0'
  return `¥${value.toFixed(value >= 1 ? 2 : 4)}`
}

/**
 * Token 用量:汇总当前登录用户的模型调用 token 与成本。
 * 数据来自后端统一埋点,覆盖首页对话、AI 助手、面试、题库评分、简历解析。
 */
function UsagePage() {
  const [days, setDays] = useState(7)
  const [summary, setSummary] = useState<UsageSummary | null>(null)
  const [loadingSummary, setLoadingSummary] = useState(true)
  const [page, setPage] = useState(1)
  const [records, setRecords] = useState<UsageRecordPage | null>(null)
  const [loadingRecords, setLoadingRecords] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoadingSummary(true)
    getUsageSummary(days)
      .then((data) => {
        if (!cancelled) setSummary(data)
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(errMessage(e, '加载用量统计失败'))
      })
      .finally(() => {
        if (!cancelled) setLoadingSummary(false)
      })
    return () => {
      cancelled = true
    }
  }, [days])

  useEffect(() => {
    let cancelled = false
    setLoadingRecords(true)
    listUsageRecords(page, PAGE_SIZE)
      .then((data) => {
        if (!cancelled) setRecords(data)
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(errMessage(e, '加载用量明细失败'))
      })
      .finally(() => {
        if (!cancelled) setLoadingRecords(false)
      })
    return () => {
      cancelled = true
    }
  }, [page])

  const maxBizTokens = Math.max(
    1,
    ...(summary?.byBizType.map((item) => item.inputTokens + item.outputTokens) ?? [0]),
  )
  const totalPages = Math.max(1, Math.ceil((records?.total ?? 0) / PAGE_SIZE))

  return (
    <div className="flex h-full flex-col overflow-hidden bg-slate-50">
      <header className="shrink-0 border-b border-slate-200 bg-white px-4 py-4 md:px-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="flex items-center gap-2 text-base font-semibold text-slate-800">
              <Gauge size={18} className="text-teal-600" />
              Token 用量
            </h1>
            <p className="mt-1 text-xs text-slate-500">
              统计你的全部 AI 调用:首页对话、AI 助手、面试、题库评分与简历解析。
            </p>
          </div>
          <div className="flex rounded-lg border border-slate-200 bg-slate-100 p-0.5">
            {RANGES.map((item) => (
              <button
                key={item.days}
                type="button"
                onClick={() => setDays(item.days)}
                className={`rounded-md px-3 py-1 text-xs transition ${
                  days === item.days
                    ? 'bg-white font-medium text-teal-700 shadow-sm'
                    : 'text-slate-500 hover:text-slate-700'
                }`}
              >
                {item.label}
              </button>
            ))}
          </div>
        </div>
      </header>

      <div className="min-h-0 flex-1 space-y-4 overflow-y-auto p-4 md:p-6">
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

        {/* 汇总卡片 */}
        <section className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            icon={Activity}
            label="调用次数"
            value={summary ? formatNumber(summary.calls) : '—'}
            hint="按模型请求次数统计"
          />
          <StatCard
            icon={ArrowDownToLine}
            label="输入 tokens"
            value={summary ? formatNumber(summary.inputTokens) : '—'}
            hint="提问、上下文与提示词"
          />
          <StatCard
            icon={ArrowUpFromLine}
            label="输出 tokens"
            value={summary ? formatNumber(summary.outputTokens) : '—'}
            hint="模型生成的正文"
          />
          <StatCard
            icon={Coins}
            label="预估成本"
            value={summary ? formatCost(summary.cost) : '—'}
            hint={summary && summary.cost === 0 ? '未配置单价时按 0 计' : '按模型单价快照计算'}
          />
        </section>

        <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
          {/* 业务类型分布 */}
          <section className="rounded-xl border border-slate-200 bg-white p-4">
            <h2 className="text-sm font-semibold text-slate-800">业务类型分布</h2>
            {loadingSummary ? (
              <LoadingRow text="加载中…" />
            ) : !summary || summary.byBizType.length === 0 ? (
              <p className="mt-4 text-xs text-slate-400">这个时间段还没有 AI 调用记录。</p>
            ) : (
              <ul className="mt-3 space-y-3">
                {summary.byBizType.map((item) => {
                  const tokens = item.inputTokens + item.outputTokens
                  return (
                    <li key={item.bizType}>
                      <div className="flex items-center justify-between gap-2 text-xs">
                        <span className="font-medium text-slate-700">{item.label}</span>
                        <span className="text-slate-400">
                          {formatNumber(tokens)} tokens · {formatCost(item.cost)}
                        </span>
                      </div>
                      <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-slate-100">
                        <div
                          className="h-full rounded-full bg-teal-500"
                          style={{ width: `${Math.max(4, (tokens / maxBizTokens) * 100)}%` }}
                        />
                      </div>
                    </li>
                  )
                })}
              </ul>
            )}
          </section>

          {/* 模型用量 */}
          <section className="rounded-xl border border-slate-200 bg-white p-4">
            <h2 className="text-sm font-semibold text-slate-800">模型用量</h2>
            {loadingSummary ? (
              <LoadingRow text="加载中…" />
            ) : !summary || summary.byModel.length === 0 ? (
              <p className="mt-4 text-xs text-slate-400">这个时间段还没有 AI 调用记录。</p>
            ) : (
              <div className="mt-3 overflow-x-auto">
                <table className="min-w-full text-left text-xs">
                  <thead className="text-slate-400">
                    <tr>
                      <th className="py-1.5 pr-3 font-medium">模型</th>
                      <th className="py-1.5 pr-3 text-right font-medium">调用</th>
                      <th className="py-1.5 pr-3 text-right font-medium">输入</th>
                      <th className="py-1.5 pr-3 text-right font-medium">输出</th>
                      <th className="py-1.5 text-right font-medium">成本</th>
                    </tr>
                  </thead>
                  <tbody>
                    {summary.byModel.map((item) => (
                      <tr key={`${item.providerId}-${item.model}`} className="border-t border-slate-100">
                        <td className="py-1.5 pr-3">
                          <span className="font-medium text-slate-700">{item.model}</span>
                          <span className="ml-1 text-slate-400">{item.providerId}</span>
                        </td>
                        <td className="py-1.5 pr-3 text-right text-slate-500">
                          {formatNumber(item.calls)}
                        </td>
                        <td className="py-1.5 pr-3 text-right text-slate-500">
                          {formatNumber(item.inputTokens)}
                        </td>
                        <td className="py-1.5 pr-3 text-right text-slate-500">
                          {formatNumber(item.outputTokens)}
                        </td>
                        <td className="py-1.5 text-right text-slate-600">{formatCost(item.cost)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </div>

        {/* 调用明细 */}
        <section className="rounded-xl border border-slate-200 bg-white">
          <div className="flex items-center justify-between px-4 pt-4">
            <h2 className="text-sm font-semibold text-slate-800">调用明细</h2>
            <span className="text-xs text-slate-400">共 {formatNumber(records?.total ?? 0)} 条</span>
          </div>
          {loadingRecords ? (
            <LoadingRow text="加载中…" />
          ) : !records || records.items.length === 0 ? (
            <p className="px-4 py-10 text-center text-xs text-slate-400">
              还没有调用记录，去首页对话或开始一场 AI 面试试试。
            </p>
          ) : (
            <>
              <div className="mt-2 overflow-x-auto">
                <table className="min-w-full text-left text-xs">
                  <thead className="bg-slate-50 text-slate-400">
                    <tr>
                      <th className="px-4 py-2 font-medium">时间</th>
                      <th className="px-4 py-2 font-medium">业务</th>
                      <th className="px-4 py-2 font-medium">模型</th>
                      <th className="px-4 py-2 text-right font-medium">输入</th>
                      <th className="px-4 py-2 text-right font-medium">输出</th>
                      <th className="px-4 py-2 text-right font-medium">成本</th>
                    </tr>
                  </thead>
                  <tbody>
                    {records.items.map((item) => (
                      <tr key={item.id} className="border-t border-slate-100">
                        <td className="whitespace-nowrap px-4 py-2 text-slate-500">
                          {item.createdAt}
                        </td>
                        <td className="whitespace-nowrap px-4 py-2 text-slate-700">
                          {item.bizLabel}
                        </td>
                        <td className="whitespace-nowrap px-4 py-2 text-slate-500">
                          {item.model}
                          {item.estimated && (
                            <span className="ml-1 rounded bg-amber-50 px-1 py-0.5 text-[10px] text-amber-600">
                              估算
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-2 text-right text-slate-500">
                          {formatNumber(item.inputTokens)}
                        </td>
                        <td className="px-4 py-2 text-right text-slate-500">
                          {formatNumber(item.outputTokens)}
                        </td>
                        <td className="px-4 py-2 text-right text-slate-600">
                          {formatCost(item.cost)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="flex items-center justify-between border-t border-slate-100 px-4 py-3">
                <span className="text-xs text-slate-400">
                  第 {records.page} / {totalPages} 页
                </span>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    disabled={records.page <= 1}
                    onClick={() => setPage((prev) => Math.max(1, prev - 1))}
                    className="flex items-center gap-1 rounded-lg border border-slate-200 px-2 py-1 text-xs text-slate-600 transition enabled:hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    <ChevronLeft size={14} />
                    上一页
                  </button>
                  <button
                    type="button"
                    disabled={records.page >= totalPages}
                    onClick={() => setPage((prev) => prev + 1)}
                    className="flex items-center gap-1 rounded-lg border border-slate-200 px-2 py-1 text-xs text-slate-600 transition enabled:hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    下一页
                    <ChevronRight size={14} />
                  </button>
                </div>
              </div>
            </>
          )}
        </section>
      </div>
    </div>
  )
}

function StatCard({
  icon: Icon,
  label,
  value,
  hint,
}: {
  icon: typeof Activity
  label: string
  value: string
  hint: string
}) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex items-center gap-2 text-xs text-slate-500">
        <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-teal-50 text-teal-600">
          <Icon size={14} />
        </span>
        {label}
      </div>
      <div className="mt-2 text-xl font-semibold text-slate-800">{value}</div>
      <p className="mt-1 text-[11px] text-slate-400">{hint}</p>
    </div>
  )
}

function LoadingRow({ text }: { text: string }) {
  return (
    <div className="mt-3 flex items-center gap-2 text-xs text-slate-400">
      <Loader2 size={13} className="animate-spin" />
      {text}
    </div>
  )
}

export default UsagePage
