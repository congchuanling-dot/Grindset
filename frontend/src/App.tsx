import { Route, Routes } from 'react-router-dom'
import RequireAuth from './auth/RequireAuth'
import AppLayout from './layouts/AppLayout'
import AdminPricingPage from './pages/AdminPricingPage'
import AuthCallbackPage from './pages/AuthCallbackPage'
import ChatPage from './pages/ChatPage'
import InterviewPage from './pages/InterviewPage'
import InterviewRecordsPage from './pages/InterviewRecordsPage'
import LoginPage from './pages/LoginPage'
import InterviewRoomPage from './pages/InterviewRoomPage'
import QuestionDetailPage from './pages/QuestionDetailPage'
import CodingListPage from './pages/questions/CodingListPage'
import CodingWorkspacePage from './pages/questions/CodingWorkspacePage'
import QuestionBankLayout from './pages/questions/QuestionBankLayout'
import QuestionOverview from './pages/questions/QuestionOverview'
import ResumePage from './pages/ResumePage'
import UsagePage from './pages/UsagePage'
import WalletPage from './pages/WalletPage'

function App() {
  return (
    <Routes>
      {/* 登录页:谁都能访问 */}
      <Route path="/login" element={<LoginPage />} />
      {/* 管理后台:独立页面、独立登录,不套用户登录态和侧边栏 */}
      <Route path="/admin" element={<AdminPricingPage />} />
      {/* 第三方登录回调:用一次性 code 换令牌,不套 RequireAuth */}
      <Route path="/auth/callback" element={<AuthCallbackPage />} />

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

        {/* 题库:左侧题目列表 + 右侧详情,编程题是独立子模块 */}
        <Route path="/questions" element={<QuestionBankLayout />}>
          <Route index element={<QuestionOverview />} />
          <Route path=":id" element={<QuestionDetailPage />} />
          <Route path="coding" element={<CodingListPage />} />
        </Route>
        {/* 编程题工作台占满右侧,不显示题库左侧列表 */}
        <Route path="/questions/coding/:id" element={<CodingWorkspacePage />} />
        {/* AI 面试:入口配置 + 面试间(创建后直接进面试间) */}
        <Route path="/interview" element={<InterviewPage />} />
        {/* 面试记录:按简历筛选,进入详情查看复盘 */}
        <Route path="/records" element={<InterviewRecordsPage />} />
        {/* Token 用量:汇总各业务场景的模型调用 */}
        <Route path="/usage" element={<UsagePage />} />

        {/* 我的钱包:余额概览 + 流水 */}
        <Route path="/wallet" element={<WalletPage />} />
      </Route>

      {/* 面试间要全屏沉浸,单独放在侧边栏外壳之外 */}
      <Route
        path="/interview/:sessionId"
        element={
          <RequireAuth>
            <InterviewRoomPage />
          </RequireAuth>
        }
      />
    </Routes>
  )
}

export default App
