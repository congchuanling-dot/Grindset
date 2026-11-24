import { ChevronLeft, ChevronRight } from 'lucide-react'

interface PaginationProps {
  page: number
  totalPages: number
  total: number
  size: number
  sizes?: number[]
  loading?: boolean
  compact?: boolean
  onPageChange: (page: number) => void
  onSizeChange?: (size: number) => void
}

/** 题库通用分页:compact 用于左侧窄栏,普通模式展示页码 */
function Pagination({
  page,
  totalPages,
  total,
  size,
  sizes = [12, 20, 50],
  loading,
  compact,
  onPageChange,
  onSizeChange,
}: PaginationProps) {
  if (total === 0) {
    return null
  }

  const disabledPrev = page <= 1 || loading
  const disabledNext = page >= totalPages || loading

  return (
    <div
      className={`flex items-center justify-between gap-2 text-xs text-slate-500 ${
        compact ? 'px-3 py-2' : 'flex-wrap'
      }`}
    >
      <div className="flex items-center gap-2">
        <span className="whitespace-nowrap">
          共 {total}
          {compact ? '' : ' 道'} · {page}/{totalPages}
        </span>
        {onSizeChange && (
          <select
            value={size}
            onChange={(e) => onSizeChange(Number(e.target.value))}
            className="rounded-md border border-slate-200 bg-white px-1.5 py-0.5 text-[11px] text-slate-500 outline-none focus:border-teal-400"
          >
            {sizes.map((item) => (
              <option key={item} value={item}>
                {item} 条
              </option>
            ))}
          </select>
        )}
      </div>

      <div className="flex items-center gap-1">
        <button
          type="button"
          onClick={() => onPageChange(Math.max(1, page - 1))}
          disabled={disabledPrev}
          className="flex items-center rounded-md border border-slate-200 bg-white px-1.5 py-1 transition hover:border-slate-300 disabled:cursor-not-allowed disabled:text-slate-300"
          aria-label="上一页"
        >
          <ChevronLeft size={13} />
        </button>

        {!compact &&
          pageItems(page, totalPages).map((item, index) =>
            item === 'ellipsis' ? (
              <span key={`e-${index}`} className="px-1 text-slate-300">
                …
              </span>
            ) : (
              <button
                key={item}
                type="button"
                onClick={() => onPageChange(item)}
                disabled={item === page || loading}
                className={`min-w-7 rounded-md border px-1.5 py-1 transition ${
                  item === page
                    ? 'border-teal-300 bg-teal-50 font-medium text-teal-700'
                    : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300'
                }`}
              >
                {item}
              </button>
            ),
          )}

        <button
          type="button"
          onClick={() => onPageChange(Math.min(totalPages, page + 1))}
          disabled={disabledNext}
          className="flex items-center rounded-md border border-slate-200 bg-white px-1.5 py-1 transition hover:border-slate-300 disabled:cursor-not-allowed disabled:text-slate-300"
          aria-label="下一页"
        >
          <ChevronRight size={13} />
        </button>
      </div>
    </div>
  )
}

function pageItems(current: number, total: number): (number | 'ellipsis')[] {
  if (total <= 7) {
    return Array.from({ length: total }, (_, index) => index + 1)
  }
  const items: (number | 'ellipsis')[] = [1]
  const start = Math.max(2, current - 1)
  const end = Math.min(total - 1, current + 1)
  if (start > 2) items.push('ellipsis')
  for (let page = start; page <= end; page += 1) {
    items.push(page)
  }
  if (end < total - 1) items.push('ellipsis')
  items.push(total)
  return items
}

export default Pagination
