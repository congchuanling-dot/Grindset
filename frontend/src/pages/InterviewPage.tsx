import { errMessage } from '../utils/error'
import { AlertCircle, Briefcase, Clock3, FileText, Loader2, Sparkles } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  createInterview,
  listInterviews,
  type InterviewSummary,
} from '../api/interview'
import {
  getResume,
  listResumes,
  type ResumeDetail,
  type ResumeSummary,
} from '../api/resume'
import ResumeViewer from '../components/resume/ResumeViewer'
import { SessionStatus } from '../constants/status'

const TYPE_LABELS: Record<string, string> = {
  FRESH_GRADUATE: '应届生',
  INTERN: '找实习',
  EXPERIENCED: '社招',
  UNKNOWN: '未识别',
}

const POSITIONS = [
  '2027 校招 · AI 应用开发工程师',
  '2027 校招 · Java 后端',
  '实习 · 后端开发',
  '社招 · 高级后端',
]

const TONES = [
  { value: 'pressure', label: '压力面' },
  { value: 'middle', label: '适中' },
  { value: 'relaxed', label: '轻松' },
]

const ROUNDS = ['一面 · 技术基础', '二面 · 项目深挖', 'HR 面']

const FOCUS_OPTIONS = ['项目深挖', '基础八股', '系统设计', '场景题']

const QUESTION_COUNTS = [3, 5, 8]

interface StartSettings {
  position: string
  style: string
  round: string
  focus: string[]
}

/**
 * AI 面试入口:选择简历(带预览)、配置岗位/风格/轮次/题数,创建会话后进入对话页。
 * 下方展示历史面试记录。
 */
function InterviewPage() {
  const navigate = useNavigate()

  const [resumes, setResumes] = useState<ResumeSummary[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [detail, setDetail] = useState<ResumeDetail | null>(null)
  const [loadingList, setLoadingList] = useState(true)
  const [loadingDetail, setLoadingDetail] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [questionCount, setQuestionCount] = useState(5)
  const [starting, setStarting] = useState(false)

  const [history, setHistory] = useState<InterviewSummary[]>([])
  const [historyLoading, setHistoryLoading] = useState(true)

  // 进页面拉简历列表,默认选中最新一份
  useEffect(() => {
    let cancelled = false
    listResumes()
      .then((list) => {
        if (cancelled) return
        setResumes(list)
        if (list.length > 0) setSelectedId((prev) => prev ?? list[0].id)
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(errMessage(e))
      })
      .finally(() => {
        if (!cancelled) setLoadingList(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    listInterviews()
      .then((list) => {
        if (!cancelled) setHistory(list)
      })
      .catch(() => {
        // 历史记录失败不打扰主流程
      })
      .finally(() => {
        if (!cancelled) setHistoryLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  // 切换简历加载详情
  useEffect(() => {
    if (!selectedId) {
      setDetail(null)
      return
    }
    let cancelled = false
    setLoadingDetail(true)
    getResume(selectedId)
      .then((data) => {
        if (!cancelled) setDetail(data)
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          setDetail(null)
          setError(errMessage(e))
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingDetail(false)
      })
    return () => {
      cancelled = true
    }
  }, [selectedId])

  async function handleStart(settings: StartSettings) {
    if (!selectedId || starting) return
    if (detail && detail.parseStatus !== 1) {
      setError('这份简历还没有解析成功，无法开始面试')
      return
    }
    setStarting(true)
    setError(null)
    try {
      const session = await createInterview({
        resumeId: selectedId,
        position: settings.position,
        style: settings.style,
        round: settings.round,
        focus: settings.focus,
        questionCount,
      })
      navigate(`/interview/${session.id}`)
    } catch (e) {
      setError(errMessage(e))
      setStarting(false)
    }
  }

  if (loadingList) {
    return (
      <div className="flex h-full items-center justify-center bg-slate-50">
        <Loader2 size={22} className="animate-spin text-brand-600" />
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col overflow-y-auto bg-slate-50">
      <header className="border-b border-slate-200/80 bg-white px-4 py-5 md:px-8">
        <h1 className="text-[22px] font-semibold leading-tight tracking-[-0.02em] text-slate-900">AI 面试</h1>
        <p className="mt-1.5 text-sm text-slate-500">
          选择一份简历并配置面试参数，AI 面试官会按简历与岗位逐题提问，每题会追问 1~3 次。
        </p>
      </header>

      <div className="flex-1 px-4 py-6 md:px-8">
        {error && (
          <div className="mb-4 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
            <AlertCircle size={16} className="mt-0.5 shrink-0" />
            <span className="flex-1">{error}</span>
            <button type="button" className="text-xs text-rose-500 hover:text-rose-700" onClick={() => setError(null)}>
              关闭
            </button>
          </div>
        )}

        {resumes.length === 0 ? (
          <NoResumeState />
        ) : (
          <div className="flex flex-col gap-4 xl:flex-row">
            <section className="min-w-0 flex-1 space-y-3">
              <div className="flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3">
                <span className="text-xs text-slate-400">面试简历</span>
                <select
                  value={selectedId ?? ''}
                  onChange={(e) => setSelectedId(e.target.value)}
                  className="max-w-[300px] truncate rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-700 outline-none focus:border-brand-400"
                >
                  {resumes.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.fileName}
                    </option>
                  ))}
                </select>
                {detail && (
                  <>
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                      {TYPE_LABELS[detail.candidateType] ?? '未识别'}
                    </span>
                    <span className="text-xs text-slate-400">共 {resumes.length} 份</span>
                  </>
                )}
              </div>

              <div className="relative">
                {loadingDetail && (
                  <div className="absolute inset-0 z-10 flex items-center justify-center rounded-xl bg-white/70">
                    <Loader2 size={22} className="animate-spin text-brand-600" />
                  </div>
                )}
                {detail ? (
                  detail.parseStatus === 2 ? (
                    <ParseFailedPanel detail={detail} />
                  ) : (
                    <ResumeViewer detail={detail} />
                  )
                ) : (
                  <div className="flex h-64 items-center justify-center rounded-xl border border-slate-200 bg-white text-sm text-slate-400">
                    选择一份简历查看解析结果
                  </div>
                )}
              </div>
            </section>

            <InterviewSettings
              canStart={Boolean(selectedId) && detail?.parseStatus === 1}
              starting={starting}
              questionCount={questionCount}
              onQuestionCountChange={setQuestionCount}
              onStart={handleStart}
            />
          </div>
        )}

        <HistorySection items={history} loading={historyLoading} />
      </div>
    </div>
  )
}

function ParseFailedPanel({ detail }: { detail: ResumeDetail }) {
  return (
    <div className="flex min-h-[420px] flex-col items-center justify-center rounded-xl border border-rose-200 bg-white px-6 text-center">
      <AlertCircle size={22} className="text-rose-500" />
      <h2 className="mt-3 text-sm font-semibold text-slate-800">这份简历解析失败</h2>
      <p className="mt-2 max-w-md text-sm leading-6 text-slate-500">
        {detail.errorMsg || '未识别到有效内容，请到简历管理页重新上传。'}
      </p>
      <Link
        to="/resume"
        className="mt-5 rounded-lg bg-brand-600 px-3.5 py-2 text-sm font-medium text-white transition hover:bg-brand-700"
      >
        去简历管理
      </Link>
    </div>
  )
}

function NoResumeState() {
  return (
    <div className="flex min-h-[360px] flex-col items-center justify-center rounded-xl border-2 border-dashed border-slate-200 bg-white px-6 text-center">
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-brand-50 text-brand-600">
        <FileText size={22} />
      </span>
      <h2 className="mt-4 text-sm font-semibold text-slate-800">还没有可用的简历</h2>
      <p className="mt-2 max-w-sm text-xs leading-5 text-slate-500">
        先上传一份简历，AI 面试才能按你的经历和技能出题。
      </p>
      <Link
        to="/resume"
        className="mt-5 rounded-lg bg-brand-600 px-3.5 py-2 text-sm font-medium text-white transition hover:bg-brand-700"
      >
        去上传简历
      </Link>
    </div>
  )
}

/** 面试设置:题数可选 3/5/8,开始后创建会话并进入对话页 */
function InterviewSettings({
  canStart,
  starting,
  questionCount,
  onQuestionCountChange,
  onStart,
}: {
  canStart: boolean
  starting: boolean
  questionCount: number
  onQuestionCountChange: (value: number) => void
  onStart: (settings: StartSettings) => void
}) {
  const [position, setPosition] = useState(POSITIONS[0])
  const [tone, setTone] = useState('middle')
  const [round, setRound] = useState(ROUNDS[0])
  const [focus, setFocus] = useState<string[]>(['项目深挖', '基础八股'])

  function toggleFocus(item: string) {
    setFocus((prev) => (prev.includes(item) ? prev.filter((v) => v !== item) : [...prev, item]))
  }

  return (
    <aside className="w-full shrink-0 xl:w-[300px]">
      <div className="rounded-xl border border-slate-200 bg-white p-4">
        <div className="flex items-center gap-2 text-sm font-semibold text-slate-800">
          <Sparkles size={16} className="text-brand-600" />
          面试设置
        </div>
        <p className="mt-1 text-[11px] text-slate-400">岗位与简历决定出题方向</p>

        <label className="mt-4 block text-xs text-slate-500">
          面试职位
          <select
            value={position}
            onChange={(e) => setPosition(e.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-700 outline-none focus:border-brand-400"
          >
            {POSITIONS.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
        </label>

        <div className="mt-4">
          <span className="text-xs text-slate-500">面试风格</span>
          <div className="mt-1 grid grid-cols-3 gap-1">
            {TONES.map((item) => (
              <button
                key={item.value}
                type="button"
                onClick={() => setTone(item.value)}
                className={`rounded-lg border px-1 py-1.5 text-xs transition ${
                  tone === item.value
                    ? 'border-brand-300 bg-brand-50 font-medium text-brand-700'
                    : 'border-slate-200 text-slate-500 hover:border-slate-300'
                }`}
              >
                {item.label}
              </button>
            ))}
          </div>
        </div>

        <label className="mt-4 block text-xs text-slate-500">
          面试轮次
          <select
            value={round}
            onChange={(e) => setRound(e.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-700 outline-none focus:border-brand-400"
          >
            {ROUNDS.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
        </label>

        <label className="mt-4 block text-xs text-slate-500">
          题目数量
          <select
            value={questionCount}
            onChange={(e) => onQuestionCountChange(Number(e.target.value))}
            className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-700 outline-none focus:border-brand-400"
          >
            {QUESTION_COUNTS.map((item) => (
              <option key={item} value={item}>
                {item} 题
              </option>
            ))}
          </select>
        </label>

        <div className="mt-4">
          <span className="text-xs text-slate-500">侧重方向</span>
          <div className="mt-1 flex flex-wrap gap-1.5">
            {FOCUS_OPTIONS.map((item) => (
              <button
                key={item}
                type="button"
                onClick={() => toggleFocus(item)}
                className={`rounded-full border px-2.5 py-1 text-xs transition ${
                  focus.includes(item)
                    ? 'border-brand-300 bg-brand-50 text-brand-700'
                    : 'border-slate-200 text-slate-500 hover:border-slate-300'
                }`}
              >
                {item}
              </button>
            ))}
          </div>
        </div>

        <button
          type="button"
          disabled={!canStart || starting}
          onClick={() => onStart({ position, style: tone, round, focus })}
          className="mt-5 flex w-full items-center justify-center gap-1.5 rounded-lg bg-brand-600 py-2 text-sm font-medium text-white transition hover:bg-brand-700 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
        >
          {starting ? <Loader2 size={15} className="animate-spin" /> : <Briefcase size={15} />}
          开始 AI 面试
        </button>
        {!canStart && (
          <p className="mt-2 text-[11px] leading-5 text-slate-400">需要一份解析成功的简历才能开始</p>
        )}
      </div>
    </aside>
  )
}

function HistorySection({ items, loading }: { items: InterviewSummary[]; loading: boolean }) {
  const navigate = useNavigate()

  if (loading) {
    return (
      <div className="mt-6 flex items-center gap-2 text-xs text-slate-400">
        <Loader2 size={13} className="animate-spin" />
        加载面试记录…
      </div>
    )
  }
  if (items.length === 0) return null

  return (
    <section className="mt-8">
      <h2 className="text-sm font-semibold text-slate-800">面试记录</h2>
      <div className="mt-2 grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
        {items.map((item) => (
          <button
            key={item.id}
            type="button"
            onClick={() => navigate(`/interview/${item.id}`)}
            className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-left transition hover:border-brand-300"
          >
            <div className="flex items-start justify-between gap-2">
              <span className="line-clamp-1 text-sm font-medium text-slate-800">{item.position}</span>
              {item.status === SessionStatus.FINISHED ? (
                <span className="shrink-0 rounded-full bg-brand-50 px-2 py-0.5 text-[11px] text-brand-700">
                  {item.overallScore != null ? `${item.overallScore} 分` : '已结束'}
                </span>
              ) : (
                <span className="shrink-0 rounded-full bg-amber-50 px-2 py-0.5 text-[11px] text-amber-700">
                  进行中
                </span>
              )}
            </div>
            <p className="mt-1 line-clamp-1 text-xs text-slate-400">{item.resumeFileName}</p>
            <div className="mt-2 flex items-center gap-3 text-[11px] text-slate-400">
              <span>
                已完成 {item.completedQuestions}/{item.questionCount} 题
              </span>
              <span className="flex items-center gap-1">
                <Clock3 size={11} />
                {item.createdAt}
              </span>
            </div>
          </button>
        ))}
      </div>
    </section>
  )
}


export default InterviewPage
