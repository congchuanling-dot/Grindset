import { errMessage } from '../../utils/error'
import { ArrowRight, BookOpen, Keyboard, Mic, Shuffle } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { getQuestionStats, getRandomQuestionId, type QuestionBankSummary } from '../../api/questionBank'

/** 题库首页右侧的引导页:没选具体题目时展示 */
function QuestionOverview() {
  const navigate = useNavigate()
  const [summary, setSummary] = useState<QuestionBankSummary | null>(null)
  const [randomizing, setRandomizing] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    getQuestionStats('TEXT')
      .then(setSummary)
      .catch(() => setSummary(null))
  }, [])

  async function openRandom() {
    if (randomizing) return
    setRandomizing(true)
    setError('')
    try {
      navigate(`/questions/${await getRandomQuestionId(undefined, 'TEXT')}`)
    } catch (e) {
      setError(errMessage(e))
    } finally {
      setRandomizing(false)
    }
  }

  const total = summary?.total ?? 0
  const attempted = (summary?.done ?? 0) + (summary?.wrong ?? 0)
  const percent = total === 0 ? 0 : Math.round((attempted / total) * 100)

  return (
    <div className="mx-auto max-w-3xl px-5 py-8 md:px-8">
      <div className="rounded-2xl border border-slate-200 bg-white px-6 py-8 md:px-8">
        <p className="text-xs font-medium text-teal-600">题库 · 问答题</p>
        <h1 className="mt-1 text-2xl font-semibold text-slate-800">从左侧选一道题开始</h1>
        <p className="mt-2 text-sm leading-6 text-slate-500">
          左侧按领域、难度和做题状态筛选题目；选中后右侧直接作答，支持语音输入和草稿自动保存。
        </p>

        <div className="mt-6 grid gap-3 sm:grid-cols-3">
          <Stat label="题目总数" value={total} />
          <Stat label="已覆盖" value={attempted} />
          <Stat label="错题" value={summary?.wrong ?? 0} tone="rose" />
        </div>

        <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100">
          <div className="h-full rounded-full bg-teal-500" style={{ width: `${percent}%` }} />
        </div>
        <p className="mt-1 text-xs text-slate-400">已覆盖 {percent}%</p>

        {error && <p className="mt-3 text-xs text-rose-600">{error}</p>}

        <div className="mt-6 flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => void openRandom()}
            disabled={randomizing}
            className="flex items-center gap-1.5 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-teal-700 disabled:opacity-60"
          >
            <Shuffle size={15} />
            随机一题
          </button>
          <Link
            to="/questions/coding"
            className="flex items-center gap-1.5 rounded-lg border border-slate-200 px-4 py-2 text-sm text-slate-600 transition hover:bg-slate-50"
          >
            <Keyboard size={15} />
            去编程题
            <ArrowRight size={14} />
          </Link>
        </div>
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-3">
        <Tip icon={<BookOpen size={15} />} title="左侧列表" text="筛选题目的同时保留滚动位置" />
        <Tip icon={<Mic size={15} />} title="语音作答" text="点麦克风直接说，识别结果追加到草稿" />
        <Tip icon={<Keyboard size={15} />} title="快捷键" text="Ctrl / ⌘ + Enter 提交作答" />
      </div>
    </div>
  )
}

function Stat({ label, value, tone = 'slate' }: { label: string; value: number; tone?: 'slate' | 'rose' }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-slate-50/60 px-4 py-3">
      <p className="text-xs text-slate-400">{label}</p>
      <p className={`mt-1 text-2xl font-semibold ${tone === 'rose' ? 'text-rose-600' : 'text-slate-800'}`}>
        {value}
      </p>
    </div>
  )
}

function Tip({ icon, title, text }: { icon: ReactNode; title: string; text: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-4 py-3">
      <p className="flex items-center gap-1.5 text-sm font-medium text-slate-700">
        <span className="text-teal-600">{icon}</span>
        {title}
      </p>
      <p className="mt-1 text-xs leading-5 text-slate-400">{text}</p>
    </div>
  )
}

export default QuestionOverview
