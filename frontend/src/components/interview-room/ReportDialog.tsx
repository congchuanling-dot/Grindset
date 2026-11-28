import { Loader2, Sparkles, X } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import type { InterviewReport } from '../../api/interview'
import { createTrainingSession } from '../../api/training'

interface ReportDialogProps {
  report: InterviewReport
  position?: string
  sessionId?: string
  onClose: () => void
}

const DIMENSION_LABELS: Record<string, string> = {
  technical: '技术深度',
  logic: '逻辑表达',
  project: '项目经验',
  communication: '沟通表达',
  编码: '编码能力',
}

function ReportList({ title, items, tone }: { title: string; items: string[]; tone: 'brand' | 'rose' | 'slate' }) {
  if (items.length === 0) return null
  const dot =
    tone === 'brand' ? 'bg-brand-500' : tone === 'rose' ? 'bg-rose-400' : 'bg-slate-400'
  return (
    <section>
      <h3 className="mb-2 text-xs font-semibold text-slate-700">{title}</h3>
      <ul className="space-y-1.5">
        {items.map((item) => (
          <li key={item} className="flex gap-2 text-xs leading-5 text-slate-600">
            <span className={`mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full ${dot}`} />
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

/** 复盘报告弹窗:总分 + 维度分 + JD 匹配度 + 弱项诊断 + 薄弱点/亮点/建议 */
function ReportDialog({ report, position, sessionId, onClose }: ReportDialogProps) {
  const navigate = useNavigate()
  const [creating, setCreating] = useState(false)
  const [trainError, setTrainError] = useState('')
  const coverage = report.jdCoverage ?? []
  const jdGaps = report.jdGaps ?? []
  const weaknesses = report.weaknesses ?? []

  async function handleStartTraining() {
    if (!sessionId || creating) return
    setCreating(true)
    setTrainError('')
    try {
      const plan = await createTrainingSession('DAILY', sessionId)
      onClose()
      navigate(`/training/${plan.id}`)
    } catch (error) {
      setTrainError(error instanceof Error ? error.message : '生成训练计划失败')
    } finally {
      setCreating(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="max-h-[85vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl">
        <div className="mb-4 flex items-start justify-between">
          <div>
            <h2 className="flex items-center gap-1.5 text-sm font-semibold text-slate-800">
              <Sparkles size={15} className="text-brand-600" />
              面试复盘
            </h2>
            {position && <p className="mt-0.5 text-[11px] text-slate-400">{position}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
          >
            <X size={16} />
          </button>
        </div>

        <div className="mb-5 flex items-end gap-2">
          <span className="text-4xl font-semibold tabular-nums text-brand-600">
            {report.overallScore}
          </span>
          <span className="pb-1 text-xs text-slate-400">/ 100 分</span>
        </div>

        {Object.keys(report.dimensionScores).length > 0 && (
          <div className="mb-5 space-y-2">
            {Object.entries(report.dimensionScores).map(([key, value]) => (
              <div key={key} className="flex items-center gap-2">
                <span className="w-20 shrink-0 text-[11px] text-slate-500">
                  {DIMENSION_LABELS[key] ?? key}
                </span>
                <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-100">
                  <span
                    className="block h-full rounded-full bg-brand-500"
                    style={{ width: `${Math.max(0, Math.min(100, value))}%` }}
                  />
                </span>
                <span className="w-7 text-right text-[11px] tabular-nums text-slate-500">
                  {value}
                </span>
              </div>
            ))}
          </div>
        )}

        {(report.jdMatchScore != null || coverage.length > 0) && (
          <div className="mb-5 rounded-xl border border-brand-100 bg-brand-50/50 px-4 py-3">
            <div className="mb-2 flex items-end justify-between">
              <div>
                <span className="text-xs font-semibold text-slate-700">JD 匹配度</span>
                <p className="mt-0.5 text-[10px] text-slate-400">按岗位能力项加权计算</p>
              </div>
              <span className="text-2xl font-semibold tabular-nums text-brand-700">
                {report.jdMatchScore ?? '—'}
              </span>
            </div>

            {coverage.length > 0 && (
              <div className="space-y-1.5">
                {coverage.map((item) => (
                  <div key={item.name} className="flex items-center gap-2">
                    <span
                      className="w-24 shrink-0 truncate text-[11px] text-slate-500"
                      title={item.name}
                    >
                      {item.name}
                    </span>
                    <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-white">
                      <span
                        className="block h-full rounded-full bg-brand-500"
                        style={{ width: `${Math.max(0, Math.min(100, item.score ?? 0))}%` }}
                      />
                    </span>
                    <span className="w-7 text-right text-[11px] tabular-nums text-slate-500">
                      {item.score ?? '—'}
                    </span>
                  </div>
                ))}
              </div>
            )}

            {jdGaps.length > 0 && (
              <div className="mt-3">
                <ReportList title="JD 差距" items={jdGaps} tone="rose" />
              </div>
            )}
          </div>
        )}

        {report.coding && (
          <div className="mb-5 rounded-xl border border-brand-100 bg-brand-50/50 px-4 py-3">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs font-semibold text-slate-700">手撕代码</span>
              <span className="text-[11px] text-slate-500">{report.coding.title}</span>
              <span className="ml-auto rounded-full bg-white px-2 py-0.5 text-[11px] font-medium text-brand-700">
                {report.coding.status ?? '未运行'}
              </span>
            </div>
            <div className="mt-2 flex flex-wrap items-center gap-3 text-[11px] text-slate-500">
              {report.coding.exitCode != null && <span>退出码 {report.coding.exitCode}</span>}
              {report.coding.runtimeMs != null && <span>{report.coding.runtimeMs}ms</span>}
              {report.coding.score != null && <span>本题 {report.coding.score}/5 分</span>}
            </div>
            {report.coding.stdout && (
              <pre className="mt-2 max-h-24 overflow-auto whitespace-pre-wrap rounded-lg bg-white px-2.5 py-1.5 font-mono text-[10px] leading-5 text-slate-600">
                {report.coding.stdout}
              </pre>
            )}
            {report.coding.stderr && (
              <pre className="mt-1.5 max-h-24 overflow-auto whitespace-pre-wrap rounded-lg bg-rose-50 px-2.5 py-1.5 font-mono text-[10px] leading-5 text-rose-600">
                {report.coding.stderr}
              </pre>
            )}
            {report.coding.comment && (
              <p className="mt-2 text-[11px] leading-5 text-slate-600">{report.coding.comment}</p>
            )}
          </div>
        )}

        {weaknesses.length > 0 && (
          <div className="mb-5">
            <div className="mb-2 flex items-center justify-between">
              <h3 className="text-xs font-semibold text-slate-700">弱项诊断</h3>
              <span className="text-[10px] text-slate-400">{weaknesses.length} 个可补</span>
            </div>
            <div className="space-y-2">
              {weaknesses.map((item) => (
                <div
                  key={item.kpKey}
                  className="rounded-xl border border-rose-100 bg-rose-50/40 px-3 py-2.5"
                >
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-medium text-slate-800">{item.kpName}</span>
                    <span className="ml-auto flex items-center gap-0.5">
                      {[1, 2, 3].map((level) => (
                        <span
                          key={level}
                          className={`h-1.5 w-1.5 rounded-full ${
                            level <= item.severity ? 'bg-rose-400' : 'bg-rose-100'
                          }`}
                        />
                      ))}
                    </span>
                    {item.questionSeq != null && (
                      <span className="text-[10px] text-slate-400">第 {item.questionSeq} 题</span>
                    )}
                  </div>
                  {item.evidence && (
                    <p className="mt-1 text-[11px] leading-5 text-slate-500">“{item.evidence}”</p>
                  )}
                  {item.advice && (
                    <p className="mt-0.5 text-[11px] leading-5 text-slate-400">建议：{item.advice}</p>
                  )}
                </div>
              ))}
            </div>

            {sessionId && (
              <button
                type="button"
                onClick={() => void handleStartTraining()}
                disabled={creating}
                className="mt-3 flex w-full items-center justify-center gap-1.5 rounded-xl bg-brand-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-brand-700 disabled:opacity-60"
              >
                {creating ? (
                  <Loader2 size={14} className="animate-spin" />
                ) : (
                  <Sparkles size={14} />
                )}
                {creating ? '正在生成训练…' : '生成专项训练 · 约 8 分钟'}
              </button>
            )}
            {trainError && <p className="mt-1.5 text-[11px] text-rose-500">{trainError}</p>}
          </div>
        )}

        <div className="space-y-4">
          <ReportList title="亮点" items={report.highlights} tone="brand" />
          {weaknesses.length === 0 && (
            <ReportList title="薄弱点" items={report.weakPoints} tone="rose" />
          )}
          <ReportList title="改进建议" items={report.advice} tone="slate" />
        </div>
      </div>
    </div>
  )
}

export default ReportDialog
