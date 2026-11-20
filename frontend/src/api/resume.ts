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
