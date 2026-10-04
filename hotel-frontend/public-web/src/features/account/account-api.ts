import { clientApi, jsonBody } from "@/lib/api/client-fetch";
import type {
  Folio,
  FolioRefund,
  InvoiceView,
  StayPage,
  WalletAccount,
  WalletTopUp,
  WalletTransaction,
} from "@/types/financial";

export const accountApi = {
  wallet: (token: string) =>
    clientApi<WalletAccount>("/api/public/customer/wallet", {}, token),
  walletTransactions: (token: string, after = 0) =>
    clientApi<WalletTransaction[]>(
      `/api/public/customer/wallet/transactions?after=${after}`,
      {},
      token,
    ),
  walletTopUps: (token: string, after = 0) =>
    clientApi<WalletTopUp[]>(
      `/api/public/customer/wallet/top-ups?after=${after}`,
      {},
      token,
    ),
  requestTopUp: (token: string, amount: number, requestKey: string) =>
    clientApi<WalletTopUp>(
      "/api/public/customer/wallet/top-ups",
      { method: "POST", ...jsonBody({ amount, requestKey }) },
      token,
    ),
  stays: (token: string, page = 1, pageSize = 20) =>
    clientApi<StayPage>(
      `/api/public/customer/stays?page=${page}&pageSize=${pageSize}`,
      {},
      token,
    ),
  folioByBooking: (token: string, bookingId: number) =>
    clientApi<Folio>(
      `/api/public/customer/bookings/${bookingId}/folio`,
      {},
      token,
    ),
  refunds: (token: string, folioId: number) =>
    clientApi<FolioRefund[]>(
      `/api/public/customer/folios/${folioId}/refunds`,
      {},
      token,
    ),
  invoicesByFolio: (token: string, folioId: number) =>
    clientApi<InvoiceView[]>(
      `/api/public/customer/folios/${folioId}/invoices`,
      {},
      token,
    ),
  invoice: (token: string, invoiceId: number) =>
    clientApi<InvoiceView>(
      `/api/public/customer/invoices/${invoiceId}`,
      {},
      token,
    ),
};

