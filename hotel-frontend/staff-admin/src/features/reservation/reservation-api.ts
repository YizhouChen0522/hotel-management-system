import { apiFetch } from '../../lib/api/client'
import type {
  BookingGuest,
  DepositBalance,
  DepositPayment,
  DepositRefund,
  PageResult,
  PriceSnapshot,
  Reservation,
  Room,
} from '../../types/reservation'

const root = '/api/admin/bookings'

export interface ReservationFilters {
  status?: number
  startDate?: string
  endDate?: string
  page: number
  pageSize: number
}

export function listReservations(filters: ReservationFilters) {
  const params = new URLSearchParams({
    page: String(filters.page),
    pageSize: String(filters.pageSize),
  })
  if (filters.status != null) {
    params.set('status', String(filters.status))
    return apiFetch<PageResult<Reservation>>(`${root}/status?${params}`)
  }
  if (filters.startDate && filters.endDate) {
    params.set('startDate', filters.startDate)
    params.set('endDate', filters.endDate)
    return apiFetch<PageResult<Reservation>>(`${root}/check-in-range?${params}`)
  }
  return apiFetch<PageResult<Reservation>>(`${root}?${params}`)
}

export const getReservation = (id: number) => apiFetch<Reservation>(`${root}/${id}`)
export const getEligibleRooms = (id: number) => apiFetch<Room[]>(`${root}/${id}/eligible-rooms`)
export const getPriceSnapshot = (id: number) => apiFetch<PriceSnapshot>(`${root}/${id}/price-snapshot`)
export const getBookingGuests = (id: number) => apiFetch<BookingGuest[]>(`${root}/${id}/guests`)
export const getDeposit = (id: number) => apiFetch<DepositBalance>(`/api/bookings/${id}/deposit`)
export const getDepositPayments = (id: number) => apiFetch<PageResult<DepositPayment>>(`/api/bookings/${id}/deposit/payments?page=1&pageSize=100`)
export const getDepositRefunds = (id: number) => apiFetch<PageResult<DepositRefund>>(`/api/bookings/${id}/deposit/refunds?page=1&pageSize=100`)

export const approveReservation = (id: number, roomId: number) =>
  apiFetch<Reservation>(`${root}/${id}/approve`, {
    method: 'POST',
    body: JSON.stringify({ assignedRoomId: roomId }),
  })

export const declineReservation = (id: number) =>
  apiFetch<Reservation>(`${root}/${id}/reject`, { method: 'POST' })

export const assignRoom = (id: number, roomId: number, reason: string) =>
  apiFetch<Reservation>(`${root}/${id}/assigned-room`, {
    method: 'PUT',
    body: JSON.stringify({ newRoomId: roomId, reason }),
  })
