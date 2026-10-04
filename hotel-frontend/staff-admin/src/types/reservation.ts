export interface PageResult<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
  hasNext: boolean
  searchMode: boolean
}

export interface ReservationPolicyBand {
  id: number
  startDays: number
  endDays: number | null
  refundPercent: number
}

export interface ReservationPolicyView {
  policy: { id: number; name: string; versionNo: number; status: string }
  bands: ReservationPolicyBand[]
}

export interface Reservation {
  id: number
  userId: number | null
  bookerGuestProfileId: number | null
  bookerName: string | null
  bookerEmail: string | null
  bookerPhone: string | null
  createdByUserId: number | null
  roomTypeId: number
  roomTypeName: string | null
  reservedRoomId: number | null
  reservedRoomNumber: string | null
  reservationSource: string
  reservationPolicyId: number | null
  cancellationPolicy: ReservationPolicyView | null
  guestCount: number
  checkInDate: string
  checkOutDate: string
  status: number
  totalPrice: number
  createTime: string
  updateTime: string
}

export interface Room {
  id: number
  roomNumber: string
  roomTypeId: number
  roomTypeName: string | null
  floor: number
  status: number
}

export interface GuestProfile {
  id: number
  firstName: string
  lastName: string
  phone: string | null
  email: string | null
  nationality: string | null
  documentType: string | null
  documentNumber: string | null
}

export interface BookingGuest {
  relationId: number
  bookingId: number
  role: 'PRIMARY' | 'ACCOMPANYING'
  guest: GuestProfile
}

export interface DepositBalance {
  received: number
  refunded: number
  transferred: number
  pendingRefund: number
  balance: number
  available: number
  forfeited: number
  externalRefunded: number
}

export interface DepositPayment {
  id: number
  amount: number
  paymentMethod: string
  referenceNo: string | null
  receivedTime: string
  businessDate: string
}

export interface DepositRefund {
  id: number
  amount: number
  currency: string
  status: number
  reason: string
  createTime: string
}

export interface PriceVersion {
  id: number
  versionNo: number
  changeType: string
  reason: string | null
  totalPrice: number
  currency: string
  createTime: string
}

export interface NightlyRate {
  id: number
  stayDate: string
  roomTypeId: number
  rateAmount: number
  rateSource: string
}

export interface PriceSnapshot {
  version: PriceVersion | null
  nightlyRates: NightlyRate[]
}
