import { ArrowLeft, Users } from 'lucide-react'
import type { ReactNode } from 'react'

interface RoomHeaderProps {
  title: string
  subtitle: string
  participantCount: number
  onBack: () => void
  /** 右侧扩展位,比如「返回答题」 */
  actions?: ReactNode
}

function RoomHeader({ title, subtitle, participantCount, onBack, actions }: RoomHeaderProps) {
  return (
    <header className="flex flex-wrap items-center gap-3 border-b border-white/10 bg-black/30 px-4 py-3 backdrop-blur md:px-6">
      <button
        type="button"
        onClick={onBack}
        className="flex items-center gap-1 rounded-lg px-2 py-1.5 text-sm text-white/70 transition hover:bg-white/10 hover:text-white"
      >
        <ArrowLeft size={15} />
        返回
      </button>

      <div className="min-w-0">
        <h1 className="truncate text-sm font-semibold text-white">{title}</h1>
        <p className="mt-0.5 truncate text-[11px] text-white/50">{subtitle}</p>
      </div>

      <div className="ml-auto flex items-center gap-3">
        <span className="flex items-center gap-1 rounded-full bg-white/10 px-2.5 py-1 text-[11px] text-white/70">
          <Users size={12} />
          {participantCount} 人
        </span>
        {actions}
      </div>
    </header>
  )
}

export default RoomHeader
