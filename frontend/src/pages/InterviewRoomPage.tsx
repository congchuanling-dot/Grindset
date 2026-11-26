import { AlertCircle, CircleStop, Loader2, MessageSquare, RotateCw, Sparkles } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  finishInterview,
  getInterview,
  streamNextQuestion,
  submitAnswer,
  type InterviewDetail,
} from '../api/interview'
import { AVATAR_MODELS, AVATAR_STORAGE_KEY } from '../components/avatar/avatarModels'
import type { VrmAvatarHandle } from '../components/avatar/VrmAvatarStage'
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
import { useBargeIn } from '../hooks/useBargeIn'
import { useInterviewerVoice } from '../hooks/useInterviewerVoice'
import { useLocalMedia } from '../hooks/useLocalMedia'
import { useRoomParticipants } from '../hooks/useRoomParticipants'
import { useVoiceInput } from '../hooks/useVoiceInput'

/** 面试间主流程:出题 -> 播报 -> 收音 -> 提交 -> 追问/下一题 */
type RoomPhase = 'idle' | 'asking' | 'speaking' | 'listening' | 'thinking'

const STYLE_LABELS: Record<string, string> = {
  pressure: '压力面',
  middle: '适中',
  relaxed: '轻松',
}

const PHASE_LABELS: Record<RoomPhase, string> = {
  idle: '',
  asking: '面试官正在出题',
  speaking: '面试官正在说话',
  listening: '正在听你说',
  thinking: '正在分析你的回答',
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

  // ---------- 自动面试链路 ----------

  const stageRef = useRef<VrmAvatarHandle>(null)
  const [phase, setPhase] = useState<RoomPhase>('idle')
  const [subtitle, setSubtitle] = useState('')
  const [heard, setHeard] = useState('')
  const autoStartedRef = useRef(false)
  // 三个步骤互相调用(出题->播报->收音->提交->出题),用 ref 打破循环依赖
  const flowRef = useRef({
    ask: async (): Promise<void> => {},
    listen: () => {},
    answer: async (_text: string): Promise<boolean> => false,
  })

  const voice = useInterviewerVoice({
    // 播报结束自动开始收音;播报失败(例如 TTS 未开通)也要继续,否则整场卡住
    onEnd: () => flowRef.current.listen(),
    onError: (message) => {
      setActionError(message)
      flowRef.current.listen()
    },
    onLevel: (level) => stageRef.current?.setMouth(level),
  })

  const asr = useVoiceInput({
    maxSeconds: 120,
    autoStop: {},
    onPartial: (text) => setHeard(text),
    onFinal: (text) => {
      setHeard('')
      const answer = text.trim()
      if (answer) {
        void flowRef.current.answer(answer)
      } else {
        // 什么也没识别到,继续听,不打断用户
        flowRef.current.listen()
      }
    },
    onError: (message) => {
      setActionError(message)
      setPhase('idle')
    },
  })

  /** 面试官播报时检测到用户抢话:立即停播报并开始收音 */
  useBargeIn(phase === 'speaking', () => {
    voice.stop()
    setPhase('listening')
    setHeard('')
    void asr.start()
  })

  /** 让面试官出下一题:SSE 文本边收边显示,拿到整题后播报并收音 */
  const askNext = useCallback(async () => {
    setAsking(true)
    setPhase('asking')
    setActionError(null)
    setPendingText('')
    setSubtitle('')
    setHeard('')
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
      setPhase('idle')
      return
    } finally {
      setPendingText('')
      setAsking(false)
    }
    await refresh()
    const question = text.trim()
    if (!question) {
      setPhase('idle')
      return
    }
    setSubtitle(question)
    setPhase('speaking')
    await voice.speak(question)
  }, [sessionId, refresh, handleExpired, voice])

  /** 开始收音。收起对话记录时也能看到实时字幕 */
  const startListening = useCallback(() => {
    setPhase('listening')
    setHeard('')
    void asr.start()
  }, [asr])

  /** 提交一轮回答:有追问就继续答,没有就出下一题或生成复盘 */
  const handleAnswer = useCallback(
    async (answer: string): Promise<boolean> => {
      if (submitting) return false
      setSubmitting(true)
      setPhase('thinking')
      setHeard('')
      setActionError(null)
      try {
        const result = await submitAnswer(sessionId, answer)
        await refresh()
        if (result.followUpQuestion) {
          setSubtitle(result.followUpQuestion)
          setPhase('speaking')
          await voice.speak(result.followUpQuestion)
          return true
        }
        if (result.nextAction === 'REPORT_READY') {
          setPhase('idle')
          await finishAndReport()
          return true
        }
        await askNext()
        return true
      } catch (err) {
        setActionError(err instanceof Error ? err.message : '提交回答失败,请重试')
        setPhase('idle')
        return false
      } finally {
        setSubmitting(false)
      }
    },
    // finishAndReport 定义在下面,用 ref 调用避免循环依赖
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [submitting, sessionId, refresh, askNext, voice],
  )

  useEffect(() => {
    flowRef.current = {
      ask: askNext,
      listen: startListening,
      answer: handleAnswer,
    }
  })

  /** 手动结束(顶栏按钮) */
  const handleFinish = useCallback(async () => {
    if (finishing) return
    voice.stop()
    asr.cancel()
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
  }, [finishing, sessionId, refresh, navigate, voice, asr])

  /** 答完最后一题后自动出报告 */
  const finishAndReport = useCallback(async () => {
    if (finishing) return
    setFinishing(true)
    try {
      const report = await finishInterview(sessionId)
      await refresh()
      if (report) setReportOpen(true)
    } catch (err) {
      setActionError(err instanceof Error ? err.message : '生成复盘失败,请稍后重试')
    } finally {
      setFinishing(false)
    }
  }, [finishing, sessionId, refresh])

  // 新会话进面试间就自动开始第一题
  useEffect(() => {
    if (!detail || autoStartedRef.current) return
    if (detail.status !== SessionStatus.ONGOING) return
    autoStartedRef.current = true
    if (detail.questions.length === 0) {
      void flowRef.current.ask()
    }
  }, [detail])

  const handleLeave = useCallback(() => {
    voice.stop()
    asr.cancel()
    stop()
    navigate('/records')
  }, [voice, asr, stop, navigate])

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
          <div className="relative min-w-0 flex-1">
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
                      stageRef={stageRef}
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

            {/* 字幕条:对话记录默认收起,靠它看面试官问了什么、自己说了什么 */}
            {(phase !== 'idle' || subtitle) && (
              <div className="pointer-events-none absolute inset-x-4 bottom-4 z-10 flex flex-col items-center gap-2">
                <span className="rounded-full bg-black/55 px-3 py-1 text-[11px] text-white/70 backdrop-blur">
                  {PHASE_LABELS[phase] || '等待开始'}
                </span>
                {(heard || subtitle || pendingText) && (
                  <p className="max-w-3xl rounded-xl bg-black/60 px-4 py-2 text-center text-sm leading-6 text-white/90 shadow-lg backdrop-blur">
                    {heard || pendingText || subtitle}
                  </p>
                )}
              </div>
            )}

            {phase === 'idle' && canAsk && !sessionFinished && (
              <div className="absolute inset-x-0 bottom-4 z-10 flex justify-center">
                <button
                  type="button"
                  onClick={() => void flowRef.current.ask()}
                  className="pointer-events-auto rounded-full bg-teal-600 px-4 py-1.5 text-xs font-medium text-white shadow-lg transition hover:bg-teal-700"
                >
                  继续面试
                </button>
              </div>
            )}
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
              onAsk={() => void flowRef.current.ask()}
              onSubmit={handleAnswer}
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
