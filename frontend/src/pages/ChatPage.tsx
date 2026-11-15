import { ArrowUp, Plus, Sparkles } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'

interface Message {
  id: number
  role: 'user' | 'assistant'
  content: string
}

// 模型选择先占位,后端接多 provider 后再换成真实列表
const MODEL_OPTIONS = ['DeepSeek-V3', 'GPT-4o', 'Claude 3.5']
const SUGGESTIONS = ['不知道怎么开始？', '帮我出 3 道 Redis 题', '知识点掌握度是什么？']

function ChatPage() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [model, setModel] = useState(MODEL_OPTIONS[0])
  const listRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)

  // 新消息或输入变化后滚到底部
  useEffect(() => {
    const list = listRef.current
    if (list) {
      list.scrollTop = list.scrollHeight
    }
  }, [messages])

  function send(text: string) {
    setMessages((prev) => [
      ...prev,
      { id: Date.now(), role: 'user', content: text },
      // AI 接口没接,先占位;接入后换成真实请求
      {
        id: Date.now() + 1,
        role: 'assistant',
        content:
          'AI 服务还在接入中。接好之后我会按你的简历和薄弱知识点出题、答疑，也能带你走完整个复习流程。',
      },
    ])
    setInput('')
    if (inputRef.current) {
      inputRef.current.style.height = 'auto'
    }
  }

  function handleSend() {
    const text = input.trim()
    if (!text) return
    send(text)
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
            {messages.map((message) => (
              <div
                key={message.id}
                className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}
              >
                <div
                  className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-sm leading-6 ${
                    message.role === 'user'
                      ? 'bg-teal-600 text-white'
                      : 'bg-slate-100 text-slate-700'
                  }`}
                >
                  {message.content}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="shrink-0 px-4 pb-5">
        <div className="mx-auto w-full max-w-3xl">
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
              placeholder="随心输入，Enter 发送"
              className="max-h-40 flex-1 resize-none bg-transparent py-1.5 text-sm leading-6 text-slate-800 outline-none placeholder:text-slate-400"
            />

            {/* 模型选择:接多 provider 后换成真实数据 */}
            <select
              value={model}
              onChange={(e) => setModel(e.target.value)}
              title="选择模型"
              className="h-8 shrink-0 cursor-pointer rounded-lg bg-slate-100 px-2 text-xs text-slate-600 outline-none transition hover:bg-slate-200"
            >
              {MODEL_OPTIONS.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </select>

            <button
              type="button"
              onClick={handleSend}
              disabled={!input.trim()}
              title="发送"
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-teal-600 text-white transition hover:bg-teal-700 disabled:cursor-not-allowed disabled:bg-slate-300"
            >
              <ArrowUp size={16} />
            </button>
          </div>
          <p className="mt-2 text-center text-xs text-slate-400">
            当前为占位回复，AI 服务与附件功能待接入
          </p>
        </div>
      </div>
    </div>
  )
}

export default ChatPage
