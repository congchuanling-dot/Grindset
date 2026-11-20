import { errMessage } from '../utils/error'
import Editor, { loader } from '@monaco-editor/react'
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  Clock,
  Loader2,
  Play,
  Send,
  Terminal,
  XCircle,
} from 'lucide-react'
import * as monaco from 'monaco-editor'
import editorWorker from 'monaco-editor/editor/editor.worker?worker'
import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  getCodingDetail,
  getCodingSubmissions,
  getSubmission,
  runCode,
  submitCode,
  type CodeLanguage,
  type CodeRunResult,
  type CodingDetail,
  type CodingStatus,
  type CodingSubmission,
} from '../api/questionBank'
import { difficultyClass, difficultyLabel } from './QuestionBankPage'

// 用本地打包的 monaco，避免编辑器依赖外部 CDN
self.MonacoEnvironment = { getWorker: () => new editorWorker() }
loader.config({ monaco })

const LANGUAGE_LABELS: Record<CodeLanguage, string> = {
  JAVA: 'Java',
  PYTHON: 'Python',
}

const STATUS_LABELS: Record<CodingStatus, string> = {
  PENDING: '排队中',
  RUNNING: '判题中',
  ACCEPTED: '通过',
  WRONG_ANSWER: '答案错误',
  COMPILE_ERROR: '编译错误',
  RUNTIME_ERROR: '运行错误',
  TIME_LIMIT_EXCEEDED: '超出时间限制',
  SYSTEM_ERROR: '系统错误',
}

const PENDING_STATUSES: CodingStatus[] = ['PENDING', 'RUNNING']

/**
 * 编程题工作台:左侧题面与例题,右侧 Monaco 编辑器,可运行样例、提交判题。
 * 提交是异步的,前端轮询判题结果,隐藏用例只回传通过数。
 */
function CodingWorkspace({ questionId }: { questionId: string }) {
  const navigate = useNavigate()

  const [detail, setDetail] = useState<CodingDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [language, setLanguage] = useState<CodeLanguage>('JAVA')
  const [codes, setCodes] = useState<Record<CodeLanguage, string>>({ JAVA: '', PYTHON: '' })

  const [running, setRunning] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [runResult, setRunResult] = useState<CodeRunResult | null>(null)
  const [submission, setSubmission] = useState<CodingSubmission | null>(null)
  const [history, setHistory] = useState<CodingSubmission[]>([])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setLoadError(null)
    Promise.all([getCodingDetail(questionId), getCodingSubmissions(questionId)])
      .then(([coding, submissions]) => {
        if (cancelled) return
        setDetail(coding)
        setHistory(submissions)
        const starter = coding.starterCode
        setCodes({
          JAVA: starter.JAVA ?? '',
          PYTHON: starter.PYTHON ?? '',
        })
        if (!starter.JAVA && starter.PYTHON) {
          setLanguage('PYTHON')
        }
      })
      .catch((e: unknown) => {
        if (!cancelled) setLoadError(errMessage(e))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [questionId])

  // 提交后轮询判题结果
  useEffect(() => {
    if (!submission || !PENDING_STATUSES.includes(submission.status)) return
    const timer = window.setInterval(async () => {
      try {
        const latest = await getSubmission(submission.id)
        setSubmission(latest)
        if (!PENDING_STATUSES.includes(latest.status)) {
          window.clearInterval(timer)
          const [nextHistory, nextDetail] = await Promise.all([
            getCodingSubmissions(questionId),
            getCodingDetail(questionId),
          ])
          setHistory(nextHistory)
          setDetail(nextDetail)
        }
      } catch {
        // 查询失败继续轮询,直到超时或成功
      }
    }, 1200)
    return () => window.clearInterval(timer)
  }, [questionId, submission])

  const code = codes[language] ?? ''

  function changeCode(next: string) {
    setCodes((prev) => ({ ...prev, [language]: next }))
  }

  const run = useCallback(async () => {
    if (!detail || running || submitting) return
    setRunning(true)
    setActionError(null)
    setRunResult(null)
    try {
      setRunResult(await runCode(questionId, language, code))
    } catch (e) {
      setActionError(errMessage(e))
    } finally {
      setRunning(false)
    }
  }, [code, detail, language, questionId, running, submitting])

  const submit = useCallback(async () => {
    if (!detail || running || submitting) return
    setSubmitting(true)
    setActionError(null)
    setRunResult(null)
    try {
      setSubmission(await submitCode(questionId, language, code))
    } catch (e) {
      setActionError(errMessage(e))
    } finally {
      setSubmitting(false)
    }
  }, [code, detail, language, questionId, running, submitting])

  const judging = submission != null && PENDING_STATUSES.includes(submission.status)

  return (
    <div className="flex h-full flex-col overflow-hidden bg-slate-50">
      <header className="flex shrink-0 items-center gap-2 border-b border-slate-200 bg-white px-4 py-2.5 md:px-6">
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="flex items-center gap-1 rounded-lg px-2 py-1 text-xs text-slate-500 transition hover:bg-slate-100 hover:text-slate-700"
        >
          <ArrowLeft size={14} />
          返回题库
        </button>
        {detail && (
          <div className="ml-1 flex min-w-0 flex-wrap items-center gap-1.5">
            <h1 className="truncate text-sm font-semibold text-slate-800">{detail.title}</h1>
            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] text-slate-600">
              {detail.domain}
            </span>
            <span className={`rounded-full px-2 py-0.5 text-[11px] ${difficultyClass(detail.difficulty)}`}>
              {difficultyLabel(detail.difficulty)}
            </span>
            <span className="rounded-full bg-teal-50 px-2 py-0.5 text-[11px] text-teal-700">
              {detail.accepted ? '已通过' : detail.status === 'WRONG' ? '待通过' : '未作答'}
            </span>
          </div>
        )}
        <div className="ml-auto flex shrink-0 items-center gap-2 text-[11px] text-slate-400">
          <span>时限 {detail?.timeLimitMs ?? 2000}ms</span>
          <span>内存 {detail?.memoryLimitMb ?? 256}MB</span>
        </div>
      </header>

      {loading ? (
        <div className="flex flex-1 items-center justify-center gap-2 text-xs text-slate-400">
          <Loader2 size={14} className="animate-spin" />
          加载中…
        </div>
      ) : loadError || !detail ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 text-center">
          <AlertCircle size={22} className="text-rose-500" />
          <p className="text-sm text-slate-600">{loadError || '题目不存在或已下架'}</p>
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-600"
          >
            返回题库
          </button>
        </div>
      ) : (
        <div className="grid min-h-0 flex-1 grid-cols-1 gap-3 overflow-y-auto p-3 lg:grid-cols-2 lg:overflow-hidden">
          {/* 左侧:题面与例题 */}
          <section className="min-h-0 overflow-y-auto rounded-xl border border-slate-200 bg-white px-4 py-4">
            <p className="whitespace-pre-wrap text-sm leading-7 text-slate-800">{detail.content}</p>

            <div className="mt-5">
              <p className="flex items-center gap-1.5 text-xs font-medium text-slate-700">
                <Terminal size={13} className="text-teal-600" />
                例题（{detail.samples.length}）
              </p>
              <div className="mt-2 space-y-2">
                {detail.samples.map((sample, index) => (
                  <div key={index} className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                    <p className="text-[11px] font-medium text-slate-500">示例 {index + 1}</p>
                    <p className="mt-1 text-[11px] text-slate-400">输入</p>
                    <pre className="mt-0.5 overflow-x-auto whitespace-pre-wrap rounded bg-white px-2 py-1 font-mono text-xs text-slate-700">
                      {sample.input || '(空)'}
                    </pre>
                    <p className="mt-1.5 text-[11px] text-slate-400">期望输出</p>
                    <pre className="mt-0.5 overflow-x-auto whitespace-pre-wrap rounded bg-white px-2 py-1 font-mono text-xs text-slate-700">
                      {sample.expectedOutput}
                    </pre>
                  </div>
                ))}
              </div>
            </div>

            {history.length > 0 && (
              <div className="mt-5">
                <p className="flex items-center gap-1.5 text-xs font-medium text-slate-700">
                  <Clock size={13} className="text-slate-400" />
                  提交历史（最近 {history.length} 次）
                </p>
                <div className="mt-2 space-y-1.5">
                  {history.map((item) => (
                    <SubmissionRow key={item.id} item={item} />
                  ))}
                </div>
              </div>
            )}
          </section>

          {/* 右侧:编辑器 + 操作 + 结果 */}
          <section className="flex min-h-0 flex-col overflow-hidden rounded-xl border border-slate-200 bg-white">
            <div className="flex shrink-0 flex-wrap items-center gap-2 border-b border-slate-200 px-3 py-2">
              <select
                value={language}
                onChange={(e) => setLanguage(e.target.value as CodeLanguage)}
                className="rounded-lg border border-slate-200 bg-white px-2 py-1 text-xs text-slate-700 outline-none focus:border-teal-400"
              >
                {(Object.keys(LANGUAGE_LABELS) as CodeLanguage[]).map((lang) => (
                  <option key={lang} value={lang}>
                    {LANGUAGE_LABELS[lang]}
                  </option>
                ))}
              </select>

              <span className="text-[11px] text-slate-400">
                {language === 'JAVA' ? 'Main.java · 类名必须是 Main，从 stdin 读入' : 'main.py · 从 stdin 读入'}
              </span>

              <div className="ml-auto flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => void run()}
                  disabled={running || submitting || judging}
                  className="flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-300"
                >
                  {running ? <Loader2 size={13} className="animate-spin" /> : <Play size={13} />}
                  运行样例
                </button>
                <button
                  type="button"
                  onClick={() => void submit()}
                  disabled={running || submitting || judging}
                  className="flex items-center gap-1.5 rounded-lg bg-teal-600 px-3.5 py-1.5 text-xs font-medium text-white transition hover:bg-teal-700 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
                >
                  {submitting || judging ? <Loader2 size={13} className="animate-spin" /> : <Send size={13} />}
                  {judging ? '判题中…' : '提交'}
                </button>
              </div>
            </div>

            <div className="min-h-[280px] flex-1">
              <Editor
                height="100%"
                language={language === 'JAVA' ? 'java' : 'python'}
                theme="vs-dark"
                value={code}
                onChange={(value) => changeCode(value ?? '')}
                options={{
                  fontSize: 13,
                  minimap: { enabled: false },
                  scrollBeyondLastLine: false,
                  tabSize: 4,
                  automaticLayout: true,
                  wordWrap: 'on',
                  padding: { top: 10 },
                }}
              />
            </div>

            <div className="max-h-[46%] shrink-0 overflow-y-auto border-t border-slate-200 px-3 py-2">
              {actionError && (
                <div className="mb-2 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
                  <AlertCircle size={14} className="mt-0.5 shrink-0" />
                  <span>{actionError}</span>
                </div>
              )}
              {runResult ? (
                <RunResultView result={runResult} />
              ) : submission ? (
                <SubmissionView submission={submission} />
              ) : (
                <p className="py-2 text-[11px] text-slate-400">
                  点“运行样例”只跑例题，点“提交”会跑全部用例并记录到提交历史；未通过自动进错题本。
                </p>
              )}
            </div>
          </section>
        </div>
      )}
    </div>
  )
}

function RunResultView({ result }: { result: CodeRunResult }) {
  return (
    <div>
      <div className="flex flex-wrap items-center gap-2">
        <StatusChip status={result.status} />
        <span className="text-[11px] text-slate-500">
          通过 {result.passed} / {result.total}
        </span>
        {result.runtimeMs > 0 && (
          <span className="text-[11px] text-slate-400">{result.runtimeMs}ms</span>
        )}
      </div>

      {result.errorMessage && (
        <pre className="mt-2 max-h-40 overflow-auto whitespace-pre-wrap rounded-lg bg-slate-900 px-3 py-2 font-mono text-[11px] leading-5 text-rose-200">
          {result.errorMessage}
        </pre>
      )}

      {result.cases.length > 0 && (
        <div className="mt-2 space-y-1.5">
          {result.cases.map((item) => (
            <div
              key={item.index}
              className={`rounded-lg border px-3 py-2 ${
                item.passed ? 'border-teal-100 bg-teal-50/50' : 'border-rose-100 bg-rose-50/50'
              }`}
            >
              <p className="flex items-center gap-1 text-[11px] font-medium text-slate-600">
                {item.passed ? (
                  <CheckCircle2 size={12} className="text-teal-600" />
                ) : (
                  <XCircle size={12} className="text-rose-500" />
                )}
                示例 {item.index} · {item.message}
              </p>
              <div className="mt-1 grid gap-1 md:grid-cols-3">
                <Field label="输入" value={item.input} />
                <Field label="期望" value={item.expectedOutput} />
                <Field label="实际" value={item.actualOutput || '(无输出)'} />
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function SubmissionView({ submission }: { submission: CodingSubmission }) {
  const pending = PENDING_STATUSES.includes(submission.status)
  return (
    <div>
      <div className="flex flex-wrap items-center gap-2">
        <StatusChip status={submission.status} />
        <span className="text-[11px] text-slate-500">
          通过 {submission.passed} / {submission.total}
        </span>
        {submission.runtimeMs != null && (
          <span className="text-[11px] text-slate-400">{submission.runtimeMs}ms</span>
        )}
        {submission.createdAt && <span className="text-[11px] text-slate-400">{submission.createdAt}</span>}
      </div>
      {pending && (
        <p className="mt-2 flex items-center gap-1.5 text-[11px] text-slate-500">
          <Loader2 size={12} className="animate-spin" />
          正在排队判题，结果会自动刷新…
        </p>
      )}
      {submission.errorMessage && (
        <pre className="mt-2 max-h-40 overflow-auto whitespace-pre-wrap rounded-lg bg-slate-900 px-3 py-2 font-mono text-[11px] leading-5 text-rose-200">
          {submission.errorMessage}
        </pre>
      )}
    </div>
  )
}

function SubmissionRow({ item }: { item: CodingSubmission }) {
  return (
    <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1.5">
      <StatusChip status={item.status} />
      <span className="text-[11px] text-slate-500">
        {item.passed} / {item.total}
      </span>
      <span className="text-[11px] text-slate-400">{LANGUAGE_LABELS[item.language] ?? item.language}</span>
      <span className="ml-auto text-[11px] text-slate-400">{item.createdAt ?? ''}</span>
    </div>
  )
}

function StatusChip({ status }: { status: CodingStatus }) {
  const tone: Record<CodingStatus, string> = {
    PENDING: 'bg-slate-100 text-slate-500',
    RUNNING: 'bg-amber-50 text-amber-700',
    ACCEPTED: 'bg-teal-50 text-teal-700',
    WRONG_ANSWER: 'bg-rose-50 text-rose-600',
    COMPILE_ERROR: 'bg-rose-50 text-rose-600',
    RUNTIME_ERROR: 'bg-rose-50 text-rose-600',
    TIME_LIMIT_EXCEEDED: 'bg-amber-50 text-amber-700',
    SYSTEM_ERROR: 'bg-rose-50 text-rose-600',
  }
  return (
    <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${tone[status]}`}>
      {STATUS_LABELS[status] ?? status}
    </span>
  )
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded bg-white px-2 py-1">
      <p className="text-[10px] text-slate-400">{label}</p>
      <pre className="mt-0.5 max-h-24 overflow-auto whitespace-pre-wrap font-mono text-[11px] text-slate-700">
        {value}
      </pre>
    </div>
  )
}


export default CodingWorkspace
