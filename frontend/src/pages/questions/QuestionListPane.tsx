import { errMessage } from '../../utils/error'
import { AlertCircle, Loader2, Search, Shuffle } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useMatch, useNavigate, useSearchParams } from 'react-router-dom'
import {
  getQuestionFacets,
  getQuestions,
  getRandomQuestionId,
  type QuestionBankFacets,
  type QuestionBankList,
  type QuestionStatus,
  type QuestionType,
} from '../../api/questionBank'
import Pagination from '../../components/questions/Pagination'
import { DifficultyBadge, StatusBadge, TYPE_LABELS } from '../../components/questions/QuestionBadges'

const STATUS_TABS: { key: QuestionStatus | 'ALL'; label: string }[] = [
  { key: 'ALL', label: '全部' },
  { key: 'TODO', label: '未做' },
  { key: 'DONE', label: '已做' },
  { key: 'WRONG', label: '错题' },
]

const DIFFICULTIES = [
  { value: 0, label: '全部难度' },
  { value: 1, label: '简单' },
  { value: 2, label: '中等' },
  { value: 3, label: '困难' },
]

const PAGE_SIZE_DEFAULT = 20

/** 问答题左侧栏:进度、筛选和题目列表;筛选条件保存在 URL 上 */
function QuestionListPane() {
  const navigate = useNavigate()
  const match = useMatch('/questions/:id')
  const activeId = match?.params.id
  const [params, setParams] = useSearchParams()

  const domain = params.get('domain') ?? ''
  const type = (params.get('type') ?? '') as QuestionType | ''
  const difficulty = Number(params.get('difficulty') ?? 0)
  const status = (params.get('status') ?? 'ALL') as QuestionStatus | 'ALL'
  const keyword = params.get('q') ?? ''
  const page = Math.max(1, Number(params.get('page') ?? 1))
  const pageSize = Number(params.get('size') ?? PAGE_SIZE_DEFAULT)

  const [keywordInput, setKeywordInput] = useState(keyword)
  const [data, setData] = useState<QuestionBankList | null>(null)
  const [facets, setFacets] = useState<QuestionBankFacets | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [randomizing, setRandomizing] = useState(false)

  useEffect(() => {
    setKeywordInput(keyword)
  }, [keyword])

  useEffect(() => {
    getQuestionFacets('TEXT')
      .then(setFacets)
      .catch(() => setFacets(null))
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
      judgeMode: 'TEXT',
      page,
      size: pageSize,
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
  }, [domain, type, difficulty, status, keyword, page, pageSize])

  function update(patch: Record<string, string | number | undefined>, resetPage = true) {
    const next = new URLSearchParams(params)
    Object.entries(patch).forEach(([key, value]) => {
      if (value === undefined || value === '' || value === 0) {
        next.delete(key)
      } else {
        next.set(key, String(value))
      }
    })
    if (resetPage) next.delete('page')
    setParams(next, { replace: true })
  }

  function clearFilters() {
    setParams(new URLSearchParams(), { replace: true })
    setKeywordInput('')
  }

  async function openRandom() {
    if (randomizing) return
    setRandomizing(true)
    try {
      const id = await getRandomQuestionId(activeId, 'TEXT')
      navigate(questionLink(id))
    } catch (e) {
      setError(errMessage(e))
    } finally {
      setRandomizing(false)
    }
  }

  function questionLink(id: string) {
    const search = new URLSearchParams(params)
    // 换题时回到「作答」Tab,不把上一题的 tab 带过去
    search.delete('tab')
    const query = search.toString()
    return `/questions/${id}${query ? `?${query}` : ''}`
  }

  const summary = data?.summary
  const items = data?.items ?? []
  const currentPage = data?.page ?? page
  const currentSize = data?.size ?? pageSize
  const total = summary?.total ?? 0
  const attempted = (summary?.done ?? 0) + (summary?.wrong ?? 0)
  const percent = total === 0 ? 0 : Math.round((attempted / total) * 100)
  const totalPages = data ? Math.max(1, Math.ceil(data.filteredTotal / data.size)) : 1
  const hasFilter = Boolean(domain || type || difficulty || keyword || status !== 'ALL')

  const statusCounts = useMemo<Record<QuestionStatus | 'ALL', number>>(
    () => ({
      ALL: total,
      TODO: summary?.todo ?? 0,
      DONE: summary?.done ?? 0,
      WRONG: summary?.wrong ?? 0,
    }),
    [summary, total],
  )

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="shrink-0 border-b border-slate-100 px-3 py-3">
        <div className="rounded-lg border border-slate-200 bg-slate-50/70 px-3 py-2">
          <div className="flex items-center justify-between gap-2 text-xs text-slate-500">
            <span>
              已覆盖 <span className="font-semibold text-slate-800">{attempted}</span> / {total}
            </span>
            <button
              type="button"
              onClick={() => void openRandom()}
              disabled={randomizing}
              className="flex items-center gap-1 rounded-md border border-teal-200 bg-white px-2 py-0.5 text-[11px] font-medium text-teal-700 transition hover:border-teal-300 hover:bg-teal-50 disabled:opacity-60"
            >
              {randomizing ? <Loader2 size={11} className="animate-spin" /> : <Shuffle size={11} />}
              随机
            </button>
          </div>
          <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-slate-200">
            <div
              className="h-full rounded-full bg-teal-500 transition-all"
              style={{ width: `${percent}%` }}
            />
          </div>
          <div className="mt-1 flex items-center justify-between text-[11px] text-slate-400">
            <span>{percent}%</span>
            <span className="text-rose-500">错题 {summary?.wrong ?? 0}</span>
          </div>
        </div>

        <form
          className="relative mt-2"
          onSubmit={(e) => {
            e.preventDefault()
            update({ q: keywordInput.trim() })
          }}
        >
          <Search
            size={13}
            className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400"
          />
          <input
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
            placeholder="搜索题目，回车确认"
            className="w-full rounded-lg border border-slate-200 py-1.5 pl-7 pr-7 text-xs text-slate-700 outline-none transition focus:border-teal-400"
          />
        </form>

        <div className="mt-2 grid grid-cols-2 gap-1.5">
          <select
            value={domain}
            onChange={(e) => update({ domain: e.target.value || undefined })}
            className="col-span-2 rounded-lg border border-slate-200 px-2 py-1.5 text-xs text-slate-600 outline-none focus:border-teal-400"
          >
            <option value="">全部领域 {total}</option>
            {facets?.domains.map((option) => (
              <option key={option.value} value={option.value}>
                {option.value} {option.count}
              </option>
            ))}
          </select>
          <select
            value={difficulty}
            onChange={(e) => update({ difficulty: Number(e.target.value) || undefined })}
            className="rounded-lg border border-slate-200 px-2 py-1.5 text-xs text-slate-600 outline-none focus:border-teal-400"
          >
            {DIFFICULTIES.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <select
            value={type}
            onChange={(e) => update({ type: e.target.value || undefined })}
            className="rounded-lg border border-slate-200 px-2 py-1.5 text-xs text-slate-600 outline-none focus:border-teal-400"
          >
            <option value="">全部题型</option>
            {facets?.types.map((option) => (
              <option key={option.value} value={option.value}>
                {TYPE_LABELS[option.value as QuestionType] ?? option.value} {option.count}
              </option>
            ))}
          </select>
        </div>

        <div className="mt-2 flex flex-wrap items-center gap-1">
          {STATUS_TABS.map((tab) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => update({ status: tab.key === 'ALL' ? undefined : tab.key })}
              className={`rounded-full border px-2 py-0.5 text-[11px] transition ${
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
          {hasFilter && (
            <button
              type="button"
              onClick={clearFilters}
              className="ml-auto text-[11px] text-slate-400 hover:text-slate-600"
            >
              清除
            </button>
          )}
        </div>

        {error && (
          <div className="mt-2 flex items-start gap-1.5 rounded-lg border border-rose-200 bg-rose-50 px-2 py-1.5 text-[11px] text-rose-700">
            <AlertCircle size={12} className="mt-0.5 shrink-0" />
            <span>{error}</span>
          </div>
        )}
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        {loading && !data ? (
          <ListSkeleton />
        ) : items.length === 0 ? (
          <div className="px-4 py-10 text-center text-xs text-slate-400">
            没有符合条件的题目
          </div>
        ) : (
          items.map((item, index) => {
            const active = item.id === activeId
            return (
              <Link
                key={item.id}
                to={questionLink(item.id)}
                className={`flex items-start gap-2 border-b border-slate-100 px-3 py-2.5 transition ${
                  active
                    ? 'border-l-2 border-l-teal-500 bg-teal-50'
                    : 'border-l-2 border-l-transparent hover:bg-slate-50'
                }`}
              >
                <span className="w-6 shrink-0 pt-0.5 text-[11px] tabular-nums text-slate-400">
                  {(currentPage - 1) * currentSize + index + 1}
                </span>
                <div className="min-w-0 flex-1">
                  <p
                    className={`truncate text-sm ${
                      active ? 'font-medium text-teal-800' : 'text-slate-700'
                    }`}
                  >
                    {item.title}
                  </p>
                  <div className="mt-1 flex flex-wrap items-center gap-1">
                    <span className="text-[11px] text-slate-400">{item.domain}</span>
                    <DifficultyBadge difficulty={item.difficulty} />
                    <StatusBadge status={item.status} />
                  </div>
                </div>
                {item.lastScore != null && (
                  <span className="shrink-0 pt-0.5 text-[11px] text-slate-400">
                    {item.lastScore} 分
                  </span>
                )}
              </Link>
            )
          })
        )}
      </div>

      <div className="shrink-0 border-t border-slate-100">
        <Pagination
          compact
          page={data?.page ?? page}
          totalPages={totalPages}
          total={data?.filteredTotal ?? 0}
          size={data?.size ?? pageSize}
          loading={loading}
          onPageChange={(next) => update({ page: next }, false)}
          onSizeChange={(size) => update({ size })}
        />
      </div>
    </div>
  )
}

function ListSkeleton() {
  return (
    <div>
      {Array.from({ length: 8 }).map((_, index) => (
        <div key={index} className="border-b border-slate-100 px-3 py-3">
          <div className="h-3.5 w-2/3 animate-pulse rounded bg-slate-100" />
          <div className="mt-2 h-2.5 w-1/2 animate-pulse rounded bg-slate-100" />
        </div>
      ))}
    </div>
  )
}

export default QuestionListPane
