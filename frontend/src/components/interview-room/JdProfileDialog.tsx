import { Briefcase, X } from 'lucide-react'
import type { JdProfile } from '../../api/interview'

interface JdProfileDialogProps {
  profile: JdProfile
  position?: string
  onClose: () => void
}

const LEVEL_LABELS: Record<string, string> = {
  intern: '实习',
  campus: '校招',
  experienced: '社招',
  senior: '高级/专家',
}

function ChipList({ title, items }: { title: string; items: string[] }) {
  if (items.length === 0) return null
  return (
    <section>
      <h3 className="mb-1.5 text-xs font-semibold text-slate-700">{title}</h3>
      <div className="flex flex-wrap gap-1.5">
        {items.map((item) => (
          <span
            key={item}
            className="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-[11px] text-slate-600"
          >
            {item}
          </span>
        ))}
      </div>
    </section>
  )
}

/** 面试间顶部「JD 定制」弹窗:展示解析出的岗位画像,便于开始前确认 */
function JdProfileDialog({ profile, position, onClose }: JdProfileDialogProps) {
  const heading = profile.title ?? position ?? 'JD 定制'
  const level = profile.level ? (LEVEL_LABELS[profile.level] ?? profile.level) : null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="max-h-[85vh] w-full max-w-lg overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl">
        <div className="mb-4 flex items-start justify-between">
          <div>
            <h2 className="flex items-center gap-1.5 text-sm font-semibold text-slate-800">
              <Briefcase size={15} className="text-brand-600" />
              JD 定制
            </h2>
            <p className="mt-0.5 text-[11px] text-slate-400">
              {heading}
              {profile.company ? ` · ${profile.company}` : ''}
              {level ? ` · ${level}` : ''}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
          >
            <X size={16} />
          </button>
        </div>

        <div className="space-y-4">
          <ChipList title="必须项" items={profile.mustHave ?? []} />
          <ChipList title="加分项" items={profile.niceToHave ?? []} />

          {(profile.competencies ?? []).length > 0 && (
            <section>
              <h3 className="mb-1.5 text-xs font-semibold text-slate-700">能力项与权重</h3>
              <div className="space-y-1.5">
                {(profile.competencies ?? []).map((item) => (
                  <div
                    key={item.name}
                    className="rounded-lg border border-slate-200 bg-slate-50/60 px-3 py-2"
                  >
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-medium text-slate-700">{item.name}</span>
                      <span className="ml-auto text-[11px] tabular-nums text-slate-400">
                        {item.weight}%
                      </span>
                    </div>
                    {item.expectation && (
                      <p className="mt-0.5 text-[11px] leading-5 text-slate-500">
                        {item.expectation}
                      </p>
                    )}
                  </div>
                ))}
              </div>
            </section>
          )}

          <ChipList title="简历缺口" items={profile.resumeGaps ?? []} />
        </div>
      </div>
    </div>
  )
}

export default JdProfileDialog
