import { useEffect, useRef, useState } from 'react'
import { useBargeIn } from './useBargeIn'
import { useVoiceInput, type VoiceState } from './useVoiceInput'

interface CodingConversationOptions {
  active: boolean
  /** 面试官正在 TTS 播报 */
  speaking: boolean
  /** 面试官正在生成回复 */
  thinking: boolean
  onAsk: (text: string) => Promise<boolean>
  onStopSpeaking: () => void
  onError?: (message: string) => void
}

export interface CodingConversationController {
  state: VoiceState
  seconds: number
  heard: string
}

/**
 * 编程阶段的全双工语音交流:
 * - 候选人说话时持续收音,停顿后自动切段;
 * - 只有像"提问/对面试官说话"的段落才发送,思考自言自语不会打断;
 * - 面试官播报时可随时开口打断,自动停播并恢复收音。
 */
export function useCodingConversation({
  active,
  speaking,
  thinking,
  onAsk,
  onStopSpeaking,
  onError,
}: CodingConversationOptions): CodingConversationController {
  const [heard, setHeard] = useState('')
  const activeRef = useRef(active)
  const busyRef = useRef(false)
  const onAskRef = useRef(onAsk)
  const asrRef = useRef<ReturnType<typeof useVoiceInput> | null>(null)
  activeRef.current = active
  busyRef.current = speaking || thinking
  onAskRef.current = onAsk

  const asr = useVoiceInput({
    maxSeconds: 60,
    autoStop: { silenceMs: 1500, endDelayMs: 800, minMs: 800 },
    onPartial: (text) => setHeard(text),
    onFinal: (text) => {
      setHeard('')
      const message = text.trim()
      if (!message || !activeRef.current) return
      if (looksLikeQuestion(message)) {
        void onAskRef.current(message).finally(() => {
          // 如果请求失败,父组件没有进入 busy,这里兜底恢复收音
          window.setTimeout(() => {
            if (activeRef.current && !busyRef.current) void asrRef.current?.start()
          }, 400)
        })
        return
      }
      // 思考自言自语:不发送,直接继续听
      window.setTimeout(() => {
        if (activeRef.current && !busyRef.current) void asrRef.current?.start()
      }, 400)
    },
    onError: (message) => onError?.(message),
  })
  asrRef.current = asr

  const busy = speaking || thinking

  useEffect(() => {
    if (!active) {
      asr.cancel()
      return
    }
    if (busy) {
      asr.cancel()
      return
    }
    const timer = window.setTimeout(() => {
      void asr.start()
    }, 250)
    return () => window.clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active, busy])

  // 面试官播报时检测打断:停播后 speaking 变 false,上面的 effect 会恢复收音
  useBargeIn(active && speaking, onStopSpeaking)

  return { state: asr.state, seconds: asr.seconds, heard }
}

/** 只有明确对面试官说、或带疑问词的句子才发送,避免思考自言自语刷屏 */
function looksLikeQuestion(text: string): boolean {
  const value = text.trim()
  if (!value) return false
  if (value.includes('面试官') || value.includes('老师')) return true
  if (/[?？]/.test(value)) return true
  return /(怎么|如何|为什么|可以吗|行不行|对不对|是不是|有没有|能不能|帮我|看一下|这里.*(吗|呢))/.test(value)
}
