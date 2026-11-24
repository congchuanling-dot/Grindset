import { BookOpen, Code2, ListTree, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import CodingFilterPane from './CodingFilterPane'
import QuestionListPane from './QuestionListPane'

/** 题库工作台:左侧题目列表/筛选,右侧详情或列表 */
function QuestionBankLayout() {
  const { pathname } = useLocation()
  const isCoding = pathname.startsWith('/questions/coding')
  const [drawerOpen, setDrawerOpen] = useState(false)

  useEffect(() => {
    setDrawerOpen(false)
  }, [pathname])

  const pane = isCoding ? <CodingFilterPane /> : <QuestionListPane />

  return (
    <div className="flex h-full min-h-0 bg-slate-50">
      <aside className="hidden w-[340px] shrink-0 flex-col border-r border-slate-200 bg-white lg:flex">
        <ModeSwitch isCoding={isCoding} />
        {pane}
      </aside>

      <main className="min-w-0 flex-1 overflow-y-auto">
        <Outlet />
      </main>

      <button
        type="button"
        onClick={() => setDrawerOpen(true)}
        className="fixed bottom-4 left-4 z-30 flex items-center gap-1.5 rounded-full bg-teal-600 px-4 py-2 text-sm font-medium text-white shadow-lg transition hover:bg-teal-700 lg:hidden"
      >
        <ListTree size={15} />
        题目列表
      </button>

      {drawerOpen && (
        <div className="fixed inset-0 z-40 flex lg:hidden">
          <div
            className="absolute inset-0 bg-slate-900/30"
            onClick={() => setDrawerOpen(false)}
          />
          <aside className="relative flex h-full w-[88%] max-w-sm flex-col bg-white shadow-xl">
            <div className="flex shrink-0 items-center justify-between border-b border-slate-100 px-3 py-2.5">
              <span className="text-sm font-medium text-slate-700">题库</span>
              <button
                type="button"
                onClick={() => setDrawerOpen(false)}
                className="rounded-lg p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
                aria-label="关闭"
              >
                <X size={16} />
              </button>
            </div>
            <ModeSwitch isCoding={isCoding} />
            {pane}
          </aside>
        </div>
      )}
    </div>
  )
}

function ModeSwitch({ isCoding }: { isCoding: boolean }) {
  const base = 'flex items-center justify-center gap-1.5 rounded-md px-3 py-1.5 text-xs transition'
  return (
    <div className="shrink-0 border-b border-slate-100 px-3 py-3">
      <div className="grid grid-cols-2 gap-1 rounded-lg bg-slate-100 p-1">
        <Link
          to="/questions"
          className={`${base} ${
            isCoding
              ? 'text-slate-500 hover:text-slate-700'
              : 'bg-white font-medium text-teal-700 shadow-sm'
          }`}
        >
          <BookOpen size={14} />
          问答题
        </Link>
        <Link
          to="/questions/coding"
          className={`${base} ${
            isCoding
              ? 'bg-white font-medium text-teal-700 shadow-sm'
              : 'text-slate-500 hover:text-slate-700'
          }`}
        >
          <Code2 size={14} />
          编程题
        </Link>
      </div>
    </div>
  )
}

export default QuestionBankLayout
