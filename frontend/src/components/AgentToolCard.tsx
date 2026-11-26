import {
  AlertCircle,
  BarChart3,
  Check,
  ChevronRight,
  FileText,
  Globe,
  ListChecks,
  Loader2,
  MessagesSquare,
  Wrench,
} from 'lucide-react'
import type { AgentToolStep } from '../api/agent'

const ICONS = {
  get_question_stats: BarChart3,
  list_questions: ListChecks,
  list_recent_interviews: MessagesSquare,
  get_my_resume: FileText,
  web_search: Globe,
  fetch_url: FileText,
} as const

/**
 * Agent 单次工具调用卡片:执行中转圈,完成后打勾并显示一行结论。
 * 工具返回选择型数据(card=selector)时,额外渲染可点击的选项列表。
 */
function AgentToolCard({
  step,
  onSelect,
}: {
  step: AgentToolStep
  /** prompt 是发给模型的真实提问,display 是气泡展示文案(不含内部 ID) */
  onSelect?: (prompt: string, display: string) => void
}) {
  const Icon = ICONS[step.name as keyof typeof ICONS] ?? Wrench
  const argsText = formatArgs(step.args)
  const running = step.status === 'running'
  const failed = step.status === 'failed'
  const selector = step.status === 'done' ? readSelector(step.data) : null

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
      {selector && (
        <div className="mt-2 space-y-1.5 pl-6">
          <p className="text-xs text-slate-500">{selector.title}</p>
          {selector.items.map((item) => (
            <button
              key={item.key}
              type="button"
              disabled={!onSelect}
              onClick={() => onSelect?.(item.prompt, item.display ?? item.title)}
              className="flex w-full items-center gap-2 rounded-lg border border-slate-200 px-2.5 py-2 text-left transition hover:border-teal-300 hover:bg-teal-50/60 disabled:cursor-default disabled:hover:border-slate-200 disabled:hover:bg-transparent"
            >
              <FileText size={14} className="shrink-0 text-teal-600" />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-xs font-medium text-slate-700">
                  {item.title}
                </span>
                {item.subtitle && (
                  <span className="block truncate text-[11px] text-slate-400">
                    {item.subtitle}
                  </span>
                )}
              </span>
              <ChevronRight size={14} className="shrink-0 text-slate-300" />
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

/** 参数拼成 level=WEAK · limit=20 这样的紧凑形式;ID 类参数不外露 */
function formatArgs(args: Record<string, unknown>): string {
  return Object.entries(args ?? {})
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .filter(([key]) => !key.toLowerCase().endsWith('id'))
    .map(([key, value]) => `${key}=${String(value)}`)
    .join(' · ')
}

interface SelectorItem {
  key: string
  title: string
  subtitle?: string
  prompt: string
  /** 点击后展示在气泡里的文案;缺省用 title */
  display?: string
}

interface SelectorCard {
  title: string
  items: SelectorItem[]
}

/** 工具数据结构约定:card=selector + items[{key,title,subtitle,prompt}] */
function readSelector(data?: Record<string, unknown>): SelectorCard | null {
  if (!data || data.card !== 'selector' || !Array.isArray(data.items)) return null
  const items = data.items.filter(isSelectorItem)
  if (items.length === 0) return null
  return {
    title: typeof data.title === 'string' ? data.title : '请选择',
    items,
  }
}

function isSelectorItem(value: unknown): value is SelectorItem {
  if (typeof value !== 'object' || value === null) return false
  const item = value as Record<string, unknown>
  return (
    typeof item.key === 'string'
    && typeof item.title === 'string'
    && typeof item.prompt === 'string'
  )
}

export default AgentToolCard
