/** 面试间参与者类型:自己 / AI 面试官 / 后续连麦的真人同面 */
export type ParticipantKind = 'self' | 'interviewer' | 'peer'

export interface Participant {
  id: string
  kind: ParticipantKind
  name: string
  /** 面试官:岗位名;真人:身份说明 */
  role?: string
  /** 仅 interviewer 使用,对应 avatarModels 里的 id */
  avatarId?: string
  cameraOn?: boolean
  micOn?: boolean
  /** 预留:说话高亮 */
  speaking?: boolean
}
