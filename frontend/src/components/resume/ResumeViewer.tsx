import { Download, Eye, FileText, Loader2 } from 'lucide-react'
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import {
  fetchResumeOriginal,
  updateResume,
  type ResumeDetail,
  type ResumeUpdatePayload,
} from '../../api/resume'
import { errMessage } from '../../utils/error'
import ResumePaper from './ResumePaper'

type ViewerTab = 'original' | 'text'

interface ResumeViewerProps {
  detail: ResumeDetail
  /** 刚上传的文件只在当前会话有本地临时地址；为空时「原件」tab 展示占位 */
  originalUrl?: string | null
  /** 文字版是否可编辑：上传页传 true，AI 面试页默认只读 */
  editable?: boolean
  /** 保存文字版后的最新详情，由页面同步列表与当前详情 */
  onSaved?: (detail: ResumeDetail) => void
}

/**
 * 简历预览，上传页和 AI 面试页共用同一套视图，避免两处样式/交互分叉。
 * 原件存 OSS 私有桶，由后端下发带时效的签名 URL；未配置 OSS 时用刚上传的本地地址兜底。
 */
function ResumeViewer({ detail, originalUrl = null, editable = false, onSaved }: ResumeViewerProps) {
  const [tab, setTab] = useState<ViewerTab>('text')
  const [dirty, setDirty] = useState(false)

  // 切换简历时重置编辑状态
  useEffect(() => {
    setDirty(false)
  }, [detail.id])

  // OSS 签名下载链接优先；未配置 OSS 时用刚上传的本地地址兜底
  const downloadUrl = detail.originalDownloadUrl ?? originalUrl

  return (
    <div className="flex min-h-[560px] flex-col overflow-hidden rounded-xl border border-slate-200 bg-white">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 px-3 py-2">
        <div className="flex items-center gap-1">
          <TabButton active={tab === 'original'} onClick={() => setTab('original')} icon={<Eye size={15} />}>
            原件
          </TabButton>
          <TabButton active={tab === 'text'} onClick={() => setTab('text')} icon={<FileText size={15} />}>
            文字版
          </TabButton>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs text-slate-400">
            {dirty ? '文字版有未保存的修改' : '解析结果'}
          </span>
          {downloadUrl && (
            <a
              href={downloadUrl}
              download={detail.fileName}
              title="下载原件"
              className="flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-xs text-slate-600 transition hover:border-brand-300 hover:text-brand-700"
            >
              <Download size={14} />
              下载原件
            </a>
          )}
        </div>
      </div>

      {/* 两个视图都保持挂载:切回文字版时未保存的编辑不会丢 */}
      <div className={tab === 'original' ? 'flex flex-1 flex-col' : 'hidden'}>
        <OriginalPreview detail={detail} originalUrl={originalUrl} downloadUrl={downloadUrl} />
      </div>
      <div className={tab === 'text' ? 'flex flex-1 flex-col' : 'hidden'}>
        {editable ? (
          <TextEditor detail={detail} dirty={dirty} onDirty={setDirty} onSaved={onSaved} />
        ) : (
          <div className="flex-1 overflow-auto bg-slate-100 p-4">
            <ResumePaper detail={detail} />
          </div>
        )}
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
        active ? 'bg-brand-50 font-medium text-brand-700' : 'text-slate-500 hover:bg-slate-100'
      }`}
    >
      {icon}
      {children}
    </button>
  )
}

function OriginalPreview({
  detail,
  originalUrl,
  downloadUrl,
}: {
  detail: ResumeDetail
  originalUrl: string | null
  downloadUrl: string | null
}) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [textContent, setTextContent] = useState<string | null>(null)
  const [textLoading, setTextLoading] = useState(false)
  const [textError, setTextError] = useState('')
  const isPdf = detail.fileType === 'PDF'
  const isText = detail.fileType === 'TXT' || detail.fileType === 'MD'

  // OSS 默认域名会强制下载，PDF 预览改由后端代理取字节再转成本地 blob
  useEffect(() => {
    if (originalUrl || !isPdf || !detail.hasOriginal) {
      setBlobUrl(null)
      setLoading(false)
      setLoadError('')
      return
    }
    let cancelled = false
    let created: string | null = null
    setLoading(true)
    setLoadError('')
    fetchResumeOriginal(detail.id)
      .then((blob) => {
        if (cancelled) return
        created = URL.createObjectURL(blob)
        setBlobUrl(created)
      })
      .catch((e: unknown) => {
        if (!cancelled) setLoadError(errMessage(e))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
      if (created) URL.revokeObjectURL(created)
    }
  }, [detail.id, isPdf, detail.hasOriginal, originalUrl])

  // TXT / MD 直接按纯文本读取；原件已存 OSS 时走后端代理，避免签名域名强制下载和跨域
  useEffect(() => {
    if (!isText) {
      setTextContent(null)
      setTextLoading(false)
      setTextError('')
      return
    }
    if (!detail.hasOriginal && !originalUrl) {
      setTextContent(null)
      setTextLoading(false)
      setTextError('')
      return
    }
    let cancelled = false
    setTextContent(null)
    setTextLoading(true)
    setTextError('')
    loadText(detail.id, detail.hasOriginal, originalUrl)
      .then((text) => {
        if (!cancelled) setTextContent(text)
      })
      .catch((e: unknown) => {
        if (!cancelled) setTextError(errMessage(e))
      })
      .finally(() => {
        if (!cancelled) setTextLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [detail.id, detail.hasOriginal, isText, originalUrl])

  // 后端签名 URL 优先；本地 ObjectURL 只在未配置 OSS 时兜底
  const url = originalUrl ?? blobUrl
  const canInline = isPdf && url !== null
  return (
    <div className="flex flex-1 flex-col">
      {isText ? (
        textLoading ? (
          <div className="flex flex-1 items-center justify-center">
            <Loader2 size={22} className="animate-spin text-brand-600" />
          </div>
        ) : textError ? (
          <OriginalError message={textError} downloadUrl={downloadUrl} fileName={detail.fileName} />
        ) : textContent !== null ? (
          <div className="flex-1 overflow-auto bg-slate-100 p-4">
            <pre className="mx-auto min-h-[680px] w-full max-w-[760px] whitespace-pre-wrap break-words rounded-sm bg-white p-8 font-sans text-sm leading-7 text-slate-700 shadow-sm ring-1 ring-slate-200">
              {textContent}
            </pre>
          </div>
        ) : (
          <OriginalPlaceholder hasOriginal={url !== null} />
        )
      ) : loading && !url ? (
        <div className="flex flex-1 items-center justify-center">
          <Loader2 size={22} className="animate-spin text-brand-600" />
        </div>
      ) : canInline ? (
        <div className="flex-1 overflow-auto bg-slate-100 p-4">
          <iframe
            src={`${url}#toolbar=0&navpanes=0&view=FitH`}
            title="简历原件"
            className="mx-auto h-[680px] w-full max-w-[760px] rounded-sm bg-white shadow-sm ring-1 ring-slate-200"
          />
        </div>
      ) : loadError ? (
        <OriginalError message={loadError} downloadUrl={downloadUrl} fileName={detail.fileName} />
      ) : (
        <OriginalPlaceholder hasOriginal={url !== null} />
      )}
    </div>
  )
}

/** 原件最多展示的字符数，避免超长 TXT 把浏览器渲染卡死 */
const MAX_TEXT_PREVIEW_CHARS = 200_000

async function loadText(resumeId: string, hasOriginal: boolean, originalUrl: string | null): Promise<string> {
  const blob = hasOriginal
    ? await fetchResumeOriginal(resumeId)
    : await fetchLocalBlob(originalUrl)
  return readTextBlob(blob)
}

async function fetchLocalBlob(originalUrl: string | null): Promise<Blob> {
  if (!originalUrl) {
    throw new Error('原件未存储')
  }
  const res = await fetch(originalUrl)
  if (!res.ok) {
    throw new Error('加载原件失败，请刷新后重试')
  }
  return res.blob()
}

/** 按 UTF-8 严格解码，失败回退 GB18030，和后端文本抽取规则保持一致 */
async function readTextBlob(blob: Blob): Promise<string> {
  const bytes = new Uint8Array(await blob.arrayBuffer())
  let text: string
  if (bytes.length >= 3 && bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
    text = new TextDecoder('utf-8').decode(bytes.subarray(3))
  } else {
    try {
      text = new TextDecoder('utf-8', { fatal: true }).decode(bytes)
    } catch {
      try {
        text = new TextDecoder('gb18030').decode(bytes)
      } catch {
        text = new TextDecoder('utf-8').decode(bytes)
      }
    }
  }
  return text.length > MAX_TEXT_PREVIEW_CHARS
    ? `${text.slice(0, MAX_TEXT_PREVIEW_CHARS)}\n\n……内容过长，仅展示前 ${MAX_TEXT_PREVIEW_CHARS} 个字符`
    : text
}

function OriginalError({
  message,
  downloadUrl,
  fileName,
}: {
  message: string
  downloadUrl: string | null
  fileName: string
}) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center px-6 py-16 text-center">
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-rose-50 text-rose-500">
        <Eye size={22} />
      </span>
      <h2 className="mt-4 text-sm font-semibold text-slate-800">原件加载失败</h2>
      <p className="mt-2 max-w-sm text-xs leading-5 text-slate-500">{message}</p>
      {downloadUrl && (
        <a
          href={downloadUrl}
          download={fileName}
          className="mt-4 rounded-lg border border-slate-200 bg-white px-3.5 py-2 text-sm font-medium text-slate-600 transition hover:border-brand-300 hover:text-brand-700"
        >
          下载原件
        </a>
      )}
    </div>
  )
}

function OriginalPlaceholder({ hasOriginal }: { hasOriginal: boolean }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center px-6 py-16 text-center">
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
        <Eye size={22} />
      </span>
      <h2 className="mt-4 text-sm font-semibold text-slate-800">
        {hasOriginal ? '该格式暂不支持内嵌预览' : '原件未存储'}
      </h2>
      <p className="mt-2 max-w-sm text-xs leading-5 text-slate-500">
        {hasOriginal
          ? 'PDF 可直接内嵌查看，DOCX 请点击右上角的下载按钮后打开。'
          : '未配置 OSS 或原件上传失败，只能查看解析后的文字版。'}
      </p>
    </div>
  )
}

interface ItemForm {
  title: string
  subtitle: string
  startDate: string
  endDate: string
  description: string
  tags: string
}

interface SectionForm {
  type: string
  title: string
  items: ItemForm[]
}

interface ResumeForm {
  candidateName: string
  sections: SectionForm[]
}

/** 详情 → 表单；tags 用顿号拼成一行方便编辑 */
function toForm(detail: ResumeDetail): ResumeForm {
  return {
    candidateName: detail.candidateName ?? '',
    sections: detail.sections.map((section) => ({
      type: section.type,
      title: section.title,
      items: section.items.map((item) => ({
        title: item.title ?? '',
        subtitle: item.subtitle ?? '',
        startDate: item.startDate ?? '',
        endDate: item.endDate ?? '',
        description: item.description ?? '',
        tags: item.tags.join('、'),
      })),
    })),
  }
}

/** 描述可能很长，按内容自动撑高，不出现内部滚动条 */
function AutoResizeTextarea({
  value,
  onChange,
  placeholder,
}: {
  value: string
  onChange: (value: string) => void
  placeholder: string
}) {
  const ref = useRef<HTMLTextAreaElement>(null)

  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${el.scrollHeight}px`
  }, [value])

  return (
    <textarea
      ref={ref}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      rows={1}
      className="mt-1 w-full resize-none overflow-hidden bg-transparent text-xs leading-6 text-slate-600 outline-none placeholder:text-slate-300"
    />
  )
}

function TextEditor({
  detail,
  dirty,
  onDirty,
  onSaved,
}: {
  detail: ResumeDetail
  dirty: boolean
  onDirty: (value: boolean) => void
  onSaved?: (detail: ResumeDetail) => void
}) {
  const [form, setForm] = useState<ResumeForm>(() => toForm(detail))
  const [hint, setHint] = useState('')
  const [hintError, setHintError] = useState(false)
  const [saving, setSaving] = useState(false)

  // 切换简历或保存成功后，用最新详情重置表单
  useEffect(() => {
    setForm(toForm(detail))
  }, [detail])

  // 切换简历时清空上一次的保存提示
  useEffect(() => {
    setHint('')
    setHintError(false)
  }, [detail.id])

  /** 所有编辑都在结构化草稿上改，提交时直接序列化成 sections/items/skills */
  function edit(mutate: (draft: ResumeForm) => void) {
    setForm((prev) => {
      const draft: ResumeForm = {
        candidateName: prev.candidateName,
        sections: prev.sections.map((section) => ({
          ...section,
          items: section.items.map((item) => ({ ...item })),
        })),
      }
      mutate(draft)
      return draft
    })
    onDirty(true)
  }

  function handleSave() {
    const payload: ResumeUpdatePayload = {
      candidateName: form.candidateName.trim(),
      sections: form.sections
        .map((section) => ({
          type: section.type,
          title: section.title.trim(),
          items: section.items.map((item) => ({
            title: item.title.trim(),
            subtitle: item.subtitle.trim(),
            startDate: item.startDate.trim(),
            endDate: item.endDate.trim(),
            description: item.description,
            tags: item.tags
              .split(/[,，、]/)
              .map((tag) => tag.trim())
              .filter(Boolean),
          })),
        }))
        .filter((section) => section.title.length > 0 || section.items.some((item) => item.title.length > 0)),
      skills: detail.skills.map((skill) => ({
        name: skill.name,
        category: skill.category,
        proficiency: skill.proficiency,
        evidence: skill.evidence,
      })),
    }
    if (payload.sections.length === 0 && payload.skills.length === 0) {
      setHintError(true)
      setHint('简历内容不能为空')
      return
    }

    setSaving(true)
    setHint('')
    setHintError(false)
    updateResume(detail.id, payload)
      .then((updated) => {
        onDirty(false)
        onSaved?.(updated)
        setHint('已保存，改动已写入结构化数据')
      })
      .catch((e: unknown) => {
        setHintError(true)
        setHint(errMessage(e))
      })
      .finally(() => setSaving(false))
  }

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
          <span className="text-xs text-slate-400">直接编辑字段，保存后写入章节与条目</span>
        </div>
        <button
          type="button"
          onClick={handleSave}
          disabled={saving}
          className="flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-600 transition hover:border-brand-300 hover:text-brand-700 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {saving && <Loader2 size={13} className="animate-spin" />}
          {saving ? '保存中…' : '保存修改'}
        </button>
      </div>

      <div className="flex-1 overflow-auto bg-slate-100 p-4">
        <div className="mx-auto min-h-[620px] w-full max-w-[760px] bg-white px-8 py-8 shadow-sm ring-1 ring-slate-200 md:px-12">
          <input
            value={form.candidateName}
            onChange={(e) => {
              const value = e.target.value
              edit((draft) => {
                draft.candidateName = value
              })
            }}
            placeholder="未识别姓名"
            className="w-full bg-transparent text-center text-xl font-semibold text-slate-800 outline-none focus:bg-slate-50"
          />
          <p className="mt-1 text-center text-xs text-slate-400">{detail.fileName}</p>

          {form.sections.map((section, sectionIndex) => (
            <section key={sectionIndex} className="mt-6">
              <input
                value={section.title}
                onChange={(e) => {
                  const value = e.target.value
                  edit((draft) => {
                    draft.sections[sectionIndex].title = value
                  })
                }}
                placeholder="章节标题"
                className="w-full border-b border-slate-200 bg-transparent pb-1 text-sm font-semibold tracking-wide text-slate-700 outline-none focus:border-brand-400"
              />
              {section.items.length === 0 && <p className="mt-2 text-xs text-slate-400">（无条目）</p>}
              {section.items.map((item, itemIndex) => {
                const hasSubtitle = item.subtitle.length > 0
                const hasTime = item.startDate.length > 0 || item.endDate.length > 0
                const hasTags = item.tags.length > 0
                return (
                  <div
                    key={itemIndex}
                    className="mt-2 rounded-lg border border-slate-200/80 bg-white px-3 py-2 transition focus-within:border-brand-300 focus-within:bg-brand-50/20"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <input
                          value={item.title}
                          onChange={(e) => {
                            const value = e.target.value
                            edit((draft) => {
                              draft.sections[sectionIndex].items[itemIndex].title = value
                            })
                          }}
                          placeholder="主体（公司 / 学校 / 项目）"
                          className="w-full bg-transparent text-sm font-medium text-slate-800 outline-none placeholder:text-slate-300"
                        />
                        {hasSubtitle && (
                          <input
                            value={item.subtitle}
                            onChange={(e) => {
                              const value = e.target.value
                              edit((draft) => {
                                draft.sections[sectionIndex].items[itemIndex].subtitle = value
                              })
                            }}
                            placeholder="角色 / 专业"
                            className="mt-0.5 w-full bg-transparent text-xs text-slate-500 outline-none placeholder:text-slate-300"
                          />
                        )}
                      </div>
                      {hasTime && (
                        <div className="flex shrink-0 items-center gap-1 text-xs text-slate-400">
                          <input
                            value={item.startDate}
                            onChange={(e) => {
                              const value = e.target.value
                              edit((draft) => {
                                draft.sections[sectionIndex].items[itemIndex].startDate = value
                              })
                            }}
                            className="w-16 bg-transparent text-right outline-none"
                          />
                          <span className="text-slate-300">-</span>
                          <input
                            value={item.endDate}
                            onChange={(e) => {
                              const value = e.target.value
                              edit((draft) => {
                                draft.sections[sectionIndex].items[itemIndex].endDate = value
                              })
                            }}
                            className="w-16 bg-transparent outline-none"
                          />
                        </div>
                      )}
                    </div>
                    <AutoResizeTextarea
                      value={item.description}
                      onChange={(value) =>
                        edit((draft) => {
                          draft.sections[sectionIndex].items[itemIndex].description = value
                        })
                      }
                      placeholder="描述"
                    />
                    {hasTags && (
                      <div className="mt-0.5 flex items-center gap-1.5 text-[11px]">
                        <span className="shrink-0 text-slate-300">标签</span>
                        <input
                          value={item.tags}
                          onChange={(e) => {
                            const value = e.target.value
                            edit((draft) => {
                              draft.sections[sectionIndex].items[itemIndex].tags = value
                            })
                          }}
                          className="min-w-0 flex-1 bg-transparent text-brand-700 outline-none"
                        />
                      </div>
                    )}
                  </div>
                )
              })}
            </section>
          ))}

          {detail.skills.length > 0 && (
            <section className="mt-6">
              <h3 className="border-b border-slate-200 pb-1 text-sm font-semibold tracking-wide text-slate-700">
                技能清单
              </h3>
              <div className="mt-2 flex flex-wrap gap-1.5">
                {detail.skills.map((skill) => (
                  <span key={skill.id} className="rounded bg-brand-50 px-2 py-0.5 text-xs text-brand-700">
                    {skill.name}
                    {skill.proficiency ? ` · ${skill.proficiency}/5` : ''}
                  </span>
                ))}
              </div>
              <p className="mt-2 text-[11px] text-slate-400">技能清单暂不支持在这里修改</p>
            </section>
          )}
        </div>
      </div>

      {hint && (
        <p
          className={`border-t border-slate-200 bg-white px-4 py-2 text-[11px] ${
            hintError ? 'text-rose-600' : 'text-brand-600'
          }`}
        >
          {hint}
        </p>
      )}
    </div>
  )
}

export default ResumeViewer
