import { useCallback, useEffect, useRef, useState } from 'react'
import { errMessage } from '../utils/error'

function toMessage(error: unknown): string {
  if (error instanceof DOMException) {
    if (error.name === 'NotAllowedError') return '摄像头/麦克风权限被拒绝,请在浏览器地址栏允许后重试'
    if (error.name === 'NotFoundError') return '未检测到可用的摄像头或麦克风'
    if (error.name === 'NotReadableError') return '设备被其他程序占用,请关闭后重试'
  }
  return errMessage(error, '无法获取音视频设备')
}

/**
 * 本地摄像头 + 麦克风。
 *
 * 一次 getUserMedia 拿同一条流;开关只切 track.enabled,不重新申请,恢复更快。
 * 离开页面时统一 stop,避免摄像头指示灯一直亮着。
 */
export function useLocalMedia() {
  const streamRef = useRef<MediaStream | null>(null)
  const startingRef = useRef(false)
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [cameraOn, setCameraOn] = useState(true)
  const [micOn, setMicOn] = useState(true)
  const [starting, setStarting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const start = useCallback(async () => {
    if (streamRef.current || startingRef.current) return
    startingRef.current = true
    setStarting(true)
    setError(null)
    try {
      const media = await navigator.mediaDevices.getUserMedia({
        video: { width: 1280, height: 720 },
        audio: true,
      })
      streamRef.current = media
      setStream(media)
      setCameraOn(true)
      setMicOn(true)
    } catch (err) {
      setError(toMessage(err))
    } finally {
      startingRef.current = false
      setStarting(false)
    }
  }, [])

  const stop = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop())
    streamRef.current = null
    setStream(null)
  }, [])

  const toggleCamera = useCallback(() => {
    const track = streamRef.current?.getVideoTracks()[0]
    if (!track) return
    track.enabled = !track.enabled
    setCameraOn(track.enabled)
  }, [])

  const toggleMic = useCallback(() => {
    const track = streamRef.current?.getAudioTracks()[0]
    if (!track) return
    track.enabled = !track.enabled
    setMicOn(track.enabled)
  }, [])

  useEffect(() => {
    return () => {
      streamRef.current?.getTracks().forEach((track) => track.stop())
      streamRef.current = null
    }
  }, [])

  return { stream, cameraOn, micOn, starting, error, start, stop, toggleCamera, toggleMic }
}
