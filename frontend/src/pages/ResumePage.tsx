import { AlertCircle, FileText, Loader2, Plus, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  deleteResume,
  getResume,
  listResumes,
  uploadResume,
  type ResumeDetail,
  type ResumeSummary,
} from '../api/resume'
import ResumeViewer from '../components/resume/ResumeViewer'

const ACCEPT = '.pdf,.docx,.md,.txt'
const MAX_BYTES = 10 * 1024 * 1024

const TYPE_LABELS: Record<string, string> = {
  FRESH_GRADUATE: '应届生',
  INTERN: '找实习',
  EXPERIENCED: '社招',
  UNKNOWN: '未识别',
}

function ResumePage() {
  const [resumes, setResumes] = useState<ResumeSummary[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [detail, setDetail] = useState<ResumeDetail | null>(null)
  const [originalUrl, setOriginalUrl] = useState<string | null>(null)
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
            <h1 className="text-base font-semibold text-slate-800">简历管理</h1>
            <p className="mt-1 text-xs text-slate-500">
              上传 PDF / DOCX / MD / TXT，一人可保存多份，随时切换查看解析结果。原件只在本次会话可预览，不落库。
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

            <div className="relative mt-4">
              {loadingDetail && (
                <div className="absolute inset-0 z-10 flex items-center justify-center rounded-xl bg-white/70">
                  <Loader2 size={22} className="animate-spin text-teal-600" />
                </div>
              )}
              {detail ? (
                detail.parseStatus === 2 ? (
                  <ParseFailedPanel detail={detail} onPick={() => fileInputRef.current?.click()} />
                ) : (
                  <ResumeViewer detail={detail} originalUrl={originalUrl} editable />
                )
              ) : (
                <div className="flex h-64 items-center justify-center rounded-xl border border-slate-200 bg-white text-sm text-slate-400">
                  选择一份简历查看解析结果
                </div>
              )}
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

export default ResumePage
