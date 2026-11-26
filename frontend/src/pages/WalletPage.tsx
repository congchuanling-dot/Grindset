import { AlertCircle, ArrowDownLeft, ArrowUpRight, Coins, Loader2, RefreshCw, Wallet } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
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
 * 余额从上一个值滚动到新值。
 * 纯展示动效,系统开启"减少动态效果"时直接跳到结果。
 */
function useCountUp(target: number | undefined, duration = 700): number | null {
  const [value, setValue] = useState<number | null>(null)
  const fromRef = useRef(0)

  useEffect(() => {
    if (target === undefined) {
      return
    }
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      fromRef.current = target
      setValue(target)
      return
    }

    const from = fromRef.current
    const start = performance.now()
    let frame = 0

    const tick = (now: number) => {
      const progress = Math.min(1, (now - start) / duration)
      // easeOutCubic:起步快、结尾缓,数字滚动更自然
      const eased = 1 - Math.pow(1 - progress, 3)
      setValue(from + (target - from) * eased)
      if (progress < 1) {
        frame = requestAnimationFrame(tick)
      } else {
        fromRef.current = target
      }
    }

    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [target, duration])

  return value
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

  // 余额数字滚动:拿到数据后从 0 滚到实际值,刷新时从旧值滚到新值
  const animatedBalance = useCountUp(wallet?.balance)

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
    <div className="h-full overflow-y-auto bg-slate-50 px-4 py-8 md:px-10">
      <div className="mx-auto max-w-4xl space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-[22px] font-semibold leading-tight tracking-[-0.02em] text-slate-900">我的钱包</h1>
            <p className="mt-1 text-sm text-slate-500">AI 调用按实际 token 成本扣费</p>
          </div>
          <button
            type="button"
            onClick={loadWallet}
            title="刷新余额"
            className="flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs text-slate-600 shadow-xs transition hover:border-slate-300 hover:text-slate-900"
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

        {/* 余额：横向账本，强调色只用在一条马克笔标记上 */}
        <section className="border border-slate-200 bg-white px-6 py-7">
          <div className="flex items-center gap-2 text-xs text-slate-500">
            <Coins size={14} />
            账户余额
          </div>
          <div className="mt-3 flex flex-wrap items-baseline justify-between gap-4">
            <span className="text-[44px] font-semibold leading-none tracking-[-0.03em] tabular-nums text-slate-900">
              {wallet && animatedBalance !== null ? formatMoney(animatedBalance) : '--'}
            </span>
            <span className="text-xs text-slate-400">
              {wallet?.updatedAt ? `更新于 ${wallet.updatedAt}` : '注册即送 1 元体验额度'}
            </span>
          </div>
          <div className="mt-5 h-1 w-14 bg-marker" aria-hidden />
          <div className="mt-4 flex items-center gap-3">
            <button
              type="button"
              disabled
              title="充值功能即将上线"
              className="cursor-not-allowed border border-slate-200 px-4 py-2 text-sm text-slate-400"
            >
              充值
            </button>
            <span className="text-xs text-slate-400">充值功能即将上线</span>
          </div>
        </section>

        {/* 流水明细 */}
        <div className="border border-slate-200 bg-white">
          <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
            <h2 className="text-sm font-semibold text-slate-800">余额流水</h2>
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
            <div className="divide-y divide-slate-200/70">
              {logs.items.map((item) => {
                const income = item.changeAmount > 0
                return (
                  <div key={item.id} className="flex items-center gap-3 px-5 py-3.5">
                    <span className={`shrink-0 ${income ? 'text-slate-900' : 'text-rose-600'}`}>
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
                          income ? 'text-brand-600' : 'text-rose-500'
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
