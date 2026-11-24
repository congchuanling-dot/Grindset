import { AlertCircle, CheckCircle2, CircleDot } from 'lucide-react'
import type { QuestionStatus, QuestionType } from '../../api/questionBank'

export const TYPE_LABELS: Record<QuestionType, string> = {
  BASICS: '八股',
  SCENARIO: '场景',
  PROJECT: '项目',
  CODE: '编程',
}

export function StatusBadge({ status }: { status: QuestionStatus }) {
  if (status === 'DONE') {
    return (
      <span className="flex items-center gap-1 rounded-full bg-teal-50 px-2 py-0.5 text-xs text-teal-700">
        <CheckCircle2 size={11} />
        已做
      </span>
    )
  }
  if (status === 'WRONG') {
    return (
      <span className="flex items-center gap-1 rounded-full bg-rose-50 px-2 py-0.5 text-xs text-rose-600">
        <AlertCircle size={11} />
        错题
      </span>
    )
  }
  return (
    <span className="flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500">
      <CircleDot size={11} />
      未做
    </span>
  )
}

export function difficultyLabel(difficulty: number): string {
  if (difficulty === 1) return '简单'
  if (difficulty === 3) return '困难'
  return '中等'
}

export function difficultyClass(difficulty: number): string {
  if (difficulty === 1) return 'bg-teal-50 text-teal-700'
  if (difficulty === 3) return 'bg-rose-50 text-rose-600'
  return 'bg-amber-50 text-amber-700'
}

export function DifficultyBadge({ difficulty }: { difficulty: number }) {
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs ${difficultyClass(difficulty)}`}>
      {difficultyLabel(difficulty)}
    </span>
  )
}
