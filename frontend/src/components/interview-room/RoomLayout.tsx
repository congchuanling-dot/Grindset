import type { ReactNode } from 'react'

interface RoomLayoutProps {
  header: ReactNode
  /** 底部控制条:默认隐藏,鼠标进入面试间时淡入 */
  footer: ReactNode
  children: ReactNode
}

/**
 * 面试间骨架:顶栏 + 深色主区 + 悬浮底部控制条。
 *
 * 控制条用绝对定位浮在主区上,不占布局;显隐完全交给 group-hover + transition,
 * 鼠标离开面试间区域就自动收起。
 */
function RoomLayout({ header, footer, children }: RoomLayoutProps) {
  return (
    <div className="group relative flex h-dvh flex-col overflow-hidden bg-[#14161a]">
      <div className="relative z-20 shrink-0">{header}</div>

      <div className="relative min-h-0 flex-1">{children}</div>

      <div className="pointer-events-none absolute inset-x-0 bottom-0 z-30 flex justify-center pb-6 opacity-0 transition-all duration-300 ease-out group-hover:pointer-events-auto group-hover:translate-y-0 group-hover:opacity-100 translate-y-4">
        {footer}
      </div>
    </div>
  )
}

export default RoomLayout
