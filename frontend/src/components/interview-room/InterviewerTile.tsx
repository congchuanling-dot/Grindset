import { UserRoundPen } from 'lucide-react'
import { useState, type Ref } from 'react'
import VrmAvatarStage, { type VrmAvatarHandle } from '../avatar/VrmAvatarStage'
import { resolveAvatarModel } from '../avatar/avatarModels'
import AvatarPicker from './AvatarPicker'
import ParticipantTile from './ParticipantTile'

interface InterviewerTileProps {
  avatarId: string
  onChangeAvatar: (id: string) => void
  role?: string
  /** 交给面试间驱动口型 */
  stageRef?: Ref<VrmAvatarHandle>
}

/** 面试官席位:VRM 半身形象 + 换形象入口 */
function InterviewerTile({ avatarId, onChangeAvatar, role, stageRef }: InterviewerTileProps) {
  const [pickerOpen, setPickerOpen] = useState(false)
  const avatar = resolveAvatarModel(avatarId)

  return (
    <ParticipantTile
      name="AI 面试官"
      role={role}
      action={
        <div className="relative">
          <button
            type="button"
            onClick={() => setPickerOpen((open) => !open)}
            className="flex items-center gap-1 rounded-full bg-black/40 px-2.5 py-1 text-[11px] text-white/70 opacity-0 transition hover:bg-black/60 hover:text-white group-hover/tile:opacity-100"
          >
            <UserRoundPen size={12} />
            换形象
          </button>
          {pickerOpen && (
            <div className="absolute right-0 top-9 z-30">
              <AvatarPicker
                value={avatarId}
                onSelect={(id) => {
                  onChangeAvatar(id)
                  setPickerOpen(false)
                }}
              />
            </div>
          )}
        </div>
      }
    >
      <div className="group/tile h-full w-full">
        <VrmAvatarStage key={avatar.url} ref={stageRef} modelUrl={avatar.url} framing="half" />
      </div>
    </ParticipantTile>
  )
}

export default InterviewerTile
