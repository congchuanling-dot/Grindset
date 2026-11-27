import { MicOff } from 'lucide-react'
import type { ReactNode } from 'react'

interface ParticipantTileProps {
  name: string
  role?: string
  /** 静音时在右上角显示麦克风关闭标记 */
  muted?: boolean
  /** 席位右上角额外操作(比如面试官的「换形象」) */
  action?: ReactNode
  children: ReactNode
}

/** 单个席位外壳:负责边框、名字条、静音标记,内容完全由外部注入 */
function ParticipantTile({ name, role, muted, action, children }: ParticipantTileProps) {
  return (
    <div className="group/tile relative h-full min-h-0 overflow-hidden rounded-2xl bg-[#202329] ring-1 ring-white/10">
      <div className="absolute inset-0">{children}</div>

      {action && <div className="absolute right-2 top-2 z-20">{action}</div>}

      {muted && (
        <div className="absolute right-2 top-2 z-10 flex h-7 w-7 items-center justify-center rounded-full bg-rose-500/90 text-white">
          <MicOff size={13} />
        </div>
      )}

      <div className="pointer-events-none absolute inset-x-0 bottom-0 z-10 flex items-center justify-between gap-2 bg-gradient-to-t from-black/70 to-transparent px-3 pb-2 pt-8">
        <span className="truncate text-xs font-medium text-white/90">{name}</span>
        {role && <span className="shrink-0 text-[11px] text-white/50">{role}</span>}
      </div>
    </div>
  )
}

export default ParticipantTile
