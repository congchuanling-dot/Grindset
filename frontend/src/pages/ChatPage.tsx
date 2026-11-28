import { isAbortError } from '../api/sse'
import {
  ArrowUp,
  Loader2,
  PanelLeft,
  Pencil,
  Plus,
  Sparkles,
  Square,
  Trash2,
  X,
} from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  deleteAgentConversation,
  getAgentConversation,
  listAgentConversations,
  renameAgentConversation,
  streamAgentChat,
  type AgentConversationSummary,
  type AgentHistoryMessage,
  type AgentToolStep,
} from '../api/agent'
import { fetchModels, type ProviderInfo } from '../api/chat'
import AgentToolCard from '../components/AgentToolCard'
import VoiceInputButton from '../components/VoiceInputButton'
import { useVoiceInput } from '../hooks/useVoiceInput'
import { errMessage } from '../utils/error'

interface Message {
  /** 本地消息用 local- 前缀,历史消息用后端 ID */
  id: string
  role: 'user' | 'assistant'
  content: string
  /** 这条回复是不是报错信息,报错用红色展示 */
  error?: boolean
  /** 生成被中断(点停止 / 关页面),没有正文时只显示一行提示 */
  interrupted?: boolean
  /** Agent 本轮调用过的工具,按执行顺序排列 */
  steps?: AgentToolStep[]
  /** 当前阶段提示,回答开始输出后清空 */
  status?: string
}

const SUGGESTIONS = ['分析一下我的薄弱点', '我最近面试表现怎么样', '我该优先刷哪些题']

/**
 * 首页 AI 助手。
 *
 * <p>左侧是会话列表(新建 / 切换 / 重命名 / 删除),右侧是对话区。
 * 当前会话 ID 放在 URL 的 ?c= 参数里,刷新页面也能恢复到同一段对话。</p>
 */
function ChatPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const urlConversationId = searchParams.get('c')

  const [conversations, setConversations] = useState<AgentConversationSummary[]>([])
  const [listLoading, setListLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [listOpen, setListOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingTitle, setEditingTitle] = useState('')

  const [activeId, setActiveId] = useState<string | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [detailLoading, setDetailLoading] = useState(false)

  const [input, setInput] = useState('')
  const [providers, setProviders] = useState<ProviderInfo[]>([])
  const [selection, setSelection] = useState('')
  const [modelsLoading, setModelsLoading] = useState(true)
  const [modelsError, setModelsError] = useState('')
  const [streamingId, setStreamingId] = useState<string | null>(null)

  /** 语音识别错误提示,显示在输入框下方 */
  const [voiceError, setVoiceError] = useState('')

  const listRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const abortRef = useRef<AbortController | null>(null)
  const idRef = useRef(0)
  /** 开始录音时的输入内容,实时结果总是"原内容 + 识别文本" */
  const voiceBaseRef = useRef('')
  /** Escape 取消重命名时,input 失焦仍会触发 commit,用标记跳过这一次 */
  const cancelRenameRef = useRef(false)
  /**
   * 当前会话 ID 的同步副本。
   * 新建会话时 onConversation 会同时改 activeId 和 URL,而路由更新可能先于 state 提交触发 effect;
   * 用 ref 判断可以避免"URL 已经是新会话、state 还是 null"时误加载详情,把正在流式输出的消息清掉。
   */
  const activeIdRef = useRef<string | null>(null)

  const streaming = streamingId !== null

  /** 同步更新当前会话,保证 URL effect 读到的永远是最新值 */
  function setActiveConversation(id: string | null) {
    activeIdRef.current = id
    setActiveId(id)
  }

  function nextId() {
    idRef.current += 1
    return `local-${idRef.current}`
  }

  /** 拉会话列表。首轮结束、删除、重命名后都靠它把侧边栏校准到服务端状态 */
  const refreshConversations = useCallback(async () => {
    try {
      const list = await listAgentConversations()
      setConversations(list)
      setListError('')
    } catch (error) {
      setListError(errMessage(error, '加载会话列表失败'))
    } finally {
      setListLoading(false)
    }
  }, [])

  // 进页面拉一次会话列表和可用模型
  useEffect(() => {
    void refreshConversations()
  }, [refreshConversations])

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
        setModelsError(errMessage(error, '加载模型列表失败'))
      })
      .finally(() => {
        if (!cancelled) setModelsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  /** 语音结果写回输入框,并同步 textarea 高度 */
  function applyVoiceText(text: string) {
    setInput(text)
    requestAnimationFrame(() => {
      const element = inputRef.current
      if (!element) return
      element.style.height = 'auto'
      element.style.height = `${Math.min(element.scrollHeight, 160)}px`
    })
  }

  const voice = useVoiceInput({
    // 首页 Agent 单次 60 秒;其他页面复用 Hook 时传各自场景的值
    maxSeconds: 60,
    onPartial: (text) => applyVoiceText(voiceBaseRef.current + text),
    onFinal: (text) => {
      applyVoiceText(voiceBaseRef.current + text)
      inputRef.current?.focus()
    },
    onError: (message) => setVoiceError(message),
  })

  function handleVoiceToggle() {
    if (voice.state === 'idle') {
      setVoiceError('')
      voiceBaseRef.current = input
      void voice.start()
    } else if (voice.state === 'recording') {
      void voice.stop()
    }
  }

  // URL 里的会话变化时加载详情;新建会话时清空对话区
  useEffect(() => {
    if (!urlConversationId) {
      setActiveConversation(null)
      setMessages([])
      setDetailLoading(false)
      return
    }
    // 刚发完消息时 onConversation 会同时更新会话和 URL,这里不能重复拉一次。
    // 必须用 ref 比较:路由更新可能先于 state 提交,用 activeId 会读到旧值
    if (urlConversationId === activeIdRef.current) {
      return
    }

    let cancelled = false
    setDetailLoading(true)
    setMessages([])
    getAgentConversation(urlConversationId)
      .then((detail) => {
        if (cancelled) return
        setActiveConversation(detail.id)
        setMessages(detail.messages.map(toMessage))
        // 标题可能刚被 LLM 更新,顺手同步到侧边栏
        setConversations((prev) =>
          prev.map((item) => (item.id === detail.id ? { ...item, title: detail.title } : item)),
        )
      })
      .catch((error: unknown) => {
        if (cancelled) return
        setListError(errMessage(error, '加载会话失败'))
        // 会话不存在或无权限时,退回新会话状态,避免 URL 卡在坏 ID 上
        setActiveConversation(null)
        setMessages([])
        setSearchParams({}, { replace: true })
      })
      .finally(() => {
        if (!cancelled) setDetailLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [urlConversationId, setSearchParams])

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

  function updateMessage(id: string, patch: Partial<Message>) {
    setMessages((prev) => prev.map((item) => (item.id === id ? { ...item, ...patch } : item)))
  }

  function appendStep(id: string, step: AgentToolStep) {
    setMessages((prev) =>
      prev.map((item) =>
        item.id === id ? { ...item, steps: [...(item.steps ?? []), step] } : item,
      ),
    )
  }

  function updateStep(messageId: string, stepId: string, patch: Partial<AgentToolStep>) {
    setMessages((prev) =>
      prev.map((item) =>
        item.id === messageId
          ? {
              ...item,
              steps: (item.steps ?? []).map((step) =>
                step.id === stepId ? { ...step, ...patch } : step,
              ),
            }
          : item,
      ),
    )
  }

  /** 正文开始输出后,阶段提示就没用了 */
  function appendDelta(id: string, text: string) {
    setMessages((prev) =>
      prev.map((item) =>
        item.id === id
          ? { ...item, content: item.content + text, status: undefined }
          : item,
      ),
    )
  }

  async function send(text: string, displayText?: string) {
    if (streaming || !selection || detailLoading) return

    const [provider, model] = selection.split('::')
    const assistantId = nextId()
    setMessages((prev) => [
      ...prev,
      { id: nextId(), role: 'user', content: displayText ?? text },
      { id: assistantId, role: 'assistant', content: '' },
    ])
    setInput('')
    if (inputRef.current) {
      inputRef.current.style.height = 'auto'
    }
    setListOpen(false)

    const controller = new AbortController()
    abortRef.current = controller
    setStreamingId(assistantId)
    let aborted = false

    try {
      await streamAgentChat(
        {
          conversationId: activeId ?? undefined,
          provider,
          model,
          message: text,
          displayMessage: displayText,
        },
        {
          onConversation: (id, title) => {
            // 新会话立刻出现在侧边栏,并把 URL 指过去;已有会话只更新标题
            setActiveConversation(id)
            setSearchParams({ c: id }, { replace: true })
            setConversations((prev) => {
              const exists = prev.some((item) => item.id === id)
              if (exists) {
                return prev.map((item) => (item.id === id ? { ...item, title } : item))
              }
              return [{ id, title, messageCount: 1, lastMessageAt: '', createdAt: '' }, ...prev]
            })
          },
          onDone: (id, title) => {
            setConversations((prev) =>
              prev.map((item) => (item.id === id ? { ...item, title: title || item.title } : item)),
            )
          },
          onStatus: (status) => updateMessage(assistantId, { status }),
          onToolCall: (step) => appendStep(assistantId, step),
          onToolResult: (id, ok, summary, data) =>
            updateStep(assistantId, id, { status: ok ? 'done' : 'failed', summary, data }),
          onDelta: (delta) => appendDelta(assistantId, delta),
          onError: (message) =>
            updateMessage(assistantId, { content: message, error: true, status: undefined }),
        },
        controller.signal,
      )
    } catch (error) {
      if (isAbortError(error)) {
        aborted = true
      } else {
        updateMessage(assistantId, {
          content: errMessage(error, '对话失败,请重试'),
          error: true,
        })
      }
    } finally {
      // 主动停止且一个字都没生成时,给一条明确提示,不留空气泡
      if (aborted) {
        setMessages((prev) =>
          prev.map((item) =>
            item.id === assistantId && !item.content && (item.steps?.length ?? 0) === 0
              ? { ...item, interrupted: true }
              : item,
          ),
        )
      }
      setStreamingId(null)
      abortRef.current = null
      // 服务端可能刚更新了标题和最后消息时间,重新拉一次列表
      void refreshConversations()
    }
  }

  function handleSend() {
    const text = input.trim()
    if (!text) return
    // 正在录音/识别时先取消,避免结果回填到已经发送的输入框
    voice.cancel()
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

  /** 新建会话:不清空服务端数据,只是回到一个还没有 ID 的空白对话 */
  function handleNewChat() {
    if (streaming) return
    setActiveConversation(null)
    setSearchParams({}, { replace: true })
    setMessages([])
    setListOpen(false)
    inputRef.current?.focus()
  }

  function handleSelectConversation(id: string) {
    if (streaming || id === activeId) {
      setListOpen(false)
      return
    }
    setSearchParams({ c: id })
    setListOpen(false)
  }

  function startRename(item: AgentConversationSummary) {
    cancelRenameRef.current = false
    setEditingId(item.id)
    setEditingTitle(item.title)
  }

  async function commitRename() {
    if (cancelRenameRef.current) {
      cancelRenameRef.current = false
      return
    }
    const id = editingId
    if (!id) return
    const title = editingTitle.trim()
    setEditingId(null)

    const previous = conversations.find((item) => item.id === id)?.title ?? ''
    if (!title || title === previous) return

    // 先乐观更新,失败再回滚
    setConversations((prev) =>
      prev.map((item) => (item.id === id ? { ...item, title } : item)),
    )
    try {
      await renameAgentConversation(id, title)
    } catch (error) {
      setListError(errMessage(error, '重命名失败'))
      setConversations((prev) =>
        prev.map((item) => (item.id === id ? { ...item, title: previous } : item)),
      )
    }
  }

  async function handleDelete(id: string) {
    if (streaming) return
    if (!window.confirm('删除后不可恢复，确定删除这个会话吗？')) return
    try {
      await deleteAgentConversation(id)
      setConversations((prev) => prev.filter((item) => item.id !== id))
      if (activeId === id) {
        handleNewChat()
      }
    } catch (error) {
      setListError(errMessage(error, '删除会话失败'))
    }
  }

  const activeTitle = conversations.find((item) => item.id === activeId)?.title ?? 'AI 助手'

  const caption = voiceError
    ? voiceError
    : modelsError
      ? modelsError
      : !modelsLoading && providers.length === 0
        ? '服务端未配置任何模型的 API Key，请设置环境变量后重启后端'
        : 'AI 回复由所选模型实时生成，内容仅供参考'

  return (
    <div className="relative flex h-full overflow-hidden bg-white">
      {/* 会话面板:窄屏是抽屉,md 以上固定在左侧 */}
      <aside
        className={`absolute inset-y-0 left-0 z-30 flex w-64 shrink-0 flex-col border-r border-slate-200/80 bg-slate-50 transition-transform md:static md:z-auto md:translate-x-0 ${
          listOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="flex h-14 shrink-0 items-center gap-2 px-4">
          <span className="text-[15px] font-semibold tracking-tight text-slate-900">对话记录</span>
          <button
            type="button"
            onClick={() => setListOpen(false)}
            title="收起"
            className="ml-auto rounded p-1 text-slate-400 hover:bg-slate-200/60 md:hidden"
          >
            <X size={16} />
          </button>
        </div>

        <div className="px-3 pb-2">
          <button
            type="button"
            onClick={handleNewChat}
            disabled={streaming}
            className="flex w-full items-center justify-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 transition hover:border-brand-300 hover:text-brand-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <Plus size={15} />
            新建会话
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
          {listLoading ? (
            <div className="flex items-center gap-2 px-2 py-3 text-xs text-slate-400">
              <Loader2 size={13} className="animate-spin" />
              加载会话…
            </div>
          ) : conversations.length === 0 ? (
            <p className="px-2 py-3 text-xs leading-5 text-slate-400">
              还没有历史会话，发送第一条消息后会自动保存。
            </p>
          ) : (
            <ul className="space-y-0.5">
              {conversations.map((item) => (
                <li
                  key={item.id}
                  className={`group flex items-center gap-1 rounded-xl px-3 py-2 text-sm transition ${
                    item.id === activeId
                      ? 'bg-slate-100 font-medium text-slate-900'
                      : 'text-slate-500 hover:bg-slate-100'
                  }`}
                >
                  {editingId === item.id ? (
                    <input
                      autoFocus
                      value={editingTitle}
                      maxLength={64}
                      onChange={(e) => setEditingTitle(e.target.value)}
                      onBlur={() => void commitRename()}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault()
                          void commitRename()
                        } else if (e.key === 'Escape') {
                          cancelRenameRef.current = true
                          setEditingId(null)
                        }
                      }}
                      className="min-w-0 flex-1 rounded border border-brand-300 bg-white px-1.5 py-0.5 text-xs text-slate-700 outline-none"
                    />
                  ) : (
                    <button
                      type="button"
                      onClick={() => handleSelectConversation(item.id)}
                      title={item.title}
                      className="min-w-0 flex-1 truncate text-left"
                    >
                      {item.title}
                    </button>
                  )}

                  {editingId !== item.id && (
                    <span className="hidden shrink-0 items-center gap-0.5 group-hover:flex">
                      <button
                        type="button"
                        title="重命名"
                        disabled={streaming}
                        onClick={() => startRename(item)}
                        className="rounded p-1 text-slate-400 transition hover:bg-white hover:text-brand-600 disabled:opacity-40"
                      >
                        <Pencil size={12} />
                      </button>
                      <button
                        type="button"
                        title="删除"
                        disabled={streaming}
                        onClick={() => void handleDelete(item.id)}
                        className="rounded p-1 text-slate-400 transition hover:bg-rose-50 hover:text-rose-500 disabled:opacity-40"
                      >
                        <Trash2 size={12} />
                      </button>
                    </span>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      </aside>

      {listOpen && (
        <div
          className="absolute inset-0 z-20 bg-slate-900/20 md:hidden"
          onClick={() => setListOpen(false)}
        />
      )}

      <section className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 shrink-0 items-center gap-2 border-b border-slate-200/80 px-4">
          <button
            type="button"
            onClick={() => setListOpen(true)}
            title="会话记录"
            className="rounded-lg p-1.5 text-slate-500 transition hover:bg-slate-100 md:hidden"
          >
            <PanelLeft size={18} />
          </button>
          <span className="truncate text-sm font-medium text-slate-900">{activeTitle}</span>
          {detailLoading && <Loader2 size={14} className="animate-spin text-brand-500" />}
        </header>

        {listError && (
          <div className="flex items-start gap-2 border-b border-rose-100 bg-rose-50 px-4 py-2 text-xs text-rose-700">
            <span className="flex-1">{listError}</span>
            <button
              type="button"
              onClick={() => setListError('')}
              className="text-rose-400 hover:text-rose-600"
            >
              关闭
            </button>
          </div>
        )}

        <div ref={listRef} className="min-h-0 flex-1 overflow-y-auto">
          {detailLoading ? (
            <div className="flex h-full items-center justify-center">
              <Loader2 size={20} className="animate-spin text-brand-600" />
            </div>
          ) : messages.length === 0 ? (
            <div className="flex h-full flex-col items-center justify-center px-6 text-center">
              <span className="flex h-12 w-12 items-center justify-center rounded-full bg-brand-50 text-brand-600">
                <Sparkles size={22} />
              </span>
              <h1 className="mt-4 text-xl font-semibold text-slate-800">
                你想让我帮你复习什么？
              </h1>
              <p className="mt-2 max-w-sm text-sm leading-6 text-slate-500">
                我可以直接查你的刷题记录和面试记录，再给可执行的建议。
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
                    className="rounded-full border border-slate-200 px-3 py-1.5 text-xs text-slate-600 transition hover:border-brand-300 hover:text-brand-700"
                  >
                    {item}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <div className="mx-auto w-full max-w-3xl space-y-5 px-6 py-8">
              {messages.map((message, index) => {
                const steps = message.steps ?? []
                // 选择卡片放在回答正文之后:先让用户看到现状说明,再看到可选项
                const toolSteps = steps.filter((step) => !isSelectorStep(step))
                const selectorSteps = steps.filter(isSelectorStep)
                const pending =
                  message.role === 'assistant'
                  && !message.content
                  && steps.length === 0
                  && !message.interrupted
                return (
                  <div
                    key={message.id}
                    className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'} ${
                      // 只给最新一条做淡入，表明"刚刚新增了什么"
                      index === messages.length - 1 ? 'anim-fade-in' : ''
                    }`}
                  >
                    <div
                      className={`flex flex-col gap-2 ${
                        message.role === 'user' ? 'max-w-[80%] items-end' : 'max-w-[85%] items-start'
                      }`}
                    >
                      {toolSteps.length > 0 && (
                        <div className="w-full space-y-1.5">
                          {toolSteps.map((step) => (
                            <AgentToolCard key={step.id} step={step} />
                          ))}
                        </div>
                      )}

                      {(pending || message.content) && (
                        <div
                          className={`whitespace-pre-wrap rounded-2xl px-4 py-2.5 text-sm leading-6 ${
                            message.role === 'user'
                              ? 'bg-brand-600 text-white'
                              : message.error
                                ? 'bg-red-50 text-red-600'
                                : 'bg-slate-100 text-slate-700'
                          }`}
                        >
                          {pending ? (
                            <span className="flex items-center gap-1 py-0.5" aria-label="正在思考">
                              <span className="thinking-dot" />
                              <span className="thinking-dot" />
                              <span className="thinking-dot" />
                            </span>
                          ) : (
                            <>
                              {message.content}
                              {message.role === 'assistant' && message.id === streamingId && (
                                <span className="streaming-caret" aria-hidden />
                              )}
                            </>
                          )}
                        </div>
                      )}

                      {selectorSteps.length > 0 && (
                        <div className="w-full space-y-1.5">
                          {selectorSteps.map((step) => (
                            <AgentToolCard
                              key={step.id}
                              step={step}
                              onSelect={
                                streaming
                                  ? undefined
                                  : (prompt, display) => void send(prompt, display)
                              }
                            />
                          ))}
                        </div>
                      )}

                      {message.interrupted && (
                        <p className="px-1 text-xs text-amber-600">生成已中断</p>
                      )}

                      {message.status && !message.content && steps.length > 0 && (
                        <p className="flex items-center gap-1.5 px-1 text-xs text-slate-400">
                          <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-brand-500" />
                          {message.status}
                        </p>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>

        <div className="shrink-0 px-6 pb-6">
          <div className="mx-auto w-full max-w-3xl">
            <div className="flex items-end gap-2 rounded-3xl border border-slate-200/80 bg-white px-4 py-3 shadow-sm transition focus-within:border-brand-500/60 focus-within:shadow-md">
              {/* 附件入口:先占位,后续接上传 */}
              <button
                type="button"
                disabled
                title="上传附件（即将上线）"
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:hover:bg-transparent"
              >
                <Plus size={18} />
              </button>

              <VoiceInputButton
                state={voice.state}
                seconds={voice.seconds}
                disabled={streaming || !selection || detailLoading}
                onClick={handleVoiceToggle}
              />

              <textarea
                ref={inputRef}
                value={input}
                onChange={handleInputChange}
                onKeyDown={handleKeyDown}
                rows={1}
                placeholder={
                  voice.state === 'recording'
                    ? '正在听你说…'
                    : providers.length > 0
                      ? '随心输入，Enter 发送，点击麦克风语音输入'
                      : '请先在服务端配置模型 API Key'
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
                    <optgroup key={provider.id} label={provider.name}>
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
                  className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand-600 text-white transition hover:bg-brand-700 disabled:cursor-not-allowed disabled:bg-slate-300"
                >
                  <ArrowUp size={16} />
                </button>
              )}
            </div>
            <p className="mt-2 text-center text-xs text-slate-400">{caption}</p>
          </div>
        </div>
      </section>
    </div>
  )
}

/** 带选择卡片数据的工具步骤单独渲染到回答下方 */
function isSelectorStep(step: AgentToolStep): boolean {
  return step.data?.card === 'selector'
}

/** 把后端的展示消息转成页面内部的 Message */
function toMessage(message: AgentHistoryMessage): Message {
  return {
    id: message.id,
    role: message.role,
    content: message.content,
    error: message.status === 2,
    interrupted: message.status === 3,
    steps: message.steps.map((step) => ({
      id: step.id,
      name: step.name,
      label: step.label,
      args: step.args,
      status: step.ok ? 'done' : 'failed',
      summary: step.summary,
      data: step.data,
    })),
  }
}

export default ChatPage
