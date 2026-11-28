import { ArrowLeft, CheckCircle2, ChevronRight, Loader2, Play, Volume2 } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  getTrainingSession,
  submitTrainingAnswer,
  type SimilarQuestion,
  type TrainingAnswerResult,
  type TrainingItem,
  type TrainingSessionDetail,
} from '../../api/training'
import MarkdownContent from '../../components/MarkdownContent'
import VoiceInputButton from '../../components/VoiceInputButton'
import { useInterviewerVoice } from '../../hooks/useInterviewerVoice'
import { useVoiceInput } from '../../hooks/useVoiceInput'

const STATUS_COMPLETED = 2

const STAGE_LABELS: Record<string, string> = {
  BASELINE: '基线测评',
  DAILY: '今日训练',
  RETEST: '阶段复测',
  MISTAKE: '错题重练',
}

const QUESTION_TYPE_LABELS: Record<string, string> = {
  CONCEPT: '概念解释',
  PRINCIPLE: '原理分析',
  CODING: '编程代码',
  DESIGN: '场景设计',
  ESSAY: '综合论述',
}

function firstUndone(items: TrainingItem[]): TrainingItem | null {
  return items.find((item) => item.status === 0) ?? items[items.length - 1] ?? null
}

/** 沉浸式弱项训练：逐题作答、语音输入、TTS 点评、同类题推送。 */
function TrainingSessionPage() {
  const { sessionId } = useParams<{ sessionId: string }>()
  const [session, setSession] = useState<TrainingSessionDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [answer, setAnswer] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState('')
  const [lastResult, setLastResult] = useState<TrainingAnswerResult | null>(null)

  const voiceBaseRef = useRef('')
  const voice = useVoiceInput({
    maxSeconds: 180,
    onPartial: (text) => setAnswer((voiceBaseRef.current + text).slice(0, 4000)),
    onFinal: (text) => setAnswer((voiceBaseRef.current + text).slice(0, 4000)),
    onError: (message) => setSubmitError(message),
  })
  const tts = useInterviewerVoice({ onError: () => undefined })

  useEffect(() => {
    if (!sessionId) return
    let cancelled = false
    setLoading(true)
    setLoadError('')
    getTrainingSession(sessionId)
      .then((data) => {
        if (cancelled) return
        setSession(data)
        const first = firstUndone(data.items)
        setSelectedId(first?.id ?? null)
        setAnswer('')
        setLastResult(null)
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setLoadError(error instanceof Error ? error.message : '加载训练失败')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [sessionId])

  const current = useMemo(
    () => session?.items.find((item) => item.id === selectedId) ?? null,
    [session, selectedId],
  )
  const completed = session?.status === STATUS_COMPLETED
  const progress = session && session.itemCount > 0
    ? Math.round((session.completedCount / session.itemCount) * 100)
    : 0

  function selectItem(item: TrainingItem) {
    voice.cancel()
    setSelectedId(item.id)
    setAnswer('')
    setSubmitError('')
    setLastResult(null)
  }

  async function handleSubmit() {
    if (!session || !current || submitting) return
    const text = answer.trim()
    if (text.length < 10) {
      setSubmitError('再想想，至少写 10 个字')
      return
    }
    setSubmitting(true)
    setSubmitError('')
    try {
      const result = await submitTrainingAnswer(session.id, current.id, text)
      setLastResult(result)
      const fresh = await getTrainingSession(session.id)
      setSession(fresh)
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : '提交失败，请重试')
    } finally {
      setSubmitting(false)
    }
  }

  function handleNext() {
    if (!session || !current) return
    const index = session.items.findIndex((item) => item.id === current.id)
    const next = session.items.slice(index + 1).find((item) => item.status === 0)
      ?? session.items.find((item) => item.status === 0)
    if (next) selectItem(next)
  }

  function toggleVoice() {
    if (voice.state === 'recording') {
      void voice.stop()
      return
    }
    voiceBaseRef.current = answer
    void voice.start()
  }

  function speakFeedback() {
    const summary = lastResult?.summary || current?.summary
      || lastResult?.comment || current?.comment
    const improvement = lastResult?.improvements?.[0] || current?.improvements?.[0]
      || lastResult?.suggestion || current?.suggestion
    const text = [summary, improvement].filter(Boolean).join('。')
    if (text) void tts.speak(text)
  }

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center bg-slate-50">
        <Loader2 size={20} className="animate-spin text-brand-600" />
      </div>
    )
  }

  if (loadError || !session) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 bg-slate-50 px-6 text-center">
        <p className="text-sm text-slate-500">{loadError || '训练不存在'}</p>
        <Link to="/training" className="text-sm font-medium text-brand-600 hover:text-brand-700">
          返回弱项训练
        </Link>
      </div>
    )
  }

  return (
    <div className="h-full overflow-y-auto bg-slate-50">
      <div className="mx-auto max-w-3xl px-4 py-6">
        <div className="mb-4 flex items-center gap-2">
          <Link
            to="/training"
            className="rounded-lg p-1.5 text-slate-400 transition hover:bg-white hover:text-slate-600"
            aria-label="返回"
          >
            <ArrowLeft size={16} />
          </Link>
          <div className="min-w-0">
            <h1 className="truncate text-base font-semibold text-slate-800">{session.title}</h1>
            <p className="mt-0.5 text-[11px] text-slate-400">
              {STAGE_LABELS[session.stage] ?? session.stage}
              {completed && session.avgScore != null ? ` · 平均 ${session.avgScore}/5` : ''}
            </p>
          </div>
          <span className="ml-auto rounded-full bg-white px-2.5 py-1 text-[11px] font-medium text-brand-700 shadow-sm">
            {session.completedCount} / {session.itemCount}
          </span>
        </div>

        <div className="mb-4 h-1.5 overflow-hidden rounded-full bg-slate-200">
          <div
            className="h-full rounded-full bg-brand-500 transition-all"
            style={{ width: `${Math.max(0, Math.min(100, progress))}%` }}
          />
        </div>

        <div className="mb-4 flex flex-wrap gap-1.5">
          {session.items.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => selectItem(item)}
              className={`rounded-full px-2.5 py-1 text-[11px] transition ${
                item.id === selectedId
                  ? 'bg-brand-600 text-white'
                  : item.status === 1
                    ? 'bg-white text-slate-500 hover:text-slate-700'
                    : 'bg-white text-slate-400 hover:text-slate-600'
              }`}
            >
              {item.seq}. {item.kpName}
              {item.role === 'RETEST' ? ' · 复测' : ''}
            </button>
          ))}
        </div>

        {completed && (
          <div className="mb-4 rounded-2xl border border-emerald-100 bg-white p-4 shadow-sm">
            <div className="mb-3 flex items-center gap-1.5">
              <CheckCircle2 size={15} className="text-emerald-500" />
              <h2 className="text-sm font-semibold text-slate-800">训练结算</h2>
            </div>
            {session.dimensionResult.length > 0 && (
              <div className="mb-3 space-y-2">
                {session.dimensionResult.map((item) => (
                  <div key={item.dimension}>
                    <div className="mb-1 flex items-center justify-between text-[11px]">
                      <span className="text-slate-600">{item.label}</span>
                      <span className="tabular-nums text-slate-500">
                        {item.before} → <span className="font-semibold text-brand-700">{item.after}</span>
                        <span className={item.delta >= 0 ? 'ml-1 text-emerald-600' : 'ml-1 text-rose-500'}>
                          {item.delta >= 0 ? '+' : ''}{item.delta}
                        </span>
                      </span>
                    </div>
                    <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
                      <div className="h-full rounded-full bg-brand-500"
                        style={{ width: `${Math.max(0, Math.min(100, item.after))}%` }} />
                    </div>
                  </div>
                ))}
              </div>
            )}
            {session.result.length > 0 && (
              <div className="space-y-1.5">
                {session.result.map((item) => (
                  <div key={item.kpKey} className="flex items-center gap-2 text-[11px]">
                    <span className="min-w-0 truncate text-slate-600">{item.kpName}</span>
                    {item.eliminated && (
                      <span className="rounded-full bg-emerald-50 px-1.5 py-0.5 text-[10px] text-emerald-600">
                        已消弭
                      </span>
                    )}
                    <span className="ml-auto tabular-nums text-slate-500">
                      {item.before} → <span className="font-semibold text-brand-700">{item.after}</span>
                    </span>
                  </div>
                ))}
              </div>
            )}
            <div className="mt-4 flex flex-wrap gap-2">
              <Link to="/interview"
                className="rounded-lg bg-brand-600 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-brand-700">
                去面试
              </Link>
              <Link to="/training"
                className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-600 transition hover:bg-slate-50">
                回仪表盘
              </Link>
            </div>
          </div>
        )}

        {current && (
          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="mb-3 flex flex-wrap items-center gap-2">
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-brand-50 text-[11px] font-semibold text-brand-700">
                {current.seq}
              </span>
              <span className="text-xs font-medium text-slate-600">知识点：{current.kpName}</span>
              <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] text-slate-500">
                {current.dimensionLabel}
              </span>
              {current.role === 'RETEST' && (
                <span className="rounded-full bg-amber-50 px-2 py-0.5 text-[10px] font-medium text-amber-700">
                  复测
                </span>
              )}
              <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] text-slate-500">
                {current.source === 'AI' ? 'AI 变式' : '题库'}
              </span>
              {current.questionType && (
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] text-slate-500">
                  {QUESTION_TYPE_LABELS[current.questionType] ?? current.questionType}
                </span>
              )}
              <span className="ml-auto flex items-center gap-1 text-[10px] text-slate-400">
                难度
                {[1, 2, 3].map((level) => (
                  <span key={level}
                    className={`h-1.5 w-1.5 rounded-full ${
                      level <= (lastResult?.nextDifficulty ?? current.difficulty ?? 2)
                        ? 'bg-brand-500' : 'bg-slate-200'
                    }`} />
                ))}
              </span>
            </div>

            <h2 className="text-sm font-semibold text-slate-800">题目：{current.title}</h2>
            <MarkdownContent content={current.content} className="mt-2 text-xs" />

            {current.status === 0 ? (
              <div className="mt-4">
                <div className="relative">
                  <textarea
                    value={answer}
                    onChange={(event) => setAnswer(event.target.value)}
                    placeholder="写下你的回答，像在面试里口述一样组织要点…"
                    rows={7}
                    className="w-full resize-y rounded-xl border border-slate-200 px-3 py-2.5 pb-10 text-xs leading-6 text-slate-700 outline-none transition focus:border-brand-300 focus:ring-2 focus:ring-brand-100"
                  />
                  <div className="absolute bottom-2 left-2">
                    <VoiceInputButton
                      state={voice.state}
                      seconds={voice.seconds}
                      disabled={submitting}
                      onClick={toggleVoice}
                    />
                  </div>
                </div>
                {submitError && <p className="mt-1.5 text-[11px] text-rose-500">{submitError}</p>}
                <div className="mt-3 flex items-center justify-between">
                  <span className="text-[10px] text-slate-400">{answer.length} 字</span>
                  <button
                    type="button"
                    onClick={() => void handleSubmit()}
                    disabled={submitting}
                    className="flex items-center gap-1.5 rounded-lg bg-brand-600 px-4 py-2 text-xs font-medium text-white transition hover:bg-brand-700 disabled:opacity-60"
                  >
                    {submitting ? <Loader2 size={13} className="animate-spin" /> : <Play size={13} />}
                    {submitting ? '批改中…' : '提交评分'}
                  </button>
                </div>
              </div>
            ) : (
              <div className="mt-4">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-2xl font-semibold tabular-nums text-brand-600">
                    {current.score ?? '-'}
                  </span>
                  <span className="text-[11px] text-slate-400">/ 5 分</span>
                  {lastResult?.mastery && (
                    <span className="text-[11px] text-slate-500">
                      {lastResult.mastery.kpName} {lastResult.mastery.before} →{' '}
                      <span className="font-medium text-brand-700">{lastResult.mastery.after}</span>
                      （{lastResult.mastery.delta >= 0 ? '+' : ''}{lastResult.mastery.delta}）
                      {lastResult.eliminated && (
                        <span className="ml-1 rounded-full bg-emerald-50 px-1.5 py-0.5 text-[10px] text-emerald-600">
                          弱项消弭
                        </span>
                      )}
                    </span>
                  )}
                  <button
                    type="button"
                    onClick={speakFeedback}
                    className="ml-auto flex items-center gap-1 rounded-lg border border-slate-200 px-2.5 py-1 text-[11px] text-slate-500 transition hover:bg-slate-50"
                  >
                    <Volume2 size={12} />
                    播放点评
                  </button>
                </div>

                <div className="mt-3 grid gap-2 sm:grid-cols-2">
                  <PointList title="命中要点" items={current.hitPoints} tone="emerald" />
                  <PointList title="遗漏要点" items={current.missedPoints} tone="rose" />
                </div>
                {(current.summary || current.comment) && (
                  <p className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-[11px] leading-5 text-slate-600">
                    {current.summary || current.comment}
                  </p>
                )}
                {current.detailedFeedback && (
                  <div className="mt-3 rounded-xl border border-slate-100 bg-white px-3 py-2.5">
                    <p className="mb-1 text-[10px] font-semibold text-slate-500">详细批改</p>
                    <MarkdownContent content={current.detailedFeedback} className="text-xs" />
                  </div>
                )}
                {current.scoringPoints.length > 0 && (
                  <div className="mt-3 rounded-xl bg-slate-50 px-3 py-2.5">
                    <p className="mb-1 text-[10px] font-semibold text-slate-500">评分点</p>
                    <ul className="space-y-1.5">
                      {current.scoringPoints.map((point, index) => (
                        <li key={`${point.point}-${index}`} className="text-[11px] leading-5 text-slate-600">
                          <span className="font-medium">{point.point}</span>
                          <span className="ml-1 text-slate-400">
                            （{point.weight} 分{point.required ? '，必答' : ''}）
                          </span>
                          {point.acceptable && (
                            <span className="block text-[10px] leading-4 text-slate-400">
                              可接受表达：{point.acceptable}
                            </span>
                          )}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                {current.improvements.length > 0 ? (
                  <div className="mt-3 rounded-xl bg-slate-50 px-3 py-2.5">
                    <p className="mb-1 text-[10px] font-semibold text-slate-500">改进项</p>
                    <ul className="space-y-1">
                      {current.improvements.map((item, index) => (
                        <li key={`${item}-${index}`} className="text-[11px] leading-5 text-slate-600">
                          · {item}
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : current.suggestion && (
                  <p className="mt-1.5 text-[11px] leading-5 text-slate-500">
                    建议：{current.suggestion}
                  </p>
                )}
                {current.referenceAnswer && (
                  <details className="mt-3 rounded-lg border border-slate-100 px-3 py-2">
                    <summary className="cursor-pointer text-[11px] font-medium text-slate-500">
                      查看示范答案
                    </summary>
                    <MarkdownContent content={current.referenceAnswer} className="mt-2 text-xs" />
                  </details>
                )}

                {current.similarQuestions.length > 0 && (
                  <div className="mt-3 rounded-xl border border-brand-100 bg-brand-50/40 px-3 py-2.5">
                    <p className="mb-1.5 text-[10px] font-semibold text-brand-700">同类题 · 举一反三</p>
                    <div className="space-y-1">
                      {current.similarQuestions.map((item) => (
                        <SimilarLink key={item.questionId} item={item} />
                      ))}
                    </div>
                  </div>
                )}

                <div className="mt-4 flex justify-end">
                  {completed ? (
                    <Link to="/training"
                      className="flex items-center gap-1 rounded-lg border border-slate-200 px-3 py-1.5 text-xs text-slate-600 transition hover:bg-slate-50">
                      回仪表盘 <ChevronRight size={12} />
                    </Link>
                  ) : (
                    <button
                      type="button"
                      onClick={handleNext}
                      className="flex items-center gap-1 rounded-lg bg-brand-600 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-brand-700"
                    >
                      下一题 <ChevronRight size={12} />
                    </button>
                  )}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function SimilarLink({ item }: { item: SimilarQuestion }) {
  return (
    <Link
      to={`/questions/${item.questionId}`}
      className="flex items-center gap-2 text-[11px] text-slate-600 transition hover:text-brand-700"
    >
      <span className="min-w-0 truncate">{item.title}</span>
      <span className="ml-auto shrink-0 text-[10px] text-slate-400">
        {item.difficulty == null ? '' : `难度 ${item.difficulty}/3`}
      </span>
    </Link>
  )
}

function PointList({ title, items, tone }: {
  title: string
  items: string[]
  tone: 'emerald' | 'rose'
}) {
  const color = tone === 'emerald' ? 'text-emerald-600' : 'text-rose-500'
  return (
    <div className="rounded-xl bg-slate-50 px-3 py-2">
      <h3 className={`mb-1 text-[10px] font-semibold ${color}`}>{title}</h3>
      {items.length === 0 ? (
        <p className="text-[11px] text-slate-400">无</p>
      ) : (
        <ul className="space-y-1">
          {items.map((item, index) => (
            <li key={`${item}-${index}`} className="text-[11px] leading-5 text-slate-600">· {item}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default TrainingSessionPage
