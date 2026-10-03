import { clientApi, jsonBody } from "@/lib/api/client-fetch";
import type {
  Availability,
  Booking,
  CheckoutSession,
  DepositBalance,
  PageResult,
  PaymentAttempt,
  PublicQuote,
  RoomType,
} from "@/types/booking";

const query = (values: Record<string, string | number>) => new URLSearchParams(
  Object.entries(values).map(([key, value]) => [key, String(value)]),
).toString();

export const bookingApi = {
  roomTypes: () => clientApi<RoomType[]>("/api/public/catalog/room-types"),
  availability: (roomTypeId: number, checkInDate: string, checkOutDate: string) =>
    clientApi<Availability>(`/api/public/catalog/availability?${query({ roomTypeId, checkInDate, checkOutDate })}`),
  quote: (roomTypeId: number, checkInDate: string, checkOutDate: string) =>
    clientApi<PublicQuote>(`/api/public/catalog/quote?${query({ roomTypeId, checkInDate, checkOutDate })}`),
  createWalletBooking: (token: string, value: { roomTypeId: number; checkInDate: string; checkOutDate: string; guestCount: number; requestKey: string }) =>
    clientApi<Booking>("/api/public/customer/bookings", { method: "POST", ...jsonBody(value) }, token),
  createCheckout: (token: string, value: { roomTypeId: number; checkInDate: string; checkOutDate: string; guestCount: number; requestKey: string }) =>
    clientApi<CheckoutSession>("/api/public/customer/payments/reservation-checkouts", { method: "POST", ...jsonBody(value) }, token),
  createAttempt: (token: string, checkoutId: number, value: { provider: string; requestKey: string }) =>
    clientApi<PaymentAttempt>(`/api/public/customer/payments/reservation-checkouts/${checkoutId}/attempts`, { method: "POST", ...jsonBody(value) }, token),
  attempt: (token: string, id: number) => clientApi<PaymentAttempt>(`/api/public/customer/payments/attempts/${id}`, {}, token),
  myBookings: (token: string, page: number, pageSize = 10) =>
    clientApi<PageResult<Booking>>(`/api/public/customer/bookings/my?${query({ page, pageSize })}`, {}, token),
  booking: (token: string, id: number) => clientApi<Booking>(`/api/public/customer/bookings/${id}`, {}, token),
  deposit: (token: string, id: number) => clientApi<DepositBalance>(`/api/public/customer/bookings/${id}/deposit`, {}, token),
  cancel: (token: string, id: number) => clientApi<Booking>(`/api/public/customer/bookings/${id}/cancel`, { method: "POST" }, token),
};
