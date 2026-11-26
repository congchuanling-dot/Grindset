import { useEffect, useRef, useState } from 'react'
import { getSession } from '../auth/authStore'

export type VoiceState = 'idle' | 'connecting' | 'recording' | 'finishing'

/** 自动断句:命中结束语或长时间没有新文本时自动结束这一段 */
export interface VoiceAutoStop {
  /** 说这些词就算答完了,例如"就这些""说完了" */
  endPhrases?: string[]
  /** 命中结束语后再等这么久没有新文本才结束(毫秒),防止把后面的补充截掉 */
  endDelayMs?: number
  /** 多久没有新的识别文本就兜底结束(毫秒),从识别到第一句话开始计时 */
  silenceMs?: number
  /** 最短录音时长(毫秒),低于这个时长不触发自动结束 */
  minMs?: number
}

export interface VoiceInputOptions {
  /** 单次录音时长上限(秒),不同场景传不同值;服务端会用硬上限截断 */
  maxSeconds?: number
  /** 实时中间结果(整段覆盖式) */
  onPartial?: (text: string) => void
  /** 最终识别结果 */
  onFinal?: (text: string) => void
  onError?: (message: string) => void
  /** 传了才启用自动断句 */
  autoStop?: VoiceAutoStop
}

export interface VoiceInputController {
  state: VoiceState
  seconds: number
  start: () => Promise<void>
  stop: () => Promise<void>
  /** 放弃本次录音,不回调结果 */
  cancel: () => void
}

const SAMPLE_RATE = 16000
/** 火山建议 100~200ms 一包:16k * 16bit * 200ms */
const CHUNK_BYTES = 6400
/** 默认结束语:用户说完这类话就认为这一轮回答结束 */
export const DEFAULT_END_PHRASES = [
  '就这些',
  '大概就这些',
  '就这么多',
  '说完了',
  '答完了',
  '回答完毕',
  '完毕',
  '以上',
  '差不多',
  '没了',
  '就这样',
]
const END_DELAY_MS = 1200
const SILENCE_MS = 5000
const MIN_RECORD_MS = 2000

/**
 * 语音输入公共 Hook:麦克风 -> 16k PCM -> 后端 /api/asr/stream -> 实时/最终文本。
 *
 * <pre>
 * const voice = useVoiceInput({ maxSeconds: 60, onPartial, onFinal, onError })
 * voice.state === 'recording' ? voice.stop() : voice.start()
 * </pre>
 */
export function useVoiceInput(options: VoiceInputOptions): VoiceInputController {
  const [state, setState] = useState<VoiceState>('idle')
  const [seconds, setSeconds] = useState(0)

  const optionsRef = useRef(options)
  optionsRef.current = options
  const stateRef = useRef<VoiceState>('idle')
  const wsRef = useRef<WebSocket | null>(null)
  const recorderRef = useRef<PcmRecorder | null>(null)
  const timerRef = useRef<number | null>(null)
  const fallbackRef = useRef<number | null>(null)
  const lastPartialRef = useRef('')
  const finishedRef = useRef(false)
  const aliveRef = useRef(true)
  const limitRef = useRef(60)
  const stopRef = useRef<() => Promise<void>>(async () => {})
  /** 最后一次识别到新文本的时间;0 表示这一轮还没开口 */
  const lastTextAtRef = useRef(0)
  const startedAtRef = useRef(0)
  /** 当前文本是否以结束语结尾 */
  const endCandidateRef = useRef(false)

  function phase(next: VoiceState) {
    stateRef.current = next
    setState(next)
  }

  function clearTimers() {
    if (timerRef.current !== null) {
      window.clearInterval(timerRef.current)
      timerRef.current = null
    }
    if (fallbackRef.current !== null) {
      window.clearTimeout(fallbackRef.current)
      fallbackRef.current = null
    }
  }

  function cleanup() {
    clearTimers()
    recorderRef.current?.cancel()
    recorderRef.current = null
    const ws = wsRef.current
    wsRef.current = null
    if (ws) {
      ws.onopen = null
      ws.onmessage = null
      ws.onerror = null
      ws.onclose = null
      if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
        try {
          ws.close()
        } catch {
          // 忽略关闭异常
        }
      }
    }
  }

  function fail(message: string) {
    if (finishedRef.current) return
    finishedRef.current = true
    cleanup()
    phase('idle')
    setSeconds(0)
    optionsRef.current.onError?.(message)
  }

  function finishWith(text: string) {
    if (finishedRef.current) return
    finishedRef.current = true
    cleanup()
    phase('idle')
    setSeconds(0)
    optionsRef.current.onFinal?.(text)
  }

  async function startRecording() {
    const ws = wsRef.current
    if (!ws || ws.readyState !== WebSocket.OPEN) return
    const recorder = new PcmRecorder((chunk) => {
      if (ws.readyState === WebSocket.OPEN) ws.send(chunk)
    })
    try {
      await recorder.start()
    } catch (error) {
      const denied = error instanceof DOMException
        && (error.name === 'NotAllowedError' || error.name === 'SecurityError')
      fail(denied
        ? '请允许浏览器使用麦克风（语音输入需要 HTTPS 或 localhost）'
        : error instanceof Error ? error.message : '无法访问麦克风')
      return
    }
    if (!aliveRef.current || stateRef.current !== 'connecting') {
      recorder.cancel()
      return
    }
    recorderRef.current = recorder
    phase('recording')

    const startedAt = Date.now()
    startedAtRef.current = startedAt
    lastTextAtRef.current = 0
    endCandidateRef.current = false
    timerRef.current = window.setInterval(() => {
      const elapsed = Math.floor((Date.now() - startedAt) / 1000)
      setSeconds(elapsed)
      if (elapsed >= limitRef.current) {
        void stopRef.current()
        return
      }
      const autoStop = optionsRef.current.autoStop
      if (!autoStop) return
      const now = Date.now()
      if (now - startedAt < (autoStop.minMs ?? MIN_RECORD_MS)) return
      if (lastTextAtRef.current === 0) return
      const idle = now - lastTextAtRef.current
      if (endCandidateRef.current && idle >= (autoStop.endDelayMs ?? END_DELAY_MS)) {
        void stopRef.current()
      } else if (idle >= (autoStop.silenceMs ?? SILENCE_MS)) {
        void stopRef.current()
      }
    }, 250)
  }

  function handleMessage(raw: unknown) {
    if (typeof raw !== 'string') return
    let message: { type?: string; text?: string; message?: string }
    try {
      message = JSON.parse(raw)
    } catch {
      return
    }
    if (message.type === 'ready') {
      void startRecording()
    } else if (message.type === 'partial') {
      lastPartialRef.current = message.text ?? ''
      optionsRef.current.onPartial?.(lastPartialRef.current)
      // 记下最后一次文本变化,自动断句用它判断"还在说"还是"说完了"
      lastTextAtRef.current = Date.now()
      const autoStop = optionsRef.current.autoStop
      if (autoStop) {
        const tail = lastPartialRef.current.slice(-12)
        const phrases = autoStop.endPhrases ?? DEFAULT_END_PHRASES
        endCandidateRef.current = phrases.some((phrase) => tail.includes(phrase))
      }
    } else if (message.type === 'final') {
      finishWith(message.text ?? lastPartialRef.current)
    } else if (message.type === 'error') {
      fail(message.message || '语音识别失败，请重试')
    }
  }

  async function start() {
    if (stateRef.current !== 'idle') return
    const token = getSession()?.accessToken
    if (!token) {
      optionsRef.current.onError?.('登录已过期，请刷新页面后重试')
      return
    }
    finishedRef.current = false
    lastPartialRef.current = ''
    lastTextAtRef.current = 0
    endCandidateRef.current = false
    limitRef.current = Math.max(5, optionsRef.current.maxSeconds ?? 60)
    setSeconds(0)
    phase('connecting')

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${protocol}://${window.location.host}/api/asr/stream`
      + `?token=${encodeURIComponent(token)}&maxSeconds=${limitRef.current}`
    const ws = new WebSocket(url)
    ws.binaryType = 'arraybuffer'
    wsRef.current = ws
    ws.onmessage = (event) => handleMessage(event.data)
    ws.onerror = () => fail('语音识别连接失败，请稍后重试')
    ws.onclose = () => {
      if (finishedRef.current || !aliveRef.current) return
      if (lastPartialRef.current) finishWith(lastPartialRef.current)
      else fail('语音识别连接已断开，请重试')
    }
  }

  async function stop() {
    if (stateRef.current !== 'recording') return
    phase('finishing')
    if (timerRef.current !== null) {
      window.clearInterval(timerRef.current)
      timerRef.current = null
    }
    const recorder = recorderRef.current
    recorderRef.current = null
    try {
      await recorder?.stop()
    } catch {
      // 采集收尾失败不影响识别
    }
    const ws = wsRef.current
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'finish' }))
    }
    fallbackRef.current = window.setTimeout(() => {
      if (finishedRef.current) return
      if (lastPartialRef.current) finishWith(lastPartialRef.current)
      else fail('没有识别到声音，请再说一遍')
    }, 10000)
  }
  stopRef.current = stop

  function cancel() {
    finishedRef.current = true
    cleanup()
    phase('idle')
    setSeconds(0)
  }

  useEffect(() => {
    aliveRef.current = true
    return () => {
      aliveRef.current = false
      finishedRef.current = true
      cleanup()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return { state, seconds, start, stop, cancel }
}

/** 麦克风 PCM 采集:16k/16bit/单声道,按 200ms 切片 */
class PcmRecorder {
  private readonly onChunk: (chunk: ArrayBuffer) => void
  private stream: MediaStream | null = null
  private context: AudioContext | null = null
  private source: MediaStreamAudioSourceNode | null = null
  private node: ScriptProcessorNode | null = null
  private gain: GainNode | null = null
  private carry = new Uint8Array(0)
  private active = false

  constructor(onChunk: (chunk: ArrayBuffer) => void) {
    this.onChunk = onChunk
  }

  async start(): Promise<void> {
    if (!navigator.mediaDevices?.getUserMedia) throw new Error('当前浏览器不支持录音')
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true },
    })
    let context: AudioContext
    try {
      context = new AudioContext({ sampleRate: SAMPLE_RATE })
    } catch {
      context = new AudioContext()
    }
    this.stream = stream
    this.context = context
    try {
      if (context.state === 'suspended') await context.resume()
    } catch {
      // 部分浏览器需要用户手势后才能 resume
    }
    this.source = context.createMediaStreamSource(stream)
    this.node = context.createScriptProcessor(4096, 1, 1)
    this.gain = context.createGain()
    this.gain.gain.value = 0 // 静音输出,避免回放麦克风声音
    this.active = true
    this.node.onaudioprocess = (event) => {
      if (!this.active) return
      this.push(toPcm16(event.inputBuffer.getChannelData(0), context.sampleRate))
    }
    this.source.connect(this.node)
    this.node.connect(this.gain)
    this.gain.connect(context.destination)
  }

  async stop(): Promise<void> {
    if (!this.active) return
    this.active = false
    if (this.carry.length > 0) {
      this.onChunk(this.carry.buffer.slice(0, this.carry.byteLength) as ArrayBuffer)
      this.carry = new Uint8Array(0)
    }
    await this.teardown()
  }

  cancel(): void {
    this.active = false
    this.carry = new Uint8Array(0)
    void this.teardown()
  }

  private push(pcm: Uint8Array): void {
    const merged = new Uint8Array(this.carry.length + pcm.length)
    merged.set(this.carry)
    merged.set(pcm, this.carry.length)
    let offset = 0
    while (merged.length - offset >= CHUNK_BYTES) {
      const chunk = merged.slice(offset, offset + CHUNK_BYTES)
      this.onChunk(chunk.buffer as ArrayBuffer)
      offset += CHUNK_BYTES
    }
    this.carry = merged.slice(offset)
  }

  private async teardown(): Promise<void> {
    try {
      this.node?.disconnect()
      this.source?.disconnect()
      this.gain?.disconnect()
    } catch {
      // 节点可能已经断开
    }
    this.node = null
    this.source = null
    this.gain = null
    this.stream?.getTracks().forEach((track) => track.stop())
    this.stream = null
    const context = this.context
    this.context = null
    if (context) {
      try {
        await context.close()
      } catch {
        // 忽略关闭异常
      }
    }
  }
}

function toPcm16(input: Float32Array, sourceRate: number): Uint8Array {
  const samples = sourceRate === SAMPLE_RATE ? input : resample(input, sourceRate)
  const out = new Uint8Array(samples.length * 2)
  const view = new DataView(out.buffer)
  for (let i = 0; i < samples.length; i += 1) {
    const value = Math.max(-1, Math.min(1, samples[i]))
    view.setInt16(i * 2, value < 0 ? value * 0x8000 : value * 0x7fff, true)
  }
  return out
}

/** 简单线性插值降采样,16k 足够语音识别使用 */
function resample(input: Float32Array, sourceRate: number): Float32Array {
  const ratio = sourceRate / SAMPLE_RATE
  const length = Math.max(1, Math.floor(input.length / ratio))
  const output = new Float32Array(length)
  for (let i = 0; i < length; i += 1) {
    const position = i * ratio
    const index = Math.floor(position)
    const next = Math.min(index + 1, input.length - 1)
    const fraction = position - index
    output[i] = input[index] * (1 - fraction) + input[next] * fraction
  }
  return output
}
