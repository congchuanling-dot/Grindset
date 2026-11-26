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
    <div className="mx-auto max-w-3xl px-5 py-10 md:px-10">
      <div className="border-y border-slate-200 bg-white px-6 py-9 md:px-10">
        <h1 className="text-[28px] font-semibold leading-tight tracking-[-0.03em] text-slate-900">
          从左侧选一道题开始
        </h1>
        <p className="mt-3 max-w-lg text-sm leading-6 text-slate-500">
          左侧按领域、难度和做题状态筛选题目；选中后右侧直接作答，支持语音输入和草稿自动保存。
        </p>

        <div className="mt-8 grid grid-cols-3 divide-x divide-slate-200 border-y border-slate-200">
          <Stat label="题目总数" value={total} />
          <Stat label="已覆盖" value={attempted} />
          <Stat label="错题" value={summary?.wrong ?? 0} tone="rose" />
        </div>

        <div className="mt-4 h-1 bg-slate-200">
          <div
            className="h-full bg-marker transition-all duration-500"
            style={{ width: `${percent}%` }}
          />
        </div>
        <p className="mt-2 text-xs tabular-nums text-slate-500">已覆盖 {percent}%</p>

        {error && <p className="mt-3 text-xs text-rose-600">{error}</p>}

        <div className="mt-7 flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => void openRandom()}
            disabled={randomizing}
            className="flex items-center gap-1.5 bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-black disabled:opacity-60"
          >
            <Shuffle size={15} />
            随机一题
          </button>
          <Link
            to="/questions/coding"
            className="flex items-center gap-1.5 border border-slate-300 px-4 py-2 text-sm text-slate-600 transition hover:border-slate-900 hover:text-slate-900"
          >
            <Keyboard size={15} />
            去编程题
            <ArrowRight size={14} />
          </Link>
        </div>
      </div>

      <div className="mt-6 grid border-t border-slate-200 sm:grid-cols-3 sm:divide-x sm:divide-slate-200">
        <Tip icon={<BookOpen size={15} />} title="左侧列表" text="筛选题目的同时保留滚动位置" />
        <Tip icon={<Mic size={15} />} title="语音作答" text="点麦克风直接说，识别结果追加到草稿" />
        <Tip icon={<Keyboard size={15} />} title="快捷键" text="Ctrl / ⌘ + Enter 提交作答" />
      </div>
    </div>
  )
}

function Stat({ label, value, tone = 'slate' }: { label: string; value: number; tone?: 'slate' | 'rose' }) {
  return (
    <div className="px-4 py-4 first:pl-0 last:pr-0">
      <p className="text-xs text-slate-500">{label}</p>
      <p
        className={`mt-1.5 text-[28px] font-semibold leading-none tabular-nums ${
          tone === 'rose' ? 'text-rose-600' : 'text-slate-900'
        }`}
      >
        {value}
      </p>
    </div>
  )
}

function Tip({ icon, title, text }: { icon: ReactNode; title: string; text: string }) {
  return (
    <div className="py-4 sm:px-5">
      <p className="flex items-center gap-1.5 text-sm font-medium text-slate-800">
        <span className="text-slate-400">{icon}</span>
        {title}
      </p>
      <p className="mt-1 text-xs leading-5 text-slate-500">{text}</p>
    </div>
  )
}

export default QuestionOverview
