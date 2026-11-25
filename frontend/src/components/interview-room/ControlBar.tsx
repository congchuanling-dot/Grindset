import { Mic, MicOff, MonitorUp, Paperclip, PhoneOff, Video, VideoOff } from 'lucide-react'
import ControlButton from './ControlButton'

interface ControlBarProps {
  micOn: boolean
  cameraOn: boolean
  onToggleMic: () => void
  onToggleCamera: () => void
  onLeave: () => void
}

/** 底部控制条:麦克风/摄像头可用,共享屏幕与上传文件先占位 */
function ControlBar({ micOn, cameraOn, onToggleMic, onToggleCamera, onLeave }: ControlBarProps) {
  return (
    <div className="flex items-end gap-1 rounded-2xl bg-[#1b1e24]/90 px-3 py-2 ring-1 ring-white/10 backdrop-blur">
      <ControlButton
        icon={micOn ? Mic : MicOff}
        label={micOn ? '麦克风' : '已静音'}
        active={micOn}
        off={!micOn}
        onClick={onToggleMic}
      />
      <ControlButton
        icon={cameraOn ? Video : VideoOff}
        label={cameraOn ? '摄像头' : '已关闭'}
        active={cameraOn}
        off={!cameraOn}
        onClick={onToggleCamera}
      />
      <ControlButton icon={MonitorUp} label="共享屏幕" comingSoon />
      <ControlButton icon={Paperclip} label="上传文件" comingSoon />

      <span className="mx-1 h-8 w-px self-center bg-white/10" />

      <ControlButton icon={PhoneOff} label="离开" danger onClick={onLeave} />
    </div>
  )
}

export default ControlBar
