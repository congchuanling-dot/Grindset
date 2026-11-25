import { useCallback, useState } from 'react'
import type { Participant } from '../types/interview-room'

/**
 * 面试间参与者列表。
 *
 * 第一版只有「自己 + AI 面试官」两位;后续真人连麦时,只要通过 addParticipant
 * 往列表里加 peer,宫格布局会自动重排,不需要改渲染层。
 */
export function useRoomParticipants(initial: Participant[]) {
  const [participants, setParticipants] = useState<Participant[]>(initial)

  const addParticipant = useCallback((participant: Participant) => {
    setParticipants((prev) =>
      prev.some((item) => item.id === participant.id) ? prev : [...prev, participant],
    )
  }, [])

  const removeParticipant = useCallback((id: string) => {
    setParticipants((prev) => prev.filter((item) => item.id !== id))
  }, [])

  const updateParticipant = useCallback((id: string, patch: Partial<Participant>) => {
    setParticipants((prev) => prev.map((item) => (item.id === id ? { ...item, ...patch } : item)))
  }, [])

  return { participants, addParticipant, removeParticipant, updateParticipant }
}
