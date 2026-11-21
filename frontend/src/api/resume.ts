// 简历接口封装:上传走 multipart,其余走普通 JSON。
import { authFetch, unwrapJson } from './http'

export interface ResumeSkill {
  id: string
  name: string
  category: string | null
  proficiency: number | null
  evidence: string | null
}

export interface ResumeItem {
  id: string
  title: string | null
  subtitle: string | null
  startDate: string | null
  endDate: string | null
  description: string | null
  tags: string[]
}

export interface ResumeSection {
  id: string
  type: string
  title: string
  items: ResumeItem[]
}

export interface ResumeDetail {
  id: string
  fileName: string
  fileType: string
  fileSize: number
  /** 后端是否成功保存了原件（未配置 OSS 或上传失败时为 false） */
  hasOriginal: boolean
  /** 原件签名预览 URL，可能为空或过期，重新拉详情即可刷新 */
  originalUrl: string | null
  /** 原件签名下载 URL，带原文件名 */
  originalDownloadUrl: string | null
  parseStatus: number
  errorMsg: string | null
  candidateName: string | null
  candidateType: string
  model: string | null
  createdAt: string
  skills: ResumeSkill[]
  sections: ResumeSection[]
}

export interface ResumeSummary {
  id: string
  fileName: string
  candidateName: string | null
  candidateType: string
  parseStatus: number
  createdAt: string
}

/** 上传并同步解析。FormData 由浏览器自动带 boundary,不要手动设 Content-Type */
export async function uploadResume(file: File): Promise<ResumeDetail> {
  const form = new FormData()
  form.append('file', file)
  let res: Response
  try {
    res = await authFetch('/api/resumes', { method: 'POST', body: form })
  } catch {
    throw new Error('无法连接服务器，请确认后端已启动')
  }
  return unwrapJson<ResumeDetail>(res, '简历解析失败')
}

export async function listResumes(): Promise<ResumeSummary[]> {
  let res: Response
  try {
    res = await authFetch('/api/resumes')
  } catch {
    throw new Error('无法连接服务器，请确认后端已启动')
  }
  return unwrapJson<ResumeSummary[]>(res, '加载简历列表失败')
}

export async function getResume(id: string): Promise<ResumeDetail> {
  let res: Response
  try {
    res = await authFetch(`/api/resumes/${id}`)
  } catch {
    throw new Error('无法连接服务器，请确认后端已启动')
  }
  return unwrapJson<ResumeDetail>(res, '加载简历详情失败')
}

export async function deleteResume(id: string): Promise<void> {
  let res: Response
  try {
    res = await authFetch(`/api/resumes/${id}`, { method: 'DELETE' })
  } catch {
    throw new Error('无法连接服务器，请确认后端已启动')
  }
  await unwrapJson<null>(res, '删除简历失败')
}

export interface ResumeUpdateSkill {
  name: string
  category: string | null
  proficiency: number | null
  evidence: string | null
}

export interface ResumeUpdateItem {
  title: string
  subtitle: string
  startDate: string
  endDate: string
  description: string
  tags: string[]
}

export interface ResumeUpdateSection {
  type: string
  title: string
  items: ResumeUpdateItem[]
}

/** 文字版编辑保存：直接提交结构化内容，后端覆盖章节/条目/技能，不重新调用模型 */
export interface ResumeUpdatePayload {
  candidateName: string
  sections: ResumeUpdateSection[]
  skills: ResumeUpdateSkill[]
}

export async function updateResume(id: string, payload: ResumeUpdatePayload): Promise<ResumeDetail> {
  let res: Response
  try {
    res = await authFetch(`/api/resumes/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
  } catch {
    throw new Error('无法连接服务器，请确认后端已启动')
  }
  return unwrapJson<ResumeDetail>(res, '保存简历失败')
}

/** 通过后端代理获取原件字节，用于 PDF 内嵌预览（绕开 OSS 默认域名的强制下载） */
export async function fetchResumeOriginal(id: string): Promise<Blob> {
  let res: Response
  try {
    res = await authFetch(`/api/resumes/${id}/original`)
  } catch {
    throw new Error('无法连接服务器，请确认后端已启动')
  }
  if (!res.ok) {
    throw new Error(res.status === 404 ? '原件未存储' : '加载原件失败，请刷新后重试')
  }
  return res.blob()
}
