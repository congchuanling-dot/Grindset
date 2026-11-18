import {
  AlertCircle,
  BarChart3,
  BookX,
  Brain,
  Check,
  FileText,
  Globe,
  ListChecks,
  Loader2,
  MessagesSquare,
  Wrench,
} from 'lucide-react'
import type { AgentToolStep } from '../api/agent'

const ICONS = {
  get_knowledge_profile: Brain,
  get_wrong_questions: BookX,
  get_question_stats: BarChart3,
  list_questions: ListChecks,
  list_recent_interviews: MessagesSquare,
  web_search: Globe,
  fetch_url: FileText,
} as const

/**
 * Agent 单次工具调用卡片:执行中转圈,完成后打勾并显示一行结论。
 */
function AgentToolCard({ step }: { step: AgentToolStep }) {
  const Icon = ICONS[step.name as keyof typeof ICONS] ?? Wrench
  const argsText = formatArgs(step.args)
  const running = step.status === 'running'
  const failed = step.status === 'failed'

  return (
    <div className="rounded-xl border border-slate-200 bg-white px-3 py-2">
      <div className="flex items-center gap-2 text-xs">
        <Icon size={14} className="shrink-0 text-slate-400" />
        <span className="shrink-0 font-medium text-slate-700">{step.label}</span>
        {argsText && (
          <span className="truncate text-slate-400" title={argsText}>
            {argsText}
          </span>
        )}
        <span className="ml-auto shrink-0">
          {running ? (
            <Loader2 size={13} className="animate-spin text-teal-600" />
          ) : failed ? (
            <AlertCircle size={13} className="text-rose-500" />
          ) : (
            <Check size={13} className="text-teal-600" />
          )}
        </span>
      </div>
      {step.summary && (
        <p className={`mt-1 pl-6 text-xs ${failed ? 'text-rose-500' : 'text-slate-500'}`}>
          {step.summary}
        </p>
      )}
    </div>
  )
}

/** 参数拼成 level=WEAK · limit=20 这样的紧凑形式 */
function formatArgs(args: Record<string, unknown>): string {
  return Object.entries(args ?? {})
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([key, value]) => `${key}=${String(value)}`)
    .join(' · ')
}

export default AgentToolCard
