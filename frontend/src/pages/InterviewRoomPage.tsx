import { AlertCircle, CircleStop, Loader2, MessageSquare, RotateCw, Sparkles } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  finishInterview,
  getInterview,
  streamNextQuestion,
  submitAnswer,
  type InterviewDetail,
} from '../api/interview'
import { AVATAR_MODELS, AVATAR_STORAGE_KEY } from '../components/avatar/avatarModels'
import ControlBar from '../components/interview-room/ControlBar'
import InterviewerTile from '../components/interview-room/InterviewerTile'
import ParticipantGrid from '../components/interview-room/ParticipantGrid'
import ParticipantTile from '../components/interview-room/ParticipantTile'
import ReportDialog from '../components/interview-room/ReportDialog'
import RoomHeader from '../components/interview-room/RoomHeader'
import RoomLayout from '../components/interview-room/RoomLayout'
import SelfVideo from '../components/interview-room/SelfVideo'
import TranscriptPanel from '../components/interview-room/TranscriptPanel'
import { QuestionStatus, SessionStatus } from '../constants/status'
import { useLocalMedia } from '../hooks/useLocalMedia'
import { useRoomParticipants } from '../hooks/useRoomParticipants'

const STYLE_LABELS: Record<string, string> = {
  pressure: '压力面',
  middle: '适中',
  relaxed: '轻松',
}

function readStoredAvatarId(): string {
  const stored = localStorage.getItem(AVATAR_STORAGE_KEY)
  return AVATAR_MODELS.some((model) => model.id === stored) ? (stored as string) : AVATAR_MODELS[0].id
}

/**
 * AI 面试间。
 *
 * 左侧本地摄像头、右侧 VRM 面试官、最右对话记录(面试官提问 / 我的回答 / 点评)。
 * 出题与评分仍走后端原有接口,语音输入复用 /api/asr/stream;复盘在结束面试后生成。
 */
function InterviewRoomPage() {
  const { sessionId = '' } = useParams()
  const navigate = useNavigate()

  const [detail, setDetail] = useState<InterviewDetail | null>(null)
  const [avatarId, setAvatarId] = useState<string>(readStoredAvatarId)
  const [pendingText, setPendingText] = useState('')
  const [asking, setAsking] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [finishing, setFinishing] = useState(false)
  const [reportOpen, setReportOpen] = useState(false)
  // 对话记录默认收起,需要时再展开
  const [transcriptOpen, setTranscriptOpen] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const {
    stream,
    cameraOn,
    micOn,
    starting,
    error: mediaError,
    start,
    stop,
    toggleCamera,
    toggleMic,
  } = useLocalMedia()
  // 第一版只有自己和面试官;真人连麦时往列表里追加 peer 即可,宫格会自动重排
  const { participants } = useRoomParticipants([
    { id: 'self', kind: 'self', name: '我', role: '候选人' },
    { id: 'interviewer', kind: 'interviewer', name: 'AI 面试官' },
  ])

  useEffect(() => {
    void start()
  }, [start])

  const refresh = useCallback(async () => {
    try {
      const data = await getInterview(sessionId)
      setDetail(data)
      return data
    } catch {
      return null
    }
  }, [sessionId])

  useEffect(() => {
    let cancelled = false
    getInterview(sessionId)
      .then((data) => {
        if (!cancelled) setDetail(data)
      })
      .catch(() => {
        // 拉不到会话信息不影响面试间本身,顶栏退化成通用文案
      })
    return () => {
      cancelled = true
    }
  }, [sessionId])

  const handleAvatarChange = useCallback((id: string) => {
    setAvatarId(id)
    localStorage.setItem(AVATAR_STORAGE_KEY, id)
  }, [])

  const handleExpired = useCallback(
    (message: string) => {
      setActionError(message)
    },
    [],
  )

  /** 让面试官出下一题:SSE 文本边收边显示,完成后重新拉详情落库状态 */
  const handleAsk = useCallback(async () => {
    if (asking) return
    setAsking(true)
    setActionError(null)
    setPendingText('')
    let text = ''
    try {
      await streamNextQuestion(sessionId, {
        onDelta: (delta) => {
          text += delta
          setPendingText(text)
        },
        onError: (message) => handleExpired(message),
      })
    } catch (err) {
      setActionError(err instanceof Error ? err.message : '出题失败,请重试')
    } finally {
      setPendingText('')
      setAsking(false)
      await refresh()
    }
  }, [asking, sessionId, refresh, handleExpired])

  /** 提交我的回答,成功返回 true 让对话面板清空输入框 */
  const handleSubmitAnswer = useCallback(
    async (answer: string) => {
      if (submitting) return false
      setSubmitting(true)
      setActionError(null)
      try {
        await submitAnswer(sessionId, answer)
        await refresh()
        return true
      } catch (err) {
        setActionError(err instanceof Error ? err.message : '提交回答失败,请重试')
        return false
      } finally {
        setSubmitting(false)
      }
    },
    [submitting, sessionId, refresh],
  )

  const handleLeave = useCallback(() => {
    stop()
    navigate('/records')
  }, [stop, navigate])

  const handleFinish = useCallback(async () => {
    if (finishing) return
    setFinishing(true)
    setActionError(null)
    try {
      const report = await finishInterview(sessionId)
      const latest = await refresh()
      if (report) {
        setReportOpen(true)
      } else if (!latest) {
        navigate('/records')
      }
    } catch (err) {
      setActionError(err instanceof Error ? err.message : '结束面试失败,请稍后重试')
    } finally {
      setFinishing(false)
    }
  }, [finishing, sessionId, refresh, navigate])

  const sessionFinished = detail?.status === SessionStatus.FINISHED
  const questions = detail?.questions ?? []
  const lastQuestion = questions[questions.length - 1]
  const canAsk =
    !sessionFinished &&
    !asking &&
    !submitting &&
    (questions.length === 0 || lastQuestion?.status === QuestionStatus.CLOSED) &&
    (detail ? detail.completedQuestions < detail.questionCount : true)

  const subtitleParts = [
    detail?.resumeFileName,
    detail ? (STYLE_LABELS[detail.style] ?? detail.style) : null,
    detail?.round,
    detail ? `已完成 ${detail.completedQuestions}/${detail.questionCount} 题` : null,
  ].filter(Boolean)

  return (
    <>
      <RoomLayout
        header={
          <RoomHeader
            title={detail?.position ?? 'AI 面试间'}
            subtitle={subtitleParts.join(' · ') || '正在加载面试信息...'}
            participantCount={participants.length}
            onBack={() => navigate('/interview')}
            actions={
              <>
                <button
                  type="button"
                  onClick={() => setTranscriptOpen((open) => !open)}
                  className={`relative flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs transition ${
                    transcriptOpen
                      ? 'bg-white/15 text-white'
                      : 'border border-white/15 text-white/80 hover:bg-white/10'
                  }`}
                >
                  <MessageSquare size={13} />
                  对话记录
                  {!transcriptOpen && questions.length === 0 && (
                    <span className="absolute -right-0.5 -top-0.5 h-2 w-2 animate-pulse rounded-full bg-teal-400" />
                  )}
                </button>
                {detail?.report ? (
                  <button
                    type="button"
                    onClick={() => setReportOpen(true)}
                    className="flex items-center gap-1.5 rounded-lg bg-teal-600 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-teal-700"
                  >
                    <Sparkles size={13} />
                    查看复盘
                  </button>
                ) : sessionFinished ? null : (
                  <button
                    type="button"
                    onClick={() => void handleFinish()}
                    disabled={finishing}
                    className="flex items-center gap-1.5 rounded-lg border border-white/15 px-3 py-1.5 text-xs text-white/80 transition hover:border-rose-300/40 hover:text-rose-200 disabled:opacity-50"
                  >
                    {finishing ? (
                      <Loader2 size={13} className="animate-spin" />
                    ) : (
                      <CircleStop size={13} />
                    )}
                    结束面试
                  </button>
                )}
              </>
            }
          />
        }
        footer={
          <ControlBar
            micOn={micOn}
            cameraOn={cameraOn}
            onToggleMic={toggleMic}
            onToggleCamera={toggleCamera}
            onLeave={handleLeave}
          />
        }
      >
        {(mediaError || actionError) && (
          <div className="absolute left-4 top-4 z-20 flex items-center gap-2 rounded-lg border border-rose-400/30 bg-rose-500/15 px-3 py-2 text-xs text-rose-100 backdrop-blur">
            <AlertCircle size={14} className="shrink-0" />
            <span>{mediaError ?? actionError}</span>
            {mediaError && (
              <button
                type="button"
                onClick={() => void start()}
                className="ml-1 flex items-center gap-1 rounded px-1.5 py-0.5 text-rose-100/80 transition hover:bg-white/10"
              >
                <RotateCw size={12} />
                重试
              </button>
            )}
          </div>
        )}

        <div
          className={`relative flex h-full min-h-0 transition-[padding] duration-300 ${
            transcriptOpen ? 'md:pr-[380px]' : ''
          }`}
        >
          <div className="min-w-0 flex-1">
            <ParticipantGrid count={participants.length}>
              {participants.map((participant) => {
                if (participant.kind === 'self') {
                  return (
                    <ParticipantTile key={participant.id} name="我" role="候选人" muted={!micOn}>
                      <SelfVideo
                        stream={stream}
                        cameraOn={cameraOn}
                        starting={starting}
                        onEnable={() => void start()}
                      />
                    </ParticipantTile>
                  )
                }

                if (participant.kind === 'interviewer') {
                  return (
                    <InterviewerTile
                      key={participant.id}
                      avatarId={avatarId}
                      onChangeAvatar={handleAvatarChange}
                      role={detail?.position}
                    />
                  )
                }

                return (
                  <ParticipantTile
                    key={participant.id}
                    name={participant.name}
                    role={participant.role}
                  >
                    <div className="flex h-full items-center justify-center text-xs text-white/40">
                      等待入会
                    </div>
                  </ParticipantTile>
                )
              })}
            </ParticipantGrid>
          </div>

          <div
            className={`absolute inset-y-0 right-0 z-30 w-[380px] max-w-[88vw] shadow-2xl transition-transform duration-300 ease-out ${
              transcriptOpen ? 'translate-x-0' : 'translate-x-full'
            }`}
          >
            <TranscriptPanel
              questions={questions}
              pendingText={pendingText}
              asking={asking}
              submitting={submitting}
              sessionFinished={Boolean(sessionFinished)}
              canAsk={canAsk}
              onAsk={() => void handleAsk()}
              onSubmit={handleSubmitAnswer}
              onClose={() => setTranscriptOpen(false)}
            />
          </div>
        </div>
      </RoomLayout>

      {reportOpen && detail?.report && (
        <ReportDialog
          report={detail.report}
          position={detail.position}
          onClose={() => setReportOpen(false)}
        />
      )}
    </>
  )
}

export default InterviewRoomPage
