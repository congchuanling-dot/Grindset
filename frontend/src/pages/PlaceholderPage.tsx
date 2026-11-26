import { Construction } from 'lucide-react'
import { Link } from 'react-router-dom'

// 侧边栏里还没做的栏目统一用这个页面占位
function PlaceholderPage({ title, description }: { title: string; description: string }) {
  return (
    <div className="flex h-full flex-col items-center justify-center px-6 text-center">
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
        <Construction size={22} />
      </span>
      <h1 className="mt-4 text-lg font-semibold text-slate-800">{title}</h1>
      <p className="mt-2 max-w-sm text-sm leading-6 text-slate-500">{description}</p>
      <Link
        to="/"
        className="mt-5 rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-600 transition hover:border-brand-300 hover:text-brand-700"
      >
        先去问问 AI 助手
      </Link>
    </div>
  )
}

export default PlaceholderPage
