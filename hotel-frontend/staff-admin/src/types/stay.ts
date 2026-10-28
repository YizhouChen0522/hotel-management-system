import type { PageResult, Reservation, Room } from './reservation'

export interface Stay {
  id: number
  bookingId: number
  primaryGuestId: number
  registrationId: number
  status: number
  actualCheckInTime: string
  actualCheckOutTime: string | null
  checkedInBy: number
  checkedOutBy: number | null
  createTime: string
  updateTime: string
}

export interface StayGuest {
  id: number
  stayId: number
  guestId: number
  role: 'PRIMARY' | 'ACCOMPANYING'
  firstName: string
  lastName: string
}

export interface StayAssignment {
  id: number
  stayId: number
  bookingId: number
  roomId: number
  roomTypeId: number
  assignmentType: string
  startTime: string
  endTime: string | null
  changeReason: string | null
  createdBy: number
  createTime: string
}

export interface StayAdjustment {
  id: number
  stayId: number
  folioId: number
  assignmentId: number
  operatorId: number
  adjustmentType: number
  oldEnd: string
  newEnd: string
  effectiveTime: string
  requestKey: string
  reason: string
  lockedRate: number | null
  rateSource: string | null
  conflictBookingId: number | null
  operationalNotice: string | null
}

export interface RoomChangeCandidate {
  id: number
  roomNumber: string
  roomTypeId: number
  roomTypeName: string
  floor: number
  status: number
}

export interface RoomChangeOptions {
  remainingFrom: string
  effectiveCheckOutDate: string
  currentRoomId: number
  rooms: RoomChangeCandidate[]
}

export interface FolioItem {
  id: number
  itemType: string
  description: string
  businessDate: string
  quantity: number
  unitPrice: number
  amount: number
  sourceType: string | null
  sourceId: number | null
}

export interface FolioPayment {
  id: number
  folioId: number
  amount: number
  paymentMethod: string
  status: string
  referenceNo: string | null
  paidTime: string
}

export interface FolioExpense {
  id: number
  amount: number
  description?: string | null
  expenseType?: string | null
}

export interface Folio {
  id: number
  stayId: number
  bookingId: number
  currency: string
  status: number
  totalAmount: number
  paidAmount: number
  refundedAmount: number
  arTransferredAmount: number
  balanceAmount: number
  closedTime: string | null
  items: FolioItem[]
  payments: FolioPayment[]
  expenses: FolioExpense[]
}

export interface TurnoverTask {
  id: number
  taskId: number
  roomId: number
  stayId: number
  assignmentId: number
  status: number
  acceptedBy: number | null
  completedBy: number | null
  acceptedTime: string | null
  completedTime: string | null
  createTime: string
  updateTime: string
  note: string | null
}

export interface StayListRow {
  stay: Stay
  booking: Reservation | null
  assignment: StayAssignment | null
  room: Room | null
}

export type StayPage = PageResult<Stay>

