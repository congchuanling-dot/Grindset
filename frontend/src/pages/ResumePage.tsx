import {
  AlertCircle,
  Briefcase,
  ChevronLeft,
  ChevronRight,
  Download,
  Eye,
  FileText,
  Loader2,
  Plus,
  Printer,
  RotateCw,
  Sparkles,
  Trash2,
  ZoomIn,
  ZoomOut,
} from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  deleteResume,
  getResume,
  listResumes,
  uploadResume,
  type ResumeDetail,
  type ResumeSummary,
} from '../api/resume'

const ACCEPT = '.pdf,.docx,.md,.txt'
const MAX_BYTES = 10 * 1024 * 1024

const TYPE_LABELS: Record<string, string> = {
  FRESH_GRADUATE: '应届生',
  INTERN: '找实习',
  EXPERIENCED: '社招',
  UNKNOWN: '未识别',
}

type ViewerTab = 'original' | 'text'

function ResumePage() {
  const [resumes, setResumes] = useState<ResumeSummary[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [detail, setDetail] = useState<ResumeDetail | null>(null)
  const [originalUrl, setOriginalUrl] = useState<string | null>(null)
  const [tab, setTab] = useState<ViewerTab>('original')
  const [dirty, setDirty] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [loadingDetail, setLoadingDetail] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)

  const fileInputRef = useRef<HTMLInputElement>(null)
  const objectUrlRef = useRef<string | null>(null)

  const clearOriginal = useCallback(() => {
    if (objectUrlRef.current) {
      URL.revokeObjectURL(objectUrlRef.current)
      objectUrlRef.current = null
    }
    setOriginalUrl(null)
  }, [])

  // 页面卸载时释放临时预览地址
  useEffect(() => {
    return () => {
      if (objectUrlRef.current) {
        URL.revokeObjectURL(objectUrlRef.current)
        objectUrlRef.current = null
      }
    }
  }, [])

  // 首次进页面拉列表,默认选中第一份
  useEffect(() => {
    let cancelled = false
    listResumes()
      .then((list) => {
        if (cancelled) return
        setResumes(list)
        if (list.length > 0) {
          setSelectedId((prev) => prev ?? list[0].id)
        }
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(errMessage(e))
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

  useEffect(() => {
    setDirty(false)
  }, [detail?.id])

  async function handleFile(file: File | null | undefined) {
    if (!file) return
    if (!/\.(pdf|docx|md|txt)$/i.test(file.name)) {
      setError('仅支持 PDF、DOCX、MD、TXT 格式,.doc 请另存为 .docx')
      return
    }
    if (file.size > MAX_BYTES) {
      setError('文件不能超过 10MB')
      return
    }

    setError(null)
    setUploading(true)
    try {
      const created = await uploadResume(file)
      setResumes((prev) => [toSummary(created), ...prev.filter((item) => item.id !== created.id)])
      setSelectedId(created.id)
      setDetail(created)
      setTab('original')
      clearOriginal()
      const url = URL.createObjectURL(file)
      objectUrlRef.current = url
      setOriginalUrl(url)
    } catch (e) {
      setError(errMessage(e))
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  function handleSelect(id: string) {
    if (id === selectedId) return
    clearOriginal()
    setSelectedId(id)
    setTab('original')
    setError(null)
  }

  async function handleDelete() {
    if (!detail) return
    if (!window.confirm(`确定删除简历「${detail.fileName}」吗？删除后无法恢复。`)) return
    try {
      await deleteResume(detail.id)
      clearOriginal()
      const rest = resumes.filter((item) => item.id !== detail.id)
      setResumes(rest)
      setSelectedId(rest[0]?.id ?? null)
      setDetail(null)
      setError(null)
    } catch (e) {
      setError(errMessage(e))
    }
  }

  return (
    <div className="flex h-full flex-col overflow-y-auto bg-slate-50">
      <header className="border-b border-slate-200 bg-white px-4 py-4 md:px-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-base font-semibold text-slate-800">简历解析</h1>
            <p className="mt-1 text-xs text-slate-500">
              上传一次，AI 拆成技能、项目与经历。原件只在本次会话可预览，不落库。
            </p>
          </div>
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading}
            className="flex items-center gap-2 rounded-lg bg-teal-600 px-3.5 py-2 text-sm font-medium text-white transition hover:bg-teal-700 disabled:cursor-not-allowed disabled:bg-teal-400"
          >
            {uploading ? <Loader2 size={16} className="animate-spin" /> : <Plus size={16} />}
            {uploading ? '正在解析…' : '上传简历'}
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept={ACCEPT}
            className="hidden"
            onChange={(e) => handleFile(e.target.files?.[0])}
          />
        </div>
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
          <EmptyState
            uploading={uploading}
            dragging={dragging}
            onPick={() => fileInputRef.current?.click()}
            onFile={handleFile}
            onDragging={setDragging}
          />
        ) : (
          <>
            <ResumeSwitcher
              resumes={resumes}
              detail={detail}
              selectedId={selectedId}
              onSelect={handleSelect}
              onDelete={handleDelete}
            />

            <div className="mt-4 flex flex-col gap-4 min-[780px]:flex-row">
              <div className="relative min-w-0 flex-1">
                {loadingDetail && (
                  <div className="absolute inset-0 z-10 flex items-center justify-center rounded-xl bg-white/70">
                    <Loader2 size={22} className="animate-spin text-teal-600" />
                  </div>
                )}
                {detail ? (
                  detail.parseStatus === 2 ? (
                    <ParseFailedPanel detail={detail} onPick={() => fileInputRef.current?.click()} />
                  ) : (
                    <ResumeViewer
                      detail={detail}
                      tab={tab}
                      onTab={setTab}
                      originalUrl={originalUrl}
                      dirty={dirty}
                      onDirty={setDirty}
                    />
                  )
                ) : (
                  <div className="flex h-64 items-center justify-center rounded-xl border border-slate-200 bg-white text-sm text-slate-400">
                    选择一份简历查看解析结果
                  </div>
                )}
              </div>

              <InterviewSettings />
            </div>
          </>
        )}
      </div>
    </div>
  )
}

function EmptyState({
  uploading,
  dragging,
  onPick,
  onFile,
  onDragging,
}: {
  uploading: boolean
  dragging: boolean
  onPick: () => void
  onFile: (file: File | null | undefined) => void
  onDragging: (value: boolean) => void
}) {
  return (
    <div
      onDragOver={(e) => {
        e.preventDefault()
        onDragging(true)
      }}
      onDragLeave={() => onDragging(false)}
      onDrop={(e) => {
        e.preventDefault()
        onDragging(false)
        if (!uploading) onFile(e.dataTransfer.files?.[0])
      }}
      className={`flex min-h-[360px] flex-col items-center justify-center rounded-xl border-2 border-dashed px-6 text-center transition ${
        dragging ? 'border-teal-400 bg-teal-50' : 'border-slate-200 bg-white'
      }`}
    >
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-teal-50 text-teal-600">
        {uploading ? <Loader2 size={22} className="animate-spin" /> : <FileText size={22} />}
      </span>
      <h2 className="mt-4 text-sm font-semibold text-slate-800">
        {uploading ? '正在解析简历，通常需要 10~40 秒' : '把简历拖到这里，或点击选择文件'}
      </h2>
      <p className="mt-2 text-xs text-slate-500">支持 PDF / DOCX / MD / TXT，单文件不超过 10MB</p>
      <button
        type="button"
        onClick={onPick}
        disabled={uploading}
        className="mt-5 rounded-lg border border-teal-200 bg-teal-50 px-3.5 py-2 text-sm font-medium text-teal-700 transition hover:bg-teal-100 disabled:opacity-60"
      >
        {uploading ? '解析中…' : '选择简历文件'}
      </button>
    </div>
  )
}

function ResumeSwitcher({
  resumes,
  detail,
  selectedId,
  onSelect,
  onDelete,
}: {
  resumes: ResumeSummary[]
  detail: ResumeDetail | null
  selectedId: string | null
  onSelect: (id: string) => void
  onDelete: () => void
}) {
  const status = detail?.parseStatus
  return (
    <div className="flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3">
      <span className="text-xs text-slate-400">当前简历</span>
      <select
        value={selectedId ?? ''}
        onChange={(e) => onSelect(e.target.value)}
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
          <span
            className={`rounded-full px-2 py-0.5 text-xs ${
              status === 1
                ? 'bg-teal-50 text-teal-700'
                : status === 2
                  ? 'bg-rose-50 text-rose-600'
                  : 'bg-amber-50 text-amber-700'
            }`}
          >
            {status === 1 ? '解析成功' : status === 2 ? '解析失败' : '解析中'}
          </span>
          {detail.model && <span className="text-xs text-slate-400">{detail.model}</span>}
        </>
      )}

      <div className="ml-auto flex items-center gap-3">
        <span className="text-xs text-slate-400">共 {resumes.length} 份</span>
        <button
          type="button"
          onClick={onDelete}
          disabled={!detail}
          className="flex items-center gap-1 rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs text-slate-500 transition hover:border-rose-200 hover:text-rose-600 disabled:opacity-50"
        >
          <Trash2 size={14} />
          删除
        </button>
      </div>
    </div>
  )
}

function ParseFailedPanel({ detail, onPick }: { detail: ResumeDetail; onPick: () => void }) {
  return (
    <div className="flex min-h-[420px] flex-col items-center justify-center rounded-xl border border-rose-200 bg-white px-6 text-center">
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-rose-50 text-rose-500">
        <AlertCircle size={22} />
      </span>
      <h2 className="mt-4 text-sm font-semibold text-slate-800">这份简历解析失败</h2>
      <p className="mt-2 max-w-md text-sm leading-6 text-slate-500">
        {detail.errorMsg || '未识别到有效内容，请换一份文字版简历重试。'}
      </p>
      <button
        type="button"
        onClick={onPick}
        className="mt-5 rounded-lg bg-teal-600 px-3.5 py-2 text-sm font-medium text-white transition hover:bg-teal-700"
      >
        重新上传
      </button>
    </div>
  )
}

function ResumeViewer({
  detail,
  tab,
  onTab,
  originalUrl,
  dirty,
  onDirty,
}: {
  detail: ResumeDetail
  tab: ViewerTab
  onTab: (tab: ViewerTab) => void
  originalUrl: string | null
  dirty: boolean
  onDirty: (value: boolean) => void
}) {
  return (
    <div className="flex min-h-[560px] flex-col overflow-hidden rounded-xl border border-slate-200 bg-white">
      <div className="flex items-center justify-between border-b border-slate-200 px-3 py-2">
        <div className="flex items-center gap-1">
          <TabButton active={tab === 'original'} onClick={() => onTab('original')} icon={<Eye size={15} />}>
            原件
          </TabButton>
          <TabButton active={tab === 'text'} onClick={() => onTab('text')} icon={<FileText size={15} />}>
            文字版
          </TabButton>
        </div>
        <span className="text-xs text-slate-400">
          {dirty ? '文字版有未保存的修改' : '解析结果'}
        </span>
      </div>

      {/* 两个视图都保持挂载:切回文字版时未保存的编辑不会丢 */}
      <div className={tab === 'original' ? 'flex flex-1 flex-col' : 'hidden'}>
        <OriginalPreview detail={detail} originalUrl={originalUrl} />
      </div>
      <div className={tab === 'text' ? 'flex flex-1 flex-col' : 'hidden'}>
        <TextEditor detail={detail} dirty={dirty} onDirty={onDirty} />
      </div>
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

function OriginalPreview({ detail, originalUrl }: { detail: ResumeDetail; originalUrl: string | null }) {
  const canInline = detail.fileType === 'PDF' && originalUrl !== null
  return (
    <div className="flex flex-1 flex-col">
      <div className="flex flex-wrap items-center gap-1 border-b border-slate-200 bg-slate-50 px-3 py-2 text-slate-500">
        <ToolbarButton title="上一页（占位）">
          <ChevronLeft size={15} />
        </ToolbarButton>
        <span className="px-1 text-xs">1 / 1</span>
        <ToolbarButton title="下一页（占位）">
          <ChevronRight size={15} />
        </ToolbarButton>
        <span className="mx-1 h-4 w-px bg-slate-200" />
        <ToolbarButton title="缩小（占位）">
          <ZoomOut size={15} />
        </ToolbarButton>
        <span className="px-1 text-xs">100%</span>
        <ToolbarButton title="放大（占位）">
          <ZoomIn size={15} />
        </ToolbarButton>
        <ToolbarButton title="旋转（占位）">
          <RotateCw size={15} />
        </ToolbarButton>
        <span className="mx-1 h-4 w-px bg-slate-200" />
        {originalUrl ? (
          <a
            href={originalUrl}
            download={detail.fileName}
            title="下载原件"
            className="rounded p-1.5 transition hover:bg-slate-200/70 hover:text-slate-700"
          >
            <Download size={15} />
          </a>
        ) : (
          <ToolbarButton title="原件不落库，刷新后无法下载">
            <Download size={15} />
          </ToolbarButton>
        )}
        <ToolbarButton title="打印（占位）">
          <Printer size={15} />
        </ToolbarButton>
      </div>

      <div className="flex-1 overflow-auto bg-slate-100 p-4">
        {canInline ? (
          <iframe
            src={`${originalUrl}#toolbar=0&navpanes=0&view=FitH`}
            title="简历原件"
            className="mx-auto h-[680px] w-full max-w-[760px] rounded-sm bg-white shadow-sm ring-1 ring-slate-200"
          />
        ) : (
          <PaperResume detail={detail} />
        )}
      </div>

      {!canInline && (
        <div className="border-t border-slate-200 bg-white px-4 py-2 text-[11px] leading-5 text-slate-400">
          {originalUrl
            ? '当前格式不支持内嵌预览，以上为解析结果还原的版式。'
            : '原件不保存：刷新后只能查看文字版。重新上传该文件即可再次预览原件。'}
        </div>
      )}
    </div>
  )
}

function ToolbarButton({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <button
      type="button"
      title={title}
      className="rounded p-1.5 transition hover:bg-slate-200/70 hover:text-slate-700"
    >
      {children}
    </button>
  )
}

/** 用解析结果还原的纸张版式,同时作为原件不可用时的兜底 */
function PaperResume({ detail }: { detail: ResumeDetail }) {
  return (
    <div className="mx-auto min-h-[680px] w-full max-w-[760px] bg-white px-8 py-8 shadow-sm ring-1 ring-slate-200 md:px-12">
      <h2 className="text-center text-xl font-semibold text-slate-800">
        {detail.candidateName || '未识别姓名'}
      </h2>
      <p className="mt-1 text-center text-xs text-slate-400">{detail.fileName}</p>

      {detail.sections.map((section) => (
        <section key={section.id} className="mt-6">
          <h3 className="border-b border-slate-200 pb-1 text-sm font-semibold tracking-wide text-slate-700">
            {section.title}
          </h3>
          {section.items.length === 0 && <p className="mt-2 text-xs text-slate-400">（无条目）</p>}
          {section.items.map((item) => (
            <div key={item.id} className="mt-3">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <p className="text-sm font-medium text-slate-800">
                  {item.title || '未命名'}
                  {item.subtitle && <span className="font-normal text-slate-500"> · {item.subtitle}</span>}
                </p>
                {(item.startDate || item.endDate) && (
                  <span className="text-xs text-slate-400">
                    {[item.startDate, item.endDate].filter(Boolean).join(' - ')}
                  </span>
                )}
              </div>
              {item.description && (
                <p className="mt-1 text-xs leading-6 whitespace-pre-line text-slate-600">{item.description}</p>
              )}
              {item.tags.length > 0 && (
                <div className="mt-1.5 flex flex-wrap gap-1">
                  {item.tags.map((tag) => (
                    <span key={tag} className="rounded bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-500">
                      {tag}
                    </span>
                  ))}
                </div>
              )}
            </div>
          ))}
        </section>
      ))}

      {detail.skills.length > 0 && (
        <section className="mt-6">
          <h3 className="border-b border-slate-200 pb-1 text-sm font-semibold tracking-wide text-slate-700">
            技能清单
          </h3>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {detail.skills.map((skill) => (
              <span key={skill.id} className="rounded bg-teal-50 px-2 py-0.5 text-xs text-teal-700">
                {skill.name}
                {skill.proficiency ? ` · ${skill.proficiency}/5` : ''}
              </span>
            ))}
          </div>
        </section>
      )}
    </div>
  )
}

function TextEditor({
  detail,
  dirty,
  onDirty,
}: {
  detail: ResumeDetail
  dirty: boolean
  onDirty: (value: boolean) => void
}) {
  const editorRef = useRef<HTMLDivElement>(null)
  const [hint, setHint] = useState('')

  // contenteditable 的内容由原生 DOM 管理,详情变化时整体重置
  useEffect(() => {
    if (editorRef.current) {
      editorRef.current.innerHTML = toEditableHtml(detail)
    }
    setHint('')
  }, [detail])

  return (
    <div className="flex flex-1 flex-col">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 bg-slate-50 px-3 py-2">
        <div className="flex items-center gap-2">
          <span
            className={`rounded-full px-2 py-0.5 text-xs ${
              dirty ? 'bg-amber-50 text-amber-700' : 'bg-slate-100 text-slate-500'
            }`}
          >
            {dirty ? '未保存' : '已同步'}
          </span>
          <span className="text-xs text-slate-400">点击正文即可编辑（占位，不写入数据库）</span>
        </div>
        <button
          type="button"
          onClick={() => {
            onDirty(false)
            setHint('第一版暂未开放保存，修改不会写入数据库')
          }}
          className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-600 transition hover:border-teal-300 hover:text-teal-700"
        >
          保存修改
        </button>
      </div>

      <div className="flex-1 overflow-auto bg-slate-100 p-4">
        <div
          ref={editorRef}
          contentEditable
          suppressContentEditableWarning
          onInput={() => onDirty(true)}
          className="mx-auto min-h-[620px] w-full max-w-[760px] bg-white px-8 py-8 text-sm leading-7 text-slate-700 shadow-sm ring-1 ring-slate-200 outline-none md:px-12 [&_h1]:text-center [&_h1]:text-xl [&_h1]:font-semibold [&_h1]:text-slate-800 [&_h2]:mt-5 [&_h2]:border-b [&_h2]:border-slate-200 [&_h2]:pb-1 [&_h2]:text-sm [&_h2]:font-semibold [&_h2]:text-slate-700 [&_p]:my-1"
        />
      </div>

      {hint && <p className="border-t border-slate-200 bg-white px-4 py-2 text-[11px] text-amber-600">{hint}</p>}
    </div>
  )
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

/** 右侧 AI 面试设置:纯前端占位,不提交任何请求 */
function InterviewSettings() {
  const [position, setPosition] = useState(POSITIONS[0])
  const [tone, setTone] = useState('middle')
  const [round, setRound] = useState(ROUNDS[0])
  const [focus, setFocus] = useState<string[]>(['项目深挖', '基础八股'])
  const [hint, setHint] = useState('')

  function toggleFocus(item: string) {
    setFocus((prev) => (prev.includes(item) ? prev.filter((v) => v !== item) : [...prev, item]))
  }

  return (
    <aside className="w-full shrink-0 min-[780px]:w-[280px]">
      <div className="rounded-xl border border-slate-200 bg-white p-4">
        <div className="flex items-center gap-2 text-sm font-semibold text-slate-800">
          <Sparkles size={16} className="text-teal-600" />
          AI 面试设置
        </div>
        <p className="mt-1 text-[11px] text-slate-400">前端占位，后端接入后生效</p>

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
          <span className="text-xs text-slate-500">面试官倾向</span>
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
          onClick={() => setHint('AI 面试尚未接入，当前只完成简历解析')}
          className="mt-5 flex w-full items-center justify-center gap-1.5 rounded-lg bg-teal-600 py-2 text-sm font-medium text-white transition hover:bg-teal-700"
        >
          <Briefcase size={15} />
          开始 AI 面试
        </button>
        {hint && <p className="mt-2 text-[11px] leading-5 text-slate-400">{hint}</p>}
      </div>
    </aside>
  )
}

function toSummary(detail: ResumeDetail): ResumeSummary {
  return {
    id: detail.id,
    fileName: detail.fileName,
    candidateName: detail.candidateName,
    candidateType: detail.candidateType,
    parseStatus: detail.parseStatus,
    createdAt: detail.createdAt,
  }
}

function errMessage(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/** 把结构化详情转成可编辑的 HTML,交给 contenteditable 渲染 */
function toEditableHtml(detail: ResumeDetail): string {
  const parts: string[] = []
  parts.push(`<h1>${escapeHtml(detail.candidateName || '未识别姓名')}</h1>`)
  parts.push(`<p style="text-align:center;color:#94a3b8;font-size:12px">${escapeHtml(detail.fileName)}</p>`)

  for (const section of detail.sections) {
    parts.push(`<h2>${escapeHtml(section.title)}</h2>`)
    if (section.items.length === 0) {
      parts.push('<p style="color:#94a3b8;font-size:12px">（无条目）</p>')
    }
    for (const item of section.items) {
      const head = [item.title, item.subtitle].filter(Boolean).map((v) => escapeHtml(v as string)).join(' · ')
      const time = [item.startDate, item.endDate].filter(Boolean).map((v) => escapeHtml(v as string)).join(' - ')
      parts.push(
        `<p><strong>${head || '未命名'}</strong>${
          time ? `<span style="color:#94a3b8;font-size:12px">　${time}</span>` : ''
        }</p>`,
      )
      if (item.description) {
        parts.push(`<p style="font-size:13px;color:#475569">${escapeHtml(item.description)}</p>`)
      }
      if (item.tags.length > 0) {
        parts.push(
          `<p style="font-size:12px;color:#0f766e">${item.tags.map((tag) => `#${escapeHtml(tag)}`).join('　')}</p>`,
        )
      }
    }
  }

  if (detail.skills.length > 0) {
    parts.push('<h2>技能清单</h2>')
    parts.push(
      `<p style="font-size:13px;color:#475569">${detail.skills
        .map((skill) => `${escapeHtml(skill.name)}${skill.proficiency ? `（${skill.proficiency}/5）` : ''}`)
        .join('、')}</p>`,
    )
  }
  return parts.join('')
}

export default ResumePage
