import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react'
import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'

type ToastKind = 'success' | 'error' | 'info'

interface ToastItem {
  id: number
  kind: ToastKind
  message: string
}

interface ToastContextValue {
  show: (kind: ToastKind, message: string) => void
  success: (message: string) => void
  error: (message: string) => void
  info: (message: string) => void
}

const ToastContext = createContext<ToastContextValue | null>(null)

const KIND_STYLES: Record<ToastKind, { icon: typeof CheckCircle2; iconClass: string; bar: string }> = {
  success: { icon: CheckCircle2, iconClass: 'text-brand-600', bar: 'bg-brand-500' },
  error: { icon: AlertCircle, iconClass: 'text-rose-500', bar: 'bg-rose-500' },
  info: { icon: Info, iconClass: 'text-slate-500', bar: 'bg-slate-400' },
}

const AUTO_DISMISS_MS = 3000

/**
 * 轻提示:右上角弹出,自动消失,也可手动关闭。
 * 目前用于登录成功,后续操作结果都可以直接复用 useToast()。
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])
  const nextId = useRef(1)

  const remove = useCallback((id: number) => {
    setItems((prev) => prev.filter((item) => item.id !== id))
  }, [])

  const show = useCallback(
    (kind: ToastKind, message: string) => {
      const id = nextId.current++
      setItems((prev) => [...prev.slice(-3), { id, kind, message }])
      window.setTimeout(() => remove(id), AUTO_DISMISS_MS)
    },
    [remove],
  )

  const value = useMemo<ToastContextValue>(
    () => ({
      show,
      success: (message) => show('success', message),
      error: (message) => show('error', message),
      info: (message) => show('info', message),
    }),
    [show],
  )

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        aria-live="polite"
        className="pointer-events-none fixed right-4 top-4 z-[100] flex w-[min(360px,calc(100vw-2rem))] flex-col gap-2"
      >
        {items.map((item) => {
          const style = KIND_STYLES[item.kind]
          const Icon = style.icon
          return (
            <div
              key={item.id}
              role="status"
              className="toast-in pointer-events-auto relative flex items-start gap-2.5 overflow-hidden rounded-xl border border-slate-200 bg-white px-3.5 py-3 shadow-xl"
            >
              <span className={`absolute left-0 top-0 h-full w-0.5 ${style.bar}`} aria-hidden />
              <Icon size={17} className={`mt-0.5 shrink-0 ${style.iconClass}`} />
              <p className="min-w-0 flex-1 text-sm leading-5 text-slate-700">{item.message}</p>
              <button
                type="button"
                onClick={() => remove(item.id)}
                aria-label="关闭通知"
                className="shrink-0 rounded p-0.5 text-slate-300 transition hover:bg-slate-100 hover:text-slate-500"
              >
                <X size={14} />
              </button>
            </div>
          )
        })}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast 必须在 ToastProvider 内使用')
  return ctx
}
