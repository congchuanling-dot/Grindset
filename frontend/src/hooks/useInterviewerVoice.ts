import { useCallback, useEffect, useRef, useState } from 'react'
import { authFetch, readApiError } from '../api/http'
import { readField, readSse, isAbortError } from '../api/sse'
import { errMessage } from '../utils/error'

export interface InterviewerVoiceOptions {
  /** 播报自然结束(被打断不算) */
  onEnd?: () => void
  onError?: (message: string) => void
  /** 口型幅度 0~1,逐帧回调,直接用 ref 写给 VRM */
  onLevel?: (level: number) => void
}

export interface InterviewerVoiceController {
  speaking: boolean
  /** 播报一段文本;播放完回调 onEnd */
  speak: (text: string) => Promise<void>
  /** 打断播报:停止播放并丢弃剩余音频 */
  stop: () => void
}

const SAMPLE_RATE = 24000

/**
 * 面试官语音播报:后端 TTS(SSE) -> base64 PCM -> WebAudio 排队播放。
 *
 * <p>按块解码、边收边播,所以首字延迟只取决于前端拿到第一块音频的时间;
 * 播放中的音频能量会通过 onLevel 回调驱动 VRM 口型。</p>
 */
export function useInterviewerVoice(options: InterviewerVoiceOptions = {}): InterviewerVoiceController {
  const [speaking, setSpeaking] = useState(false)

  const optionsRef = useRef(options)
  optionsRef.current = options

  const ctxRef = useRef<AudioContext | null>(null)
  const analyserRef = useRef<AnalyserNode | null>(null)
  const waveRef = useRef<Float32Array<ArrayBuffer> | null>(null)
  const nextTimeRef = useRef(0)
  const sourcesRef = useRef<Set<AudioBufferSourceNode>>(new Set())
  const abortRef = useRef<AbortController | null>(null)
  const levelRef = useRef(0)
  const activeRef = useRef(false)
  const rafRef = useRef<number | null>(null)
  const finishTimerRef = useRef<number | null>(null)
  const endedRef = useRef(false)

  const stopLevelLoop = useCallback(() => {
    if (rafRef.current !== null) {
      cancelAnimationFrame(rafRef.current)
      rafRef.current = null
    }
  }, [])

  const startLevelLoop = useCallback(() => {
    stopLevelLoop()
    const loop = () => {
      // 直接分析"正在播放"的音频:接口返回的音频会一次性到齐,
      // 只有按块峰值驱动的话,嘴只在开头动一下,后面整段都是闭着的
      const analyser = analyserRef.current
      if (analyser) {
        let wave = waveRef.current
        if (!wave || wave.length !== analyser.fftSize) {
          wave = new Float32Array(analyser.fftSize)
          waveRef.current = wave
        }
        analyser.getFloatTimeDomainData(wave)
        let sum = 0
        for (let i = 0; i < wave.length; i++) {
          sum += wave[i] * wave[i]
        }
        const rms = Math.sqrt(sum / wave.length)
        levelRef.current = Math.min(1, rms * 3.5)
      } else {
        levelRef.current *= 0.82
      }
      optionsRef.current.onLevel?.(levelRef.current)
      if (activeRef.current) {
        rafRef.current = requestAnimationFrame(loop)
      }
    }
    rafRef.current = requestAnimationFrame(loop)
  }, [stopLevelLoop])

  const clearPlayback = useCallback(() => {
    for (const source of sourcesRef.current) {
      try {
        source.stop()
      } catch {
        // 已经播完的节点再 stop 会抛错,忽略
      }
    }
    sourcesRef.current.clear()
    nextTimeRef.current = 0
    levelRef.current = 0
    optionsRef.current.onLevel?.(0)
  }, [])

  const finish = useCallback(() => {
    if (endedRef.current) return
    endedRef.current = true
    activeRef.current = false
    stopLevelLoop()
    if (finishTimerRef.current !== null) {
      window.clearTimeout(finishTimerRef.current)
      finishTimerRef.current = null
    }
    clearPlayback()
    setSpeaking(false)
    optionsRef.current.onEnd?.()
  }, [clearPlayback, stopLevelLoop])

  const stop = useCallback(() => {
    abortRef.current?.abort()
    abortRef.current = null
    endedRef.current = true
    activeRef.current = false
    stopLevelLoop()
    if (finishTimerRef.current !== null) {
      window.clearTimeout(finishTimerRef.current)
      finishTimerRef.current = null
    }
    clearPlayback()
    setSpeaking(false)
  }, [clearPlayback, stopLevelLoop])

  const waitDone = useCallback(() => {
    const ctx = ctxRef.current
    if (!ctx) {
      finish()
      return
    }
    if (nextTimeRef.current <= ctx.currentTime + 0.05) {
      finish()
      return
    }
    finishTimerRef.current = window.setTimeout(waitDone, 120)
  }, [finish])

  const speak = useCallback(
    async (text: string) => {
      const content = text.trim()
      if (!content) return

      // 新一段播报先清掉上一段,避免叠音
      stop()
      endedRef.current = false
      activeRef.current = true
      setSpeaking(true)

      let ctx = ctxRef.current
      if (!ctx) {
        ctx = new AudioContext()
        ctxRef.current = ctx
        const analyser = ctx.createAnalyser()
        analyser.fftSize = 1024
        analyser.connect(ctx.destination)
        analyserRef.current = analyser
      }
      if (ctx.state === 'suspended') {
        await ctx.resume().catch(() => undefined)
      }
      startLevelLoop()

      const controller = new AbortController()
      abortRef.current = controller

      try {
        const res = await authFetch('/api/tts', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
          body: JSON.stringify({ text: content }),
          signal: controller.signal,
        })
        if (!res.ok) {
          throw new Error(await readApiError(res, '语音合成失败'))
        }

        await readSse(res, ({ event, data }) => {
          if (event === 'error') {
            throw new Error(readField(data, 'message') || '语音合成失败')
          }
          if (event !== 'delta') return
          const audio = readField(data, 'audio')
          if (audio) {
            enqueue(audio)
          }
        })
        if (activeRef.current) {
          waitDone()
        }
      } catch (error) {
        if (isAbortError(error)) return
        activeRef.current = false
        stopLevelLoop()
        clearPlayback()
        setSpeaking(false)
        optionsRef.current.onError?.(
          errMessage(error, '语音播放失败'),
        )
      }
    },
    [clearPlayback, startLevelLoop, stop, stopLevelLoop, waitDone],
  )

  /** base64 PCM -> AudioBuffer 排队播放 */
  function enqueue(base64: string) {
    const ctx = ctxRef.current
    if (!ctx) return
    const binary = atob(base64)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i)
    }
    const pcm = new Int16Array(bytes.buffer, 0, Math.floor(bytes.length / 2))
    if (pcm.length === 0) return

    const samples = new Float32Array(pcm.length)
    for (let i = 0; i < pcm.length; i++) {
      samples[i] = pcm[i] / 32768
    }

    const buffer = ctx.createBuffer(1, samples.length, SAMPLE_RATE)
    buffer.copyToChannel(samples, 0)
    const source = ctx.createBufferSource()
    source.buffer = buffer
    // 接到 analyser 上,口型由实时播放的电平驱动
    source.connect(analyserRef.current ?? ctx.destination)
    const startAt = Math.max(ctx.currentTime + 0.06, nextTimeRef.current)
    source.start(startAt)
    nextTimeRef.current = startAt + buffer.duration
    sourcesRef.current.add(source)
    source.onended = () => {
      sourcesRef.current.delete(source)
    }
  }

  useEffect(() => {
    return () => {
      abortRef.current?.abort()
      stopLevelLoop()
      if (finishTimerRef.current !== null) window.clearTimeout(finishTimerRef.current)
      for (const source of sourcesRef.current) {
        try {
          source.stop()
        } catch {
          // 忽略
        }
      }
      void ctxRef.current?.close().catch(() => undefined)
      ctxRef.current = null
      analyserRef.current = null
      waveRef.current = null
    }
  }, [stopLevelLoop])

  return { speaking, speak, stop }
}
