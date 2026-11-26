import type { ResumeDetail } from '../../api/resume'

/**
 * 用解析结果还原的纸张版式。
 * 上传简历页的原件兜底、AI 面试页的文字版预览共用这一份。
 */
function ResumePaper({ detail }: { detail: ResumeDetail }) {
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
              <span key={skill.id} className="rounded bg-brand-50 px-2 py-0.5 text-xs text-brand-700">
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

export default ResumePaper
