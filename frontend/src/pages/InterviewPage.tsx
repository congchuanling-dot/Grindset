import { errMessage } from '../utils/error'
import { AlertCircle, Briefcase, FileText, Loader2, Sparkles, Target, Wand2, Zap } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  createInterview,
  previewInterviewPlan,
  type InterviewPlanPreview,
  type JdProfile,
} from '../api/interview'
import {
  getResume,
  listResumes,
  type ResumeDetail,
  type ResumeSummary,
} from '../api/resume'
import ResumeViewer from '../components/resume/ResumeViewer'

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

const HR_FOCUS_OPTIONS = ['行为面', '动机与职业规划', '团队协作', '稳定性']

const QUESTION_COUNTS = [3, 5, 8]

const DURATIONS = [15, 25, 40]

const COMPANY_OPTIONS = ['字节跳动', '阿里巴巴', '腾讯', '美团', '京东', '拼多多', '百度', '快手', '小红书']

const JOB_LEVELS = [
  { value: '', label: '跟随简历' },
  { value: 'intern', label: '实习' },
  { value: 'campus', label: '校招' },
  { value: 'experienced', label: '社招' },
  { value: 'senior', label: '高级/专家' },
]

type InterviewMode = 'quick' | 'jd' | 'drill'

const MODES: { value: InterviewMode; label: string; desc: string }[] = [
  { value: 'quick', label: '快速面试', desc: '不粘 JD，系统按简历和目标岗位自动生成，最快开始。' },
  { value: 'jd', label: '按 JD 定制', desc: '粘贴 JD，解析岗位能力项，出题和评分都对齐 JD。' },
  { value: 'drill', label: '专项训练', desc: '只练选中的能力项，题少、时间短，适合复盘后补短板。' },
]

const SETTINGS_KEY = 'grindset.interview.settings'

interface SavedSettings {
  mode?: InterviewMode
  position?: string
  company?: string
  jobLevel?: string
  tone?: string
  round?: string
  focus?: string[]
  codingEnabled?: boolean
  codingLimit?: number
  durationMin?: number
}

function loadSavedSettings(): SavedSettings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY)
    if (!raw) return {}
    const data = JSON.parse(raw) as SavedSettings
    const result: SavedSettings = {}
    if (data.mode === 'quick' || data.mode === 'jd' || data.mode === 'drill') result.mode = data.mode
    if (typeof data.position === 'string') result.position = data.position
    if (typeof data.company === 'string') result.company = data.company
    if (typeof data.jobLevel === 'string') result.jobLevel = data.jobLevel
    if (typeof data.tone === 'string') result.tone = data.tone
    if (typeof data.round === 'string') result.round = data.round
    if (Array.isArray(data.focus)) {
      result.focus = data.focus.filter((item): item is string => typeof item === 'string')
    }
    if (typeof data.codingEnabled === 'boolean') result.codingEnabled = data.codingEnabled
    if (typeof data.codingLimit === 'number') result.codingLimit = data.codingLimit
    if (typeof data.durationMin === 'number' && [15, 25, 40].includes(data.durationMin)) {
      result.durationMin = data.durationMin
    }
    return result
  } catch {
    return {}
  }
}

interface StartSettings {
  mode: InterviewMode
  position: string
  company: string
  jobLevel: string
  jdText: string
  style: string
  round: string
  focus: string[]
  codingEnabled: boolean
  codingTimeLimitSec: number
  jdProfile: JdProfile | null
}

/**
 * AI 面试入口:选择简历(带预览)、配置岗位/风格/轮次/题数,创建会话后进入对话页。
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
        company: settings.mode === 'jd' ? settings.company.trim() || undefined : undefined,
        jobLevel: settings.mode === 'jd' ? settings.jobLevel || undefined : undefined,
        jdText: settings.mode === 'jd' ? settings.jdText.trim() || undefined : undefined,
        jdProfile: settings.mode === 'jd' ? settings.jdProfile ?? undefined : undefined,
        style: settings.style,
        round: settings.round,
        focus: settings.focus,
        questionCount,
        codingEnabled: settings.codingEnabled,
        codingTimeLimitSec: settings.codingTimeLimitSec,
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
          选一份简历和目标岗位，系统会生成面试计划；确认后进入 AI 面试，每题会追问 1~3 次。
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
              resumeId={selectedId}
              canStart={Boolean(selectedId) && detail?.parseStatus === 1}
              starting={starting}
              questionCount={questionCount}
              onQuestionCountChange={setQuestionCount}
              onStart={handleStart}
            />
          </div>
        )}
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

/** 面试设置:模式 + 简历 + 目标岗位 → 生成计划 → 开始 */
function InterviewSettings({
  resumeId,
  canStart,
  starting,
  questionCount,
  onQuestionCountChange,
  onStart,
}: {
  resumeId: string | null
  canStart: boolean
  starting: boolean
  questionCount: number
  onQuestionCountChange: (value: number) => void
  onStart: (settings: StartSettings) => void
}) {
  const [saved] = useState(loadSavedSettings)
  const [mode, setMode] = useState<InterviewMode>(
    saved.mode === 'jd' || saved.mode === 'drill' ? saved.mode : 'quick',
  )
  const [position, setPosition] = useState(saved.position ?? POSITIONS[0])
  const [company, setCompany] = useState(saved.company ?? '')
  const [jobLevel, setJobLevel] = useState(saved.jobLevel ?? '')
  const [jdText, setJdText] = useState('')
  const [tone, setTone] = useState(saved.tone ?? 'middle')
  const [round, setRound] = useState(saved.round ?? ROUNDS[0])
  const [focus, setFocus] = useState<string[]>(saved.focus ?? ['项目深挖', '基础八股'])
  const [codingEnabled, setCodingEnabled] = useState(saved.codingEnabled ?? true)
  const [codingLimit, setCodingLimit] = useState(saved.codingLimit ?? 1800)
  const [durationMin, setDurationMin] = useState(saved.durationMin ?? 25)
  const [advancedOpen, setAdvancedOpen] = useState(false)
  const [jdProfile, setJdProfile] = useState<JdProfile | null>(null)
  const [preview, setPreview] = useState<InterviewPlanPreview | null>(null)
  const [previewing, setPreviewing] = useState(false)
  const [previewError, setPreviewError] = useState<string | null>(null)

  useEffect(() => {
    const payload = {
      mode, position, company, jobLevel, tone, round, focus,
      codingEnabled, codingLimit, durationMin,
    }
    try {
      localStorage.setItem(SETTINGS_KEY, JSON.stringify(payload))
    } catch {
      // 忽略隐私模式下的存储失败
    }
  }, [mode, position, company, jobLevel, tone, round, focus, codingEnabled, codingLimit, durationMin])

  useEffect(() => {
    if (round.includes('HR')) {
      setCodingEnabled(false)
      setFocus((prev) => {
        const kept = prev.filter((item) => HR_FOCUS_OPTIONS.includes(item))
        return kept.length > 0 ? kept : HR_FOCUS_OPTIONS.slice(0, 2)
      })
    } else {
      setCodingEnabled(true)
    }
  }, [round])

  function changeMode(next: InterviewMode) {
    setMode(next)
    setPreview(null)
    setPreviewError(null)
    if (next !== 'jd') {
      setJdProfile(null)
      setFocus(next === 'drill' ? ['项目深挖'] : ['项目深挖', '基础八股'])
    }
    if (next === 'drill') {
      if (round.includes('HR')) setRound(ROUNDS[0])
      setDurationMin(15)
      onQuestionCountChange(3)
    }
  }

  function changeDuration(value: number) {
    setDurationMin(value)
    onQuestionCountChange(value <= 15 ? 3 : value >= 40 ? 8 : 5)
    setPreview(null)
  }

  function toggleFocus(item: string) {
    setPreview(null)
    setFocus((prev) => (prev.includes(item)
      ? prev.filter((value) => value !== item)
      : [...prev, item].slice(0, 4)))
  }

  function changeJd(value: string) {
    setJdText(value)
    setJdProfile(null)
    setPreview(null)
    setPreviewError(null)
  }

  function changeTarget(next: () => void) {
    next()
    setPreview(null)
  }

  async function handlePreview() {
    if (!resumeId) {
      setPreviewError('请先选择一份简历')
      return
    }
    setPreviewing(true)
    setPreviewError(null)
    try {
      const result = await previewInterviewPlan({
        resumeId,
        mode,
        position,
        company: mode === 'jd' ? company : undefined,
        jobLevel: mode === 'jd' ? jobLevel : undefined,
        jdText: mode === 'jd' ? jdText : undefined,
        style: tone,
        round,
        durationMin,
        questionCount,
        focus,
        codingEnabled,
        codingTimeLimitSec: codingLimit,
      })
      setPreview(result)
      setJdProfile(result.jdProfile)
      if (result.position) setPosition(result.position)
      if (result.plan.round && result.plan.mode !== 'drill') setRound(result.plan.round)
      if (result.plan.style) setTone(result.plan.style)
      if (result.plan.durationMin) setDurationMin(result.plan.durationMin)
      if (result.plan.questionCount) onQuestionCountChange(result.plan.questionCount)
      const names = result.jdProfile?.competencies?.map((item) => item.name).filter(Boolean) ?? []
      if (names.length > 0) {
        setFocus((prev) => {
          const kept = prev.filter((name) => names.includes(name))
          return kept.length > 0 ? kept : names.slice(0, 3)
        })
      } else if (result.plan.competencies?.length) {
        setFocus(result.plan.competencies.map((item) => item.name).slice(0, 3))
      }
      setCodingEnabled(result.plan.mix?.some((item) => item.type === 'CODING') ?? false)
    } catch (e) {
      setPreviewError(errMessage(e))
    } finally {
      setPreviewing(false)
    }
  }

  const plan = preview?.plan
  const summaryRound = plan?.round ?? round
  const summaryDuration = plan?.durationMin ?? durationMin
  const summaryCount = plan?.questionCount ?? questionCount
  const mixText = plan?.mix?.length
    ? plan.mix.map((item) => `${item.count} ${item.label}`).join(' + ')
    : `${summaryCount} 题`
  const focusOptions = jdProfile?.competencies?.length
    ? jdProfile.competencies.map((item) => item.name)
    : mode === 'drill'
      ? FOCUS_OPTIONS
      : round.includes('HR')
        ? HR_FOCUS_OPTIONS
        : FOCUS_OPTIONS
  const selectedMode = MODES.find((item) => item.value === mode) ?? MODES[0]

  return (
    <aside className="w-full shrink-0 xl:sticky xl:top-4 xl:max-h-[calc(100vh-2rem)] xl:w-[300px] xl:overflow-y-auto">
      <div className="rounded-xl border border-slate-200 bg-white p-4">
        <div className="flex items-center gap-2 text-sm font-semibold text-slate-800">
          <Sparkles size={16} className="text-brand-600" />
          面试计划
        </div>
        <p className="mt-1 text-[11px] text-slate-400">选简历和目标岗位，系统给出本场面试计划</p>

        <div className="mt-4">
          <span className="text-xs text-slate-500">面试模式</span>
          <div className="mt-1 grid grid-cols-3 gap-1">
            {MODES.map((item) => (
              <button
                key={item.value}
                type="button"
                onClick={() => changeMode(item.value)}
                className={`flex items-center justify-center gap-1 rounded-lg border px-1 py-1.5 text-[11px] transition ${
                  mode === item.value
                    ? 'border-brand-300 bg-brand-50 font-medium text-brand-700'
                    : 'border-slate-200 text-slate-500 hover:border-slate-300'
                }`}
              >
                {item.value === 'quick' ? <Zap size={11} /> : item.value === 'jd' ? <Wand2 size={11} /> : <Target size={11} />}
                {item.label}
              </button>
            ))}
          </div>
          <p className="mt-1 text-[11px] leading-5 text-slate-400">{selectedMode.desc}</p>
        </div>

        <div className="mt-4">
          <div className="flex items-center justify-between">
            <span className="text-xs text-slate-500">目标岗位</span>
            {mode !== 'jd' && <span className="text-[10px] text-slate-400">可输入自定义</span>}
          </div>

          {mode === 'jd' ? (
            <>
              <textarea
                value={jdText}
                onChange={(event) => changeJd(event.target.value)}
                maxLength={8000}
                rows={5}
                placeholder="粘贴岗位职责与任职要求，系统会解析能力项"
                className="mt-1 w-full resize-y rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-xs leading-5 text-slate-700 outline-none focus:border-brand-400"
              />
              <span className="mt-1 block text-right text-[10px] text-slate-400">
                {jdText.length} / 8000
              </span>
              <div className="mt-2 grid grid-cols-2 gap-2">
                <label className="block text-[11px] text-slate-500">
                  公司
                  <input
                    list="interview-company-options"
                    value={company}
                    onChange={(event) => {
                      setCompany(event.target.value)
                      setJdProfile(null)
                      setPreview(null)
                    }}
                    maxLength={64}
                    placeholder="可留空"
                    className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-xs text-slate-700 outline-none focus:border-brand-400"
                  />
                  <datalist id="interview-company-options">
                    {COMPANY_OPTIONS.map((item) => (
                      <option key={item} value={item} />
                    ))}
                  </datalist>
                </label>
                <label className="block text-[11px] text-slate-500">
                  级别
                  <select
                    value={jobLevel}
                    onChange={(event) => {
                      setJobLevel(event.target.value)
                      setJdProfile(null)
                      setPreview(null)
                    }}
                    className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-xs text-slate-700 outline-none focus:border-brand-400"
                  >
                    {JOB_LEVELS.map((item) => (
                      <option key={item.value} value={item.value}>
                        {item.label}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <input
                value={position}
                onChange={(event) => changeTarget(() => setPosition(event.target.value))}
                maxLength={64}
                placeholder="岗位名称（可选，留空从 JD 提取）"
                className="mt-2 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-xs text-slate-700 outline-none focus:border-brand-400"
              />
            </>
          ) : (
            <>
              <input
                list="interview-position-options"
                value={position}
                onChange={(event) => changeTarget(() => setPosition(event.target.value))}
                maxLength={64}
                placeholder="输入目标岗位"
                className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-700 outline-none focus:border-brand-400"
              />
              <datalist id="interview-position-options">
                {POSITIONS.map((item) => (
                  <option key={item} value={item} />
                ))}
              </datalist>
              {mode === 'drill' && (
                <div className="mt-2">
                  <span className="text-[11px] text-slate-500">专项能力</span>
                  <div className="mt-1 flex flex-wrap gap-1.5">
                    {focusOptions.map((item) => (
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
              )}
            </>
          )}
        </div>

        <div className="mt-4 rounded-xl border border-brand-100 bg-brand-50/50 p-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-700">本场面试计划</span>
            <span className="text-[10px] text-brand-600">{plan ? '系统推荐' : '待生成'}</span>
          </div>
          <p className="mt-1 text-sm font-medium text-slate-800">{summaryRound}</p>
          <p className="mt-0.5 text-[11px] text-slate-500">
            约 {summaryDuration} 分钟 · {summaryCount} 题
          </p>
          <p className="mt-1 text-[11px] leading-5 text-slate-600">{mixText}</p>

          <div className="mt-2 flex flex-wrap gap-1">
            {(plan?.competencies?.length ? plan.competencies.map((item) => item.name) : focus).map((item) => (
              <span
                key={item}
                className="rounded-full bg-white px-2 py-0.5 text-[10px] text-brand-700 ring-1 ring-brand-100"
              >
                {item}
              </span>
            ))}
          </div>

          {plan?.rationale?.length ? (
            <ul className="mt-2 space-y-0.5">
              {plan.rationale.map((item) => (
                <li key={item} className="flex gap-1 text-[10px] leading-4 text-slate-500">
                  <span className="mt-1 h-1 w-1 shrink-0 rounded-full bg-brand-400" />
                  {item}
                </li>
              ))}
            </ul>
          ) : null}

          {plan?.warnings?.length ? (
            <ul className="mt-2 space-y-0.5">
              {plan.warnings.map((item) => (
                <li key={item} className="text-[10px] leading-4 text-amber-700">· {item}</li>
              ))}
            </ul>
          ) : null}

          {previewError && <p className="mt-2 text-[10px] text-rose-600">{previewError}</p>}

          <div className="mt-2 flex gap-1.5">
            <button
              type="button"
              onClick={() => void handlePreview()}
              disabled={previewing || !resumeId}
              className="flex flex-1 items-center justify-center gap-1 rounded-lg bg-white px-2 py-1.5 text-[11px] font-medium text-brand-700 ring-1 ring-brand-200 transition hover:bg-brand-50 disabled:opacity-50"
            >
              {previewing ? <Loader2 size={12} className="animate-spin" /> : <Sparkles size={12} />}
              {plan ? '刷新计划' : '生成计划'}
            </button>
            <button
              type="button"
              onClick={() => setAdvancedOpen((open) => !open)}
              className="rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-[11px] text-slate-600 transition hover:bg-slate-50"
            >
              {advancedOpen ? '收起' : '调整'}
            </button>
          </div>
        </div>

        {advancedOpen && (
          <div className="mt-3 space-y-3 rounded-lg border border-slate-200 bg-slate-50/60 p-3">
            <label className="block text-xs text-slate-500">
              面试轮次
              <select
                value={round}
                onChange={(event) => {
                  setRound(event.target.value)
                  setPreview(null)
                }}
                className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-700 outline-none focus:border-brand-400"
              >
                {ROUNDS.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </label>

            <div>
              <span className="text-xs text-slate-500">面试风格</span>
              <div className="mt-1 grid grid-cols-3 gap-1">
                {TONES.map((item) => (
                  <button
                    key={item.value}
                    type="button"
                    onClick={() => {
                      setTone(item.value)
                      setPreview(null)
                    }}
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

            <div className="grid grid-cols-2 gap-2">
              <label className="block text-[11px] text-slate-500">
                时长
                <select
                  value={durationMin}
                  onChange={(event) => changeDuration(Number(event.target.value))}
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-xs text-slate-700 outline-none focus:border-brand-400"
                >
                  {DURATIONS.map((item) => (
                    <option key={item} value={item}>
                      {item} 分钟
                    </option>
                  ))}
                </select>
              </label>
              <label className="block text-[11px] text-slate-500">
                题数
                <select
                  value={questionCount}
                  onChange={(event) => {
                    onQuestionCountChange(Number(event.target.value))
                    setPreview(null)
                  }}
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-xs text-slate-700 outline-none focus:border-brand-400"
                >
                  {QUESTION_COUNTS.map((item) => (
                    <option key={item} value={item}>
                      {item} 题
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <div className="rounded-lg border border-slate-200 bg-white p-2.5">
              <div className="flex items-start gap-2">
                <input
                  type="checkbox"
                  checked={!round.includes('HR')}
                  disabled
                  readOnly
                  className="mt-0.5 h-3.5 w-3.5 rounded border-slate-300 text-brand-600 focus:ring-brand-400 disabled:opacity-60"
                />
                <span>
                  <span className="flex items-center gap-1.5 text-xs font-medium text-slate-700">
                    最后一题手撕代码
                    <span
                      className={`rounded-full px-1.5 py-0.5 text-[10px] ${
                        round.includes('HR')
                          ? 'bg-slate-100 text-slate-500'
                          : 'bg-brand-50 text-brand-700'
                      }`}
                    >
                      {round.includes('HR') ? 'HR 面不含' : '技术面必含'}
                    </span>
                  </span>
                  <span className="mt-0.5 block text-[10px] leading-4 text-slate-400">
                    {round.includes('HR')
                      ? 'HR 面不考代码'
                      : '从题库随机选一道编程题，可在面试间写代码并和面试官讨论'}
                  </span>
                </span>
              </div>
              {!round.includes('HR') && (
                <label className="mt-2 flex items-center gap-2 text-[11px] text-slate-500">
                  编程时限
                  <select
                    value={codingLimit}
                    onChange={(event) => {
                      setCodingLimit(Number(event.target.value))
                      setPreview(null)
                    }}
                    className="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs text-slate-700 outline-none focus:border-brand-400"
                  >
                    <option value={1200}>20 分钟</option>
                    <option value={1800}>30 分钟</option>
                    <option value={2700}>45 分钟</option>
                  </select>
                </label>
              )}
            </div>

            {mode !== 'drill' && (
              <div>
                <span className="text-xs text-slate-500">侧重方向</span>
                <div className="mt-1 flex flex-wrap gap-1.5">
                  {focusOptions.map((item) => (
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
            )}
          </div>
        )}

        <button
          type="button"
          disabled={!canStart || starting || previewing}
          onClick={() => onStart({
            mode,
            position,
            company,
            jobLevel,
            jdText,
            style: tone,
            round,
            focus,
            codingEnabled,
            codingTimeLimitSec: codingLimit,
            jdProfile,
          })}
          className="mt-4 flex w-full items-center justify-center gap-1.5 rounded-lg bg-brand-600 py-2 text-sm font-medium text-white transition hover:bg-brand-700 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
        >
          {starting ? <Loader2 size={15} className="animate-spin" /> : <Briefcase size={15} />}
          开始 AI 面试
        </button>
        <p className="mt-2 text-center text-[10px] text-slate-400">
          {canStart
            ? `预计 ${summaryCount} 题 · 约 ${summaryDuration} 分钟`
            : '需要一份解析成功的简历才能开始'}
        </p>
      </div>
    </aside>
  )
}

export default InterviewPage
