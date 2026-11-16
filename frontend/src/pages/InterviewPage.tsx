import { AlertCircle, Briefcase, Eye, FileText, Loader2, Sparkles } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  getResume,
  listResumes,
  type ResumeDetail,
  type ResumeSummary,
} from '../api/resume'
import ResumePaper from '../components/resume/ResumePaper'

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

type PreviewTab = 'original' | 'text'

/**
 * AI 面试入口:选择一份简历(带预览),配置岗位/风格/轮次,再开始面试。
 * 面试对话尚未接入,预览的原件也暂不保存,这里只做入口与占位。
 */
function InterviewPage() {
  const [resumes, setResumes] = useState<ResumeSummary[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [detail, setDetail] = useState<ResumeDetail | null>(null)
  const [loadingList, setLoadingList] = useState(true)
  const [loadingDetail, setLoadingDetail] = useState(false)
  const [tab, setTab] = useState<PreviewTab>('text')
  const [error, setError] = useState<string | null>(null)

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

  if (loadingList) {
    return (
      <div className="flex h-full items-center justify-center bg-slate-50">
        <Loader2 size={22} className="animate-spin text-teal-600" />
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col overflow-y-auto bg-slate-50">
      <header className="border-b border-slate-200 bg-white px-4 py-4 md:px-6">
        <h1 className="text-base font-semibold text-slate-800">AI 面试</h1>
        <p className="mt-1 text-xs text-slate-500">
          选择一份简历并配置面试参数，后续由 AI 面试官按简历与岗位追问。
        </p>
      </header>

      <div className="flex-1 px-4 py-4 md:px-6">
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
            <section className="min-w-0 flex-1">
              <div className="flex min-h-[560px] flex-col overflow-hidden rounded-xl border border-slate-200 bg-white">
                <div className="flex flex-wrap items-center gap-3 border-b border-slate-200 px-4 py-3">
                  <span className="text-xs text-slate-400">面试简历</span>
                  <select
                    value={selectedId ?? ''}
                    onChange={(e) => {
                      setSelectedId(e.target.value)
                      setTab('text')
                    }}
                    className="max-w-[300px] truncate rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-700 outline-none focus:border-teal-400"
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

                <div className="flex items-center gap-1 border-b border-slate-200 px-3 py-2">
                  <TabButton active={tab === 'original'} onClick={() => setTab('original')} icon={<Eye size={15} />}>
                    原件
                  </TabButton>
                  <TabButton active={tab === 'text'} onClick={() => setTab('text')} icon={<FileText size={15} />}>
                    文字版
                  </TabButton>
                </div>

                <div className="relative flex-1">
                  {loadingDetail && (
                    <div className="absolute inset-0 z-10 flex items-center justify-center bg-white/70">
                      <Loader2 size={22} className="animate-spin text-teal-600" />
                    </div>
                  )}
                  {tab === 'original' ? (
                    <OriginalPlaceholder />
                  ) : detail ? (
                    detail.parseStatus === 2 ? (
                      <div className="flex h-full min-h-[420px] flex-col items-center justify-center px-6 text-center">
                        <AlertCircle size={22} className="text-rose-500" />
                        <h2 className="mt-3 text-sm font-semibold text-slate-800">这份简历解析失败</h2>
                        <p className="mt-2 max-w-md text-sm leading-6 text-slate-500">
                          {detail.errorMsg || '未识别到有效内容，请到简历管理页重新上传。'}
                        </p>
                        <Link
                          to="/resume"
                          className="mt-5 rounded-lg bg-teal-600 px-3.5 py-2 text-sm font-medium text-white transition hover:bg-teal-700"
                        >
                          去简历管理
                        </Link>
                      </div>
                    ) : (
                      <div className="h-full overflow-auto bg-slate-100 p-4">
                        <ResumePaper detail={detail} />
                      </div>
                    )
                  ) : (
                    <div className="flex h-full min-h-[420px] items-center justify-center text-sm text-slate-400">
                      选择一份简历查看解析结果
                    </div>
                  )}
                </div>
              </div>
            </section>

            <InterviewSettings />
          </div>
        )}
      </div>
    </div>
  )
}

function NoResumeState() {
  return (
    <div className="flex min-h-[360px] flex-col items-center justify-center rounded-xl border-2 border-dashed border-slate-200 bg-white px-6 text-center">
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-teal-50 text-teal-600">
        <FileText size={22} />
      </span>
      <h2 className="mt-4 text-sm font-semibold text-slate-800">还没有可用的简历</h2>
      <p className="mt-2 max-w-sm text-xs leading-5 text-slate-500">
        先上传一份简历，AI 面试才能按你的经历和技能出题。
      </p>
      <Link
        to="/resume"
        className="mt-5 rounded-lg bg-teal-600 px-3.5 py-2 text-sm font-medium text-white transition hover:bg-teal-700"
      >
        去上传简历
      </Link>
    </div>
  )
}

function OriginalPlaceholder() {
  return (
    <div className="flex h-full min-h-[420px] flex-col items-center justify-center px-6 text-center">
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
        <Eye size={22} />
      </span>
      <h2 className="mt-4 text-sm font-semibold text-slate-800">原件预览暂未开放</h2>
      <p className="mt-2 max-w-sm text-xs leading-5 text-slate-500">
        简历原件暂不保存，当前只能查看解析后的文字版。原件预览会在后续版本接入。
      </p>
    </div>
  )
}

function TabButton({
  active,
  onClick,
  icon,
  children,
}: {
  active: boolean
  onClick: () => void
  icon: React.ReactNode
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm transition ${
        active ? 'bg-teal-50 font-medium text-teal-700' : 'text-slate-500 hover:bg-slate-100'
      }`}
    >
      {icon}
      {children}
    </button>
  )
}

/** 面试设置:前端状态，暂不落库；“开始”按钮等面试接入后再开放 */
function InterviewSettings() {
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
          <Sparkles size={16} className="text-teal-600" />
          面试设置
        </div>
        <p className="mt-1 text-[11px] text-slate-400">先保存选择，AI 面试接入后生效</p>

        <label className="mt-4 block text-xs text-slate-500">
          面试职位
          <select
            value={position}
            onChange={(e) => setPosition(e.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-700 outline-none focus:border-teal-400"
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
                    ? 'border-teal-300 bg-teal-50 font-medium text-teal-700'
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
            className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-700 outline-none focus:border-teal-400"
          >
            {ROUNDS.map((item) => (
              <option key={item} value={item}>
                {item}
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
                    ? 'border-teal-300 bg-teal-50 text-teal-700'
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
          disabled
          title="AI 面试功能建设中，暂未开放"
          className="mt-5 flex w-full cursor-not-allowed items-center justify-center gap-1.5 rounded-lg bg-slate-200 py-2 text-sm font-medium text-slate-400"
        >
          <Briefcase size={15} />
          开始 AI 面试
        </button>
        <p className="mt-2 text-[11px] leading-5 text-slate-400">AI 面试功能建设中，暂未开放</p>
      </div>
    </aside>
  )
}

function errMessage(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

export default InterviewPage
