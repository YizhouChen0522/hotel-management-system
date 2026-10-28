import { apiFetch } from '../../lib/api/client'
import type { PageResult, Reservation, Room } from '../../types/reservation'
import type {
  Folio,
  Stay,
  StayAssignment,
  StayAdjustment,
  StayGuest,
  RoomChangeOptions,
  TurnoverTask,
} from '../../types/stay'

export function listStays(page: number, pageSize: number, status?: number) {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  if (status != null) params.set('status', String(status))
  return apiFetch<PageResult<Stay>>(`/api/stays?${params}`)
}

export const getStay = (id: number) => apiFetch<Stay>(`/api/stays/${id}`)

export function getStayByBooking(bookingId: number) {
  return apiFetch<PageResult<Stay>>(`/api/stays?bookingId=${bookingId}&page=1&pageSize=1`)
}

export const getStayGuests = (id: number) => apiFetch<StayGuest[]>(`/api/stays/${id}/guests`)

export const getStayAssignments = (id: number) =>
  apiFetch<StayAssignment[]>(`/api/stays/${id}/assignments`)

export const getStayFolio = (id: number) => apiFetch<Folio>(`/api/stays/${id}/folio`)

export const checkInReservation = (bookingId: number) =>
  apiFetch<Stay>(`/api/bookings/${bookingId}/check-in`, { method: 'POST' })

export const confirmGuestRegistration = (bookingId: number) =>
  apiFetch(`/api/admin/bookings/${bookingId}/guest-registration/confirm`, { method: 'POST' })

export const checkOutStay = (stayId: number) =>
  apiFetch<Stay>(`/api/stays/${stayId}/check-out`, { method: 'POST' })

export const getRoomChangeOptions = (stayId: number) =>
  apiFetch<RoomChangeOptions>(`/api/stays/${stayId}/room-change-options`)

export function changeStayRoom(stayId: number, newRoomId: number, reason: string) {
  return apiFetch<Stay>(`/api/stays/${stayId}/room-change`, {
    method: 'POST',
    body: JSON.stringify({ newRoomId, reason: reason.trim() || null }),
  })
}

export const listStayAdjustments = (stayId: number) =>
  apiFetch<StayAdjustment[]>(`/api/admin/stays/${stayId}/stay-adjustments`)

export function applyStayAdjustment(
  stayId: number,
  type: 'EXTENSION' | 'EARLY_CHECKOUT' | 'LATE_CHECKOUT',
  reason: string,
  newEnd?: string,
) {
  return apiFetch<StayAdjustment>(`/api/admin/stays/${stayId}/stay-adjustments`, {
    method: 'POST',
    body: JSON.stringify({
      type,
      newEnd: type === 'EXTENSION' ? newEnd : null,
      reason: reason.trim(),
      requestKey: crypto.randomUUID().replaceAll('-', ''),
    }),
  })
}

export const getRoom = (roomId: number) => apiFetch<Room>(`/api/admin/rooms/${roomId}`)

export const getBooking = (bookingId: number) =>
  apiFetch<Reservation>(`/api/admin/bookings/${bookingId}`)

export function listTurnovers(roomId: number) {
  return apiFetch<PageResult<TurnoverTask>>(
    `/api/admin/turnover-tasks?roomId=${roomId}&page=1&pageSize=100`,
  )
}

export function recordFolioPayment(
  folioId: number,
  amount: number,
  paymentMethod: string,
  referenceNo: string,
) {
  return apiFetch(`/api/admin/billing/folios/${folioId}/payments`, {
    method: 'POST',
    body: JSON.stringify({
      amount,
      paymentMethod,
      referenceNo: referenceNo.trim() || null,
      note: 'Front desk folio settlement',
      idempotencyKey: crypto.randomUUID(),
    }),
  })
}

