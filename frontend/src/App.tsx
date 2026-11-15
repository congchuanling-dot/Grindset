import { Route, Routes } from 'react-router-dom'
import RequireAuth from './auth/RequireAuth'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'

function App() {
  return (
    <Routes>
      {/* 登录页:谁都能访问 */}
      <Route path="/login" element={<LoginPage />} />

      {/* 主页:用 RequireAuth 包住,没登录会被踢到 /login */}
      <Route
        path="/"
        element={
          <RequireAuth>
            <HomePage />
          </RequireAuth>
        }
      />
    </Routes>
  )
}

export default App
