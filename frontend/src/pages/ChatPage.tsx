import { ArrowUp, Plus, Sparkles, Square, Wallet } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import {
  fetchModels,
  streamChat,
  type BalanceInfo,
  type ChatMessage,
  type ProviderInfo,
} from '../api/chat'

interface Message {
  id: number
  role: 'user' | 'assistant'
  content: string
  /** 这条回复是不是报错信息,报错用红色展示 */
  error?: boolean
}

const SUGGESTIONS = ['不知道怎么开始？', '帮我出 3 道 Redis 题', '知识点掌握度是什么？']

function ChatPage() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [providers, setProviders] = useState<ProviderInfo[]>([])
  const [selection, setSelection] = useState('')
  const [modelsLoading, setModelsLoading] = useState(true)
  const [modelsError, setModelsError] = useState('')
  const [streamingId, setStreamingId] = useState<number | null>(null)

  const listRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const abortRef = useRef<AbortController | null>(null)
  const idRef = useRef(0)

  const streaming = streamingId !== null

  function nextId() {
    idRef.current += 1
    return idRef.current
  }

  // 进页面拉一次可用的模型服务,默认选中第一个厂商的第一个模型
  useEffect(() => {
    let cancelled = false
    fetchModels()
      .then((list) => {
        if (cancelled) return
        setProviders(list)
        if (list.length > 0) {
          setSelection(`${list[0].id}::${list[0].models[0].id}`)
        }
      })
      .catch((error: unknown) => {
        if (cancelled) return
        setModelsError(error instanceof Error ? error.message : '加载模型列表失败')
      })
      .finally(() => {
        if (!cancelled) setModelsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  // 新消息或流式增量到达后滚到底部
  useEffect(() => {
    const list = listRef.current
    if (list) {
      list.scrollTop = list.scrollHeight
    }
  }, [messages])

  // 离开页面时停掉正在进行的请求
  useEffect(() => {
    return () => abortRef.current?.abort()
  }, [])

  function updateMessage(id: number, patch: Partial<Message>) {
    setMessages((prev) => prev.map((item) => (item.id === id ? { ...item, ...patch } : item)))
  }

  function appendToMessage(id: number, text: string) {
    setMessages((prev) =>
      prev.map((item) => (item.id === id ? { ...item, content: item.content + text } : item)),
    )
  }

  async function send(text: string) {
    if (streaming || !selection) return

    const [provider, model] = selection.split('::')
    // 请求里带完整多轮上下文(不含刚插入的空回复)
    const history: ChatMessage[] = [
      ...messages.map(({ role, content }) => ({ role, content })),
      { role: 'user', content: text },
    ]

    const assistantId = nextId()
    setMessages((prev) => [
      ...prev,
      { id: nextId(), role: 'user', content: text },
      { id: assistantId, role: 'assistant', content: '' },
    ])
    setInput('')
    if (inputRef.current) {
      inputRef.current.style.height = 'auto'
    }

    const controller = new AbortController()
    abortRef.current = controller
    setStreamingId(assistantId)

    try {
      await streamChat(
        { provider, model, messages: history },
        {
          onDelta: (delta) => appendToMessage(assistantId, delta),
          onError: (message) => updateMessage(assistantId, { content: message, error: true }),
        },
        controller.signal,
      )
    } catch (error) {
      // 主动停止不算错误,保留已生成的内容
      if (!isAbortError(error)) {
        updateMessage(assistantId, {
          content: error instanceof Error ? error.message : '对话失败,请重试',
          error: true,
        })
      }
    } finally {
      setMessages((prev) =>
        prev.map((item) =>
          item.id === assistantId && !item.content
            ? { ...item, content: '已停止生成', error: false }
            : item,
        ),
      )
      setStreamingId(null)
      abortRef.current = null
    }
  }

  function handleSend() {
    const text = input.trim()
    if (!text) return
    void send(text)
  }

  function handleStop() {
    abortRef.current?.abort()
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    // Enter 发送,Shift + Enter 换行
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  function handleInputChange(e: React.ChangeEvent<HTMLTextAreaElement>) {
    setInput(e.target.value)
    // 输入框随内容自动长高,最高 160px
    e.currentTarget.style.height = 'auto'
    e.currentTarget.style.height = `${Math.min(e.currentTarget.scrollHeight, 160)}px`
  }

  function handleSelectionChange(e: React.ChangeEvent<HTMLSelectElement>) {
    setSelection(e.target.value)
  }

  // 当前选中厂商(余额是厂商账号级的,跟着下拉框选择走)
  const selectedProviderId = selection.split('::')[0]
  const selectedProvider = providers.find((item) => item.id === selectedProviderId)

  const caption = modelsError
    ? modelsError
    : !modelsLoading && providers.length === 0
      ? '服务端未配置任何模型的 API Key，请设置环境变量后重启后端'
      : 'AI 回复由所选模型实时生成，内容仅供参考'

  return (
    <div className="flex h-full flex-col bg-white">
      <div ref={listRef} className="flex-1 overflow-y-auto">
        {messages.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center px-6 text-center">
            <span className="flex h-12 w-12 items-center justify-center rounded-full bg-teal-50 text-teal-600">
              <Sparkles size={22} />
            </span>
            <h1 className="mt-4 text-xl font-semibold text-slate-800">
              你想让我帮你复习什么？
            </h1>
            <p className="mt-2 max-w-sm text-sm leading-6 text-slate-500">
              我是你的面试复习助手，不知道怎么操作也可以直接问我。
            </p>
            <div className="mt-5 flex flex-wrap justify-center gap-2">
              {SUGGESTIONS.map((item) => (
                <button
                  key={item}
                  type="button"
                  onClick={() => {
                    setInput(item)
                    inputRef.current?.focus()
                  }}
                  className="rounded-full border border-slate-200 px-3 py-1.5 text-xs text-slate-600 transition hover:border-teal-300 hover:text-teal-700"
                >
                  {item}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <div className="mx-auto w-full max-w-3xl space-y-4 px-4 py-6">
            {messages.map((message) => {
              const pending = message.role === 'assistant' && !message.content
              return (
                <div
                  key={message.id}
                  className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}
                >
                  <div
                    className={`max-w-[80%] whitespace-pre-wrap rounded-2xl px-4 py-2.5 text-sm leading-6 ${
                      message.role === 'user'
                        ? 'bg-teal-600 text-white'
                        : message.error
                          ? 'bg-red-50 text-red-600'
                          : 'bg-slate-100 text-slate-700'
                    }`}
                  >
                    {pending ? <span className="text-slate-400">正在思考…</span> : message.content}
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      <div className="shrink-0 px-4 pb-5">
        <div className="mx-auto w-full max-w-3xl">
          {selectedProvider?.balance && (
            <div className="mb-2 flex items-center justify-end gap-1.5 text-xs text-slate-400">
              <Wallet size={13} />
              <span>
                {selectedProvider.name} 余额 {formatBalance(selectedProvider.balance)}
              </span>
              {selectedProvider.balance.updatedAt && (
                <>
                  <span className="text-slate-300">·</span>
                  <span>{selectedProvider.balance.updatedAt} 更新</span>
                </>
              )}
            </div>
          )}
          <div className="flex items-end gap-2 rounded-2xl border border-slate-200 bg-white px-3 py-2.5 shadow-sm transition focus-within:border-teal-400">
            {/* 附件入口:先占位,后续接上传 */}
            <button
              type="button"
              disabled
              title="上传附件（即将上线）"
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:hover:bg-transparent"
            >
              <Plus size={18} />
            </button>

            <textarea
              ref={inputRef}
              value={input}
              onChange={handleInputChange}
              onKeyDown={handleKeyDown}
              rows={1}
              placeholder={
                providers.length > 0 ? '随心输入，Enter 发送' : '请先在服务端配置模型 API Key'
              }
              className="max-h-40 flex-1 resize-none bg-transparent py-1.5 text-sm leading-6 text-slate-800 outline-none placeholder:text-slate-400"
            />

            {/* 模型选择:按厂商分组的 optgroup */}
            <select
              value={selection}
              onChange={handleSelectionChange}
              disabled={providers.length === 0 || streaming}
              title="选择模型"
              className="h-8 max-w-40 shrink-0 cursor-pointer truncate rounded-lg bg-slate-100 px-2 text-xs text-slate-600 outline-none transition hover:bg-slate-200 disabled:cursor-not-allowed disabled:text-slate-400"
            >
              {providers.length === 0 ? (
                <option value="">未配置模型</option>
              ) : (
                providers.map((provider) => (
                  <optgroup
                    key={provider.id}
                    label={
                      provider.balance
                        ? `${provider.name}（余额 ${formatBalance(provider.balance)}）`
                        : provider.name
                    }
                  >
                    {provider.models.map((item) => (
                      <option key={`${provider.id}::${item.id}`} value={`${provider.id}::${item.id}`}>
                        {item.displayName}
                      </option>
                    ))}
                  </optgroup>
                ))
              )}
            </select>

            {streaming ? (
              <button
                type="button"
                onClick={handleStop}
                title="停止生成"
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-slate-800 text-white transition hover:bg-slate-700"
              >
                <Square size={13} fill="currentColor" />
              </button>
            ) : (
              <button
                type="button"
                onClick={handleSend}
                disabled={!input.trim() || !selection}
                title="发送"
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-teal-600 text-white transition hover:bg-teal-700 disabled:cursor-not-allowed disabled:bg-slate-300"
              >
                <ArrowUp size={16} />
              </button>
            )}
          </div>
          <p className="mt-2 text-center text-xs text-slate-400">{caption}</p>
        </div>
      </div>
    </div>
  )
}

function isAbortError(error: unknown): boolean {
  return typeof error === 'object' && error !== null && (error as { name?: string }).name === 'AbortError'
}

/** 余额展示:CNY 用 ¥、USD 用 $,其余币种跟在数字后面 */
function formatBalance(balance: BalanceInfo): string {
  const amount = Number(balance.amount)
  const text = Number.isFinite(amount) ? amount.toFixed(2) : String(balance.amount)
  if (balance.currency === 'CNY') return `¥${text}`
  if (balance.currency === 'USD') return `$${text}`
  return `${text} ${balance.currency}`
}

export default ChatPage
