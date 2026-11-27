import { errMessage } from '../utils/error'
import Editor, { loader } from '@monaco-editor/react'
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  Clock,
  FileText,
  Lightbulb,
  Loader2,
  Play,
  Sparkles,
  Terminal,
  XCircle,
} from 'lucide-react'
import * as monaco from 'monaco-editor'
import editorWorker from 'monaco-editor/editor/editor.worker?worker'
import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  getCodingDetail,
  getCodingReviews,
  reviewCode,
  runCode,
  type CodeLanguage,
  type CodeRunResult,
  type CodingDetail,
  type CodingReviewResult,
  type CodingReviewView,
  type FreeRunStatus,
  type QuestionStatus,
} from '../api/questionBank'
import MarkdownContent from '../components/MarkdownContent'
import { difficultyClass, difficultyLabel } from '../components/questions/QuestionBadges'

// 用本地打包的 monaco，避免编辑器依赖外部 CDN
self.MonacoEnvironment = { getWorker: () => new editorWorker() }
loader.config({ monaco })

const LANGUAGE_LABELS: Record<CodeLanguage, string> = {
  JAVA: 'Java',
  PYTHON: 'Python',
}

const RUN_STATUS_LABELS: Record<FreeRunStatus, string> = {
  SUCCESS: '运行成功',
  COMPILE_ERROR: '编译错误',
  RUNTIME_ERROR: '运行错误',
  TIME_LIMIT_EXCEEDED: '超出时间限制',
  SYSTEM_ERROR: '系统错误',
}

const PROGRESS_LABELS: Record<QuestionStatus, string> = {
  TODO: '未作答',
  DONE: '已掌握',
  WRONG: '待改进',
}

export interface CodingWorkspaceProps {
  questionId: string
}

/**
 * 编程题工作台:左侧题面,右侧 Monaco 编辑器。
 * 用户写完整 main、自己构造输入并运行;点「AI 点评」由大模型检查逻辑与边界。
 */
function CodingWorkspace({ questionId }: CodingWorkspaceProps) {
  const navigate = useNavigate()

  const [detail, setDetail] = useState<CodingDetail | null>(null)
  const [reviews, setReviews] = useState<CodingReviewView[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [language, setLanguage] = useState<CodeLanguage>('JAVA')
  const [codes, setCodes] = useState<Partial<Record<CodeLanguage, string>>>({})

  const [running, setRunning] = useState(false)
  const [reviewing, setReviewing] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [runResult, setRunResult] = useState<CodeRunResult | null>(null)
  const [reviewResult, setReviewResult] = useState<CodingReviewResult | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setLoadError(null)

    Promise.all([getCodingDetail(questionId), getCodingReviews(questionId)])
      .then(([coding, history]) => {
        if (cancelled) return
        setDetail(coding)
        setReviews(history)
        setCodes({
          JAVA: coding.starterCode.JAVA ?? '',
          PYTHON: coding.starterCode.PYTHON ?? '',
        })
        if (!coding.starterCode.JAVA && coding.starterCode.PYTHON) {
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

  const code = codes[language] ?? ''

  function changeCode(next: string) {
    setCodes((prev) => ({ ...prev, [language]: next }))
  }

  const run = useCallback(async () => {
    if (!detail || running || reviewing) return
    setRunning(true)
    setActionError(null)
    try {
      setRunResult(await runCode(questionId, language, code))
      setReviewResult(null)
    } catch (e) {
      setActionError(errMessage(e))
    } finally {
      setRunning(false)
    }
  }, [code, detail, language, questionId, reviewing, running])

  const review = useCallback(async () => {
    if (!detail || running || reviewing) return
    setReviewing(true)
    setActionError(null)
    try {
      const result = await reviewCode(questionId, language, code)
      setReviewResult(result)
      setRunResult(null)
      setDetail((prev) => (prev
        ? { ...prev, status: result.progressStatus }
        : prev))
      setReviews((prev) => [
        {
          id: result.createdAt ?? String(Date.now()),
          code,
          score: result.score,
          hitPoints: result.hitPoints,
          missedPoints: result.missedPoints,
          comment: result.comment,
          suggestion: result.suggestion,
          createdAt: result.createdAt,
        },
        ...prev,
      ])
    } catch (e) {
      setActionError(errMessage(e))
    } finally {
      setReviewing(false)
    }
  }, [code, detail, language, questionId, reviewing, running])

  return (
    <div className="flex h-full flex-col overflow-hidden bg-slate-50">
      <header className="flex shrink-0 items-center gap-2 border-b border-slate-200 bg-white px-4 py-2.5 md:px-6">
        <button
          type="button"
          onClick={() => navigate('/questions/coding')}
          className="flex items-center gap-1 rounded-lg px-2 py-1 text-xs text-slate-500 transition hover:bg-slate-100 hover:text-slate-700"
        >
          <ArrowLeft size={14} />
          返回编程题
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
            <span className="rounded-full bg-brand-50 px-2 py-0.5 text-[11px] text-brand-700">
              自由运行
            </span>
            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] text-slate-600">
              {PROGRESS_LABELS[detail.status]}
            </span>
          </div>
        )}
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
            onClick={() => navigate('/questions/coding')}
            className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-600"
          >
            返回编程题
          </button>
        </div>
      ) : (
        <div className="grid min-h-0 flex-1 grid-cols-1 gap-3 overflow-y-auto p-3 lg:grid-cols-2 lg:overflow-hidden">
          {/* 左侧:题面与点评历史 */}
          <section className="flex min-h-0 flex-col overflow-hidden rounded-xl border border-slate-200 bg-white">
            <div className="flex shrink-0 items-center gap-1.5 border-b border-slate-200 px-4 py-2.5">
              <FileText size={14} className="text-brand-600" />
              <p className="text-xs font-semibold text-slate-700">题目描述</p>
              <span className="ml-auto text-[11px] text-slate-400">
                时限 {detail.timeLimitMs || 5000}ms · 内存 {detail.memoryLimitMb || 256}MB
              </span>
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
              <MarkdownContent content={normalizeQuestionContent(detail.content)} />

              <div className="mt-4 rounded-lg border border-brand-100 bg-brand-50/60 px-3 py-2.5">
                <p className="flex items-center gap-1.5 text-[11px] font-medium text-brand-700">
                  <Lightbulb size={12} />
                  开放式编码
                </p>
                <p className="mt-1 text-[11px] leading-5 text-slate-600">
                  平台不提供测试用例。请在自己的 main 里构造输入、调用解法，用控制台输出结果；
                  点「AI 点评」会检查逻辑是否正确、有没有漏掉的边界或潜在 bug。
                </p>
              </div>

              {reviews.length > 0 && (
                <div className="mt-6">
                  <p className="flex items-center gap-1.5 text-xs font-semibold text-slate-700">
                    <Clock size={13} className="text-slate-400" />
                    点评历史（最近 {reviews.length} 次）
                  </p>
                  <div className="mt-2 space-y-1.5">
                    {reviews.map((item) => (
                      <ReviewRow key={item.id} item={item} />
                    ))}
                  </div>
                </div>
              )}
            </div>
          </section>

          {/* 右侧:编辑器 + 操作 + 结果 */}
          <section className="flex min-h-0 flex-col overflow-hidden rounded-xl border border-slate-200 bg-white">
            <div className="flex shrink-0 flex-wrap items-center gap-2 border-b border-slate-200 px-3 py-2">
              <select
                value={language}
                onChange={(e) => setLanguage(e.target.value as CodeLanguage)}
                className="rounded-lg border border-slate-200 bg-white px-2 py-1 text-xs text-slate-700 outline-none focus:border-brand-400"
              >
                {(Object.keys(LANGUAGE_LABELS) as CodeLanguage[]).map((lang) => (
                  <option key={lang} value={lang}>
                    {LANGUAGE_LABELS[lang]}
                  </option>
                ))}
              </select>

              <span className="text-[11px] text-slate-400">
                {language === 'JAVA'
                  ? 'Main.java · 完整程序，自己构造输入'
                  : 'main.py · 完整程序，自己构造输入'}
              </span>

              <div className="ml-auto flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => void run()}
                  disabled={running || reviewing}
                  className="flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-300"
                >
                  {running ? <Loader2 size={13} className="animate-spin" /> : <Play size={13} />}
                  运行
                </button>
                <button
                  type="button"
                  onClick={() => void review()}
                  disabled={running || reviewing}
                  className="flex items-center gap-1.5 rounded-lg bg-brand-600 px-3.5 py-1.5 text-xs font-medium text-white transition hover:bg-brand-700 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
                >
                  {reviewing ? <Loader2 size={13} className="animate-spin" /> : <Sparkles size={13} />}
                  {reviewing ? '点评中…' : 'AI 点评'}
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
              {reviewResult ? (
                <ReviewResultView result={reviewResult} />
              ) : runResult ? (
                <ConsoleView result={runResult} />
              ) : (
                <p className="py-2 text-[11px] text-slate-400">
                  点「运行」执行代码并查看控制台输出；点「AI 点评」会请求大模型检查逻辑与边界，并记录做题进度。
                </p>
              )}
            </div>
          </section>
        </div>
      )}
    </div>
  )
}

/**
 * 题面既可能是 Markdown(带配图、代码块),也可能是带换行的纯文本。
 * 纯文本的单个换行在 Markdown 里会被合并成一行,这里补成硬换行,保留原有断行。
 */
function normalizeQuestionContent(content: string): string {
  const lines = content.replace(/\r\n/g, '\n').split('\n')
  let inFence = false
  return lines
    .map((line) => {
      const isFence = /^\s*```/.test(line)
      if (isFence) inFence = !inFence
      if (inFence || isFence || !line.trim()) return line
      // 标题、列表、表格、引用本身就是块级语法,不需要硬换行
      if (/^\s*(#{1,6}\s|[-*+]\s|\d+\.\s|\||>)/.test(line)) return line
      return `${line}  `
    })
    .join('\n')
}

function ReviewResultView({ result }: { result: CodingReviewResult }) {
  return (
    <div>
      <div className="flex flex-wrap items-center gap-2">
        <RunStatusChip status={result.runStatus} />
        <span className="text-[11px] text-slate-500">{PROGRESS_LABELS[result.progressStatus]}</span>
        {result.runtimeMs > 0 && (
          <span className="text-[11px] text-slate-400">{result.runtimeMs}ms</span>
        )}
        <span className="ml-auto rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-700">
          AI 评分 {result.score} / 5
        </span>
      </div>

      {result.comment && (
        <p className="mt-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-700">
          {result.comment}
        </p>
      )}

      <div className="mt-2 grid gap-2 md:grid-cols-2">
        <PointList
          title="已处理"
          tone="ok"
          points={result.hitPoints}
          empty="模型暂未识别到明确的亮点"
        />
        <PointList
          title="需要注意的边界 / 风险"
          tone="warn"
          points={result.missedPoints}
          empty="没有发现明显的遗漏边界"
        />
      </div>

      {result.suggestion && (
        <p className="mt-2 flex items-start gap-1.5 rounded-lg border border-brand-100 bg-brand-50/50 px-3 py-2 text-xs text-brand-800">
          <Lightbulb size={13} className="mt-0.5 shrink-0" />
          <span>{result.suggestion}</span>
        </p>
      )}

      <ConsoleView result={result} />
    </div>
  )
}

function PointList({
  title,
  tone,
  points,
  empty,
}: {
  title: string
  tone: 'ok' | 'warn'
  points: string[]
  empty: string
}) {
  const toneClass = tone === 'ok' ? 'text-brand-700' : 'text-amber-700'
  const Icon = tone === 'ok' ? CheckCircle2 : XCircle
  return (
    <div className="rounded-lg border border-slate-200 px-3 py-2">
      <p className={`flex items-center gap-1 text-[11px] font-medium ${toneClass}`}>
        <Icon size={12} />
        {title}
      </p>
      {points.length > 0 ? (
        <ul className="mt-1 space-y-0.5">
          {points.map((point, index) => (
            <li key={index} className="flex gap-1 text-[11px] leading-5 text-slate-600">
              <span className="text-slate-300">·</span>
              <span>{point}</span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="mt-1 text-[11px] text-slate-400">{empty}</p>
      )}
    </div>
  )
}

function ConsoleView({ result }: { result: CodeRunResult }) {
  const hasStderr = result.stderr.trim().length > 0
  return (
    <div className="mt-2">
      <p className="flex items-center gap-1.5 text-[11px] font-medium text-slate-600">
        <Terminal size={12} className="text-slate-400" />
        控制台输出
        <span className="ml-1 text-slate-400">退出码 {result.exitCode}</span>
      </p>
      <pre className="mt-1 max-h-40 overflow-auto whitespace-pre-wrap rounded-lg bg-slate-900 px-3 py-2 font-mono text-[11px] leading-5 text-slate-100">
        {result.stdout.trim() || '(无标准输出)'}
      </pre>
      {hasStderr && (
        <pre className="mt-1 max-h-32 overflow-auto whitespace-pre-wrap rounded-lg bg-slate-900 px-3 py-2 font-mono text-[11px] leading-5 text-rose-200">
          {result.stderr}
        </pre>
      )}
    </div>
  )
}

function ReviewRow({ item }: { item: CodingReviewView }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1.5">
      <div className="flex items-center gap-2">
        <span className="rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-700">
          {item.score} / 5
        </span>
        <span className="ml-auto text-[11px] text-slate-400">{item.createdAt ?? ''}</span>
      </div>
      {item.comment && <p className="mt-1 text-[11px] leading-5 text-slate-600">{item.comment}</p>}
    </div>
  )
}

function RunStatusChip({ status }: { status: FreeRunStatus }) {
  const tone: Record<FreeRunStatus, string> = {
    SUCCESS: 'bg-brand-50 text-brand-700',
    COMPILE_ERROR: 'bg-rose-50 text-rose-600',
    RUNTIME_ERROR: 'bg-rose-50 text-rose-600',
    TIME_LIMIT_EXCEEDED: 'bg-amber-50 text-amber-700',
    SYSTEM_ERROR: 'bg-rose-50 text-rose-600',
  }
  return (
    <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${tone[status]}`}>
      {RUN_STATUS_LABELS[status] ?? status}
    </span>
  )
}

export default CodingWorkspace
