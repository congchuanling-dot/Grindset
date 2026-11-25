import type { LucideIcon } from 'lucide-react'

interface ControlButtonProps {
  icon: LucideIcon
  label: string
  onClick?: () => void
  /** 功能处于开启状态(麦克风/摄像头开)时用中性色;关闭时用红色底提示 */
  active?: boolean
  /** 关闭态样式:红色 */
  off?: boolean
  /** 未实现的功能,置灰并显示「即将上线」 */
  comingSoon?: boolean
  danger?: boolean
}

/** 底部控制条按钮:圆形图标 + 下方文字 */
function ControlButton({
  icon: Icon,
  label,
  onClick,
  active = false,
  off = false,
  comingSoon = false,
  danger = false,
}: ControlButtonProps) {
  const base =
    'flex h-11 w-11 items-center justify-center rounded-full transition-colors duration-200'
  const tone = danger
    ? 'bg-rose-500 text-white hover:bg-rose-600'
    : comingSoon
      ? 'bg-white/5 text-white/35'
      : off
        ? 'bg-rose-500/90 text-white hover:bg-rose-500'
        : active
          ? 'bg-white/15 text-white hover:bg-white/25'
          : 'bg-white/10 text-white/80 hover:bg-white/20'

  return (
    <div className="flex w-16 flex-col items-center gap-1" title={comingSoon ? '即将上线' : label}>
      <button
        type="button"
        onClick={onClick}
        disabled={comingSoon}
        className={`${base} ${tone} ${comingSoon ? 'cursor-not-allowed' : ''}`}
        aria-label={label}
      >
        <Icon size={18} />
      </button>
      <span className={`text-[10px] ${comingSoon ? 'text-white/30' : 'text-white/60'}`}>
        {label}
      </span>
    </div>
  )
}

export default ControlButton
