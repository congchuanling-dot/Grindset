// 用户钱包接口封装:余额概览 + 流水明细分页。
import { authFetch, unwrapJson } from './http'

export interface WalletInfo {
  balance: number
  currency: string
  updatedAt: string
}

export interface WalletLog {
  id: string
  changeAmount: number
  balanceAfter: number
  changeType: string
  changeLabel: string
  bizType: string | null
  bizLabel: string
  bizRef: string | null
  remark: string | null
  createdAt: string
}

export interface WalletLogPage {
  items: WalletLog[]
  total: number
  page: number
  size: number
}

/** 当前登录用户的余额概览 */
export async function getWallet(): Promise<WalletInfo> {
  const res = await authFetch('/api/wallet')
  return unwrapJson<WalletInfo>(res, '加载余额失败')
}

/** 余额流水分页 */
export async function listWalletLogs(page: number, size = 20): Promise<WalletLogPage> {
  const res = await authFetch(`/api/wallet/logs?page=${page}&size=${size}`)
  return unwrapJson<WalletLogPage>(res, '加载余额流水失败')
}
