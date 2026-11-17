import {
  AlertCircle,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CircleDot,
  Library,
  Loader2,
  Search,
  X,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  getQuestions,
  type QuestionBankItem,
  type QuestionBankList,
  type QuestionStatus,
  type QuestionType,
} from '../api/questionBank'

const DOMAINS = ['Redis', 'MySQL', 'JVM', 'Java', '网络', '操作系统', '项目', '场景']

const STATUS_TABS: { key: QuestionStatus | 'ALL'; label: string }[] = [
  { key: 'ALL', label: '全部' },
  { key: 'TODO', label: '未做' },
  { key: 'DONE', label: '已做' },
  { key: 'WRONG', label: '错题' },
]

const DIFFICULTY_OPTIONS = [
  { value: 0, label: '全部难度' },
  { value: 1, label: '简单' },
  { value: 2, label: '中等' },
  { value: 3, label: '困难' },
]

const PAGE_SIZE = 12

const TYPE_LABELS: Record<QuestionType, string> = {
  BASICS: '八股',
  SCENARIO: '场景',
  PROJECT: '项目',
  CODE: '编程',
}

/**
 * 题库列表:力扣式刷题入口。
 * 支持按领域、难度、做题状态和关键词筛选,点击进入作答详情。
 */
function QuestionBankPage() {
  const [data, setData] = useState<QuestionBankList | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [domain, setDomain] = useState('')
  const [difficulty, setDifficulty] = useState(0)
  const [status, setStatus] = useState<QuestionStatus | 'ALL'>('ALL')
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    getQuestions({
      domain: domain || undefined,
      difficulty: difficulty || undefined,
      status: status === 'ALL' ? undefined : status,
      keyword: keyword || undefined,
      page,
      size: PAGE_SIZE,
    })
      .then((result) => {
        if (!cancelled) setData(result)
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
  }, [domain, difficulty, status, keyword, page])

  function changeDomain(next: string) {
    setDomain(next === domain ? '' : next)
    setPage(1)
  }

  function changeStatus(next: QuestionStatus | 'ALL') {
    setStatus(next)
    setPage(1)
  }

  function clearFilters() {
    setDomain('')
    setDifficulty(0)
    setStatus('ALL')
    setKeywordInput('')
    setKeyword('')
    setPage(1)
  }

  const summary = data?.summary
  const items = data?.items ?? []
  const currentPage = data?.page ?? 1
  const currentSize = data?.size ?? PAGE_SIZE
  const totalPages = data ? Math.max(1, Math.ceil(data.filteredTotal / data.size)) : 1
  const hasFilter = Boolean(domain || difficulty || keyword || status !== 'ALL')

  return (
    <div className="flex h-full flex-col overflow-y-auto bg-slate-50">
      <header className="border-b border-slate-200 bg-white px-4 py-4 md:px-6">
        <h1 className="flex items-center gap-2 text-base font-semibold text-slate-800">
          <Library size={18} className="text-teal-600" />
          题库
        </h1>
        <p className="mt-1 text-xs text-slate-500">
          按领域和难度刷题，提交后 AI 给出评分、命中与遗漏要点；低分题会自动进入错题本。
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

        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          <StatCard label="题目总数" value={summary?.total ?? 0} tone="slate" />
          <StatCard label="未做" value={summary?.todo ?? 0} tone="amber" />
          <StatCard label="已做" value={summary?.done ?? 0} tone="teal" />
          <StatCard label="错题" value={summary?.wrong ?? 0} tone="rose" />
        </div>

        <div className="mt-4 rounded-xl border border-slate-200 bg-white p-3">
          <div className="flex flex-col gap-2 md:flex-row md:items-center">
            <form
              className="relative flex-1"
              onSubmit={(e) => {
                e.preventDefault()
                setKeyword(keywordInput.trim())
                setPage(1)
              }}
            >
              <Search size={14} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                value={keywordInput}
                onChange={(e) => setKeywordInput(e.target.value)}
                placeholder="搜索题目关键词，回车确认"
                className="w-full rounded-lg border border-slate-200 py-2 pl-8 pr-8 text-sm text-slate-700 outline-none transition focus:border-teal-400"
              />
              {keywordInput && (
                <button
                  type="button"
                  onClick={() => {
                    setKeywordInput('')
                    setKeyword('')
                    setPage(1)
                  }}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                >
                  <X size={13} />
                </button>
              )}
            </form>

            <select
              value={difficulty}
              onChange={(e) => {
                setDifficulty(Number(e.target.value))
                setPage(1)
              }}
              className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-600 outline-none transition focus:border-teal-400"
            >
              {DIFFICULTY_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          <div className="mt-2 flex flex-wrap items-center gap-1.5">
            {STATUS_TABS.map((tab) => (
              <button
                key={tab.key}
                type="button"
                onClick={() => changeStatus(tab.key)}
                className={`rounded-full border px-2.5 py-1 text-xs transition ${
                  status === tab.key
                    ? 'border-teal-300 bg-teal-50 font-medium text-teal-700'
                    : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300'
                }`}
              >
                {tab.label}
              </button>
            ))}

            <span className="mx-1 h-4 w-px bg-slate-200" />

            <button
              type="button"
              onClick={() => changeDomain('')}
              className={`rounded-full border px-2.5 py-1 text-xs transition ${
                domain === ''
                  ? 'border-teal-300 bg-teal-50 font-medium text-teal-700'
                  : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300'
              }`}
            >
              全部领域
            </button>
            {DOMAINS.map((item) => (
              <button
                key={item}
                type="button"
                onClick={() => changeDomain(item)}
                className={`rounded-full border px-2.5 py-1 text-xs transition ${
                  domain === item
                    ? 'border-teal-300 bg-teal-50 font-medium text-teal-700'
                    : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300'
                }`}
              >
                {item}
              </button>
            ))}

            {hasFilter && (
              <button
                type="button"
                onClick={clearFilters}
                className="ml-1 rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs text-slate-500 transition hover:border-slate-300"
              >
                清除筛选
              </button>
            )}
          </div>
        </div>

        {loading && !data ? (
          <div className="mt-8 flex items-center justify-center gap-2 text-xs text-slate-400">
            <Loader2 size={14} className="animate-spin" />
            加载中…
          </div>
        ) : items.length === 0 ? (
          <div className="mt-4 rounded-xl border-2 border-dashed border-slate-200 bg-white px-6 py-12 text-center">
            <p className="text-sm font-medium text-slate-700">没有符合条件的题目</p>
            <p className="mt-1 text-xs text-slate-400">
              {hasFilter ? '换个筛选条件试试，或清除筛选查看全部。' : '题库暂时为空。'}
            </p>
          </div>
        ) : (
          <div className="mt-3 overflow-hidden rounded-xl border border-slate-200 bg-white">
            <div className="hidden items-center gap-3 border-b border-slate-100 px-4 py-2 text-[11px] text-slate-400 md:flex">
              <span className="w-8 shrink-0">#</span>
              <span className="flex-1">题目</span>
              <span className="w-16 shrink-0 text-center">难度</span>
              <span className="w-16 shrink-0 text-center">状态</span>
              <span className="w-20 shrink-0 text-right">我的成绩</span>
            </div>

            {items.map((item, index) => (
              <QuestionRow
                key={item.id}
                item={item}
                index={(currentPage - 1) * currentSize + index + 1}
              />
            ))}
          </div>
        )}

        {data && data.filteredTotal > 0 && (
          <div className="mt-3 flex items-center justify-between text-xs text-slate-500">
            <span>
              共 {data.filteredTotal} 道 · 第 {data.page} / {totalPages} 页
            </span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setPage((prev) => Math.max(1, prev - 1))}
                disabled={data.page <= 1 || loading}
                className="flex items-center gap-0.5 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 transition hover:border-slate-300 disabled:cursor-not-allowed disabled:text-slate-300"
              >
                <ChevronLeft size={13} />
                上一页
              </button>
              <button
                type="button"
                onClick={() => setPage((prev) => Math.min(totalPages, prev + 1))}
                disabled={data.page >= totalPages || loading}
                className="flex items-center gap-0.5 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 transition hover:border-slate-300 disabled:cursor-not-allowed disabled:text-slate-300"
              >
                下一页
                <ChevronRight size={13} />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function StatCard({ label, value, tone }: { label: string; value: number; tone: 'amber' | 'rose' | 'teal' | 'slate' }) {
  const toneClass = {
    amber: 'text-amber-600',
    rose: 'text-rose-600',
    teal: 'text-teal-700',
    slate: 'text-slate-700',
  }[tone]

  return (
    <div className="rounded-xl border border-slate-200 bg-white px-4 py-3">
      <p className="text-xs text-slate-400">{label}</p>
      <p className={`mt-1 text-2xl font-semibold ${toneClass}`}>{value}</p>
    </div>
  )
}

function QuestionRow({ item, index }: { item: QuestionBankItem; index: number }) {
  return (
    <Link
      to={`/questions/${item.id}`}
      className="flex items-center gap-3 border-b border-slate-100 px-4 py-3 transition last:border-b-0 hover:bg-slate-50"
    >
      <span className="w-8 shrink-0 text-xs text-slate-400">{index}</span>

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-slate-800">{item.title}</p>
        <p className="mt-0.5 truncate text-xs text-slate-400">{item.preview}</p>
        <div className="mt-1 flex flex-wrap items-center gap-1 text-[11px]">
          <span
            className={`rounded px-1.5 py-0.5 ${
              item.judgeMode === 'CODING' ? 'bg-teal-50 text-teal-700' : 'bg-slate-100 text-slate-500'
            }`}
          >
            {TYPE_LABELS[item.type]}
          </span>
          {item.knowledgePoints.slice(0, 3).map((point) => (
            <span key={point} className="rounded bg-teal-50 px-1.5 py-0.5 text-teal-700">
              {point}
            </span>
          ))}
        </div>
      </div>

      <span className={`hidden w-16 shrink-0 justify-center rounded-full px-2 py-0.5 text-center text-[11px] md:flex ${difficultyClass(item.difficulty)}`}>
        {difficultyLabel(item.difficulty)}
      </span>

      <span className="hidden w-16 shrink-0 justify-center md:flex">
        <StatusBadge status={item.status} />
      </span>

      <div className="w-20 shrink-0 text-right text-[11px] text-slate-400">
        {item.attemptCount === 0 ? (
          <span>未作答</span>
        ) : item.judgeMode === 'CODING' ? (
          <>
            <p className={statusTextClass(item.status)}>
              {item.status === 'DONE' ? '已通过' : '未通过'}
            </p>
            <p className="mt-0.5">提交 {item.attemptCount} 次</p>
          </>
        ) : (
          <>
            <p className={statusTextClass(item.status)}>
              {item.lastScore ?? '—'} 分{item.status === 'WRONG' ? ' · 错题' : ''}
            </p>
            <p className="mt-0.5">已答 {item.attemptCount} 次</p>
          </>
        )}
      </div>
    </Link>
  )
}

export function StatusBadge({ status }: { status: QuestionStatus }) {
  if (status === 'DONE') {
    return (
      <span className="flex items-center gap-1 rounded-full bg-teal-50 px-2 py-0.5 text-[11px] text-teal-700">
        <CheckCircle2 size={11} />
        已做
      </span>
    )
  }
  if (status === 'WRONG') {
    return (
      <span className="flex items-center gap-1 rounded-full bg-rose-50 px-2 py-0.5 text-[11px] text-rose-600">
        <AlertCircle size={11} />
        错题
      </span>
    )
  }
  return (
    <span className="flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-[11px] text-slate-500">
      <CircleDot size={11} />
      未做
    </span>
  )
}

export function difficultyLabel(difficulty: number): string {
  if (difficulty === 1) return '简单'
  if (difficulty === 3) return '困难'
  return '中等'
}

export function difficultyClass(difficulty: number): string {
  if (difficulty === 1) return 'bg-teal-50 text-teal-700'
  if (difficulty === 3) return 'bg-rose-50 text-rose-600'
  return 'bg-amber-50 text-amber-700'
}

function statusTextClass(status: QuestionStatus): string {
  if (status === 'WRONG') return 'text-rose-500'
  if (status === 'DONE') return 'text-teal-600'
  return 'text-slate-400'
}

function errMessage(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

export default QuestionBankPage
