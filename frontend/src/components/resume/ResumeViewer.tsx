import {
  ChevronLeft,
  ChevronRight,
  Download,
  Eye,
  FileText,
  Printer,
  RotateCw,
  ZoomIn,
  ZoomOut,
} from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import type { ResumeDetail } from '../../api/resume'
import ResumePaper from './ResumePaper'

type ViewerTab = 'original' | 'text'

interface ResumeViewerProps {
  detail: ResumeDetail
  /** 刚上传的文件只在当前会话有本地临时地址；为空时「原件」tab 展示占位 */
  originalUrl?: string | null
  /** 文字版是否可编辑：上传页传 true，AI 面试页默认只读 */
  editable?: boolean
}

/**
 * 简历预览，上传页和 AI 面试页共用同一套视图，避免两处样式/交互分叉。
 * 原件暂不落库，所以除刚上传的当前会话外，「原件」tab 只显示占位。
 */
function ResumeViewer({ detail, originalUrl = null, editable = false }: ResumeViewerProps) {
  const [tab, setTab] = useState<ViewerTab>('text')
  const [dirty, setDirty] = useState(false)

  // 切换简历时重置编辑状态
  useEffect(() => {
    setDirty(false)
  }, [detail.id])

  return (
    <div className="flex min-h-[560px] flex-col overflow-hidden rounded-xl border border-slate-200 bg-white">
      <div className="flex items-center justify-between border-b border-slate-200 px-3 py-2">
        <div className="flex items-center gap-1">
          <TabButton active={tab === 'original'} onClick={() => setTab('original')} icon={<Eye size={15} />}>
            原件
          </TabButton>
          <TabButton active={tab === 'text'} onClick={() => setTab('text')} icon={<FileText size={15} />}>
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
        {editable ? (
          <TextEditor detail={detail} dirty={dirty} onDirty={setDirty} />
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
          <ToolbarButton title="原件暂不保存，无法下载">
            <Download size={15} />
          </ToolbarButton>
        )}
        <ToolbarButton title="打印（占位）">
          <Printer size={15} />
        </ToolbarButton>
      </div>

      <div className="flex flex-1 flex-col">
        {canInline ? (
          <div className="flex-1 overflow-auto bg-slate-100 p-4">
            <iframe
              src={`${originalUrl}#toolbar=0&navpanes=0&view=FitH`}
              title="简历原件"
              className="mx-auto h-[680px] w-full max-w-[760px] rounded-sm bg-white shadow-sm ring-1 ring-slate-200"
            />
          </div>
        ) : (
          <OriginalPlaceholder hasOriginal={originalUrl !== null} />
        )}
      </div>
    </div>
  )
}

function OriginalPlaceholder({ hasOriginal }: { hasOriginal: boolean }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center px-6 py-16 text-center">
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
        <Eye size={22} />
      </span>
      <h2 className="mt-4 text-sm font-semibold text-slate-800">原件预览暂未开放</h2>
      <p className="mt-2 max-w-sm text-xs leading-5 text-slate-500">
        {hasOriginal
          ? '当前格式不支持内嵌预览，请切换到「文字版」查看解析结果。'
          : '简历原件暂不保存，只能查看解析后的文字版。原件预览会在后续版本接入。'}
      </p>
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

export default ResumeViewer
