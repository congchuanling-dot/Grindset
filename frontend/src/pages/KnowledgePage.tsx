import {
  AlertCircle,
  BrainCircuit,
  Loader2,
  Minus,
  RefreshCw,
  TrendingDown,
  TrendingUp,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  getKnowledgeProfile,
  type KnowledgeLevel,
  type KnowledgeProfile,
  type KnowledgeState,
  type KnowledgeTrend,
} from '../api/knowledge'

const LEVEL_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'WEAK', label: '薄弱' },
  { value: 'LEARNING', label: '待巩固' },
  { value: 'MASTERED', label: '已掌握' },
]

const DOMAINS = ['Redis', 'MySQL', 'JVM', 'Java', '网络', '操作系统', '算法', 'AI', '项目', '其他']

const LEVEL_LABELS: Record<KnowledgeLevel, string> = {
  WEAK: '薄弱',
  LEARNING: '待巩固',
  MASTERED: '已掌握',
}

/**
 * 掌握度画像:按知识点汇总每场面试的评分。
 * 默认按掌握度从低到高展示,支持按等级、领域筛选。
 */
function KnowledgePage() {
  const [profile, setProfile] = useState<KnowledgeProfile | null>(null)
  const [level, setLevel] = useState('')
  const [domain, setDomain] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    getKnowledgeProfile({ level: level || undefined, domain: domain || undefined, limit: 200 })
      .then((data) => {
        if (!cancelled) setProfile(data)
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(errMessage(e))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [level, domain])

  const summary = profile?.summary
  const items = profile?.items ?? []

  return (
    <div className="flex h-full flex-col overflow-y-auto bg-slate-50">
      <header className="border-b border-slate-200 bg-white px-4 py-4 md:px-6">
        <h1 className="flex items-center gap-2 text-base font-semibold text-slate-800">
          <BrainCircuit size={18} className="text-teal-600" />
          掌握度画像
        </h1>
        <p className="mt-1 text-xs text-slate-500">
          每场面试的逐题评分会自动沉淀为知识点掌握度，下一场优先考察薄弱点。
        </p>
      </header>

      <div className="flex-1 px-4 py-4 md:px-6">
        {error && (
          <div className="mb-4 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
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

        {loading && !profile ? (
          <div className="flex h-64 items-center justify-center">
            <Loader2 size={22} className="animate-spin text-teal-600" />
          </div>
        ) : (
          <>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <StatCard label="平均掌握度" value={`${summary?.averageMastery ?? 0}`} tone="teal" />
              <StatCard label="薄弱" value={`${summary?.weakCount ?? 0}`} tone="rose" />
              <StatCard label="待巩固" value={`${summary?.learningCount ?? 0}`} tone="amber" />
              <StatCard label="已掌握" value={`${summary?.masteredCount ?? 0}`} tone="slate" />
            </div>

            <div className="mt-4 flex flex-wrap items-center gap-2">
              <div className="flex flex-wrap gap-1.5">
                {LEVEL_OPTIONS.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    onClick={() => setLevel(option.value)}
                    className={`rounded-full border px-2.5 py-1 text-xs transition ${
                      level === option.value
                        ? 'border-teal-300 bg-teal-50 font-medium text-teal-700'
                        : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300'
                    }`}
                  >
                    {option.label}
                  </button>
                ))}
              </div>

              <select
                value={domain}
                onChange={(e) => setDomain(e.target.value)}
                className="ml-auto rounded-lg border border-slate-200 bg-white px-2 py-1 text-xs text-slate-600 outline-none focus:border-teal-400"
              >
                <option value="">全部领域</option>
                {DOMAINS.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>

              <button
                type="button"
                onClick={() => {
                  setLevel('')
                  setDomain('')
                }}
                className="flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-2 py-1 text-xs text-slate-500 transition hover:border-slate-300"
              >
                <RefreshCw size={12} />
                重置
              </button>
            </div>

            {loading ? (
              <div className="mt-8 flex items-center justify-center gap-2 text-xs text-slate-400">
                <Loader2 size={14} className="animate-spin" />
                加载中…
              </div>
            ) : items.length === 0 ? (
              <EmptyState hasFilter={Boolean(level || domain)} />
            ) : (
              <div className="mt-3 space-y-2">
                {items.map((item) => (
                  <KnowledgeRow key={item.knowledgePointId} item={item} />
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}

function StatCard({ label, value, tone }: { label: string; value: string; tone: 'teal' | 'rose' | 'amber' | 'slate' }) {
  const toneClass = {
    teal: 'text-teal-700',
    rose: 'text-rose-600',
    amber: 'text-amber-600',
    slate: 'text-slate-700',
  }[tone]

  return (
    <div className="rounded-xl border border-slate-200 bg-white px-4 py-3">
      <p className="text-xs text-slate-400">{label}</p>
      <p className={`mt-1 text-2xl font-semibold ${toneClass}`}>{value}</p>
    </div>
  )
}

function KnowledgeRow({ item }: { item: KnowledgeState }) {
  const barColor = {
    WEAK: 'bg-rose-500',
    LEARNING: 'bg-amber-500',
    MASTERED: 'bg-teal-500',
  }[item.level]

  return (
    <div className="rounded-xl border border-slate-200 bg-white px-4 py-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="truncate text-sm font-medium text-slate-800">{item.name}</h2>
            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] text-slate-500">
              {item.domain}
            </span>
            <span className={`rounded-full px-2 py-0.5 text-[10px] ${levelClass(item.level)}`}>
              {LEVEL_LABELS[item.level]}
            </span>
          </div>
          <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-slate-400">
            <span>练习 {item.practiceCount} 次</span>
            <span>答对 {item.correctCount} 次</span>
            <span>最近得分 {item.lastScore ?? '—'}</span>
            <span>{item.lastPracticedAt ?? '尚未练习'}</span>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-1.5">
          <span className={`text-lg font-semibold ${scoreClass(item.level)}`}>{item.mastery}</span>
          <Trend trend={item.trend} />
        </div>
      </div>

      <div className="mt-2.5 flex items-center gap-3">
        <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-100">
          <div className={`h-full rounded-full ${barColor}`} style={{ width: `${item.mastery}%` }} />
        </div>
        <Link
          to={`/wrong-questions?status=ALL&kpId=${item.knowledgePointId}&kp=${encodeURIComponent(item.name)}`}
          className="shrink-0 text-[11px] text-teal-600 transition hover:text-teal-800"
        >
          错题 →
        </Link>
      </div>
    </div>
  )
}

function Trend({ trend }: { trend: KnowledgeTrend }) {
  if (trend === 'NEW') {
    return <span className="rounded bg-teal-50 px-1.5 py-0.5 text-[10px] text-teal-600">新</span>
  }
  if (trend === 'UP') {
    return <TrendingUp size={14} className="text-teal-600" />
  }
  if (trend === 'DOWN') {
    return <TrendingDown size={14} className="text-rose-500" />
  }
  return <Minus size={14} className="text-slate-300" />
}

function EmptyState({ hasFilter }: { hasFilter: boolean }) {
  if (hasFilter) {
    return (
      <div className="mt-8 rounded-xl border-2 border-dashed border-slate-200 bg-white px-6 py-12 text-center">
        <p className="text-sm font-medium text-slate-700">没有符合筛选条件的知识点</p>
        <p className="mt-1 text-xs text-slate-400">换个等级或领域再试试。</p>
      </div>
    )
  }

  return (
    <div className="mt-8 rounded-xl border-2 border-dashed border-slate-200 bg-white px-6 py-12 text-center">
      <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-teal-50 text-teal-600">
        <BrainCircuit size={22} />
      </span>
      <p className="mt-4 text-sm font-medium text-slate-700">还没有掌握度画像</p>
      <p className="mt-1 text-xs text-slate-400">完成一场 AI 面试后，这里会按知识点汇总你的表现。</p>
      <Link
        to="/interview"
        className="mt-5 inline-block rounded-lg bg-teal-600 px-3.5 py-2 text-sm font-medium text-white transition hover:bg-teal-700"
      >
        开始一场面试
      </Link>
    </div>
  )
}

function levelClass(level: KnowledgeLevel): string {
  if (level === 'WEAK') return 'bg-rose-50 text-rose-600'
  if (level === 'LEARNING') return 'bg-amber-50 text-amber-600'
  return 'bg-teal-50 text-teal-600'
}

function scoreClass(level: KnowledgeLevel): string {
  if (level === 'WEAK') return 'text-rose-600'
  if (level === 'LEARNING') return 'text-amber-600'
  return 'text-teal-600'
}

function errMessage(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

export default KnowledgePage
