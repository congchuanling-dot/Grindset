import { CircleStop, Timer } from 'lucide-react'
import { useEffect, useState, type Ref } from 'react'
import type { InterviewCodingView, InterviewQuestion, InterviewRunResult } from '../../api/interview'
import type { CodeLanguage } from '../../api/questionBank'
import type { VrmAvatarHandle } from '../avatar/VrmAvatarStage'
import CodingChatPanel from './CodingChatPanel'
import FreeCodingWorkbench, { type FreeCodingDraft } from './FreeCodingWorkbench'
import InterviewerTile from './InterviewerTile'
import ParticipantTile from './ParticipantTile'
import SelfVideo from './SelfVideo'

interface CodingStageProps {
  round: InterviewCodingView
  codingQuestion: InterviewQuestion | null
  avatarId: string
  onChangeAvatar: (id: string) => void
  stageRef: Ref<VrmAvatarHandle>
  stream: MediaStream | null
  cameraOn: boolean
  micOn: boolean
  starting: boolean
  onEnableCamera: () => void
  chatPending: string
  chatting: boolean
  interviewerSpeaking: boolean
  onChat: (message: string) => Promise<boolean>
  onStopSpeaking: () => void
  onRun: (language: CodeLanguage, code: string) => Promise<InterviewRunResult>
  onComplete: () => Promise<void>
  onFinish: () => void
  onDraftChange: (draft: FreeCodingDraft) => void
}

/** 面试间的 FREE_RUN 编码舞台:题目 + 编辑器 + 控制台,右侧面试官/自己/语音交流 */
function CodingStage({
  round,
  codingQuestion,
  avatarId,
  onChangeAvatar,
  stageRef,
  stream,
  cameraOn,
  micOn,
  starting,
  onEnableCamera,
  chatPending,
  chatting,
  interviewerSpeaking,
  onChat,
  onStopSpeaking,
  onRun,
  onComplete,
  onFinish,
  onDraftChange,
}: CodingStageProps) {
  const [remaining, setRemaining] = useState(() => remainingText(round.deadlineAtEpochMs))

  useEffect(() => {
    setRemaining(remainingText(round.deadlineAtEpochMs))
    const timer = window.setInterval(() => {
      setRemaining(remainingText(round.deadlineAtEpochMs))
    }, 1000)
    return () => window.clearInterval(timer)
  }, [round.deadlineAtEpochMs])

  return (
    <div className="flex h-full min-h-0 flex-col gap-3 p-3">
      <div className="flex shrink-0 items-center gap-3 rounded-xl border border-white/10 bg-[#1b1e23] px-4 py-2">
        <span className="rounded-full bg-brand-500/15 px-2.5 py-1 text-[11px] font-medium text-brand-300">
          FREE_RUN
        </span>
        <span className="truncate text-sm font-medium text-white/85">{round.title}</span>
        <span className="flex items-center gap-1.5 text-xs text-white/45">
          <Timer size={13} />
          剩余 {remaining}
        </span>
        <button
          type="button"
          onClick={onFinish}
          className="ml-auto flex items-center gap-1.5 rounded-lg border border-white/15 px-3 py-1.5 text-xs text-white/70 transition hover:border-rose-300/40 hover:text-rose-200"
        >
          <CircleStop size={13} />
          结束面试
        </button>
      </div>

      <div className="grid min-h-0 flex-1 grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_320px]">
        <div className="min-h-[420px] overflow-hidden">
          <FreeCodingWorkbench
            round={round}
            onRun={onRun}
            onComplete={onComplete}
            onDraftChange={onDraftChange}
          />
        </div>

        <div className="flex min-h-0 flex-col gap-3">
          <div className="grid shrink-0 grid-cols-2 gap-2">
            <div className="h-28 min-h-[112px] overflow-hidden rounded-xl">
              <InterviewerTile
                avatarId={avatarId}
                onChangeAvatar={onChangeAvatar}
                role="面试官"
                stageRef={stageRef}
              />
            </div>
            <div className="h-28 min-h-[112px] overflow-hidden rounded-xl">
              <ParticipantTile name="我" role="候选人" muted={!micOn}>
                <SelfVideo
                  stream={stream}
                  cameraOn={cameraOn}
                  starting={starting}
                  onEnable={onEnableCamera}
                />
              </ParticipantTile>
            </div>
          </div>
          <div className="min-h-0 flex-1 overflow-hidden rounded-xl">
            <CodingChatPanel
              messages={codingQuestion?.messages ?? []}
              pendingText={chatPending}
              chatting={chatting}
              micOn={micOn}
              interviewerSpeaking={interviewerSpeaking}
              onAsk={onChat}
              onStopSpeaking={onStopSpeaking}
            />
          </div>
        </div>
      </div>
    </div>
  )
}

function remainingText(epochMs: number | null): string {
  if (!epochMs) return '不限时'
  const diff = epochMs - Date.now()
  if (diff <= 0) return '00:00'
  const total = Math.floor(diff / 1000)
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

export default CodingStage
