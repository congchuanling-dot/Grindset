import { AlertCircle, ChevronRight, Clock3, FileText, History, Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { listInterviews, type InterviewSummary } from '../api/interview'
import { listResumes, type ResumeSummary } from '../api/resume'

/**
 * 面试记录:左侧按简历筛选(可多选),右侧展示对应的面试会话。
 * 点击任意会话进入详情页,已结束的会话可在详情页查看复盘报告。
 */
function InterviewRecordsPage() {
  const navigate = useNavigate()

  const [resumes, setResumes] = useState<ResumeSummary[]>([])
  const [selectedResumeIds, setSelectedResumeIds] = useState<string[]>([])
  const [sessions, setSessions] = useState<InterviewSummary[]>([])
  const [loadingResumes, setLoadingResumes] = useState(true)
  const [loadingSessions, setLoadingSessions] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listResumes()
      .then((list) => {
        if (!cancelled) setResumes(list)
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(errMessage(e))
      })
      .finally(() => {
        if (!cancelled) setLoadingResumes(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  // 选中的简历变化时重新查询;不选表示查全部
  useEffect(() => {
    let cancelled = false
    setLoadingSessions(true)
    listInterviews(selectedResumeIds)
      .then((list) => {
        if (!cancelled) setSessions(list)
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(errMessage(e))
      })
      .finally(() => {
        if (!cancelled) setLoadingSessions(false)
      })
    return () => {
      cancelled = true
    }
  }, [selectedResumeIds])

  function toggleResume(id: string) {
    setSelectedResumeIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id],
    )
  }

  return (
    <div className="flex h-full flex-col overflow-hidden bg-slate-50">
      <header className="shrink-0 border-b border-slate-200 bg-white px-4 py-4 md:px-6">
        <h1 className="flex items-center gap-2 text-base font-semibold text-slate-800">
          <History size={18} className="text-teal-600" />
          面试记录
        </h1>
        <p className="mt-1 text-xs text-slate-500">
          每场面试的题目、回答、点评和复盘都会自动保存，可按简历筛选后查看。
        </p>
      </header>

      <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto p-4 md:flex-row md:p-6">
        {error && (
          <div className="flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700 md:absolute md:right-6 md:top-20 md:z-10">
            <AlertCircle size={16} className="mt-0.5 shrink-0" />
            <span className="flex-1">{error}</span>
            <button
              type="button"
              className="text-xs text-rose-500 hover:text-rose-700"
              onClick={() => setError(null)}
            >
              关闭
            </button>
          </div>
        )}

        <aside className="w-full shrink-0 md:w-60">
          <div className="rounded-xl border border-slate-200 bg-white p-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-500">按简历筛选</span>
              {selectedResumeIds.length > 0 && (
                <button
                  type="button"
                  onClick={() => setSelectedResumeIds([])}
                  className="text-[11px] text-teal-600 hover:text-teal-700"
                >
                  清空
                </button>
              )}
            </div>

            <button
              type="button"
              onClick={() => setSelectedResumeIds([])}
              className={`mt-2 flex w-full items-center justify-between rounded-lg px-2 py-1.5 text-left text-xs transition ${
                selectedResumeIds.length === 0
                  ? 'bg-teal-50 font-medium text-teal-700'
                  : 'text-slate-600 hover:bg-slate-50'
              }`}
            >
              全部简历
            </button>

            {loadingResumes ? (
              <div className="flex items-center gap-2 px-2 py-3 text-xs text-slate-400">
                <Loader2 size={13} className="animate-spin" />
                加载简历…
              </div>
            ) : resumes.length === 0 ? (
              <p className="px-2 py-3 text-xs leading-5 text-slate-400">
                还没有简历，
                <Link to="/resume" className="text-teal-600 hover:text-teal-700">
                  去上传
                </Link>
              </p>
            ) : (
              <ul className="mt-1 space-y-0.5">
                {resumes.map((resume) => {
                  const checked = selectedResumeIds.includes(resume.id)
                  return (
                    <li key={resume.id}>
                      <label className="flex cursor-pointer items-start gap-2 rounded-lg px-2 py-1.5 text-xs text-slate-600 transition hover:bg-slate-50">
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={() => toggleResume(resume.id)}
                          className="mt-0.5 h-3.5 w-3.5 shrink-0 accent-teal-600"
                        />
                        <span className="min-w-0">
                          <span className="block truncate" title={resume.fileName}>
                            {resume.fileName}
                          </span>
                          {resume.parseStatus !== 1 && (
                            <span className="text-[10px] text-amber-600">解析未完成</span>
                          )}
                        </span>
                      </label>
                    </li>
                  )
                })}
              </ul>
            )}
          </div>
        </aside>

        <section className="min-w-0 flex-1">
          <div className="mb-3 flex items-center justify-between">
            <span className="text-xs text-slate-400">
              共 {sessions.length} 场面试
              {selectedResumeIds.length > 0 && ` · 已选 ${selectedResumeIds.length} 份简历`}
            </span>
          </div>

          {loadingSessions ? (
            <div className="flex h-40 items-center justify-center">
              <Loader2 size={20} className="animate-spin text-teal-600" />
            </div>
          ) : sessions.length === 0 ? (
            <EmptyState filtered={selectedResumeIds.length > 0} />
          ) : (
            <div className="grid gap-2 lg:grid-cols-2">
              {sessions.map((session) => (
                <button
                  key={session.id}
                  type="button"
                  onClick={() => navigate(`/interview/${session.id}`)}
                  className="group rounded-xl border border-slate-200 bg-white px-4 py-3 text-left transition hover:border-teal-300 hover:shadow-sm"
                >
                  <div className="flex items-start justify-between gap-2">
                    <span className="line-clamp-1 text-sm font-medium text-slate-800">
                      {session.position}
                    </span>
                    {session.status === 1 ? (
                      <span className="shrink-0 rounded-full bg-teal-50 px-2 py-0.5 text-[11px] text-teal-700">
                        {session.overallScore != null ? `${session.overallScore} 分` : '已结束'}
                      </span>
                    ) : (
                      <span className="shrink-0 rounded-full bg-amber-50 px-2 py-0.5 text-[11px] text-amber-700">
                        进行中
                      </span>
                    )}
                  </div>

                  <p className="mt-1 flex items-center gap-1.5 text-xs text-slate-400">
                    <FileText size={11} className="shrink-0" />
                    <span className="truncate">{session.resumeFileName}</span>
                  </p>

                  <div className="mt-2 flex items-center gap-3 text-[11px] text-slate-400">
                    <span>
                      已完成 {session.completedQuestions}/{session.questionCount} 题
                    </span>
                    <span className="flex items-center gap-1">
                      <Clock3 size={11} />
                      {session.createdAt}
                    </span>
                    <span className="ml-auto flex items-center gap-0.5 text-teal-600">
                      {session.status === 1 ? '查看复盘' : '继续面试'}
                      <ChevronRight size={12} />
                    </span>
                  </div>
                </button>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  )
}

function EmptyState({ filtered }: { filtered: boolean }) {
  return (
    <div className="flex h-60 flex-col items-center justify-center rounded-xl border-2 border-dashed border-slate-200 bg-white px-6 text-center">
      <span className="flex h-11 w-11 items-center justify-center rounded-full bg-teal-50 text-teal-600">
        <History size={20} />
      </span>
      <h2 className="mt-3 text-sm font-semibold text-slate-800">
        {filtered ? '这些简历下还没有面试记录' : '还没有面试记录'}
      </h2>
      <p className="mt-1.5 max-w-xs text-xs leading-5 text-slate-500">
        {filtered
          ? '换一份简历筛选，或者去开始一场新的面试。'
          : '开始一场 AI 面试后，题目、回答和复盘会自动保存在这里。'}
      </p>
      <Link
        to="/interview"
        className="mt-4 rounded-lg bg-teal-600 px-3.5 py-2 text-sm font-medium text-white transition hover:bg-teal-700"
      >
        去开始面试
      </Link>
    </div>
  )
}

function errMessage(error: unknown): string {
  return error instanceof Error ? error.message : '加载失败，请稍后重试'
}

export default InterviewRecordsPage
