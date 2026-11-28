import { ChevronRight, Loader2, Play, RotateCcw, Target, TrendingUp } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  createTrainingSession,
  getTrainingDashboard,
  retrainMistakes,
  type TrainingDashboard,
  type TrainingStage,
} from '../../api/training'
import { errMessage } from '../../utils/error'

/** 弱项训练仪表盘：五维雷达、成长曲线、弱项地图、错题本。 */
function TrainingDashboardPage() {
  const navigate = useNavigate()
  const [data, setData] = useState<TrainingDashboard | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [starting, setStarting] = useState('')

  useEffect(() => {
    let cancelled = false
    getTrainingDashboard()
      .then((result) => {
        if (!cancelled) setData(result)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(errMessage(err, '加载训练仪表盘失败'))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  async function start(stage: TrainingStage) {
    if (starting) return
    setStarting(stage)
    setError('')
    try {
      const session = await createTrainingSession(stage)
      navigate(`/training/${session.id}`)
    } catch (err) {
      setError(errMessage(err, '创建训练失败'))
    } finally {
      setStarting('')
    }
  }

  async function startMistakes() {
    if (starting) return
    setStarting('MISTAKE')
    setError('')
    try {
      const session = await retrainMistakes()
      navigate(`/training/${session.id}`)
    } catch (err) {
      setError(errMessage(err, '创建错题重练失败'))
    } finally {
      setStarting('')
    }
  }

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center bg-slate-50">
        <Loader2 size={20} className="animate-spin text-brand-600" />
      </div>
    )
  }

  return (
    <div className="h-full overflow-y-auto bg-slate-50">
      <div className="mx-auto max-w-4xl px-4 py-6">
        <div className="mb-5 flex flex-wrap items-center gap-2">
          <h1 className="flex items-center gap-2 text-base font-semibold text-slate-800">
            <Target size={17} className="text-brand-600" />
            弱项训练
          </h1>
          <div className="ml-auto flex gap-2">
            {data?.hasBaseline ? (
              <button
                type="button"
                onClick={() => void start('DAILY')}
                disabled={Boolean(starting)}
                className="flex items-center gap-1.5 rounded-lg bg-brand-600 px-3.5 py-2 text-xs font-medium text-white transition hover:bg-brand-700 disabled:opacity-60"
              >
                {starting === 'DAILY' ? <Loader2 size={13} className="animate-spin" /> : <Play size={13} />}
                开始今日训练
              </button>
            ) : (
              <button
                type="button"
                onClick={() => void start('BASELINE')}
                disabled={Boolean(starting)}
                className="flex items-center gap-1.5 rounded-lg bg-brand-600 px-3.5 py-2 text-xs font-medium text-white transition hover:bg-brand-700 disabled:opacity-60"
              >
                {starting === 'BASELINE' ? <Loader2 size={13} className="animate-spin" /> : <Play size={13} />}
                开始基线测评
              </button>
            )}
            {data && data.mistakeCount > 0 && (
              <button
                type="button"
                onClick={() => void startMistakes()}
                disabled={Boolean(starting)}
                className="flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs text-slate-600 transition hover:bg-slate-50 disabled:opacity-60"
              >
                {starting === 'MISTAKE'
                  ? <Loader2 size={13} className="animate-spin" />
                  : <RotateCcw size={13} />}
                错题重练
              </button>
            )}
          </div>
        </div>

        {error && (
          <div className="mb-4 rounded-xl border border-rose-100 bg-rose-50 px-4 py-3 text-xs text-rose-600">
            {error}
          </div>
        )}

        {data && (
          <>
            <div className="mb-4 grid gap-3 lg:grid-cols-[1.2fr_1fr]">
              <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <h2 className="mb-1 text-xs font-semibold text-slate-700">能力雷达</h2>
                <RadarChart dimensions={data.dimensions} />
              </section>

              <section className="flex flex-col rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <h2 className="text-xs font-semibold text-slate-700">总体 readiness</h2>
                <div className="mt-2 flex items-end gap-2">
                  <span className="text-4xl font-semibold tabular-nums text-brand-600">
                    {data.readiness}
                  </span>
                  <span className={`pb-1 text-xs ${data.readinessDelta7d >= 0 ? 'text-emerald-600' : 'text-rose-500'}`}>
                    {data.readinessDelta7d >= 0 ? '+' : ''}{data.readinessDelta7d} / 7天
                  </span>
                </div>
                {(data.dueCount > 0 || data.mistakeCount > 0) && (
                  <p className="mt-1 text-[11px] text-slate-400">
                    {data.dueCount > 0 ? `到期复习 ${data.dueCount}` : ''}
                    {data.dueCount > 0 && data.mistakeCount > 0 ? ' · ' : ''}
                    {data.mistakeCount > 0 ? `未解决错题 ${data.mistakeCount}` : ''}
                  </p>
                )}

                {data.activePlan && (
                  <Link
                    to={`/training/${data.activePlan.id}`}
                    className="mt-3 flex items-center gap-2 rounded-xl border border-brand-100 bg-brand-50/50 px-3 py-2.5 text-xs text-brand-700 transition hover:border-brand-200"
                  >
                    <Play size={13} />
                    <span className="min-w-0 truncate">{data.activePlan.title}</span>
                    <span className="ml-auto shrink-0 tabular-nums">
                      {data.activePlan.completedCount}/{data.activePlan.itemCount}
                    </span>
                    <ChevronRight size={12} />
                  </Link>
                )}
              </section>
            </div>

            <section className="mb-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="mb-2 flex items-center gap-1.5">
                <TrendingUp size={14} className="text-brand-600" />
                <h2 className="text-xs font-semibold text-slate-700">成长曲线</h2>
                <span className="ml-auto text-[10px] text-slate-400">近 14 天</span>
              </div>
              <GrowthChart points={data.growth} />
            </section>

            <section className="mb-4">
              <h2 className="mb-2 text-xs font-semibold text-slate-700">弱项地图</h2>
              {data.weaknesses.length === 0 ? (
                <div className="rounded-xl border border-dashed border-slate-200 bg-white px-4 py-6 text-center text-xs text-slate-400">
                  还没有弱项数据，先做一次基线测评或面试。
                </div>
              ) : (
                <div className="space-y-2">
                  {data.weaknesses.map((item) => (
                    <div key={item.kpKey} className="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
                      <div className="flex items-center gap-2">
                        <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] text-slate-500">
                          {item.dimensionLabel}
                        </span>
                        <span className="text-xs font-medium text-slate-800">{item.kpName}</span>
                        <span className="ml-auto text-[11px] tabular-nums text-slate-500">{item.mastery}</span>
                      </div>
                      <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-slate-100">
                        <div
                          className="h-full rounded-full bg-brand-500"
                          style={{ width: `${Math.max(0, Math.min(100, item.mastery))}%` }}
                        />
                      </div>
                      {item.evidence && (
                        <p className="mt-1.5 line-clamp-2 text-[11px] leading-5 text-slate-500">
                          “{item.evidence}”
                        </p>
                      )}
                      {item.nextReviewAt && (
                        <p className="mt-0.5 text-[10px] text-slate-400">下次复习 {item.nextReviewAt}</p>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </section>

            <section>
              <div className="mb-2 flex items-center gap-2">
                <h2 className="text-xs font-semibold text-slate-700">错题本</h2>
                {data.mistakeCount > 0 && (
                  <button
                    type="button"
                    onClick={() => void startMistakes()}
                    className="ml-auto text-[11px] font-medium text-brand-600 hover:text-brand-700"
                  >
                    去重练
                  </button>
                )}
              </div>
              {data.recentMistakes.length === 0 ? (
                <div className="rounded-xl border border-dashed border-slate-200 bg-white px-4 py-6 text-center text-xs text-slate-400">
                  暂无错题。
                </div>
              ) : (
                <div className="space-y-2">
                  {data.recentMistakes.map((item) => (
                    <div key={item.id} className="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
                      <div className="flex items-center gap-2">
                        <span className="rounded-full bg-rose-50 px-2 py-0.5 text-[10px] text-rose-600">
                          {item.dimensionLabel}
                        </span>
                        <span className="truncate text-xs font-medium text-slate-800">{item.kpName}</span>
                        <span className="ml-auto text-[11px] tabular-nums text-rose-500">{item.score}/5</span>
                      </div>
                      {item.title && (
                        <p className="mt-1 truncate text-[11px] text-slate-500">{item.title}</p>
                      )}
                      <p className="mt-0.5 text-[10px] text-slate-400">
                        {item.reviewCount > 0 ? `已重练 ${item.reviewCount} 次` : '待重练'}
                        {item.nextReviewAt ? ` · 下次 ${item.nextReviewAt}` : ''}
                      </p>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </>
        )}
      </div>
    </div>
  )
}

function RadarChart({ dimensions }: { dimensions: TrainingDashboard['dimensions'] }) {
  const size = 260
  const center = size / 2
  const radius = 82
  const count = Math.max(dimensions.length, 3)
  const pointAt = (index: number, value: number) => {
    const angle = -Math.PI / 2 + (Math.PI * 2 * index) / count
    const r = radius * Math.max(0, Math.min(100, value)) / 100
    return [center + Math.cos(angle) * r, center + Math.sin(angle) * r] as const
  }
  const labelAt = (index: number) => {
    const angle = -Math.PI / 2 + (Math.PI * 2 * index) / count
    return [center + Math.cos(angle) * (radius + 24), center + Math.sin(angle) * (radius + 24)] as const
  }
  const polygon = dimensions.map((item, index) => pointAt(index, item.mastery).join(',')).join(' ')
  return (
    <svg viewBox={`0 0 ${size} ${size}`} className="mx-auto h-52 w-full max-w-[280px] text-brand-500">
      {[0.25, 0.5, 0.75, 1].map((ratio) => (
        <polygon
          key={ratio}
          points={dimensions.map((_, index) => {
            const angle = -Math.PI / 2 + (Math.PI * 2 * index) / count
            const r = radius * ratio
            return `${center + Math.cos(angle) * r},${center + Math.sin(angle) * r}`
          }).join(' ')}
          fill="none"
          stroke="currentColor"
          strokeOpacity={0.12}
        />
      ))}
      {dimensions.map((_, index) => {
        const [x, y] = pointAt(index, 100)
        return <line key={index} x1={center} y1={center} x2={x} y2={y}
          stroke="currentColor" strokeOpacity={0.12} />
      })}
      <polygon points={polygon} fill="currentColor" fillOpacity={0.18}
        stroke="currentColor" strokeWidth={2} />
      {dimensions.map((item, index) => {
        const [x, y] = labelAt(index)
        return (
          <g key={item.code}>
            <text x={x} y={y} textAnchor="middle" dominantBaseline="middle"
              className="fill-slate-500" fontSize={10}>{item.label}</text>
            <text x={x} y={y + 12} textAnchor="middle" dominantBaseline="middle"
              className="fill-slate-400" fontSize={9}>{item.mastery}</text>
          </g>
        )
      })}
    </svg>
  )
}

function GrowthChart({ points }: { points: TrainingDashboard['growth'] }) {
  if (points.length < 2) {
    return (
      <div className="flex h-24 items-center justify-center text-[11px] text-slate-400">
        训练 2 天后会生成成长曲线
      </div>
    )
  }
  const width = 640
  const height = 110
  const padding = 14
  const min = Math.min(...points.map((item) => item.mastery), 40)
  const max = Math.max(...points.map((item) => item.mastery), 60)
  const span = Math.max(1, max - min)
  const coords = points.map((item, index) => {
    const x = padding + (index * (width - padding * 2)) / (points.length - 1)
    const y = height - padding - ((item.mastery - min) / span) * (height - padding * 2)
    return [x, y] as const
  })
  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="h-28 w-full text-brand-500">
      <polyline
        points={coords.map(([x, y]) => `${x},${y}`).join(' ')}
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {coords.map(([x, y], index) => (
        <circle key={index} cx={x} cy={y} r={2.5} fill="currentColor" />
      ))}
    </svg>
  )
}

export default TrainingDashboardPage
