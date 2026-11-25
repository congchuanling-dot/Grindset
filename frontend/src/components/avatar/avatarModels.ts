/**
 * 可选的面试官形象清单。
 *
 * 目前是 VRoid 官方示例形象(免费素材),只用于开发验证;
 * 正式上线时把 url 换成自有授权模型(建议放到 public/models 下走本地资源)。
 */
export interface AvatarModel {
  id: string
  name: string
  /** 人物简介,用于选择器里的副标题 */
  description: string
  url: string
}

const MODEL_BASE = 'https://cdn.jsdelivr.net/gh/madjin/vrm-samples@master/vroid/stable'

export const AVATAR_MODELS: AvatarModel[] = [
  {
    id: 'sample-a',
    name: '示例 A',
    description: '浅色开衫 · 亲和',
    url: `${MODEL_BASE}/AvatarSample_A.vrm`,
  },
  {
    id: 'sample-b',
    name: '示例 B',
    description: '深色系 · 干练',
    url: `${MODEL_BASE}/AvatarSample_B.vrm`,
  },
  {
    id: 'sample-c',
    name: '示例 C',
    description: '中性风 · 冷静',
    url: `${MODEL_BASE}/AvatarSample_C.vrm`,
  },
]

/** 形象选择的本地存储键 */
export const AVATAR_STORAGE_KEY = 'interview:avatarId'

export function resolveAvatarModel(id: string | undefined): AvatarModel {
  return AVATAR_MODELS.find((item) => item.id === id) ?? AVATAR_MODELS[0]
}
