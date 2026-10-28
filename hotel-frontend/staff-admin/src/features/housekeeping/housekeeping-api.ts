import { apiFetch } from '../../lib/api/client'
import type { PageResult, Room } from '../../types/reservation'
import type { TurnoverTask } from '../../types/stay'
import type {
  CleaningRecord,
  HousekeepingTaskView,
  HousekeepingPageResult,
  RoomInspection,
  RoomWorkOrder,
} from '../../types/housekeeping'

export function listTurnovers(page: number, pageSize: number, status?: number, roomId?: number | null) {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  if (status != null) params.set('status', String(status))
  if (roomId != null) params.set('roomId', String(roomId))
  return apiFetch<HousekeepingPageResult>(`/api/admin/turnover-tasks?${params}`)
}

export const getTurnover = (id: number) =>
  apiFetch<TurnoverTask>(`/api/admin/turnover-tasks/${id}`)

export const getRoom = (id: number) => apiFetch<Room>(`/api/admin/rooms/${id}`)

export function listCleaningRecords(stayId: number) {
  return apiFetch<PageResult<CleaningRecord>>(
    `/api/housekeeping/cleaning-records?stayId=${stayId}&page=1&pageSize=100`,
  )
}

export function listInspections(roomId: number) {
  return apiFetch<PageResult<RoomInspection>>(
    `/api/housekeeping/inspections?roomId=${roomId}&page=1&pageSize=100`,
  )
}

export function listBlockingWorkOrders(roomId: number) {
  return apiFetch<PageResult<RoomWorkOrder>>(
    `/api/work-orders?roomId=${roomId}&status=0&page=1&pageSize=100`,
  )
}

export const acceptTurnover = (id: number) =>
  apiFetch<TurnoverTask>(`/api/admin/turnover-tasks/${id}/accept`, { method: 'POST' })

export const completeTurnover = (id: number, note: string) =>
  apiFetch<TurnoverTask>(`/api/admin/turnover-tasks/${id}/complete`, {
    method: 'POST',
    body: JSON.stringify({ note: note.trim() || null }),
  })

export function decideInspection(
  id: number,
  passed: boolean,
  failureReason: string | null,
  notes: string,
) {
  return apiFetch<RoomInspection>(`/api/housekeeping/inspections/${id}/decision`, {
    method: 'POST',
    body: JSON.stringify({
      passed,
      failureReason: passed ? null : failureReason,
      notes: notes.trim() || null,
    }),
  })
}

export const releaseRoom = (roomId: number) =>
  apiFetch<void>(`/api/admin/rooms/${roomId}/available`, { method: 'POST' })

export const getHousekeepingTask = (taskId: number) =>
  apiFetch<HousekeepingTaskView>(`/api/tasks/${taskId}`)

export const claimHousekeepingTask = (taskId: number) =>
  apiFetch<HousekeepingTaskView>(`/api/tasks/${taskId}/claim`, { method: 'POST' })

export const acknowledgeHousekeepingTask = (taskId: number) =>
  apiFetch<HousekeepingTaskView>(`/api/tasks/${taskId}/accept`, { method: 'POST' })

export const completeReworkCleaning = (taskId: number, notes: string) =>
  apiFetch<CleaningRecord>(`/api/housekeeping/tasks/${taskId}/complete`, {
    method: 'POST',
    body: JSON.stringify({ notes: notes.trim() || null, force: false }),
  })

