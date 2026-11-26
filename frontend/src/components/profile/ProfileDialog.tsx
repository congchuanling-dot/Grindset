import { Loader2, X } from 'lucide-react'
import { useState } from 'react'
import { updateProfile } from '../../api/auth'
import { useToast } from '../toast/ToastProvider'
import { errMessage } from '../../utils/error'

interface ProfileDialogProps {
  initialNickname: string
  onClose: () => void
  onSaved: (nickname: string) => void
}

/**
 * 个人资料弹窗:目前只支持改昵称,与后端 AuthService.updateNickname 校验保持一致。
 * 由父组件在打开时挂载,关闭即卸载,表单状态天然重置。
 */
function ProfileDialog({ initialNickname, onClose, onSaved }: ProfileDialogProps) {
  const toast = useToast()
  const [nickname, setNickname] = useState(initialNickname)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const value = nickname.trim()
    // 按 code point 计长度,和后端一致;emoji 算一个字符
    const length = [...value].length
    if (length < 2 || length > 20) {
      setError('昵称长度需为 2-20 个字符')
      return
    }

    setSaving(true)
    setError('')
    try {
      const info = await updateProfile(value)
      onSaved(info.nickname)
      toast.success('昵称已更新')
      onClose()
    } catch (err) {
      setError(errMessage(err, '保存失败，请稍后重试'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 px-4"
      onMouseDown={onClose}
    >
      <div
        className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-base font-semibold text-slate-800">个人资料</h2>
          <button
            type="button"
            onClick={onClose}
            title="关闭"
            className="rounded-md p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
          >
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <label className="block">
            <span className="mb-1 block text-xs text-slate-500">昵称</span>
            <input
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              maxLength={20}
              autoFocus
              className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-800 outline-none focus:border-teal-400"
              placeholder="2-20 个字符"
            />
          </label>

          {error && <p className="text-xs text-rose-500">{error}</p>}

          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg px-3 py-2 text-sm text-slate-500 transition hover:bg-slate-100"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={saving}
              className="flex items-center gap-1.5 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-teal-700 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {saving && <Loader2 size={14} className="animate-spin" />}
              保存
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default ProfileDialog
