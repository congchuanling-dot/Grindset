import Editor, { loader } from '@monaco-editor/react'
import {
  CheckCircle2,
  FileText,
  Loader2,
  Play,
  Terminal,
  XCircle,
} from 'lucide-react'
import * as monaco from 'monaco-editor'
import editorWorker from 'monaco-editor/editor/editor.worker?worker'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { InterviewCodingView, InterviewRunResult } from '../../api/interview'
import type { CodeLanguage } from '../../api/questionBank'
import MarkdownContent from '../MarkdownContent'

self.MonacoEnvironment = { getWorker: () => new editorWorker() }
loader.config({ monaco })

export interface FreeCodingDraft {
  code: string
  language: CodeLanguage
}

interface FreeCodingWorkbenchProps {
  round: InterviewCodingView
  onRun: (language: CodeLanguage, code: string) => Promise<InterviewRunResult>
  onComplete: () => Promise<void>
  onDraftChange: (draft: FreeCodingDraft) => void
}

const LANGUAGE_LABELS: Record<CodeLanguage, string> = {
  JAVA: 'Java',
  PYTHON: 'Python',
}

/**
 * FREE_RUN 面试编码工作台:只有 main 骨架,没有测试用例。
 * 候选人写完整程序,点运行后看控制台 stdout/stderr。
 */
function FreeCodingWorkbench({ round, onRun, onComplete, onDraftChange }: FreeCodingWorkbenchProps) {
  const [language, setLanguage] = useState<CodeLanguage>(round.codeLanguage ?? 'JAVA')
  const [codes, setCodes] = useState<Record<string, string>>(() => initialCodes(round))
  const [running, setRunning] = useState(false)
  const [completing, setCompleting] = useState(false)
  const [result, setResult] = useState<InterviewRunResult | null>(round.latestRun)
  const [error, setError] = useState<string | null>(null)
  const draftRef = useRef(onDraftChange)
  draftRef.current = onDraftChange

  const code = codes[language] ?? ''

  useEffect(() => {
    draftRef.current({ code, language })
  }, [code, language])

  const changeLanguage = useCallback((next: CodeLanguage) => {
    setLanguage(next)
    setCodes((prev) => ({
      ...prev,
      [next]: prev[next] ?? round.starterCode[next] ?? '',
    }))
  }, [round.starterCode])

  const run = useCallback(async () => {
    if (running || completing) return
    setRunning(true)
    setError(null)
    try {
      setResult(await onRun(language, code))
    } catch (e) {
      setError(e instanceof Error ? e.message : '运行失败')
    } finally {
      setRunning(false)
    }
  }, [code, completing, language, onRun, running])

  const complete = useCallback(async () => {
    if (completing || running) return
    setCompleting(true)
    setError(null)
    try {
      await onComplete()
    } catch (e) {
      setError(e instanceof Error ? e.message : '完成本题失败')
    } finally {
      setCompleting(false)
    }
  }, [completing, onComplete, running])

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden rounded-xl border border-white/10 bg-[#1b1e23]">
      <div className="flex shrink-0 flex-wrap items-center gap-2 border-b border-white/10 px-3 py-2">
        <span className="rounded-full bg-brand-500/15 px-2.5 py-1 text-[11px] font-medium text-brand-300">
          开放式编码
        </span>
        <span className="text-[11px] text-white/40">只有 main 骨架，没有测试用例</span>

        <select
          value={language}
          onChange={(e) => changeLanguage(e.target.value as CodeLanguage)}
          className="ml-auto rounded-lg border border-white/15 bg-[#24282f] px-2 py-1 text-xs text-white/80 outline-none"
        >
          {(Object.keys(LANGUAGE_LABELS) as CodeLanguage[]).map((lang) => (
            <option key={lang} value={lang}>
              {LANGUAGE_LABELS[lang]}
            </option>
          ))}
        </select>

        <button
          type="button"
          onClick={() => void run()}
          disabled={running || completing}
          className="flex items-center gap-1.5 rounded-lg bg-brand-600 px-3.5 py-1.5 text-xs font-medium text-white transition hover:bg-brand-700 disabled:cursor-not-allowed disabled:bg-white/10 disabled:text-white/30"
        >
          {running ? <Loader2 size={13} className="animate-spin" /> : <Play size={13} />}
          {running ? '运行中…' : '运行'}
        </button>
        <button
          type="button"
          onClick={() => void complete()}
          disabled={running || completing}
          className="flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3.5 py-1.5 text-xs font-medium text-white transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-white/10 disabled:text-white/30"
        >
          {completing ? <Loader2 size={13} className="animate-spin" /> : <CheckCircle2 size={13} />}
          完成本题
        </button>
      </div>

      {error && (
        <div className="flex shrink-0 items-start gap-2 border-b border-rose-400/20 bg-rose-500/10 px-3 py-2 text-xs text-rose-200">
          <XCircle size={14} className="mt-0.5 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      <div className="grid min-h-0 flex-1 grid-cols-1 gap-3 overflow-y-auto p-3 lg:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)] lg:overflow-hidden">
        <section className="flex min-h-[260px] flex-col overflow-hidden rounded-lg border border-white/10 bg-white">
          <div className="flex shrink-0 items-center gap-1.5 border-b border-slate-200 px-3 py-2 text-[11px] text-slate-500">
            <FileText size={13} className="text-brand-600" />
            题目描述
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
            <h2 className="text-sm font-semibold text-slate-800">{round.title}</h2>
            <div className="mt-2 text-xs leading-6 text-slate-600">
              <MarkdownContent content={round.content ?? ''} />
            </div>
            <p className="mt-4 rounded-lg border border-brand-100 bg-brand-50/60 px-3 py-2 text-[11px] leading-5 text-brand-700">
              平台不提供测试用例。请在 main 里自己构造输入和调用，用控制台打印结果；
              写的过程中可以直接说话问我。
            </p>
          </div>
        </section>

        <section className="flex min-h-[360px] flex-col overflow-hidden rounded-lg border border-white/10 bg-[#202329]">
          <div className="flex shrink-0 items-center gap-2 border-b border-white/10 px-3 py-2">
            <span className="text-[11px] text-white/45">
              {language === 'JAVA' ? 'Main.java · 完整 main 程序' : 'main.py · 完整程序'}
            </span>
            <span className="ml-auto text-[11px] text-white/30">控制台输出调试信息</span>
          </div>
          <div
            className="min-h-[220px] flex-1"
            onKeyDown={(event) => {
              if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') {
                event.preventDefault()
                void run()
              }
            }}
          >
            <Editor
              height="100%"
              language={language === 'JAVA' ? 'java' : 'python'}
              theme="vs-dark"
              value={code}
              onChange={(value) => setCodes((prev) => ({ ...prev, [language]: value ?? '' }))}
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

          <div className="h-[38%] min-h-[120px] shrink-0 overflow-y-auto border-t border-white/10 bg-[#171a1f] px-3 py-2">
            <p className="flex items-center gap-1.5 text-[11px] font-medium text-white/45">
              <Terminal size={12} />
              控制台
            </p>
            {result ? <ConsoleView result={result} /> : (
              <p className="mt-2 text-[11px] text-white/25">
                点击运行后，这里显示 stdout / stderr / 退出码。
              </p>
            )}
          </div>
        </section>
      </div>
    </div>
  )
}

function ConsoleView({ result }: { result: InterviewRunResult }) {
  const success = result.status === 'SUCCESS'
  return (
    <div className="mt-2 space-y-2">
      <div className="flex flex-wrap items-center gap-2 text-[11px]">
        <span className={`rounded-full px-2 py-0.5 ${success ? 'bg-emerald-500/15 text-emerald-300' : 'bg-rose-500/15 text-rose-300'}`}>
          {result.status}
        </span>
        <span className="text-white/40">退出码 {result.exitCode}</span>
        <span className="text-white/30">{result.runtimeMs}ms</span>
      </div>
      <ConsoleBlock title="stdout" value={result.stdout} tone="normal" />
      <ConsoleBlock title="stderr" value={result.stderr} tone="error" />
    </div>
  )
}

function ConsoleBlock({ title, value, tone }: { title: string; value: string; tone: 'normal' | 'error' }) {
  if (!value) return null
  return (
    <div>
      <p className="text-[10px] uppercase tracking-wide text-white/25">{title}</p>
      <pre className={`mt-1 max-h-40 overflow-auto whitespace-pre-wrap rounded-lg px-2.5 py-2 font-mono text-[11px] leading-5 ${
        tone === 'error' ? 'bg-rose-500/10 text-rose-200' : 'bg-black/30 text-white/70'
      }`}>
        {value}
      </pre>
    </div>
  )
}

function initialCodes(round: InterviewCodingView): Record<string, string> {
  const language = round.codeLanguage ?? 'JAVA'
  return {
    JAVA: language === 'JAVA' && round.codeDraft
      ? round.codeDraft
      : (round.starterCode.JAVA ?? ''),
    PYTHON: language === 'PYTHON' && round.codeDraft
      ? round.codeDraft
      : (round.starterCode.PYTHON ?? ''),
  }
}

export default FreeCodingWorkbench
