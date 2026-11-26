import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react'
import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { VRMLoaderPlugin, VRMUtils } from '@pixiv/three-vrm'
import {
  VRMAnimationLoaderPlugin,
  createVRMAnimationClip,
  type VRMAnimation,
} from '@pixiv/three-vrm-animation'
import type { VRM, VRMHumanBoneName } from '@pixiv/three-vrm'

/**
 * 坐姿待机动作(VRMA)。放在 public/models 下,缺失时自动回退到代码摆的坐姿。
 * 素材:SitIdle_v1_MotionDa / AirisuAsbel(だらてん) https://daraten.booth.pm/ —— 免费可商用
 */
export const SEATED_MOTION_URL = '/models/sit-idle.vrma'

export interface VrmAvatarStageProps {
  /** VRM 模型地址 */
  modelUrl: string
  /** half:半身取景(面试间用);full:全身 */
  framing?: 'half' | 'full'
  /** 待机动作 VRMA 地址,half 取景下生效 */
  animationUrl?: string
  className?: string
}

/** 命令式接口:口型逐帧变化,不能走 props,否则每帧都触发 React 重渲染 */
export interface VrmAvatarHandle {
  /** 0~1 张嘴幅度 */
  setMouth: (level: number) => void
}

/** 把 T-Pose 调成坐姿:大腿前伸、小腿下垂、手臂自然放在身前 */
function applySeatedPose(vrm: VRM) {
  const humanoid = vrm.humanoid
  if (!humanoid) return

  const rotate = (boneName: VRMHumanBoneName, x: number, z: number) => {
    const bone = humanoid.getNormalizedBoneNode(boneName)
    if (bone) bone.rotation.set(x, 0, z)
  }

  // 手臂:略微向前,垂在身前
  rotate('leftUpperArm', -0.18, 1.18)
  rotate('rightUpperArm', -0.18, -1.18)
  rotate('leftLowerArm', -0.4, 0.25)
  rotate('rightLowerArm', -0.4, -0.25)

  // 腿:大腿水平向前,小腿竖直向下,脚掌踩地
  rotate('leftUpperLeg', -1.45, 0.05)
  rotate('rightUpperLeg', -1.45, -0.05)
  rotate('leftLowerLeg', 1.45, 0)
  rotate('rightLowerLeg', 1.45, 0)
  rotate('leftFoot', -0.05, 0)
  rotate('rightFoot', -0.05, 0)
}

/**
 * 纯渲染组件:three.js 场景 + VRM 模型。
 *
 * 不处理业务、不发请求,只负责:加载模型、待机(眨眼/呼吸/看向镜头)、按取景模式摆镜头。
 * 口型等驱动能力后续通过 ref 或 props 注入,避免业务逻辑渗进渲染层。
 */
const VrmAvatarStage = forwardRef<VrmAvatarHandle, VrmAvatarStageProps>(function VrmAvatarStage(
  { modelUrl, framing = 'half', animationUrl = SEATED_MOTION_URL, className },
  ref,
) {
  const hostRef = useRef<HTMLDivElement>(null)
  const [progress, setProgress] = useState(0)
  const [error, setError] = useState<string | null>(null)
  /** 目标口型幅度,由 useInterviewerVoice 按音频能量写入 */
  const mouthRef = useRef(0)

  useImperativeHandle(ref, () => ({
    setMouth: (level: number) => {
      mouthRef.current = Math.max(0, Math.min(1, level))
    },
  }), [])

  useEffect(() => {
    const host = hostRef.current
    if (!host) return
    const hostEl: HTMLDivElement = host

    let disposed = false
    let renderer: THREE.WebGLRenderer | null = null
    let vrm: VRM | null = null
    let mixer: THREE.AnimationMixer | null = null
    let mouthOpenName = 'aa'
    let mouthRoundName: string | null = 'oh'

    setProgress(0)
    setError(null)

    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(30, 1, 0.1, 30)

    async function setup() {
      renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
      renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
      renderer.setSize(hostEl.clientWidth || 1, hostEl.clientHeight || 1)
      renderer.outputColorSpace = THREE.SRGBColorSpace
      hostEl.appendChild(renderer.domElement)

      // 灯光:按背景(右侧窗户暖光)布光,让 MToon 不至于发灰、也不跟背景脱节
      scene.add(new THREE.HemisphereLight(0xdfe8ff, 0x8a8175, 1.0))
      const keyLight = new THREE.DirectionalLight(0xfff2e0, 1.4)
      keyLight.position.set(1.8, 2.2, 1.8)
      scene.add(keyLight)
      const fillLight = new THREE.DirectionalLight(0xbfd4ff, 0.45)
      fillLight.position.set(-1.5, 1.2, 1.2)
      scene.add(fillLight)

      try {
        const loader = new GLTFLoader()
        loader.register((parser) => new VRMLoaderPlugin(parser))

        const gltf = await loader.loadAsync(modelUrl, (event) => {
          if (event.total > 0) {
            setProgress(Math.min(99, Math.floor((event.loaded / event.total) * 100)))
          }
        })
        if (disposed) return

        const loadedVrm = gltf.userData.vrm as VRM | undefined
        if (!loadedVrm) {
          throw new Error('文件不是合法的 VRM 模型')
        }
        vrm = loadedVrm

        // VRM 0.x 朝向是 -Z,统一转成 +Z,否则会背对镜头
        VRMUtils.rotateVRM0(vrm)
        VRMUtils.removeUnnecessaryVertices(gltf.scene)
        VRMUtils.combineSkeletons(gltf.scene)
        // 关掉视锥剔除,避免头发/裙摆被误判剔除
        vrm.scene.traverse((object) => {
          object.frustumCulled = false
        })

        // 口型预设名按模型实际情况取:VRM1 是 aa/oh,部分 VRM0 只留了 a/o
        const hasExpression = (name: string) => Boolean(vrm?.expressionManager?.getExpression(name))
        mouthOpenName = hasExpression('aa') ? 'aa' : hasExpression('a') ? 'a' : 'aa'
        mouthRoundName = hasExpression('oh') ? 'oh' : hasExpression('o') ? 'o' : null

        // 用外层 group 承担呼吸/摆动,不能直接改 vrm.scene 的 rotation,
        // 否则会覆盖 rotateVRM0 设置的 180° 朝向,导致背对镜头
        const swayRoot = new THREE.Group()
        scene.add(swayRoot)
        swayRoot.add(vrm.scene)

        if (framing === 'half') {
          // 坐姿优先用动画师做的 VRMA;没有再退回代码摆姿
          if (animationUrl) {
            try {
              const animationLoader = new GLTFLoader()
              animationLoader.register((parser) => new VRMAnimationLoaderPlugin(parser))
              const animationGltf = await animationLoader.loadAsync(animationUrl)
              const vrmAnimations = animationGltf.userData.vrmAnimations as
                | VRMAnimation[]
                | undefined
              const animation = vrmAnimations?.[0]
              if (animation) {
                const clip = createVRMAnimationClip(animation, vrm)
                mixer = new THREE.AnimationMixer(vrm.scene)
                mixer.clipAction(clip).play()
                // 先吃第一帧,才能量到头部位置和桌面高度
                mixer.update(0)
              }
            } catch {
              // 动作文件缺失/格式不对:静默回退,不阻断渲染
            }
          }
          if (!mixer) {
            applySeatedPose(vrm)
            vrm.scene.position.y = -0.45
          }
        } else {
          applySeatedPose(vrm)
        }
        vrm.scene.updateMatrixWorld(true)

        const head = vrm.humanoid?.getNormalizedBoneNode('head')
        const headPosition = new THREE.Vector3(0, 1.5, 0)
        if (head) {
          head.getWorldPosition(headPosition)
        }

        if (framing === 'half') {
          // 以头骨位置推算发顶取景:头骨在颅底,发顶约再高 0.14m。
          // 不能用包围盒 —— 蒙皮网格的包围盒是 T-Pose(站立)的,会把头顶算高一大截。
          const avatarTop = headPosition.y + 0.14
          const frameHeight = 0.52
          const headroom = 0.12
          const centerY = avatarTop + headroom - frameHeight / 2
          const distance = frameHeight / 2 / Math.tan((camera.fov * Math.PI) / 360)
          camera.position.set(0, centerY + 0.02, distance)
          camera.lookAt(0, centerY, 0)
        } else {
          const targetY = headPosition.y * 0.55
          camera.position.set(0, targetY + 0.05, headPosition.y * 2.1)
          camera.lookAt(0, targetY, 0)
        }

        if (vrm.lookAt) {
          vrm.lookAt.target = camera
        }

        let lastTime = performance.now()
        let elapsed = 0
        let mouthValue = 0
        const blinkTimer = { next: 2.5, closing: -1 }

        renderer.setAnimationLoop(() => {
          if (!vrm || !renderer) return
          const now = performance.now()
          const delta = Math.min(0.1, (now - lastTime) / 1000)
          lastTime = now
          elapsed += delta

          // 呼吸:轻微上下浮动 + 左右微摆
          swayRoot.position.y = Math.sin(elapsed * 1.2) * 0.006
          swayRoot.rotation.y = Math.sin(elapsed * 0.35) * 0.04

          // 动作先驱动骨骼。必须放在表情之前:VRMA 里可能带表情轨道,
          // 先 mixer 再写口型,否则每帧都被重置回 0,嘴就不动了
          mixer?.update(delta)

          const expressionManager = vrm.expressionManager
          if (expressionManager) {
            // 口型:目标值来自正在播放的语音能量,这里做平滑,避免一帧一变地抽搐
            mouthValue += (mouthRef.current - mouthValue) * Math.min(1, delta * 18)
            expressionManager.setValue(mouthOpenName, mouthValue)
            if (mouthRoundName) {
              expressionManager.setValue(mouthRoundName, mouthValue * 0.35)
            }

            if (blinkTimer.closing < 0 && elapsed > blinkTimer.next) {
              blinkTimer.closing = 0
            }
            if (blinkTimer.closing >= 0) {
              blinkTimer.closing += delta
              const t = blinkTimer.closing / 0.12
              const value = t < 1 ? 1 - Math.abs(t - 0.5) * 2 : 0
              expressionManager.setValue('blink', Math.max(0, value))
              if (t >= 1) {
                blinkTimer.closing = -1
                blinkTimer.next = elapsed + 2 + Math.random() * 3
                expressionManager.setValue('blink', 0)
              }
            }
          }

          // 最后把表情/视线/弹簧骨写进模型
          vrm.update(delta)
          renderer.render(scene, camera)
        })

        setProgress(100)
      } catch (err) {
        if (disposed) return
        setError(err instanceof Error ? err.message : '模型加载失败')
      }
    }

    void setup()

    const observer = new ResizeObserver(() => {
      if (!renderer) return
      const width = hostEl.clientWidth || 1
      const height = hostEl.clientHeight || 1
      renderer.setSize(width, height)
      camera.aspect = width / height
      camera.updateProjectionMatrix()
    })
    observer.observe(hostEl)

    return () => {
      disposed = true
      observer.disconnect()
      mixer?.stopAllAction()
      renderer?.setAnimationLoop(null)
      if (vrm) {
        vrm.scene.traverse((object) => {
          const mesh = object as THREE.Mesh
          if (mesh.geometry) mesh.geometry.dispose()
        })
        scene.remove(vrm.scene)
      }
      if (renderer) {
        renderer.dispose()
        renderer.domElement.remove()
      }
    }
  }, [modelUrl, framing, animationUrl])

  return (
    <div
      ref={hostRef}
      className={`relative h-full w-full ${className ?? ''}`}
      style={{
        // 真实办公室虚化背景,人物直接合成在上面,比纯色渐变更像视频会议
        backgroundImage: "url('/room-bg_1.jpg')",
        backgroundSize: 'cover',
        backgroundPosition: 'center',
      }}
    >
      {/* 压暗一层,让浅色头发/衣服从背景里立出来 */}
      <div className="pointer-events-none absolute inset-0 bg-black/15" />
      {progress < 100 && !error && (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 text-xs text-white/60">
          <span className="h-1.5 w-32 overflow-hidden rounded-full bg-white/10">
            <span
              className="block h-full rounded-full bg-brand-400 transition-all duration-300"
              style={{ width: `${progress}%` }}
            />
          </span>
          <span>形象加载中 {progress}%</span>
        </div>
      )}
      {error && (
        <div className="absolute inset-0 flex items-center justify-center px-4 text-center text-xs text-rose-300">
          {error}
        </div>
      )}
    </div>
  )
})

export default VrmAvatarStage
