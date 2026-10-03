export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageResult<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
  hasNext: boolean;
  searchMode: boolean;
}

export interface RoomType {
  id: number;
  name: string;
  description: string | null;
  basePrice: number;
  capacity: number;
}

export interface Availability {
  roomTypeId: number;
  checkInDate: string;
  checkOutDate: string;
  available: boolean;
  availableRoomCount: number;
}

export interface NightlyRate {
  date: string;
  price: number;
}

export interface PublicQuote {
  roomTypeId: number;
  checkInDate: string;
  checkOutDate: string;
  nights: number;
  nightlyRates: NightlyRate[];
  totalPrice: number;
}

export interface Booking {
  id: number;
  userId: number | null;
  bookerGuestProfileId: number | null;
  roomTypeId: number;
  roomTypeName: string | null;
  reservedRoomId: number | null;
  reservedRoomNumber: string | null;
  reservationSource: string;
  guestCount: number;
  checkInDate: string;
  checkOutDate: string;
  status: number;
  totalPrice: number;
  createTime: string;
  updateTime: string;
}

export interface CheckoutSession {
  id: number;
  customerUserId: number;
  roomTypeId: number;
  checkInDate: string;
  checkOutDate: string;
  guestCount: number;
  currency: string;
  quotedTotal: number;
  reservationPolicyId: number | null;
  requestKey: string;
  status: string;
  expiresAt: string;
}

export interface PaymentAttempt {
  id: number;
  checkoutSessionId: number;
  provider: string;
  merchantPaymentNo: string;
  providerPaymentId: string | null;
  amount: number;
  currency: string;
  status: string;
  fulfillmentStatus: string;
  bookingId: number | null;
  createdAt: string;
  completedAt: string | null;
}

export interface DepositBalance {
  received: number;
  refunded: number;
  transferred: number;
  pendingRefund: number;
  balance: number;
  available: number;
  forfeited: number;
  externalRefunded: number;
}
