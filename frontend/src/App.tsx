import { Route, Routes } from 'react-router-dom'
import RequireAuth from './auth/RequireAuth'
import AppLayout from './layouts/AppLayout'
import ChatPage from './pages/ChatPage'
import InterviewPage from './pages/InterviewPage'
import InterviewRecordsPage from './pages/InterviewRecordsPage'
import InterviewSessionPage from './pages/InterviewSessionPage'
import KnowledgePage from './pages/KnowledgePage'
import LoginPage from './pages/LoginPage'
import PlaceholderPage from './pages/PlaceholderPage'
import ResumePage from './pages/ResumePage'
import WrongQuestionsPage from './pages/WrongQuestionsPage'

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

        {/* 简历上传解析 */}
        <Route path="/resume" element={<ResumePage />} />

        {/* 以下栏目先占位,后续逐个实现 */}
        <Route
          path="/questions"
          element={
            <PlaceholderPage
              title="题库列表"
              description="按知识点浏览题目和历史答题记录。功能建设中。"
            />
          }
        />
        {/* AI 面试:入口配置 + 面试对话页 */}
        <Route path="/interview" element={<InterviewPage />} />
        <Route path="/interview/:sessionId" element={<InterviewSessionPage />} />
        {/* 面试记录:按简历筛选,进入详情查看复盘 */}
        <Route path="/records" element={<InterviewRecordsPage />} />
        {/* 掌握度画像:按知识点沉淀历次面试评分 */}
        <Route path="/knowledge" element={<KnowledgePage />} />
        {/* 错题本:自动收录低分题,支持单题重练 */}
        <Route path="/wrong-questions" element={<WrongQuestionsPage />} />
      </Route>
    </Routes>
  )
}

export default App
