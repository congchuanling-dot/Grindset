import { Route, Routes } from 'react-router-dom'
import RequireAuth from './auth/RequireAuth'
import AppLayout from './layouts/AppLayout'
import ChatPage from './pages/ChatPage'
import LoginPage from './pages/LoginPage'
import PlaceholderPage from './pages/PlaceholderPage'

function App() {
  return (
    <Routes>
      {/* 登录页:谁都能访问 */}
      <Route path="/login" element={<LoginPage />} />

      {/* 登录后才可见的页面,统一套一层侧边栏外壳 */}
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        {/* 登录后默认进入 AI 助手对话页 */}
        <Route path="/" element={<ChatPage />} />

        {/* 以下栏目先占位,后续逐个实现 */}
        <Route
          path="/resume"
          element={
            <PlaceholderPage
              title="上传简历"
              description="上传 PDF / Word 简历，AI 解析出技能清单与项目经历。功能建设中。"
            />
          }
        />
        <Route
          path="/questions"
          element={
            <PlaceholderPage
              title="题库列表"
              description="按知识点浏览题目和历史答题记录。功能建设中。"
            />
          }
        />
        <Route
          path="/interview"
          element={
            <PlaceholderPage
              title="AI 面试"
              description="AI 扮演面试官多轮追问，结束后生成复盘报告。功能建设中。"
            />
          }
        />
      </Route>
    </Routes>
  )
}

export default App
