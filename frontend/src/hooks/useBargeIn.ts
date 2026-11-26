import { useEffect, useRef } from 'react'

/**
 * 打断检测:面试官播报时监听本地麦克风,
 * 连续检测到人声(不是一两下杂音)就触发 onBargeIn,让调用方停止播报并开始收音。
 */
export function useBargeIn(active: boolean, onBargeIn: () => void) {
  const callbackRef = useRef(onBargeIn)
  callbackRef.current = onBargeIn

  useEffect(() => {
    if (!active) return
    let disposed = false
    let stream: MediaStream | null = null
    let context: AudioContext | null = null
    let timer: number | null = null
    let voicedSince = 0

    function teardown() {
      if (timer !== null) {
        window.clearInterval(timer)
        timer = null
      }
      stream?.getTracks().forEach((track) => track.stop())
      stream = null
      void context?.close().catch(() => undefined)
      context = null
    }

    void (async () => {
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true },
        })
        if (disposed) {
          teardown()
          return
        }
        context = new AudioContext()
        const source = context.createMediaStreamSource(stream)
        const analyser = context.createAnalyser()
        analyser.fftSize = 512
        source.connect(analyser)
        const buffer = new Float32Array(analyser.fftSize)

        timer = window.setInterval(() => {
          if (disposed || !context) return
          analyser.getFloatTimeDomainData(buffer)
          let sum = 0
          for (let i = 0; i < buffer.length; i++) {
            sum += buffer[i] * buffer[i]
          }
          const rms = Math.sqrt(sum / buffer.length)
          const now = Date.now()
          if (rms > 0.07) {
            if (voicedSince === 0) voicedSince = now
            // 持续 300ms 以上的人声才算打断,挡掉键盘声和回声毛刺
            if (now - voicedSince > 300) {
              callbackRef.current()
              teardown()
            }
          } else {
            voicedSince = 0
          }
        }, 100)
      } catch {
        // 没有麦克风权限就算了,不影响播报
      }
    })()

    return () => {
      disposed = true
      teardown()
    }
  }, [active])
}
