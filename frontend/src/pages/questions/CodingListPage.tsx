import { errMessage } from '../../utils/error'
import { AlertCircle, Code2, Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  getQuestions,
  type QuestionBankItem,
  type QuestionBankList,
  type QuestionStatus,
} from '../../api/questionBank'
import Pagination from '../../components/questions/Pagination'
import { DifficultyBadge } from '../../components/questions/QuestionBadges'

const PAGE_SIZE_DEFAULT = 20

/** 编程题列表:右侧主区域,筛选状态与左侧栏共用 URL 参数 */
function CodingListPage() {
  const [params, setParams] = useSearchParams()

  const domain = params.get('domain') ?? ''
  const difficulty = Number(params.get('difficulty') ?? 0)
  const status = (params.get('status') ?? 'ALL') as QuestionStatus | 'ALL'
  const keyword = params.get('q') ?? ''
  const page = Math.max(1, Number(params.get('page') ?? 1))
  const pageSize = Number(params.get('size') ?? PAGE_SIZE_DEFAULT)

  const [data, setData] = useState<QuestionBankList | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    getQuestions({
      judgeMode: 'CODING',
      domain: domain || undefined,
      difficulty: difficulty || undefined,
      status: status === 'ALL' ? undefined : status,
      keyword: keyword || undefined,
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
  }, [domain, difficulty, status, keyword, page, pageSize])

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

  const items = data?.items ?? []
  const totalPages = data ? Math.max(1, Math.ceil(data.filteredTotal / data.size)) : 1

  return (
    <div className="mx-auto max-w-5xl px-4 py-5 md:px-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="flex items-center gap-2 text-lg font-semibold text-slate-800">
            <Code2 size={18} className="text-teal-600" />
            编程题
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            在线写 Java / Python，运行样例或提交判题；未通过会自动进入错题本。
          </p>
        </div>
        <div className="text-xs text-slate-400">
          已通过 {data?.summary.done ?? 0} / {data?.summary.total ?? 0}
        </div>
      </header>

      {error && (
        <div className="mt-4 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
          <AlertCircle size={15} className="mt-0.5 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {loading && !data ? (
        <div className="mt-6 flex items-center justify-center gap-2 text-xs text-slate-400">
          <Loader2 size={14} className="animate-spin" />
          加载中…
        </div>
      ) : items.length === 0 ? (
        <div className="mt-6 rounded-xl border-2 border-dashed border-slate-200 bg-white px-6 py-14 text-center">
          <p className="text-sm font-medium text-slate-700">没有符合条件的编程题</p>
          <p className="mt-1 text-xs text-slate-400">换个筛选条件，或让左侧栏恢复全部。</p>
        </div>
      ) : (
        <>
          <div className="mt-4 hidden overflow-hidden rounded-xl border border-slate-200 bg-white md:block">
            <div className="flex items-center gap-3 border-b border-slate-100 bg-slate-50/70 px-4 py-2 text-xs font-medium text-slate-500">
              <span className="w-10 shrink-0">#</span>
              <span className="flex-1">题目</span>
              <span className="w-24 shrink-0">领域</span>
              <span className="w-16 shrink-0 text-center">难度</span>
              <span className="w-20 shrink-0 text-center">状态</span>
              <span className="w-28 shrink-0 text-right">提交</span>
            </div>
            {items.map((item, index) => (
              <CodingRow
                key={item.id}
                item={item}
                index={(data!.page - 1) * data!.size + index + 1}
              />
            ))}
          </div>

          <div className="mt-4 overflow-hidden rounded-xl border border-slate-200 bg-white md:hidden">
            {items.map((item, index) => (
              <CodingCard
                key={item.id}
                item={item}
                index={(data!.page - 1) * data!.size + index + 1}
              />
            ))}
          </div>

          <div className="mt-3">
            <Pagination
              page={data!.page}
              totalPages={totalPages}
              total={data!.filteredTotal}
              size={data!.size}
              loading={loading}
              onPageChange={(next) => update({ page: next }, false)}
              onSizeChange={(size) => update({ size })}
            />
          </div>
        </>
      )}
    </div>
  )
}

function CodingRow({ item, index }: { item: QuestionBankItem; index: number }) {
  return (
    <Link
      to={`/questions/coding/${item.id}`}
      className="flex items-center gap-3 border-b border-slate-100 px-4 py-3 transition last:border-b-0 hover:bg-slate-50"
    >
      <span className="w-10 shrink-0 text-xs tabular-nums text-slate-400">{index}</span>
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
        <DifficultyBadge difficulty={item.difficulty} />
      </span>
      <span className="flex w-20 shrink-0 justify-center">
        <CodingStatus status={item.status} />
      </span>
      <div className="w-28 shrink-0 text-right text-xs">
        <p className="text-slate-600">{item.attemptCount === 0 ? '未提交' : `提交 ${item.attemptCount} 次`}</p>
        {item.lastAttemptAt && <p className="mt-0.5 text-[11px] text-slate-400">{item.lastAttemptAt}</p>}
      </div>
    </Link>
  )
}

function CodingCard({ item, index }: { item: QuestionBankItem; index: number }) {
  return (
    <Link
      to={`/questions/coding/${item.id}`}
      className="block border-b border-slate-100 px-4 py-3 transition last:border-b-0 active:bg-slate-50"
    >
      <div className="flex items-start justify-between gap-3">
        <p className="min-w-0 flex-1 text-sm font-medium leading-6 text-slate-800">
          <span className="mr-1.5 text-xs tabular-nums text-slate-400">{index}.</span>
          {item.title}
        </p>
        <CodingStatus status={item.status} />
      </div>
      <p className="mt-0.5 line-clamp-1 text-xs text-slate-400">{item.preview}</p>
      <div className="mt-1.5 flex flex-wrap items-center gap-1 text-[11px]">
        <span className="rounded bg-slate-100 px-1.5 py-0.5 text-slate-500">{item.domain}</span>
        <DifficultyBadge difficulty={item.difficulty} />
        <span className="text-slate-400">
          {item.attemptCount === 0 ? '未提交' : `提交 ${item.attemptCount} 次`}
        </span>
      </div>
    </Link>
  )
}

function CodingStatus({ status }: { status: QuestionStatus }) {
  if (status === 'DONE') {
    return (
      <span className="rounded-full bg-teal-50 px-2 py-0.5 text-xs text-teal-700">已通过</span>
    )
  }
  if (status === 'WRONG') {
    return (
      <span className="rounded-full bg-rose-50 px-2 py-0.5 text-xs text-rose-600">未通过</span>
    )
  }
  return <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500">未做</span>
}

export default CodingListPage
