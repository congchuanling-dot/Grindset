import { errMessage } from '../../utils/error'
import { AlertCircle, Loader2, Search, Shuffle } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  getQuestionFacets,
  getQuestionStats,
  getRandomQuestionId,
  type QuestionBankFacets,
  type QuestionBankSummary,
} from '../../api/questionBank'

const STATUS_TABS = [
  { key: 'ALL', label: '全部' },
  { key: 'TODO', label: '未做' },
  { key: 'DONE', label: '已通过' },
  { key: 'WRONG', label: '未通过' },
] as const

const DIFFICULTIES = [
  { value: 0, label: '全部难度' },
  { value: 1, label: '简单' },
  { value: 2, label: '中等' },
  { value: 3, label: '困难' },
]

/** 编程题左侧筛选栏:进度、状态、难度、领域、搜索 */
function CodingFilterPane() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()

  const domain = params.get('domain') ?? ''
  const difficulty = Number(params.get('difficulty') ?? 0)
  const status = params.get('status') ?? 'ALL'
  const keyword = params.get('q') ?? ''

  const [summary, setSummary] = useState<QuestionBankSummary | null>(null)
  const [facets, setFacets] = useState<QuestionBankFacets | null>(null)
  const [keywordInput, setKeywordInput] = useState(keyword)
  const [error, setError] = useState('')
  const [randomizing, setRandomizing] = useState(false)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setKeywordInput(keyword)
  }, [keyword])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    Promise.all([
      getQuestionStats('CODING').catch(() => null),
      getQuestionFacets('CODING').catch(() => null),
    ])
      .then(([stats, facet]) => {
        if (cancelled) return
        setSummary(stats)
        setFacets(facet)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

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

  async function openRandom() {
    if (randomizing) return
    setRandomizing(true)
    setError('')
    try {
      navigate(`/questions/coding/${await getRandomQuestionId(undefined, 'CODING')}`)
    } catch (e) {
      setError(errMessage(e))
    } finally {
      setRandomizing(false)
    }
  }

  const total = summary?.total ?? 0
  const passed = summary?.done ?? 0
  const percent = total === 0 ? 0 : Math.round((passed / total) * 100)
  const hasFilter = Boolean(domain || difficulty || keyword || status !== 'ALL')

  const counts = useMemo<Record<string, number>>(
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
              已通过 <span className="font-semibold text-slate-800">{passed}</span> / {total}
            </span>
            <button
              type="button"
              onClick={() => void openRandom()}
              disabled={randomizing}
              className="flex items-center gap-1 rounded-md border border-brand-200 bg-white px-2 py-0.5 text-[11px] font-medium text-brand-700 transition hover:border-brand-300 hover:bg-brand-50 disabled:opacity-60"
            >
              {randomizing ? <Loader2 size={11} className="animate-spin" /> : <Shuffle size={11} />}
              随机
            </button>
          </div>
          <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-slate-200">
            <div className="h-full rounded-full bg-brand-500 transition-all" style={{ width: `${percent}%` }} />
          </div>
          <div className="mt-1 flex items-center justify-between text-[11px] text-slate-400">
            <span>{percent}%</span>
            <span className="text-rose-500">未通过 {summary?.wrong ?? 0}</span>
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
            placeholder="搜索编程题，回车确认"
            className="w-full rounded-lg border border-slate-200 py-1.5 pl-7 pr-7 text-xs text-slate-700 outline-none transition focus:border-brand-400"
          />
        </form>

        <div className="mt-2 grid grid-cols-2 gap-1.5">
          <select
            value={domain}
            onChange={(e) => update({ domain: e.target.value || undefined })}
            className="col-span-2 rounded-lg border border-slate-200 px-2 py-1.5 text-xs text-slate-600 outline-none focus:border-brand-400"
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
            className="col-span-2 rounded-lg border border-slate-200 px-2 py-1.5 text-xs text-slate-600 outline-none focus:border-brand-400"
          >
            {DIFFICULTIES.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
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
                  ? 'border-brand-300 bg-brand-50 font-medium text-brand-700'
                  : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300'
              }`}
            >
              {tab.label}
              <span className={`ml-1 ${status === tab.key ? 'text-brand-600' : 'text-slate-400'}`}>
                {counts[tab.key]}
              </span>
            </button>
          ))}
          {hasFilter && (
            <button
              type="button"
              onClick={() => setParams(new URLSearchParams(), { replace: true })}
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

        {loading && (
          <p className="mt-2 text-[11px] text-slate-400">加载进度…</p>
        )}
      </div>

      <div className="px-3 py-3 text-[11px] leading-5 text-slate-400">
        右侧列表按难度和状态筛选；点击题目进入左右分栏的判题工作台，代码和提交记录都在同一页。
      </div>
    </div>
  )
}

export default CodingFilterPane
