import { errMessage } from '../utils/error'
import {
  AlertCircle,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CircleDot,
  Library,
  Loader2,
  Search,
  Shuffle,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  getQuestionFacets,
  getQuestions,
  getRandomQuestionId,
  type QuestionBankFacets,
  type QuestionBankItem,
  type QuestionBankList,
  type QuestionStatus,
  type QuestionType,
} from '../api/questionBank'

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
 * 顶部是压缩后的进度条 + 状态 Tab,筛选项(领域/题型/难度)来自后端 facets,
 * 桌面端用表格、移动端用卡片,保证关键信息不被隐藏。
 */
function QuestionBankPage() {
  const navigate = useNavigate()

  const [data, setData] = useState<QuestionBankList | null>(null)
  const [facets, setFacets] = useState<QuestionBankFacets | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [randomizing, setRandomizing] = useState(false)

  const [domain, setDomain] = useState('')
  const [type, setType] = useState<QuestionType | ''>('')
  const [difficulty, setDifficulty] = useState(0)
  const [status, setStatus] = useState<QuestionStatus | 'ALL'>('ALL')
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)

  // 筛选项只需要加载一次;失败时退回只有搜索/难度,不阻塞列表
  useEffect(() => {
    let cancelled = false
    getQuestionFacets()
      .then((result) => {
        if (!cancelled) setFacets(result)
      })
      .catch(() => {
        if (!cancelled) setFacets(null)
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    getQuestions({
      domain: domain || undefined,
      type: type || undefined,
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
  }, [domain, type, difficulty, status, keyword, page])

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
    setType('')
    setDifficulty(0)
    setStatus('ALL')
    setKeywordInput('')
    setKeyword('')
    setPage(1)
  }

  async function openRandom() {
    if (randomizing) return
    setRandomizing(true)
    try {
      navigate(`/questions/${await getRandomQuestionId()}`)
    } catch (e) {
      setError(errMessage(e))
    } finally {
      setRandomizing(false)
    }
  }

  const summary = data?.summary
  const items = data?.items ?? []
  const currentPage = data?.page ?? 1
  const currentSize = data?.size ?? PAGE_SIZE
  const totalPages = data ? Math.max(1, Math.ceil(data.filteredTotal / data.size)) : 1
  const hasFilter = Boolean(domain || type || difficulty || keyword || status !== 'ALL')

  const attempted = (summary?.done ?? 0) + (summary?.wrong ?? 0)
  const total = summary?.total ?? 0
  const progress = total === 0 ? 0 : Math.round((attempted / total) * 100)

  const statusCounts: Record<QuestionStatus | 'ALL', number> = {
    ALL: total,
    TODO: summary?.todo ?? 0,
    DONE: summary?.done ?? 0,
    WRONG: summary?.wrong ?? 0,
  }

  const domainOptions = useMemo(() => facets?.domains ?? [], [facets])
  const typeOptions = useMemo(() => facets?.types ?? [], [facets])

  return (
    <div className="flex h-full flex-col overflow-y-auto bg-slate-50">
      <header className="sticky top-0 z-10 border-b border-slate-200 bg-white px-4 py-3 md:px-6">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h1 className="flex items-center gap-2 text-base font-semibold text-slate-800">
              <Library size={18} className="text-teal-600" />
              题库
            </h1>
            <p className="mt-1 text-xs text-slate-500">
              按领域和难度刷题，提交后 AI 给出评分、命中与遗漏要点；低分题会自动进入错题本。
            </p>
          </div>
          <button
            type="button"
            onClick={() => void openRandom()}
            disabled={randomizing}
            className="flex shrink-0 items-center gap-1.5 rounded-lg border border-teal-200 bg-teal-50 px-3 py-1.5 text-xs font-medium text-teal-700 transition hover:border-teal-300 hover:bg-teal-100 disabled:opacity-60"
          >
            {randomizing ? <Loader2 size={14} className="animate-spin" /> : <Shuffle size={14} />}
            随机一题
          </button>
        </div>
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

        {/* 进度总览:替代原来的 4 张大卡片 */}
        <section className="rounded-xl border border-slate-200 bg-white px-4 py-3">
          <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-slate-500">
            <span>
              已覆盖 <span className="text-sm font-semibold text-slate-800">{attempted}</span>
              <span className="text-slate-400"> / {total}</span>
            </span>
            <div className="h-1.5 min-w-[120px] flex-1 overflow-hidden rounded-full bg-slate-100">
              <div
                className="h-full rounded-full bg-teal-500 transition-all"
                style={{ width: `${progress}%` }}
              />
            </div>
            <span className="font-medium text-slate-600">{progress}%</span>
            <span className="text-rose-600">错题 {summary?.wrong ?? 0}</span>
            <span className="text-slate-400">累计作答 {summary?.totalAttempts ?? 0} 次</span>
          </div>
          <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
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
                <span className={`ml-1 ${status === tab.key ? 'text-teal-600' : 'text-slate-400'}`}>
                  {statusCounts[tab.key]}
                </span>
              </button>
            ))}
          </div>
        </section>

        {/* 筛选区:搜索 + 题型 + 难度 + 动态领域 */}
        <section className="mt-3 rounded-xl border border-slate-200 bg-white px-3 py-3">
          <div className="flex flex-col gap-2 md:flex-row md:items-center">
            <form
              className="relative flex-1"
              onSubmit={(e) => {
                e.preventDefault()
                setKeyword(keywordInput.trim())
                setPage(1)
              }}
            >
              <Search
                size={14}
                className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
              />
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

            <div className="flex gap-2">
              <select
                value={type}
                onChange={(e) => {
                  setType(e.target.value as QuestionType | '')
                  setPage(1)
                }}
                className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-600 outline-none transition focus:border-teal-400"
              >
                <option value="">全部题型</option>
                {typeOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {TYPE_LABELS[option.value as QuestionType] ?? option.value} {option.count}
                  </option>
                ))}
              </select>

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
          </div>

          <div className="mt-2 flex items-center gap-1.5 overflow-x-auto pb-1">
            <span className="shrink-0 pr-1 text-xs text-slate-400">领域</span>
            <button
              type="button"
              onClick={() => changeDomain('')}
              className={`shrink-0 rounded-full border px-2.5 py-1 text-xs transition ${
                domain === ''
                  ? 'border-teal-300 bg-teal-50 font-medium text-teal-700'
                  : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300'
              }`}
            >
              全部
              <span className="ml-1 text-slate-400">{total}</span>
            </button>
            {domainOptions.map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => changeDomain(option.value)}
                className={`shrink-0 rounded-full border px-2.5 py-1 text-xs transition ${
                  domain === option.value
                    ? 'border-teal-300 bg-teal-50 font-medium text-teal-700'
                    : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300'
                }`}
              >
                {option.value}
                <span className="ml-1 text-slate-400">{option.count}</span>
              </button>
            ))}

            {hasFilter && (
              <button
                type="button"
                onClick={clearFilters}
                className="ml-1 shrink-0 rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs text-slate-500 transition hover:border-slate-300"
              >
                清除筛选
              </button>
            )}
          </div>
        </section>

        {loading && !data ? (
          <ListSkeleton />
        ) : items.length === 0 ? (
          <div className="mt-4 rounded-xl border-2 border-dashed border-slate-200 bg-white px-6 py-12 text-center">
            <p className="text-sm font-medium text-slate-700">没有符合条件的题目</p>
            <p className="mt-1 text-xs text-slate-400">
              {hasFilter ? '换个筛选条件试试，或清除筛选查看全部。' : '题库暂时为空。'}
            </p>
            <div className="mt-3 flex justify-center gap-2">
              {hasFilter && (
                <button
                  type="button"
                  onClick={clearFilters}
                  className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs text-slate-600 transition hover:bg-slate-50"
                >
                  清除筛选
                </button>
              )}
              <button
                type="button"
                onClick={() => void openRandom()}
                className="rounded-lg bg-teal-600 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-teal-700"
              >
                随机一题
              </button>
            </div>
          </div>
        ) : (
          <>
            {/* 桌面端表格 */}
            <div className="mt-3 hidden overflow-hidden rounded-xl border border-slate-200 bg-white md:block">
              <div className="flex items-center gap-3 border-b border-slate-100 bg-slate-50/70 px-4 py-2 text-xs font-medium text-slate-500">
                <span className="w-12 shrink-0">题号</span>
                <span className="flex-1">题目</span>
                <span className="w-24 shrink-0">领域</span>
                <span className="w-16 shrink-0 text-center">难度</span>
                <span className="w-24 shrink-0 text-center">状态</span>
                <span className="w-24 shrink-0 text-right">成绩</span>
              </div>
              {items.map((item, index) => (
                <QuestionRow
                  key={item.id}
                  item={item}
                  index={(currentPage - 1) * currentSize + index + 1}
                />
              ))}
            </div>

            {/* 移动端卡片 */}
            <div className="mt-3 overflow-hidden rounded-xl border border-slate-200 bg-white md:hidden">
              {items.map((item, index) => (
                <QuestionCard
                  key={item.id}
                  item={item}
                  index={(currentPage - 1) * currentSize + index + 1}
                />
              ))}
            </div>
          </>
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

function QuestionRow({ item, index }: { item: QuestionBankItem; index: number }) {
  return (
    <Link
      to={`/questions/${item.id}`}
      className="flex items-center gap-3 border-b border-slate-100 px-4 py-3 transition last:border-b-0 hover:bg-slate-50"
    >
      <span className="w-12 shrink-0 text-xs tabular-nums text-slate-400">{index}</span>

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-slate-800">{item.title}</p>
        <p className="mt-0.5 truncate text-xs text-slate-400">{item.preview}</p>
        {item.knowledgePoints.length > 0 && (
          <div className="mt-1 flex flex-wrap gap-1">
            {item.knowledgePoints.slice(0, 3).map((point) => (
              <span key={point} className="rounded bg-teal-50 px-1.5 py-0.5 text-[11px] text-teal-700">
                {point}
              </span>
            ))}
          </div>
        )}
      </div>

      <span className="w-24 shrink-0 truncate text-xs text-slate-500">{item.domain}</span>

      <span className="flex w-16 shrink-0 justify-center">
        <span className={`rounded-full px-2 py-0.5 text-xs ${difficultyClass(item.difficulty)}`}>
          {difficultyLabel(item.difficulty)}
        </span>
      </span>

      <span className="flex w-24 shrink-0 justify-center">
        <StatusBadge status={item.status} />
      </span>

      <span className="w-24 shrink-0 text-right">
        <ScoreCell item={item} />
      </span>
    </Link>
  )
}

function QuestionCard({ item, index }: { item: QuestionBankItem; index: number }) {
  return (
    <Link
      to={`/questions/${item.id}`}
      className="block border-b border-slate-100 px-4 py-3 transition last:border-b-0 active:bg-slate-50"
    >
      <div className="flex items-start justify-between gap-3">
        <p className="min-w-0 flex-1 text-sm font-medium leading-6 text-slate-800">
          <span className="mr-1.5 text-xs tabular-nums text-slate-400">{index}.</span>
          {item.title}
        </p>
        <span className="shrink-0 text-right">
          <ScoreCell item={item} />
        </span>
      </div>

      <p className="mt-0.5 line-clamp-1 text-xs text-slate-400">{item.preview}</p>

      <div className="mt-1.5 flex flex-wrap items-center gap-1 text-[11px]">
        <span
          className={`rounded px-1.5 py-0.5 ${
            item.judgeMode === 'CODING' ? 'bg-teal-50 text-teal-700' : 'bg-slate-100 text-slate-500'
          }`}
        >
          {TYPE_LABELS[item.type]}
        </span>
        <span className="rounded bg-slate-100 px-1.5 py-0.5 text-slate-500">{item.domain}</span>
        <span className={`rounded px-1.5 py-0.5 ${difficultyClass(item.difficulty)}`}>
          {difficultyLabel(item.difficulty)}
        </span>
        <StatusBadge status={item.status} />
      </div>
    </Link>
  )
}

function ScoreCell({ item }: { item: QuestionBankItem }) {
  if (item.attemptCount === 0) {
    return <span className="text-xs text-slate-300">未作答</span>
  }
  if (item.judgeMode === 'CODING') {
    return (
      <div className="text-xs">
        <p className={statusTextClass(item.status)}>
          {item.status === 'DONE' ? '已通过' : '未通过'}
        </p>
        <p className="mt-0.5 text-[11px] text-slate-400">提交 {item.attemptCount} 次</p>
      </div>
    )
  }
  return (
    <div className="text-xs">
      <p className={statusTextClass(item.status)}>
        {item.lastScore ?? '—'} 分{item.status === 'WRONG' ? ' · 错题' : ''}
      </p>
      <p className="mt-0.5 text-[11px] text-slate-400">已答 {item.attemptCount} 次</p>
    </div>
  )
}

function ListSkeleton() {
  return (
    <div className="mt-3 overflow-hidden rounded-xl border border-slate-200 bg-white">
      {Array.from({ length: 6 }).map((_, index) => (
        <div key={index} className="border-b border-slate-100 px-4 py-3 last:border-b-0">
          <div className="h-4 w-1/3 animate-pulse rounded bg-slate-100" />
          <div className="mt-2 h-3 w-2/3 animate-pulse rounded bg-slate-100" />
        </div>
      ))}
    </div>
  )
}

export function StatusBadge({ status }: { status: QuestionStatus }) {
  if (status === 'DONE') {
    return (
      <span className="flex items-center gap-1 rounded-full bg-teal-50 px-2 py-0.5 text-xs text-teal-700">
        <CheckCircle2 size={11} />
        已做
      </span>
    )
  }
  if (status === 'WRONG') {
    return (
      <span className="flex items-center gap-1 rounded-full bg-rose-50 px-2 py-0.5 text-xs text-rose-600">
        <AlertCircle size={11} />
        错题
      </span>
    )
  }
  return (
    <span className="flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500">
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

export default QuestionBankPage
