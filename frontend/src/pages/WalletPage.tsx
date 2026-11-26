import { AlertCircle, ArrowDownLeft, ArrowUpRight, Coins, Loader2, RefreshCw, Wallet } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { getWallet, listWalletLogs, type WalletInfo, type WalletLogPage } from '../api/wallet'
import Pagination from '../components/questions/Pagination'
import { errMessage } from '../utils/error'

const PAGE_SIZE = 20

/** 余额展示保留 4 位小数,小额消费也能看出来 */
function formatMoney(value: number): string {
  return `¥${value.toFixed(4)}`
}

/** 流水金额带正负号 */
function formatChange(value: number): string {
  return `${value > 0 ? '+' : ''}${value.toFixed(4)}`
}

/**
 * 我的钱包:余额概览 + 流水明细分页。
 * 充值功能尚未开放,按钮只占位。
 */
function WalletPage() {
  const [wallet, setWallet] = useState<WalletInfo | null>(null)
  const [loadingWallet, setLoadingWallet] = useState(true)

  const [page, setPage] = useState(1)
  const [logs, setLogs] = useState<WalletLogPage | null>(null)
  const [loadingLogs, setLoadingLogs] = useState(true)

  const [error, setError] = useState<string | null>(null)

  const loadWallet = useCallback(async () => {
    setLoadingWallet(true)
    try {
      const data = await getWallet()
      setWallet(data)
    } catch (err) {
      setError(errMessage(err, '加载余额失败'))
    } finally {
      setLoadingWallet(false)
    }
  }, [])

  useEffect(() => {
    loadWallet()
  }, [loadWallet])

  useEffect(() => {
    let cancelled = false
    setLoadingLogs(true)
    listWalletLogs(page, PAGE_SIZE)
      .then((data) => {
        if (!cancelled) setLogs(data)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(errMessage(err, '加载余额流水失败'))
      })
      .finally(() => {
        if (!cancelled) setLoadingLogs(false)
      })
    return () => {
      cancelled = true
    }
  }, [page])

  const totalPages = Math.max(1, Math.ceil((logs?.total ?? 0) / PAGE_SIZE))

  return (
    <div className="h-full overflow-y-auto bg-slate-50 px-4 py-6 md:px-8">
      <div className="mx-auto max-w-4xl space-y-5">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-lg font-semibold text-slate-800">我的钱包</h1>
            <p className="mt-0.5 text-xs text-slate-500">AI 调用按实际 token 成本扣费</p>
          </div>
          <button
            type="button"
            onClick={loadWallet}
            title="刷新余额"
            className="flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-600 transition hover:border-teal-300 hover:text-teal-700"
          >
            <RefreshCw size={14} className={loadingWallet ? 'animate-spin' : ''} />
            刷新
          </button>
        </div>

        {error && (
          <div className="flex items-center gap-2 rounded-lg border border-rose-100 bg-rose-50 px-3 py-2 text-xs text-rose-600">
            <AlertCircle size={14} />
            {error}
          </div>
        )}

        {/* 余额卡片 + 充值占位 */}
        <div className="rounded-2xl bg-gradient-to-br from-teal-600 to-teal-500 p-6 text-white shadow-sm">
          <div className="flex items-center gap-2 text-sm text-teal-50/90">
            <Coins size={16} />
            账户余额
          </div>
          <div className="mt-3 text-4xl font-semibold tracking-tight">
            {loadingWallet && !wallet ? '--' : formatMoney(wallet?.balance ?? 0)}
          </div>
          <div className="mt-2 text-xs text-teal-50/80">
            {wallet?.updatedAt ? `更新于 ${wallet.updatedAt}` : '注册即送 1 元体验额度'}
          </div>
          <div className="mt-5 flex items-center gap-2">
            <button
              type="button"
              disabled
              title="充值功能即将上线"
              className="cursor-not-allowed rounded-lg bg-white/20 px-4 py-2 text-sm font-medium text-white/80"
            >
              充值
            </button>
            <span className="text-xs text-teal-50/70">充值功能即将上线</span>
          </div>
        </div>

        {/* 流水明细 */}
        <div className="rounded-2xl border border-slate-200 bg-white">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <h2 className="text-sm font-semibold text-slate-700">余额流水</h2>
            <span className="text-xs text-slate-400">共 {logs?.total ?? 0} 条</span>
          </div>

          {loadingLogs && !logs ? (
            <div className="flex items-center justify-center gap-2 py-12 text-sm text-slate-400">
              <Loader2 size={16} className="animate-spin" />
              加载中…
            </div>
          ) : !logs || logs.items.length === 0 ? (
            <div className="flex flex-col items-center justify-center gap-2 py-12 text-sm text-slate-400">
              <Wallet size={22} className="text-slate-300" />
              还没有余额变动记录
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {logs.items.map((item) => {
                const income = item.changeAmount > 0
                return (
                  <div key={item.id} className="flex items-center gap-3 px-4 py-3">
                    <span
                      className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${
                        income ? 'bg-teal-50 text-teal-600' : 'bg-rose-50 text-rose-500'
                      }`}
                    >
                      {income ? <ArrowDownLeft size={16} /> : <ArrowUpRight size={16} />}
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-sm text-slate-700">
                        {item.changeLabel}
                        {item.bizLabel ? ` · ${item.bizLabel}` : ''}
                      </div>
                      <div className="truncate text-xs text-slate-400">
                        {item.createdAt}
                        {item.remark ? ` · ${item.remark}` : ''}
                      </div>
                    </div>
                    <div className="text-right">
                      <div
                        className={`text-sm font-medium ${
                          income ? 'text-teal-600' : 'text-rose-500'
                        }`}
                      >
                        {formatChange(item.changeAmount)}
                      </div>
                      <div className="text-xs text-slate-400">
                        余额 {formatMoney(item.balanceAfter)}
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>
          )}

          {logs && logs.total > 0 && (
            <div className="border-t border-slate-100 px-4 py-2">
              <Pagination
                page={page}
                totalPages={totalPages}
                total={logs.total}
                size={PAGE_SIZE}
                loading={loadingLogs}
                onPageChange={setPage}
              />
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default WalletPage
