import { errMessage } from '../utils/error'
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  Clock,
  Loader2,
  Send,
  Shuffle,
  Sparkles,
  Trash2,
} from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  getQuestionDetail,
  getQuestionNeighbors,
  getRandomQuestionId,
  submitQuestionAnswer,
  type QuestionAttemptResult,
  type QuestionAttemptView,
  type QuestionBankDetail,
  type QuestionNeighbors,
} from '../api/questionBank'
import MarkdownContent from '../components/MarkdownContent'
import CodingWorkspace from './CodingWorkspace'
import { StatusBadge, difficultyClass, difficultyLabel } from './QuestionBankPage'

const TYPE_LABELS: Record<string, string> = {
  BASICS: '八股',
  SCENARIO: '场景',
  PROJECT: '项目',
  CODE: '编程',
}

const MAX_ANSWER_LENGTH = 4000

function draftKey(id: string) {
  return `grindset:question-draft:${id}`
}

/**
 * 题库题目详情:看题、作答、AI 评分结果与历史作答。
 * 题干、参考答案和点评统一走 Markdown;桌面端有面包屑和上一题/下一题导航,
 * 作答草稿按题目自动保存在本地,刷新不丢。
 */
function QuestionDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const [detail, setDetail] = useState<QuestionBankDetail | null>(null)
  const [neighbors, setNeighbors] = useState<QuestionNeighbors | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [answer, setAnswer] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [result, setResult] = useState<QuestionAttemptResult | null>(null)
  const [showReference, setShowReference] = useState(false)
  const [randomizing, setRandomizing] = useState(false)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const skipSaveRef = useRef(true)

  useEffect(() => {
    if (!id) return
    let cancelled = false
    skipSaveRef.current = true
    setLoading(true)
    setLoadError(null)
    setResult(null)
    setSubmitError(null)
    setShowReference(false)
    setNeighbors(null)
    setAnswer(localStorage.getItem(draftKey(id)) ?? '')

    Promise.all([
      getQuestionDetail(id),
      getQuestionNeighbors(id).catch(() => null),
    ])
      .then(([data, neighbor]) => {
        if (cancelled) return
        setDetail(data)
        setNeighbors(neighbor)
      })
      .catch((e: unknown) => {
        if (!cancelled) setLoadError(errMessage(e))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    // 等恢复草稿的这次渲染结束后再开启自动保存,避免空值把草稿清掉
    const timer = window.setTimeout(() => {
      skipSaveRef.current = false
    }, 0)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [id])

  useEffect(() => {
    if (!id || skipSaveRef.current) return
    const timer = window.setTimeout(() => {
      if (answer.trim()) {
        localStorage.setItem(draftKey(id), answer)
      } else {
        localStorage.removeItem(draftKey(id))
      }
    }, 400)
    return () => window.clearTimeout(timer)
  }, [answer, id])

  async function submit() {
    if (!id) return
    const text = answer.trim()
    if (text.length < 10 || submitting) return

    setSubmitting(true)
    setSubmitError(null)
    try {
      const submitted = await submitQuestionAnswer(id, text)
      setResult(submitted)
      setShowReference(true)
      setAnswer('')
      localStorage.removeItem(draftKey(id))
      // 刷新详情以更新做题进度和历史作答
      try {
        setDetail(await getQuestionDetail(id))
      } catch {
        // 详情刷新失败不影响本次评分展示
      }
    } catch (e) {
      setSubmitError(errMessage(e))
    } finally {
      setSubmitting(false)
    }
  }

  function again() {
    setResult(null)
    setAnswer('')
    setSubmitError(null)
    setShowReference(false)
    if (id) localStorage.removeItem(draftKey(id))
    setTimeout(() => textareaRef.current?.focus(), 0)
  }

  async function openRandom() {
    if (randomizing) return
    setRandomizing(true)
    try {
      navigate(`/questions/${await getRandomQuestionId(id)}`)
    } catch (e) {
      setLoadError(errMessage(e))
    } finally {
      setRandomizing(false)
    }
  }

  // 编程题走独立的工作台（编辑器 + 判题），这里只负责加载与分流
  if (!loading && !loadError && detail?.judgeMode === 'CODING') {
    return <CodingWorkspace questionId={detail.id} />
  }

  return (
    <div className="flex h-full flex-col overflow-y-auto bg-slate-50">
      <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/95 px-4 py-2.5 backdrop-blur md:px-6">
        <div className="mx-auto flex max-w-4xl items-center gap-3">
          <Link
            to="/questions"
            className="flex shrink-0 items-center gap-1 rounded-lg px-2 py-1 text-xs text-slate-500 transition hover:bg-slate-100 hover:text-slate-700"
          >
            <ArrowLeft size={14} />
            题库
          </Link>
          <nav className="min-w-0 flex-1 truncate text-xs text-slate-400">
            {detail ? (
              <span>
                {detail.domain} · 第 {detail.seq} 题
              </span>
            ) : (
              <span>题目详情</span>
            )}
          </nav>
          <button
            type="button"
            onClick={() => void openRandom()}
            disabled={randomizing}
            className="flex shrink-0 items-center gap-1.5 rounded-lg border border-teal-200 bg-teal-50 px-2.5 py-1 text-xs font-medium text-teal-700 transition hover:border-teal-300 hover:bg-teal-100 disabled:opacity-60"
          >
            {randomizing ? <Loader2 size={13} className="animate-spin" /> : <Shuffle size={13} />}
            随机一题
          </button>
        </div>
      </header>

      <div className="flex-1 px-4 py-4 md:px-6">
        <div className="mx-auto max-w-4xl">
          {loading ? (
            <div className="mt-8 flex items-center justify-center gap-2 text-xs text-slate-400">
              <Loader2 size={14} className="animate-spin" />
              加载中…
            </div>
          ) : loadError || !detail ? (
            <div className="mt-8 flex flex-col items-center gap-3 text-center">
              <AlertCircle size={22} className="text-rose-500" />
              <p className="text-sm text-slate-600">{loadError || '题目不存在或已下架'}</p>
              <Link
                to="/questions"
                className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-600"
              >
                返回题库
              </Link>
            </div>
          ) : (
            <>
              <article className="overflow-hidden rounded-xl border border-slate-200 bg-white">
                <div className="border-b border-slate-100 px-4 py-4 md:px-5">
                  <h1 className="text-lg font-semibold leading-7 text-slate-800">{detail.title}</h1>
                  <div className="mt-2 flex flex-wrap items-center gap-1.5 text-xs">
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">
                      {TYPE_LABELS[detail.type] ?? detail.type}
                    </span>
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">
                      {detail.domain}
                    </span>
                    <span className={`rounded-full px-2 py-0.5 ${difficultyClass(detail.difficulty)}`}>
                      {difficultyLabel(detail.difficulty)}
                    </span>
                    <StatusBadge status={detail.status} />
                    {detail.knowledgePoints.map((point) => (
                      <span key={point} className="rounded-full bg-teal-50 px-2 py-0.5 text-teal-700">
                        {point}
                      </span>
                    ))}
                  </div>
                  <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-400">
                    <span>已作答 {detail.attemptCount} 次</span>
                    {detail.bestScore != null && <span>最高 {detail.bestScore} 分</span>}
                    {detail.lastScore != null && <span>上次 {detail.lastScore} 分</span>}
                    {detail.lastAttemptAt && <span>最近 {detail.lastAttemptAt}</span>}
                  </div>
                </div>
                <div className="px-4 py-4 md:px-5">
                  <MarkdownContent content={detail.content} />
                </div>
              </article>

              {result ? (
                <div className="mt-4 space-y-3">
                  <section className="rounded-xl border border-slate-200 bg-white px-4 py-4 md:px-5">
                    <div className="flex items-center gap-3">
                      <span className={`text-4xl font-semibold leading-none ${scoreClass(result.score)}`}>
                        {result.score}
                      </span>
                      <span className="text-xs text-slate-400">/ 5 分</span>
                      <span
                        className={`ml-auto rounded-full px-2 py-0.5 text-xs ${
                          result.status === 'WRONG'
                            ? 'bg-rose-50 text-rose-600'
                            : 'bg-teal-50 text-teal-700'
                        }`}
                      >
                        {result.status === 'WRONG' ? '已收入错题本' : '已掌握'}
                      </span>
                    </div>

                    {result.comment && (
                      <div className="mt-3">
                        <p className="text-xs font-medium text-slate-500">点评</p>
                        <MarkdownContent content={result.comment} className="mt-0.5" />
                      </div>
                    )}
                    {result.suggestion && (
                      <div className="mt-2">
                        <p className="text-xs font-medium text-slate-500">建议</p>
                        <MarkdownContent content={result.suggestion} className="mt-0.5" />
                      </div>
                    )}

                    <div className="mt-3 grid gap-3 md:grid-cols-2">
                      <PointList
                        title="命中的要点"
                        points={result.hitPoints}
                        tone="teal"
                        empty="这次没有识别到明显命中的要点"
                      />
                      <PointList
                        title="遗漏的要点"
                        points={result.missedPoints}
                        tone="amber"
                        empty="没有明显遗漏，继续保持"
                      />
                    </div>
                  </section>

                  <ReferenceCard
                    referenceAnswer={result.referenceAnswer}
                    keyPoints={result.keyPoints}
                    open={showReference}
                    onToggle={() => setShowReference((prev) => !prev)}
                  />

                  <div className="flex justify-end gap-2">
                    <Link
                      to="/questions"
                      className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-600 transition hover:bg-slate-50"
                    >
                      返回题库
                    </Link>
                    <button
                      type="button"
                      onClick={again}
                      className="rounded-lg bg-teal-600 px-3.5 py-1.5 text-sm font-medium text-white transition hover:bg-teal-700"
                    >
                      再答一次
                    </button>
                  </div>
                </div>
              ) : (
                <section className="mt-4 overflow-hidden rounded-xl border border-slate-200 bg-white">
                  {detail.referenceAnswer && (
                    <div className="px-4 pt-4 md:px-5">
                      <ReferenceCard
                        referenceAnswer={detail.referenceAnswer}
                        keyPoints={detail.keyPoints}
                        open={showReference}
                        onToggle={() => setShowReference((prev) => !prev)}
                      />
                    </div>
                  )}

                  <div className="flex items-center justify-between px-4 py-3 md:px-5">
                    <p className="flex items-center gap-1.5 text-sm font-medium text-slate-700">
                      <Sparkles size={15} className="text-teal-600" />
                      我的回答
                    </p>
                    <span className="text-xs tabular-nums text-slate-400">
                      {answer.length} / {MAX_ANSWER_LENGTH}
                    </span>
                  </div>

                  <div className="px-4 pb-4 md:px-5">
                    <p className="text-xs text-slate-400">
                      按自己的理解写完整，提交后 AI 对照参考答案给出评分和命中/遗漏要点。
                    </p>

                    {submitError && (
                      <div className="mt-2 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
                        <AlertCircle size={14} className="mt-0.5 shrink-0" />
                        <span>{submitError}</span>
                      </div>
                    )}

                    <textarea
                      ref={textareaRef}
                      value={answer}
                      maxLength={MAX_ANSWER_LENGTH}
                      onChange={(e) => setAnswer(e.target.value)}
                      onKeyDown={(e) => {
                        if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
                          e.preventDefault()
                          void submit()
                        }
                      }}
                      rows={10}
                      className="mt-2 w-full resize-y rounded-xl border border-slate-200 px-3 py-2.5 text-sm leading-6 text-slate-700 outline-none transition focus:border-teal-400"
                      placeholder="先讲核心结论，再补充原理和细节…"
                    />

                    <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
                      <span className="text-xs text-slate-400">
                        至少 10 字 · Ctrl / ⌘ + Enter 提交 · 草稿自动保存
                      </span>
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => {
                            setAnswer('')
                            if (id) localStorage.removeItem(draftKey(id))
                            textareaRef.current?.focus()
                          }}
                          disabled={!answer}
                          className="flex items-center gap-1 rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs text-slate-500 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-300"
                        >
                          <Trash2 size={13} />
                          清空
                        </button>
                        <button
                          type="button"
                          onClick={() => void submit()}
                          disabled={answer.trim().length < 10 || submitting}
                          className="flex items-center gap-1.5 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-teal-700 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
                        >
                          {submitting ? (
                            <Loader2 size={14} className="animate-spin" />
                          ) : (
                            <Send size={14} />
                          )}
                          {submitting ? 'AI 批改中…' : '提交作答'}
                        </button>
                      </div>
                    </div>
                  </div>
                </section>
              )}

              {detail.attempts.length > 0 && (
                <section className="mt-4 rounded-xl border border-slate-200 bg-white px-4 py-4 md:px-5">
                  <p className="flex items-center gap-1.5 text-sm font-medium text-slate-700">
                    <Clock size={15} className="text-slate-400" />
                    历史作答（最近 {detail.attempts.length} 次）
                  </p>
                  <div className="mt-2 space-y-2">
                    {detail.attempts.map((attempt) => (
                      <AttemptCard key={attempt.id} attempt={attempt} />
                    ))}
                  </div>
                </section>
              )}

              <nav className="mt-4 flex items-center justify-between gap-2">
                <button
                  type="button"
                  onClick={() => {
                    if (neighbors?.prevId) navigate(`/questions/${neighbors.prevId}`)
                  }}
                  disabled={!neighbors?.prevId}
                  className="flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-300"
                >
                  <ChevronLeft size={15} />
                  上一题
                </button>
                <button
                  type="button"
                  onClick={() => void openRandom()}
                  className="flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-500 transition hover:bg-slate-50"
                >
                  <Shuffle size={14} />
                  随机一题
                </button>
                <button
                  type="button"
                  onClick={() => {
                    if (neighbors?.nextId) navigate(`/questions/${neighbors.nextId}`)
                  }}
                  disabled={!neighbors?.nextId}
                  className="flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-300"
                >
                  下一题
                  <ChevronRight size={15} />
                </button>
              </nav>
            </>
          )}
        </div>
      </div>
    </div>
  )
}

function AttemptCard({ attempt }: { attempt: QuestionAttemptView }) {
  return (
    <details className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
      <summary className="flex cursor-pointer list-none items-center gap-2 text-xs text-slate-600">
        <span className={`font-semibold ${scoreClass(attempt.score)}`}>{attempt.score} / 5</span>
        <span className="text-xs text-slate-400">{attempt.createdAt ?? ''}</span>
        {attempt.comment && (
          <span className="min-w-0 flex-1 truncate text-xs text-slate-500">{attempt.comment}</span>
        )}
      </summary>
      <div className="mt-2 space-y-2 border-t border-slate-200 pt-2">
        <div>
          <p className="text-xs text-slate-400">我的回答</p>
          <p className="mt-0.5 whitespace-pre-wrap text-xs leading-5 text-slate-600">{attempt.answer}</p>
        </div>
        {attempt.hitPoints.length > 0 && (
          <PointLine title="命中" points={attempt.hitPoints} tone="teal" />
        )}
        {attempt.missedPoints.length > 0 && (
          <PointLine title="遗漏" points={attempt.missedPoints} tone="amber" />
        )}
        {attempt.suggestion && (
          <p className="text-xs leading-5 text-slate-500">建议：{attempt.suggestion}</p>
        )}
      </div>
    </details>
  )
}

function PointList({
  title,
  points,
  tone,
  empty,
}: {
  title: string
  points: string[]
  tone: 'teal' | 'amber'
  empty: string
}) {
  const toneClass = tone === 'teal' ? 'border-teal-100 bg-teal-50/60' : 'border-amber-100 bg-amber-50/60'
  const iconClass = tone === 'teal' ? 'text-teal-600' : 'text-amber-600'
  return (
    <div className={`rounded-lg border px-3 py-2 ${toneClass}`}>
      <p className="flex items-center gap-1 text-xs font-medium text-slate-600">
        {tone === 'teal' ? (
          <CheckCircle2 size={12} className={iconClass} />
        ) : (
          <AlertCircle size={12} className={iconClass} />
        )}
        {title}
      </p>
      {points.length === 0 ? (
        <p className="mt-1 text-xs text-slate-400">{empty}</p>
      ) : (
        <ul className="mt-1 space-y-1">
          {points.map((point) => (
            <li key={point} className="flex gap-1 text-xs leading-5 text-slate-600">
              <span className={iconClass}>·</span>
              {point}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function PointLine({ title, points, tone }: { title: string; points: string[]; tone: 'teal' | 'amber' }) {
  return (
    <p className="text-xs leading-5 text-slate-500">
      <span className={tone === 'teal' ? 'text-teal-600' : 'text-amber-600'}>{title}：</span>
      {points.join('；')}
    </p>
  )
}

function ReferenceCard({
  referenceAnswer,
  keyPoints,
  open,
  onToggle,
}: {
  referenceAnswer: string | null
  keyPoints: string[]
  open: boolean
  onToggle: () => void
}) {
  if (!referenceAnswer && keyPoints.length === 0) {
    return null
  }
  return (
    <div className="rounded-xl border border-slate-200 bg-white">
      <button
        type="button"
        onClick={onToggle}
        className="flex w-full items-center justify-between px-4 py-2.5 text-sm font-medium text-slate-600"
      >
        <span className="flex items-center gap-1.5">
          <Sparkles size={14} className="text-teal-600" />
          参考答案与关键要点
        </span>
        {open ? <ChevronUp size={15} /> : <ChevronDown size={15} />}
      </button>
      {open && (
        <div className="border-t border-slate-100 px-4 py-3">
          {keyPoints.length > 0 && (
            <div className="mb-2 flex flex-wrap gap-1">
              {keyPoints.map((point) => (
                <span key={point} className="rounded bg-teal-50 px-2 py-0.5 text-xs text-teal-700">
                  {point}
                </span>
              ))}
            </div>
          )}
          {referenceAnswer && <MarkdownContent content={referenceAnswer} />}
          <p className="mt-2 text-[11px] text-slate-400">AI 生成，仅供复习参考</p>
        </div>
      )}
    </div>
  )
}

function scoreClass(score: number): string {
  if (score >= 4) return 'text-teal-700'
  if (score === 3) return 'text-amber-600'
  return 'text-rose-600'
}

export default QuestionDetailPage
