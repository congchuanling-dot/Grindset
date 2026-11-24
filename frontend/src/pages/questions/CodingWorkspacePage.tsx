import { Navigate, useParams } from 'react-router-dom'
import CodingWorkspace from '../CodingWorkspace'

/** 编程题工作台路由外壳:从 URL 取题号,渲染全宽的左右分栏判题页 */
function CodingWorkspacePage() {
  const { id } = useParams<{ id: string }>()
  if (!id) {
    return <Navigate to="/questions/coding" replace />
  }
  return <CodingWorkspace questionId={id} />
}

export default CodingWorkspacePage
