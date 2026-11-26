import { Loader2, Mic, Square } from 'lucide-react'
import type { VoiceState } from '../hooks/useVoiceInput'

interface VoiceInputButtonProps {
  state: VoiceState
  /** 已录音秒数 */
  seconds: number
  disabled?: boolean
  onClick: () => void
}

/**
 * 通用语音输入按钮:点击开始录音,再次点击结束并识别。
 * 录音中显示红点 + 计时,连接/收尾阶段显示转圈。
 */
function VoiceInputButton({ state, seconds, disabled, onClick }: VoiceInputButtonProps) {
  const busy = state === 'connecting'
  const recording = state === 'recording'
  const finishing = state === 'finishing'

  const title = recording
    ? '结束语音识别'
    : finishing
      ? '正在识别…'
      : busy
        ? '正在连接语音识别…'
        : '语音输入'

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled || busy || finishing}
      title={title}
      aria-label={title}
      className={`flex h-8 shrink-0 items-center justify-center rounded-lg transition ${
        recording
          ? 'gap-1 bg-rose-50 px-2 text-rose-600 hover:bg-rose-100'
          : 'w-8 text-slate-400 hover:bg-slate-100 hover:text-brand-600'
      } disabled:cursor-not-allowed disabled:text-slate-300 disabled:hover:bg-transparent`}
    >
      {busy || finishing ? (
        <Loader2 size={16} className="animate-spin" />
      ) : recording ? (
        <>
          <span className="h-2 w-2 animate-pulse rounded-full bg-rose-500" />
          <Square size={11} fill="currentColor" />
          <span className="text-[11px] tabular-nums">{formatSeconds(seconds)}</span>
        </>
      ) : (
        <Mic size={18} />
      )}
    </button>
  )
}

function formatSeconds(total: number): string {
  const minutes = Math.floor(total / 60)
  const rest = total % 60
  return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
}

export default VoiceInputButton
