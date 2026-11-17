import {
  AlertCircle,
  ArrowLeft,
  BookOpen,
  BookX,
  CheckCircle2,
  Loader2,
  MessageSquare,
  Send,
  Sparkles,
  Trash2,
  X,
} from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  deleteInterview,
  finishInterview,
  getInterview,
  streamNextQuestion,
  submitAnswer,
  type AnswerResult,
  type InterviewDetail,
  type InterviewMessage,
  type InterviewQuestion,
  type InterviewReport,
} from '../api/interview'
import { getResume, type ResumeDetail } from '../api/resume'
import ResumeViewer from '../components/resume/ResumeViewer'

const STYLE_LABELS: Record<string, string> = {
  pressure: '压力面',
  middle: '适中',
  relaxed: '轻松',
}

/** 面试对话页:线程式渲染主问题/追问/回答/点评,支持刷新恢复 */
function InterviewSessionPage() {
  const { sessionId = '' } = useParams()
  const navigate = useNavigate()

  const [detail, setDetail] = useState<InterviewDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [answer, setAnswer] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [asking, setAsking] = useState(false)
  const [pendingText, setPendingText] = useState('')
  const [finishing, setFinishing] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [reportOpen, setReportOpen] = useState(false)
  const [wrongHint, setWrongHint] = useState<{ questionId: string; wrongId: string } | null>(null)

  const [resume, setResume] = useState<ResumeDetail | null>(null)
  const [resumeOpen, setResumeOpen] = useState(false)

  const askingRef = useRef(false)
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    getInterview(sessionId)
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
  }, [sessionId])

  const askNext = useCallback(async () => {
    if (askingRef.current) return
    askingRef.current = true
    setAsking(true)
    setPendingText('')
    setError(null)

    let text = ''
    try {
      await streamNextQuestion(sessionId, {
        onDelta: (delta) => {
          text += delta
          setPendingText(text)
        },
        onError: (message) => setError(message),
        onDone: ({ questionId, seq }) => {
          const content = text.trim()
          if (!content) return
          setDetail((prev) => {
            if (!prev) return prev
            const question: InterviewQuestion = {
              id: questionId,
              seq,
              status: 2,
              followUpCount: 0,
              score: null,
              knowledgePoints: [],
              comment: null,
              suggestion: null,
              messages: [
                {
                  id: `q-${questionId}`,
                  seq: 1,
                  role: 'INTERVIEWER',
                  type: 'QUESTION',
                  content,
                  createdAt: '',
                },
              ],
            }
            return { ...prev, questions: [...prev.questions, question] }
          })
        },
      })
    } catch (e) {
      setError(errMessage(e))
    } finally {
      askingRef.current = false
      setAsking(false)
      setPendingText('')
    }
  }, [sessionId])

  const activeQuestion = detail?.questions.filter((item) => item.status === 2).at(-1) ?? null

  // 只要当前没有待回答的题、且还没答满,就自动出下一题
  useEffect(() => {
    if (!detail || detail.status !== 0) return
    if (askingRef.current) return
    if (detail.questions.some((item) => item.status === 2)) return
    if (detail.completedQuestions >= detail.questionCount) return
    void askNext()
  }, [detail, askNext])

  useEffect(() => {
    const node = scrollRef.current
    if (node) node.scrollTop = node.scrollHeight
  }, [detail?.questions.length, pendingText, activeQuestion?.id])

  async function handleFinish(auto = false) {
    if (finishing) return
    if (!auto && detail && detail.completedQuestions < detail.questionCount) {
      const message =
        detail.completedQuestions === 0
          ? '本场面试还没有完成任何题目，结束后不会生成复盘报告，确定结束吗？'
          : '结束后将根据已完成的题目生成复盘报告，确定现在结束吗？'
      if (!window.confirm(message)) return
    }
    setFinishing(true)
    setError(null)
    try {
      const report = await finishInterview(sessionId)
      setDetail((prev) =>
        prev
          ? {
              ...prev,
              status: 1,
              report,
              overallScore: report ? report.overallScore : prev.overallScore,
              // 服务端会把未答完的题标记为已放弃，本地状态同步一下
              questions: prev.questions.map((question) =>
                question.status === 2 ? { ...question, status: 4 } : question,
              ),
            }
          : prev,
      )
      if (report) setReportOpen(true)
    } catch (e) {
      setError(errMessage(e))
      // 结束请求可能已在服务端生效(例如报告生成失败)，重新拉详情保证状态一致
      try {
        setDetail(await getInterview(sessionId))
      } catch {
        // 刷新失败时保留当前页面
      }
    } finally {
      setFinishing(false)
    }
  }

  async function handleDelete() {
    if (deleting) return
    if (!window.confirm('删除后不可恢复，确定删除这场面试吗？')) return
    setDeleting(true)
    setError(null)
    try {
      await deleteInterview(sessionId)
      navigate('/records')
    } catch (e) {
      setError(errMessage(e))
      setDeleting(false)
    }
  }

  async function handleSubmit() {
    const content = answer.trim()
    if (!content || !activeQuestion || submitting || asking) return

    setSubmitting(true)
    setError(null)
    try {
      const result = await submitAnswer(sessionId, content)
      setAnswer('')
      setDetail((prev) => (prev ? applyAnswer(prev, activeQuestion.id, content, result) : prev))
      if (result.wrongQuestionId) {
        setWrongHint({ questionId: activeQuestion.id, wrongId: result.wrongQuestionId })
      }
      if (result.nextAction === 'REPORT_READY') {
        void handleFinish(true)
      }
    } catch (e) {
      setError(errMessage(e))
    } finally {
      setSubmitting(false)
    }
  }

  function openResume() {
    setResumeOpen(true)
    if (resume || !detail) return
    getResume(detail.resumeId)
      .then(setResume)
      .catch((e: unknown) => setError(errMessage(e)))
  }

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center bg-slate-50">
        <Loader2 size={22} className="animate-spin text-teal-600" />
      </div>
    )
  }

  if (!detail) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 bg-slate-50">
        <AlertCircle size={22} className="text-rose-500" />
        <p className="text-sm text-slate-600">{error || '面试记录不存在'}</p>
        <Link to="/interview" className="text-sm text-teal-700 hover:underline">
          返回 AI 面试
        </Link>
      </div>
    )
  }

  const finished = detail.status === 1
  const waiting = asking || submitting
  const wrongCount = detail.questions.filter(
    (question) => question.status === 3 && question.score != null && question.score <= 2,
  ).length

  return (
    <div className="flex h-full flex-col overflow-hidden bg-slate-50">
      <header className="flex flex-wrap items-center gap-3 border-b border-slate-200 bg-white px-4 py-3 md:px-6">
        <button
          type="button"
          onClick={() => navigate('/interview')}
          className="flex items-center gap-1 rounded-lg px-2 py-1.5 text-sm text-slate-500 transition hover:bg-slate-100"
        >
          <ArrowLeft size={15} />
          返回
        </button>
        <div className="min-w-0">
          <h1 className="truncate text-sm font-semibold text-slate-800">{detail.position}</h1>
          <p className="mt-0.5 flex flex-wrap items-center gap-2 text-[11px] text-slate-400">
            <span>{detail.resumeFileName}</span>
            <span>·</span>
            <span>{STYLE_LABELS[detail.style] ?? detail.style}</span>
            {detail.round && (
              <>
                <span>·</span>
                <span>{detail.round}</span>
              </>
            )}
            <span>·</span>
            <span>
              已完成 {detail.completedQuestions}/{detail.questionCount} 题
            </span>
          </p>
        </div>

        <div className="ml-auto flex items-center gap-2">
          <button
            type="button"
            onClick={openResume}
            className="flex items-center gap-1.5 rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs text-slate-600 transition hover:border-teal-300 hover:text-teal-700"
          >
            <BookOpen size={14} />
            查看简历
          </button>
          {detail.report ? (
            <button
              type="button"
              onClick={() => setReportOpen(true)}
              className="flex items-center gap-1.5 rounded-lg bg-teal-600 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-teal-700"
            >
              <Sparkles size={14} />
              查看复盘
            </button>
          ) : detail.status === 0 ? (
            <button
              type="button"
              onClick={() => handleFinish(false)}
              disabled={finishing || waiting}
              className="flex items-center gap-1.5 rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs text-slate-600 transition hover:border-rose-200 hover:text-rose-600 disabled:opacity-50"
            >
              {finishing ? <Loader2 size={14} className="animate-spin" /> : null}
              结束面试
            </button>
          ) : null}
          <button
            type="button"
            onClick={() => void handleDelete()}
            disabled={deleting || waiting || finishing}
            title="删除这场面试"
            className="flex items-center gap-1.5 rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs text-slate-500 transition hover:border-rose-200 hover:text-rose-600 disabled:opacity-50"
          >
            {deleting ? <Loader2 size={14} className="animate-spin" /> : <Trash2 size={14} />}
            删除
          </button>
        </div>
      </header>

      {error && (
        <div className="mx-4 mt-3 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700 md:mx-6">
          <AlertCircle size={16} className="mt-0.5 shrink-0" />
          <span className="flex-1">{error}</span>
          <button type="button" className="text-xs text-rose-500" onClick={() => setError(null)}>
            关闭
          </button>
        </div>
      )}

      <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-4 md:px-6">
        <div className="mx-auto max-w-3xl space-y-5">
          {detail.questions.map((question) => (
            <QuestionThread key={question.id} question={question} />
          ))}

          {wrongHint && (
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-teal-200 bg-teal-50 px-3 py-2 text-xs text-teal-800">
              <span className="flex items-center gap-1.5">
                <BookX size={13} className="shrink-0" />
                本题已加入错题本
              </span>
              <Link
                to={`/wrong-questions?open=${wrongHint.wrongId}`}
                className="font-medium text-teal-700 hover:text-teal-900"
              >
                立即重练 →
              </Link>
            </div>
          )}

          {asking && (
            <div className="flex gap-2">
              <span className="mt-1 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-teal-50 text-teal-600">
                <Sparkles size={14} />
              </span>
              <div className="max-w-[85%] rounded-2xl rounded-tl-sm border border-slate-200 bg-white px-4 py-3 text-sm leading-6 text-slate-700">
                {pendingText || '面试官正在出题…'}
                <span className="ml-0.5 inline-block h-4 w-0.5 animate-pulse bg-teal-500 align-middle" />
              </div>
            </div>
          )}

          {submitting && (
            <div className="flex items-center gap-2 pl-9 text-xs text-slate-400">
              <Loader2 size={13} className="animate-spin" />
              面试官正在点评你的回答…
            </div>
          )}

          {finished && !detail.report && (
            <div className="rounded-xl border border-slate-200 bg-white px-4 py-6 text-center">
              <CheckCircle2 size={22} className="mx-auto text-teal-600" />
              <p className="mt-2 text-sm text-slate-600">这场面试已结束</p>
              {detail.completedQuestions === 0 ? (
                <p className="mt-1 text-xs text-slate-400">
                  没有完成任何题目，未生成复盘报告。可以直接删除这场面试。
                </p>
              ) : (
                <button
                  type="button"
                  onClick={() => handleFinish(true)}
                  disabled={finishing}
                  className="mt-3 rounded-lg bg-teal-600 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-teal-700"
                >
                  {finishing ? '生成中…' : '生成复盘报告'}
                </button>
              )}
            </div>
          )}
        </div>
      </div>

      <footer className="border-t border-slate-200 bg-white px-4 py-3 md:px-6">
        <div className="mx-auto max-w-3xl">
          <div className="flex items-end gap-2">
            <textarea
              value={answer}
              onChange={(e) => setAnswer(e.target.value)}
              onKeyDown={(e) => {
                if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
                  e.preventDefault()
                  void handleSubmit()
                }
              }}
              rows={3}
              disabled={!activeQuestion || waiting || finished}
              placeholder={
                finished
                  ? '本场面试已结束'
                  : activeQuestion
                    ? '输入你的回答，Ctrl / ⌘ + Enter 提交'
                    : asking
                      ? '面试官正在出题…'
                      : '等待下一题…'
              }
              className="max-h-48 min-h-[76px] flex-1 resize-y rounded-xl border border-slate-200 px-3 py-2 text-sm leading-6 text-slate-700 outline-none transition focus:border-teal-400 disabled:bg-slate-50 disabled:text-slate-400"
            />
            <button
              type="button"
              onClick={() => void handleSubmit()}
              disabled={!activeQuestion || !answer.trim() || waiting || finished}
              className="flex h-10 items-center gap-1.5 rounded-lg bg-teal-600 px-4 text-sm font-medium text-white transition hover:bg-teal-700 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
            >
              {submitting ? <Loader2 size={15} className="animate-spin" /> : <Send size={15} />}
              提交
            </button>
          </div>
          {!activeQuestion && !asking && !finished && detail.completedQuestions < detail.questionCount && (
            <button
              type="button"
              onClick={() => void askNext()}
              className="mt-2 text-xs text-teal-700 hover:underline"
            >
              没有收到题目？点这里重新出题
            </button>
          )}
        </div>
      </footer>

      {resumeOpen && (
        <div className="fixed inset-0 z-30 flex justify-end bg-slate-900/30" onClick={() => setResumeOpen(false)}>
          <div
            className="flex h-full w-[min(92vw,460px)] flex-col bg-slate-50 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3">
              <span className="text-sm font-semibold text-slate-800">简历预览</span>
              <button
                type="button"
                onClick={() => setResumeOpen(false)}
                className="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
              >
                <X size={16} />
              </button>
            </div>
            <div className="flex-1 overflow-auto p-3">
              {resume ? (
                <ResumeViewer detail={resume} />
              ) : (
                <div className="flex h-40 items-center justify-center">
                  <Loader2 size={20} className="animate-spin text-teal-600" />
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {reportOpen && detail.report && (
        <ReportDialog
          report={detail.report}
          sessionId={detail.id}
          wrongCount={wrongCount}
          onClose={() => setReportOpen(false)}
          onRetry={() => navigate('/interview')}
        />
      )}
    </div>
  )
}

/** 一道题的线程:主问题、追问、回答、点评 */
function QuestionThread({ question }: { question: InterviewQuestion }) {
  return (
    <section className="space-y-2">
      <div className="flex items-center gap-2 text-[11px] text-slate-400">
        <span className="flex h-5 w-5 items-center justify-center rounded-full bg-slate-200 text-[11px] font-medium text-slate-600">
          {question.seq}
        </span>
        <span>第 {question.seq} 题</span>
        {question.followUpCount > 0 && <span>· 已追问 {question.followUpCount} 次</span>}
        {question.status === 3 && question.score != null && (
          <span className="rounded-full bg-teal-50 px-2 py-0.5 text-teal-700">{question.score}/5</span>
        )}
        {question.status === 4 && (
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-500">已放弃</span>
        )}
      </div>

      <div className="space-y-2">
        {question.messages.map((message) => (
          <MessageBubble key={message.id} message={message} />
        ))}
      </div>

      {question.status === 3 && question.comment && (
        <div className="ml-9 rounded-xl border border-teal-100 bg-teal-50/60 px-3 py-2 text-xs leading-5 text-teal-800">
          <p>{question.comment}</p>
          {question.suggestion && <p className="mt-1 text-teal-700">建议：{question.suggestion}</p>}
          {question.knowledgePoints.length > 0 && (
            <div className="mt-1.5 flex flex-wrap gap-1">
              {question.knowledgePoints.map((point) => (
                <span key={point} className="rounded-full bg-white px-2 py-0.5 text-[11px] text-teal-700">
                  {point}
                </span>
              ))}
            </div>
          )}
        </div>
      )}

      {question.status === 4 && (
        <p className="ml-9 text-[11px] text-slate-400">本题未答完，未计入复盘。</p>
      )}
    </section>
  )
}

function MessageBubble({ message }: { message: InterviewMessage }) {
  if (message.type === 'FEEDBACK') {
    return (
      <div className="ml-9 flex items-start gap-1.5 rounded-lg bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-800">
        <MessageSquare size={13} className="mt-0.5 shrink-0" />
        <span>{message.content}</span>
      </div>
    )
  }

  const mine = message.role === 'USER'
  return (
    <div className={`flex gap-2 ${mine ? 'flex-row-reverse' : ''}`}>
      <span
        className={`mt-1 flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-[11px] ${
          mine ? 'bg-slate-200 text-slate-600' : 'bg-teal-50 text-teal-600'
        }`}
      >
        {mine ? '我' : <Sparkles size={14} />}
      </span>
      <div className={`max-w-[85%] ${mine ? 'text-right' : ''}`}>
        {message.type === 'FOLLOW_UP' && (
          <span className="mb-1 inline-block rounded-full bg-amber-50 px-2 py-0.5 text-[10px] text-amber-700">
            追问
          </span>
        )}
        <div
          className={`inline-block rounded-2xl px-4 py-2.5 text-left text-sm leading-6 ${
            mine
              ? 'rounded-tr-sm bg-teal-600 text-white'
              : 'rounded-tl-sm border border-slate-200 bg-white text-slate-700'
          }`}
        >
          {message.content}
        </div>
      </div>
    </div>
  )
}

function ReportDialog({
  report,
  sessionId,
  wrongCount,
  onClose,
  onRetry,
}: {
  report: InterviewReport
  sessionId: string
  wrongCount: number
  onClose: () => void
  onRetry: () => void
}) {
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/40 p-4" onClick={onClose}>
      <div
        className="max-h-[88vh] w-full max-w-lg overflow-y-auto rounded-2xl bg-white p-5 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between">
          <div>
            <h2 className="flex items-center gap-1.5 text-base font-semibold text-slate-800">
              <Sparkles size={16} className="text-teal-600" />
              面试复盘
            </h2>
            <p className="mt-1 text-xs text-slate-400">基于已完成题目的对话记录生成</p>
          </div>
          <button type="button" onClick={onClose} className="rounded p-1 text-slate-400 hover:bg-slate-100">
            <X size={16} />
          </button>
        </div>

        <div className="mt-4 rounded-xl bg-teal-50 px-4 py-3 text-center">
          <p className="text-xs text-teal-700">综合得分</p>
          <p className="mt-0.5 text-3xl font-semibold text-teal-700">{report.overallScore}</p>
        </div>

        <div className="mt-4 space-y-2">
          {Object.entries(report.dimensionScores).map(([name, score]) => (
            <div key={name} className="flex items-center gap-3 text-xs">
              <span className="w-16 shrink-0 text-slate-500">{name}</span>
              <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-100">
                <div className="h-full rounded-full bg-teal-500" style={{ width: `${Math.min(100, score)}%` }} />
              </div>
              <span className="w-8 text-right text-slate-600">{score}</span>
            </div>
          ))}
        </div>

        <ReportList title="薄弱知识点" items={report.weakPoints} tone="rose" />
        <ReportList title="亮点" items={report.highlights} tone="teal" />
        <ReportList title="改进建议" items={report.advice} tone="slate" />

        {wrongCount > 0 && (
          <div className="mt-4 flex items-center justify-between gap-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
            <span>本场有 {wrongCount} 道错题已加入错题本。</span>
            <Link
              to={`/wrong-questions?status=ALL&sessionId=${sessionId}`}
              onClick={onClose}
              className="shrink-0 font-medium text-amber-700 hover:text-amber-900"
            >
              去重练 →
            </Link>
          </div>
        )}

        <div className="mt-4 flex items-center justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500">
          <span>本场评分已同步到你的掌握度画像。</span>
          <Link to="/knowledge" onClick={onClose} className="shrink-0 text-teal-600 hover:text-teal-700">
            去看看 →
          </Link>
        </div>

        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-600 transition hover:bg-slate-50"
          >
            关闭
          </button>
          <button
            type="button"
            onClick={onRetry}
            className="rounded-lg bg-teal-600 px-3 py-1.5 text-sm font-medium text-white transition hover:bg-teal-700"
          >
            再练一场
          </button>
        </div>
      </div>
    </div>
  )
}

function ReportList({ title, items, tone }: { title: string; items: string[]; tone: 'rose' | 'teal' | 'slate' }) {
  if (items.length === 0) return null
  const color = tone === 'rose' ? 'text-rose-600' : tone === 'teal' ? 'text-teal-700' : 'text-slate-600'
  return (
    <div className="mt-4">
      <h3 className={`text-xs font-medium ${color}`}>{title}</h3>
      <ul className="mt-1 space-y-1 text-xs leading-5 text-slate-600">
        {items.map((item) => (
          <li key={item} className="flex gap-1.5">
            <span className="text-slate-300">·</span>
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** 把一次答题结果合并进本地会话,避免每题都重新拉整份详情 */
function applyAnswer(
  detail: InterviewDetail,
  questionId: string,
  content: string,
  result: AnswerResult,
): InterviewDetail {
  const questions = detail.questions.map((question) => {
    if (question.id !== questionId) return question
    const messages = [...question.messages]
    let seq = messages.length + 1
    messages.push({
      id: `${questionId}-a-${seq}`,
      seq: seq++,
      role: 'USER',
      type: 'ANSWER',
      content,
      createdAt: '',
    })
    if (result.feedback) {
      messages.push({
        id: `${questionId}-f-${seq}`,
        seq: seq++,
        role: 'INTERVIEWER',
        type: 'FEEDBACK',
        content: result.feedback,
        createdAt: '',
      })
    }
    if (result.followUpQuestion) {
      messages.push({
        id: `${questionId}-q-${seq}`,
        seq,
        role: 'INTERVIEWER',
        type: 'FOLLOW_UP',
        content: result.followUpQuestion,
        createdAt: '',
      })
    }
    return {
      ...question,
      status: result.questionFinished ? 3 : 2,
      followUpCount: result.followUpQuestion ? question.followUpCount + 1 : question.followUpCount,
      score: result.evaluation?.score ?? question.score,
      knowledgePoints: result.evaluation?.knowledgePoints ?? question.knowledgePoints,
      comment: result.evaluation?.comment ?? question.comment,
      suggestion: result.evaluation?.suggestion ?? question.suggestion,
      messages,
    }
  })

  return { ...detail, questions, completedQuestions: result.completedQuestions }
}

function errMessage(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

export default InterviewSessionPage
