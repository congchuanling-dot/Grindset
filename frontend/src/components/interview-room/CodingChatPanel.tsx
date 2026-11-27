import { CornerDownLeft, Loader2, Mic, Volume2 } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import type { InterviewMessage } from '../../api/interview'
import { useCodingConversation } from '../../hooks/useCodingConversation'

interface CodingChatPanelProps {
  messages: InterviewMessage[]
  pendingText: string
  chatting: boolean
  micOn: boolean
  interviewerSpeaking: boolean
  onAsk: (message: string) => Promise<boolean>
  onStopSpeaking: () => void
  onError?: (message: string) => void
}

/**
 * 编程阶段的面试官交流面板:麦克风常开,支持随时打断面试官播报。
 * 只有"明确提问/对面试官说"的语音才会发送,思考自言自语不会刷屏。
 */
function CodingChatPanel({
  messages,
  pendingText,
  chatting,
  micOn,
  interviewerSpeaking,
  onAsk,
  onStopSpeaking,
  onError,
}: CodingChatPanelProps) {
  const [text, setText] = useState('')
  const scrollRef = useRef<HTMLDivElement>(null)
  const conversation = useCodingConversation({
    active: micOn,
    speaking: interviewerSpeaking,
    thinking: chatting,
    onAsk,
    onStopSpeaking,
    onError,
  })

  useEffect(() => {
    const el = scrollRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [messages, pendingText, conversation.heard])

  async function send(message: string) {
    const value = message.trim()
    if (!value || chatting) return
    setText('')
    await onAsk(value)
  }

  const status = !micOn
    ? '麦克风已关闭'
    : interviewerSpeaking
      ? '面试官正在说，可直接打断'
      : chatting
        ? '面试官正在思考…'
        : conversation.state === 'recording'
          ? '正在听你说…'
          : conversation.state === 'connecting'
            ? '正在连接语音…'
            : '等待你说话'

  return (
    <aside className="flex h-full min-h-0 w-full flex-col border-l border-white/10 bg-[#181b20]">
      <div className="flex shrink-0 items-center gap-2 border-b border-white/10 px-3 py-2.5">
        <Volume2 size={14} className="text-brand-400" />
        <span className="text-xs font-medium text-white/80">面试官交流</span>
        <span className="ml-auto flex items-center gap-1.5 text-[10px] text-white/40">
          <span className={`h-1.5 w-1.5 rounded-full ${
            conversation.state === 'recording' ? 'animate-pulse bg-emerald-400' : 'bg-white/20'
          }`} />
          {status}
        </span>
      </div>

      <div ref={scrollRef} className="min-h-0 flex-1 space-y-3 overflow-y-auto px-3 py-3">
        {messages.length === 0 && !pendingText && (
          <p className="mt-8 text-center text-[11px] leading-6 text-white/30">
            写代码时可以直接说话问我
            <br />
            说「面试官…」或提问才会发送
          </p>
        )}
        {messages.map((message) => {
          const isUser = message.role === 'USER'
          return (
            <div key={message.id} className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
              <p className={`max-w-[88%] whitespace-pre-wrap rounded-xl px-3 py-2 text-xs leading-5 ${
                isUser
                  ? 'bg-brand-600/20 text-brand-50'
                  : 'bg-white/8 text-white/85 ring-1 ring-white/5'
              }`}>
                {message.content}
              </p>
            </div>
          )
        })}
        {(pendingText || chatting) && (
          <div className="flex justify-start">
            <p className="max-w-[88%] whitespace-pre-wrap rounded-xl bg-white/8 px-3 py-2 text-xs leading-5 text-white/85 ring-1 ring-white/5">
              {pendingText || '思考中…'}
              {chatting && <span className="ml-0.5 animate-pulse text-brand-400">▍</span>}
            </p>
          </div>
        )}
        {conversation.heard && (
          <p className="rounded-lg bg-emerald-500/10 px-2.5 py-1.5 text-[11px] leading-5 text-emerald-200/80">
            {conversation.heard}
          </p>
        )}
      </div>

      <div className="shrink-0 border-t border-white/10 p-2.5">
        <div className="rounded-xl bg-white/5 ring-1 ring-white/10">
          <textarea
            value={text}
            onChange={(event) => setText(event.target.value)}
            onKeyDown={(event) => {
              if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') {
                void send(text)
              }
            }}
            rows={2}
            placeholder="也可以打字问面试官…"
            className="w-full resize-none bg-transparent px-3 py-2 text-xs leading-5 text-white/85 outline-none placeholder:text-white/25"
          />
          <div className="flex items-center gap-2 px-2 pb-2">
            <span className="flex items-center gap-1 text-[10px] text-white/25">
              <Mic size={11} />
              {micOn ? '麦克风常开' : '麦克风已关闭'}
            </span>
            <span className="ml-auto text-[10px] text-white/20">⌘/Ctrl + Enter 发送</span>
            <button
              type="button"
              onClick={() => void send(text)}
              disabled={!text.trim() || chatting}
              className="flex h-7 items-center gap-1 rounded-lg bg-brand-600 px-2.5 text-[11px] font-medium text-white transition hover:bg-brand-700 disabled:opacity-40"
            >
              {chatting ? <Loader2 size={12} className="animate-spin" /> : <CornerDownLeft size={12} />}
              发送
            </button>
          </div>
        </div>
      </div>
    </aside>
  )
}

export default CodingChatPanel
