import { CornerDownLeft, Loader2, Sparkles, Volume2, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import type { InterviewQuestion } from '../../api/interview'
import { QuestionStatus } from '../../constants/status'
import { useVoiceInput } from '../../hooks/useVoiceInput'
import VoiceInputButton from '../VoiceInputButton'

interface TranscriptPanelProps {
  questions: InterviewQuestion[]
  /** 面试官正在流式输出的题目文本 */
  pendingText: string
  asking: boolean
  submitting: boolean
  sessionFinished: boolean
  canAsk: boolean
  onAsk: () => void
  onSubmit: (answer: string) => Promise<boolean>
  onClose: () => void
}

/**
 * 面试间右侧对话记录:面试官提问 + 我的回答 + 点评。
 * 语音输入复用 /api/asr/stream,识别结果直接落到输入框,可编辑后再提交。
 */
function TranscriptPanel({
  questions,
  pendingText,
  asking,
  submitting,
  sessionFinished,
  canAsk,
  onAsk,
  onSubmit,
  onClose,
}: TranscriptPanelProps) {
  const [answer, setAnswer] = useState('')
  const [hint, setHint] = useState<string | null>(null)
  const scrollRef = useRef<HTMLDivElement>(null)

  const voice = useVoiceInput({
    maxSeconds: 120,
    onPartial: (text) => setAnswer(text),
    onFinal: (text) => setAnswer(text),
    onError: (message) => setHint(message),
  })

  useEffect(() => {
    const el = scrollRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [questions, pendingText])

  const locked = asking || submitting || sessionFinished

  async function handleSubmit() {
    const content = answer.trim()
    if (!content || locked) return
    setHint(null)
    const ok = await onSubmit(content)
    if (ok) setAnswer('')
  }

  return (
    // 底部 pb-20 留白:悬停出现的控制条是浮层,避免压住输入区
    <aside className="flex h-full w-full flex-col border-l border-white/10 bg-[#181b20] pb-20">
      <div className="flex items-center gap-2 border-b border-white/10 px-4 py-3">
        <Volume2 size={14} className="text-teal-400" />
        <span className="text-xs font-medium text-white/80">对话记录</span>
        <span className="ml-auto text-[11px] text-white/35">边说话边记录，复盘用</span>
        <button
          type="button"
          onClick={onClose}
          title="收起对话记录"
          className="rounded-md p-1 text-white/40 transition hover:bg-white/10 hover:text-white/80"
        >
          <X size={14} />
        </button>
      </div>

      <div ref={scrollRef} className="min-h-0 flex-1 space-y-4 overflow-y-auto px-4 py-4">
        {questions.length === 0 && !pendingText && (
          <div className="mt-10 text-center text-xs leading-6 text-white/35">
            面试还没开始
            <br />
            点下方按钮让面试官提第一个问题
          </div>
        )}

        {questions.map((question) => (
          <div key={question.id} className="space-y-2">
            <p className="text-[11px] text-white/30">第 {question.seq} 题</p>
            {question.messages.map((message) => {
              if (message.type === 'FEEDBACK') {
                return (
                  <p
                    key={message.id}
                    className="rounded-lg border-l-2 border-amber-400/50 bg-amber-400/5 px-2.5 py-1.5 text-[11px] leading-5 text-amber-100/70"
                  >
                    {message.content}
                  </p>
                )
              }
              const isUser = message.role === 'USER'
              return (
                <div key={message.id} className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
                  <p
                    className={`max-w-[85%] whitespace-pre-wrap rounded-xl px-3 py-2 text-xs leading-5 ${
                      isUser
                        ? 'bg-teal-600/20 text-teal-50'
                        : 'bg-white/8 text-white/85 ring-1 ring-white/5'
                    }`}
                  >
                    {message.content}
                  </p>
                </div>
              )
            })}

            {question.status === QuestionStatus.CLOSED && question.score != null && (
              <p className="text-[11px] text-white/30">
                本题得分 {question.score}/5
                {question.knowledgePoints.length > 0 &&
                  ` · ${question.knowledgePoints.join('、')}`}
              </p>
            )}
          </div>
        ))}

        {(asking || pendingText) && (
          <div className="space-y-2">
            <p className="text-[11px] text-white/30">面试官</p>
            <p className="whitespace-pre-wrap rounded-xl bg-white/8 px-3 py-2 text-xs leading-5 text-white/85 ring-1 ring-white/5">
              {pendingText || '思考中...'}
              {asking && <span className="ml-0.5 animate-pulse text-teal-400">▍</span>}
            </p>
          </div>
        )}
      </div>

      <div className="border-t border-white/10 p-3">
        {hint && <p className="mb-2 text-[11px] text-rose-300">{hint}</p>}

        {sessionFinished ? (
          <p className="py-2 text-center text-[11px] text-white/35">本场面试已结束，可查看复盘</p>
        ) : (
          <>
            {canAsk && (
              <button
                type="button"
                onClick={onAsk}
                disabled={asking || submitting}
                className="mb-2 flex w-full items-center justify-center gap-1.5 rounded-lg bg-teal-600 px-3 py-2 text-xs font-medium text-white transition hover:bg-teal-700 disabled:opacity-50"
              >
                {asking ? (
                  <Loader2 size={13} className="animate-spin" />
                ) : (
                  <Sparkles size={13} />
                )}
                {questions.length === 0 ? '开始面试，让面试官提问' : '让面试官继续提问'}
              </button>
            )}

            <div className="rounded-xl bg-white/5 ring-1 ring-white/10">
              <textarea
                value={answer}
                onChange={(event) => setAnswer(event.target.value)}
                onKeyDown={(event) => {
                  if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') {
                    void handleSubmit()
                  }
                }}
                rows={3}
                disabled={locked}
                placeholder="输入你的回答，或点麦克风直接说"
                className="w-full resize-none bg-transparent px-3 py-2 text-xs leading-5 text-white/85 outline-none placeholder:text-white/25 disabled:opacity-50"
              />
              <div className="flex items-center gap-1 px-2 pb-2">
                <VoiceInputButton
                  state={voice.state}
                  seconds={voice.seconds}
                  disabled={locked}
                  onClick={() => {
                    if (voice.state === 'recording') void voice.stop()
                    else void voice.start()
                  }}
                />
                <span className="ml-auto text-[10px] text-white/25">⌘/Ctrl + Enter 发送</span>
                <button
                  type="button"
                  onClick={() => void handleSubmit()}
                  disabled={locked || !answer.trim()}
                  className="flex h-8 items-center gap-1 rounded-lg bg-teal-600 px-2.5 text-[11px] font-medium text-white transition hover:bg-teal-700 disabled:opacity-40"
                >
                  {submitting ? (
                    <Loader2 size={13} className="animate-spin" />
                  ) : (
                    <CornerDownLeft size={13} />
                  )}
                  发送
                </button>
              </div>
            </div>
          </>
        )}
      </div>
    </aside>
  )
}

export default TranscriptPanel
