import { Check } from 'lucide-react'
import { AVATAR_MODELS } from '../avatar/avatarModels'

interface AvatarPickerProps {
  value: string
  onSelect: (id: string) => void
}

/** 面试官形象选择面板,由 InterviewerTile 控制显隐 */
function AvatarPicker({ value, onSelect }: AvatarPickerProps) {
  return (
    <div className="w-48 overflow-hidden rounded-xl border border-white/10 bg-[#24272e]/95 shadow-xl backdrop-blur">
      <p className="px-3 py-2 text-[11px] text-white/40">选择面试官形象</p>
      {AVATAR_MODELS.map((model) => (
        <button
          key={model.id}
          type="button"
          onClick={() => onSelect(model.id)}
          className={`flex w-full items-center justify-between gap-2 px-3 py-2 text-left transition hover:bg-white/10 ${
            model.id === value ? 'bg-white/5' : ''
          }`}
        >
          <span className="min-w-0">
            <span className="block truncate text-xs text-white/90">{model.name}</span>
            <span className="block truncate text-[10px] text-white/40">{model.description}</span>
          </span>
          {model.id === value && <Check size={14} className="shrink-0 text-brand-400" />}
        </button>
      ))}
    </div>
  )
}

export default AvatarPicker
