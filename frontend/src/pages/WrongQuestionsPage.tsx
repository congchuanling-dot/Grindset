import { errMessage } from '../utils/error'
import {
  AlertCircle,
  BookX,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Loader2,
  RefreshCw,
  Sparkles,
  X,
} from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  getWrongQuestionDetail,
  getWrongQuestions,
  retryWrongQuestion,
  type RetryResult,
  type WrongQuestionDetail,
  type WrongQuestionItem,
  type WrongQuestionList,
  type WrongStatus,
} from '../api/wrongQuestion'

type TabKey = 'PENDING' | 'MASTERED' | 'ALL'

const TABS: { key: TabKey; label: string }[] = [
  { key: 'PENDING', label: '待重练' },
  { key: 'MASTERED', label: '已掌握' },
  { key: 'ALL', label: '全部' },
]

/**
 * 错题本:面试中得分 <= 2 的题自动收录,可单题重练并回写掌握度画像。
 * 筛选参数以 URL 为准,便于从画像页、复盘报告直接跳转。
 */
function WrongQuestionsPage() {
  const [searchParams, setSearchParams] = useSearchParams()

  const statusParam = searchParams.get('status')
  const tab: TabKey =
    statusParam === 'MASTERED' ? 'MASTERED' : statusParam === 'ALL' ? 'ALL' : 'PENDING'
  const kpId = searchParams.get('kpId') ?? ''
  const kpName = searchParams.get('kp') ?? ''
  const sessionId = searchParams.get('sessionId') ?? ''
  const openId = searchParams.get('open')

  const [data, setData] = useState<WrongQuestionList | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    getWrongQuestions({
      status: tab === 'ALL' ? undefined : (tab as WrongStatus),
      kpId: kpId || undefined,
      kp: kpId ? undefined : kpName || undefined,
      sessionId: sessionId || undefined,
      limit: 100,
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
  }, [tab, kpId, kpName, sessionId, reloadKey])

  function updateParams(mutate: (params: URLSearchParams) => void) {
    const next = new URLSearchParams(searchParams)
    mutate(next)
    setSearchParams(next, { replace: true })
  }

  function switchTab(key: TabKey) {
    updateParams((params) => {
      if (key === 'PENDING') {
        params.delete('status')
      } else {
        params.set('status', key)
      }
    })
  }

  function closeDialog() {
    updateParams((params) => {
      params.delete('open')
    })
  }

  function handleUpdated(result: RetryResult) {
    setNotice(
      result.status === 'MASTERED'
        ? `重练完成：${result.score} 分，这道题已移入「已掌握」。`
        : `重练完成：${result.score} 分，继续保持待重练。`,
    )
    setReloadKey((key) => key + 1)
  }

  const summary = data?.summary
  const items = data?.items ?? []
  const hasFilter = Boolean(kpId || kpName || sessionId)

  return (
    <div className="flex h-full flex-col overflow-y-auto bg-slate-50">
      <header className="border-b border-slate-200 bg-white px-4 py-4 md:px-6">
        <h1 className="flex items-center gap-2 text-base font-semibold text-slate-800">
          <BookX size={18} className="text-teal-600" />
          错题本
        </h1>
        <p className="mt-1 text-xs text-slate-500">
          AI 面试中得分不超过 2 分的题会自动收进这里，重练后可回写掌握度画像。
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

        {notice && (
          <div className="mb-4 flex items-start gap-2 rounded-lg border border-teal-200 bg-teal-50 px-3 py-2 text-sm text-teal-700">
            <CheckCircle2 size={16} className="mt-0.5 shrink-0" />
            <span className="flex-1">{notice}</span>
            <button
              type="button"
              className="text-xs text-teal-600 hover:text-teal-800"
              onClick={() => setNotice(null)}
            >
              关闭
            </button>
          </div>
        )}

        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          <StatCard label="待重练" value={summary?.pendingCount ?? 0} tone="amber" />
          <StatCard label="今日待复习" value={summary?.dueCount ?? 0} tone="rose" />
          <StatCard label="已掌握" value={summary?.masteredCount ?? 0} tone="teal" />
          <StatCard label="累计错题" value={summary?.total ?? 0} tone="slate" />
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-2">
          <div className="flex flex-wrap gap-1.5">
            {TABS.map((item) => (
              <button
                key={item.key}
                type="button"
                onClick={() => switchTab(item.key)}
                className={`rounded-full border px-2.5 py-1 text-xs transition ${
                  tab === item.key
                    ? 'border-teal-300 bg-teal-50 font-medium text-teal-700'
                    : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300'
                }`}
              >
                {item.label}
              </button>
            ))}
          </div>

          {hasFilter && (
            <div className="flex flex-wrap items-center gap-1.5">
              {(kpId || kpName) && (
                <FilterChip
                  label={`知识点：${kpName || '已选知识点'}`}
                  onRemove={() =>
                    updateParams((params) => {
                      params.delete('kpId')
                      params.delete('kp')
                    })
                  }
                />
              )}
              {sessionId && (
                <FilterChip
                  label="仅看本场面试"
                  onRemove={() =>
                    updateParams((params) => {
                      params.delete('sessionId')
                    })
                  }
                />
              )}
              <button
                type="button"
                onClick={() =>
                  updateParams((params) => {
                    params.delete('kpId')
                    params.delete('kp')
                    params.delete('sessionId')
                  })
                }
                className="flex items-center gap-1 rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs text-slate-500 transition hover:border-slate-300"
              >
                <RefreshCw size={11} />
                清除筛选
              </button>
            </div>
          )}
        </div>

        {loading && !data ? (
          <div className="mt-8 flex items-center justify-center gap-2 text-xs text-slate-400">
            <Loader2 size={14} className="animate-spin" />
            加载中…
          </div>
        ) : summary && summary.total === 0 ? (
          <EmptyState />
        ) : items.length === 0 ? (
          <FilterEmptyState tab={tab} hasFilter={hasFilter} />
        ) : (
          <div className="mt-3 space-y-2">
            {loading && (
              <div className="flex items-center gap-2 text-xs text-slate-400">
                <Loader2 size={12} className="animate-spin" />
                刷新中…
              </div>
            )}
            {items.map((item) => (
              <WrongQuestionCard
                key={item.id}
                item={item}
                onRetry={() =>
                  updateParams((params) => {
                    params.set('open', item.id)
                  })
                }
              />
            ))}
          </div>
        )}
      </div>

      {openId && (
        <RetryDialog wrongId={openId} onClose={closeDialog} onUpdated={handleUpdated} />
      )}
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

function FilterChip({ label, onRemove }: { label: string; onRemove: () => void }) {
  return (
    <span className="flex items-center gap-1 rounded-full border border-teal-200 bg-teal-50 px-2.5 py-1 text-xs text-teal-700">
      {label}
      <button type="button" onClick={onRemove} className="text-teal-500 hover:text-teal-700">
        <X size={11} />
      </button>
    </span>
  )
}

function WrongQuestionCard({ item, onRetry }: { item: WrongQuestionItem; onRetry: () => void }) {
  const mastered = item.status === 'MASTERED'
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-4 py-3">
      <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div className="min-w-0 flex-1">
          <p className="line-clamp-2 text-sm leading-6 text-slate-800">{item.questionPreview}</p>

          <div className="mt-2 flex flex-wrap items-center gap-1.5 text-[11px]">
            <span
              className={`rounded-full px-2 py-0.5 ${
                mastered ? 'bg-teal-50 text-teal-700' : 'bg-amber-50 text-amber-700'
              }`}
            >
              {mastered ? '已掌握' : '待重练'}
            </span>
            {item.knowledgePoints.map((point) => (
              <span key={point} className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">
                {point}
              </span>
            ))}
            <span className="text-slate-400">原评分 {item.originalScore ?? '—'}/5</span>
            <span className="text-slate-400">重练 {item.retryCount} 次</span>
            {item.bestScore != null && <span className="text-slate-400">最高 {item.bestScore}</span>}
            {item.lastRetryAt && <span className="text-slate-400">最近 {item.lastRetryAt.slice(0, 10)}</span>}
            {!mastered && (
              <span className={dueLabel(item) === '今天该练了' ? 'text-rose-500' : 'text-slate-400'}>
                {dueLabel(item)}
              </span>
            )}
          </div>
        </div>

        <button
          type="button"
          onClick={onRetry}
          className={`shrink-0 self-start rounded-lg px-3 py-1.5 text-xs font-medium transition ${
            mastered
              ? 'border border-teal-200 text-teal-700 hover:bg-teal-50'
              : 'bg-teal-600 text-white hover:bg-teal-700'
          }`}
        >
          {mastered ? '再练一次' : '重练'}
        </button>
      </div>
    </div>
  )
}

function EmptyState() {
  return (
    <div className="mt-8 rounded-xl border-2 border-dashed border-slate-200 bg-white px-6 py-12 text-center">
      <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-teal-50 text-teal-600">
        <BookX size={22} />
      </span>
      <p className="mt-4 text-sm font-medium text-slate-700">还没有错题</p>
      <p className="mt-1 text-xs text-slate-400">在 AI 面试里答得不够好的题，会自动收进这里。</p>
      <Link
        to="/interview"
        className="mt-5 inline-block rounded-lg bg-teal-600 px-3.5 py-2 text-sm font-medium text-white transition hover:bg-teal-700"
      >
        去面试
      </Link>
    </div>
  )
}

function FilterEmptyState({ tab, hasFilter }: { tab: TabKey; hasFilter: boolean }) {
  const title = hasFilter
    ? '这个筛选条件下还没有错题'
    : tab === 'MASTERED'
      ? '还没有已掌握的错题'
      : '这个分类下暂时没有错题'
  return (
    <div className="mt-8 rounded-xl border-2 border-dashed border-slate-200 bg-white px-6 py-12 text-center">
      <p className="text-sm font-medium text-slate-700">{title}</p>
      <p className="mt-1 text-xs text-slate-400">
        {hasFilter ? '换个条件试试，或清除筛选查看全部错题。' : '继续重练，答到 3 分以上就会进入已掌握。'}
      </p>
    </div>
  )
}

/** 重练弹窗:提交前隐藏原回答和参考要点,提交后展示评分与历史 */
function RetryDialog({
  wrongId,
  onClose,
  onUpdated,
}: {
  wrongId: string
  onClose: () => void
  onUpdated: (result: RetryResult) => void
}) {
  const [detail, setDetail] = useState<WrongQuestionDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [answer, setAnswer] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<RetryResult | null>(null)
  const [showOriginal, setShowOriginal] = useState(false)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    getWrongQuestionDetail(wrongId)
      .then((data) => {
        if (!cancelled) setDetail(data)
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
  }, [wrongId])

  async function submit() {
    const text = answer.trim()
    if (!detail || text.length < 10 || submitting) return

    setSubmitting(true)
    setError(null)
    try {
      const retried = await retryWrongQuestion(wrongId, text)
      setResult(retried)
      onUpdated(retried)
      try {
        const fresh = await getWrongQuestionDetail(wrongId)
        setDetail(fresh)
        setShowOriginal(false)
      } catch {
        // 详情刷新失败不影响本次评分展示
      }
    } catch (e) {
      // 失败保留已输入内容,便于重试
      setError(errMessage(e))
    } finally {
      setSubmitting(false)
    }
  }

  function again() {
    setResult(null)
    setAnswer('')
    setError(null)
    setTimeout(() => textareaRef.current?.focus(), 0)
  }

  return (
    <div
      className="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/40 p-3 md:p-4"
      onClick={onClose}
    >
      <div
        className="max-h-[88vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-4 shadow-xl md:p-5"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="flex items-center gap-1.5 text-base font-semibold text-slate-800">
              <Sparkles size={16} className="text-teal-600" />
              重练这道题
            </h2>
            <p className="mt-1 text-xs text-slate-400">参考要点由 AI 生成，仅供复习参考</p>
          </div>
          <button type="button" onClick={onClose} className="rounded p-1 text-slate-400 hover:bg-slate-100">
            <X size={16} />
          </button>
        </div>

        {loading ? (
          <div className="flex h-40 items-center justify-center">
            <Loader2 size={20} className="animate-spin text-teal-600" />
          </div>
        ) : !detail ? (
          <div className="mt-6 flex flex-col items-center gap-3 text-center">
            <AlertCircle size={20} className="text-rose-500" />
            <p className="text-sm text-slate-600">{error || '错题不存在或已删除'}</p>
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-600"
            >
              关闭
            </button>
          </div>
        ) : (
          <>
            <div className="mt-3 flex flex-wrap items-center gap-1.5 text-[11px]">
              <span
                className={`rounded-full px-2 py-0.5 ${
                  detail.status === 'MASTERED' ? 'bg-teal-50 text-teal-700' : 'bg-amber-50 text-amber-700'
                }`}
              >
                {detail.status === 'MASTERED' ? '已掌握' : '待重练'}
              </span>
              <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">
                原评分 {detail.originalScore ?? '—'}/5
              </span>
              {detail.position && (
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">{detail.position}</span>
              )}
              {detail.round && (
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">{detail.round}</span>
              )}
              {detail.knowledgePoints.map((point) => (
                <span key={point} className="rounded-full bg-teal-50 px-2 py-0.5 text-teal-700">
                  {point}
                </span>
              ))}
            </div>

            <div className="mt-3 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
              <p className="whitespace-pre-wrap text-sm leading-6 text-slate-800">{detail.questionContent}</p>
              {detail.questionContext && (
                <div className="mt-2 border-t border-slate-200 pt-2">
                  <p className="text-[11px] text-slate-400">当时的追问</p>
                  <p className="mt-0.5 whitespace-pre-wrap text-xs leading-5 text-slate-600">
                    {detail.questionContext}
                  </p>
                </div>
              )}
            </div>

            {(detail.originalComment || detail.originalSuggestion) && (
              <div className="mt-2 rounded-xl border border-slate-200 px-4 py-2.5 text-xs leading-5 text-slate-600">
                {detail.originalComment && <p>原点评：{detail.originalComment}</p>}
                {detail.originalSuggestion && (
                  <p className="mt-1 text-slate-500">原建议：{detail.originalSuggestion}</p>
                )}
              </div>
            )}

            {error && (
              <div className="mt-3 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
                <AlertCircle size={14} className="mt-0.5 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            {!result ? (
              <div className="mt-3">
                <p className="text-[11px] text-slate-400">
                  提交后可查看原回答与参考要点，先自己写一遍。
                </p>
                <textarea
                  ref={textareaRef}
                  value={answer}
                  maxLength={4000}
                  onChange={(e) => setAnswer(e.target.value)}
                  onKeyDown={(e) => {
                    if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
                      e.preventDefault()
                      void submit()
                    }
                  }}
                  rows={7}
                  className="mt-1.5 w-full resize-y rounded-xl border border-slate-200 px-3 py-2 text-sm leading-6 text-slate-700 outline-none transition focus:border-teal-400"
                  placeholder="按自己的理解完整回答一遍…"
                />
                <div className="mt-2 flex items-center justify-between gap-2">
                  <span className="text-[11px] text-slate-400">
                    至少 10 字 · Ctrl / ⌘ + Enter 提交
                  </span>
                  <button
                    type="button"
                    onClick={() => void submit()}
                    disabled={answer.trim().length < 10 || submitting}
                    className="flex items-center gap-1.5 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-teal-700 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
                  >
                    {submitting ? <Loader2 size={14} className="animate-spin" /> : null}
                    {submitting ? 'AI 批改中…' : '提交重练'}
                  </button>
                </div>
              </div>
            ) : (
              <div className="mt-3 space-y-3">
                <div className="flex items-center gap-3 rounded-xl border border-teal-200 bg-teal-50 px-4 py-3">
                  <span className="text-2xl font-semibold text-teal-700">{result.score}</span>
                  <span className="text-xs text-teal-700">/ 5</span>
                  <span
                    className={`ml-auto rounded-full px-2 py-0.5 text-[11px] ${
                      result.status === 'MASTERED'
                        ? 'bg-teal-600 text-white'
                        : 'bg-amber-100 text-amber-700'
                    }`}
                  >
                    {result.status === 'MASTERED' ? '已掌握' : '待重练'}
                  </span>
                </div>

                {result.comment && (
                  <p className="text-sm leading-6 text-slate-700">点评：{result.comment}</p>
                )}
                {result.suggestion && (
                  <p className="text-sm leading-6 text-slate-600">建议：{result.suggestion}</p>
                )}

                <div className="rounded-xl border border-slate-200 px-4 py-3">
                  <p className="text-xs font-medium text-slate-700">参考要点</p>
                  <p className="mt-1 whitespace-pre-wrap text-xs leading-6 text-slate-600">
                    {result.referenceAnswer}
                  </p>
                  <p className="mt-2 text-[10px] text-slate-400">AI 生成，仅供复习参考</p>
                </div>

                {detail.originalAnswer && (
                  <div className="rounded-xl border border-slate-200">
                    <button
                      type="button"
                      onClick={() => setShowOriginal((prev) => !prev)}
                      className="flex w-full items-center justify-between px-4 py-2.5 text-xs text-slate-600"
                    >
                      <span>原回答</span>
                      {showOriginal ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                    </button>
                    {showOriginal && (
                      <p className="whitespace-pre-wrap border-t border-slate-100 px-4 py-2.5 text-xs leading-6 text-slate-500">
                        {detail.originalAnswer}
                      </p>
                    )}
                  </div>
                )}

                {detail.retries.length > 0 && (
                  <div className="rounded-xl border border-slate-200 px-4 py-3">
                    <p className="text-xs font-medium text-slate-700">重练历史（最近 3 次）</p>
                    <div className="mt-2 space-y-2">
                      {detail.retries.slice(0, 3).map((retry) => (
                        <div key={retry.id} className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-600">
                          <div className="flex items-center justify-between">
                            <span className="font-medium text-slate-700">{retry.score} / 5</span>
                            <span className="text-[11px] text-slate-400">{retry.createdAt ?? ''}</span>
                          </div>
                          {retry.comment && <p className="mt-1 leading-5">{retry.comment}</p>}
                          {retry.referenceAnswer && (
                            <details className="mt-1">
                              <summary className="cursor-pointer text-[11px] text-teal-700">查看参考要点</summary>
                              <p className="mt-1 whitespace-pre-wrap leading-5 text-slate-500">
                                {retry.referenceAnswer}
                              </p>
                            </details>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                <div className="flex justify-end gap-2">
                  <button
                    type="button"
                    onClick={onClose}
                    className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-600 transition hover:bg-slate-50"
                  >
                    关闭
                  </button>
                  <button
                    type="button"
                    onClick={again}
                    className="rounded-lg bg-teal-600 px-3 py-1.5 text-sm font-medium text-white transition hover:bg-teal-700"
                  >
                    再练一次
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}

function dueLabel(item: WrongQuestionItem): string {
  if (!item.nextReviewAt) return ''
  if (item.status === 'PENDING' && toTime(item.nextReviewAt) <= Date.now()) {
    return '今天该练了'
  }
  return `下次复习 ${item.nextReviewAt.slice(0, 10)}`
}

function toTime(value: string): number {
  const time = new Date(value.replace(' ', 'T')).getTime()
  return Number.isNaN(time) ? 0 : time
}


export default WrongQuestionsPage
