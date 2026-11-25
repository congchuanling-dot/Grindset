import { Loader2, VideoOff } from 'lucide-react'
import { useEffect, useRef } from 'react'

interface SelfVideoProps {
  stream: MediaStream | null
  cameraOn: boolean
  starting?: boolean
  onEnable?: () => void
}

/** 本地摄像头画面。关摄像头或未授权时显示占位,不阻塞整个页面 */
function SelfVideo({ stream, cameraOn, starting, onEnable }: SelfVideoProps) {
  const videoRef = useRef<HTMLVideoElement>(null)

  useEffect(() => {
    const video = videoRef.current
    if (!video) return
    video.srcObject = stream
    if (stream) {
      void video.play().catch(() => {
        // 自动播放被拦截时忽略,用户点一下即可恢复
      })
    }
  }, [stream])

  const showVideo = Boolean(stream) && cameraOn

  return (
    <div className="relative h-full w-full bg-[#202329]">
      <video
        ref={videoRef}
        autoPlay
        muted
        playsInline
        className={`h-full w-full scale-x-[-1] object-cover transition-opacity duration-300 ${
          showVideo ? 'opacity-100' : 'opacity-0'
        }`}
      />

      {!showVideo && (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-gradient-to-br from-slate-700/40 to-slate-900/60">
          <span className="flex h-16 w-16 items-center justify-center rounded-full bg-white/10 text-2xl font-medium text-white/70">
            我
          </span>
          {starting ? (
            <span className="flex items-center gap-1.5 text-xs text-white/60">
              <Loader2 size={13} className="animate-spin" />
              正在开启摄像头...
            </span>
          ) : (
            <button
              type="button"
              onClick={onEnable}
              className="flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5 text-xs text-white/70 transition hover:bg-white/20 hover:text-white"
            >
              <VideoOff size={13} />
              摄像头未开启
            </button>
          )}
        </div>
      )}
    </div>
  )
}

export default SelfVideo
