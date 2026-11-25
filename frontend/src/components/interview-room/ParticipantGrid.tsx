import type { ReactNode } from 'react'

interface ParticipantGridProps {
  count: number
  children: ReactNode
}

/**
 * 宫格布局:按人数选列数,和腾讯会议的宫格视图一致。
 * 人数只影响外层 class,具体席位由 children 提供,方便后续加真人。
 */
function gridClass(count: number): string {
  if (count <= 1) return 'grid-cols-1'
  if (count <= 4) return 'grid-cols-1 md:grid-cols-2'
  if (count <= 6) return 'grid-cols-2 md:grid-cols-3'
  return 'grid-cols-2 md:grid-cols-3 xl:grid-cols-4'
}

function ParticipantGrid({ count, children }: ParticipantGridProps) {
  return (
    <div className="h-full overflow-y-auto p-4 md:p-6">
      <div className={`grid h-full auto-rows-fr gap-4 ${gridClass(count)}`}>{children}</div>
    </div>
  )
}

export default ParticipantGrid
