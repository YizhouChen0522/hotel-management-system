import type { PageResult } from "@/types/booking";

export type WalletStatus = "ACTIVE" | "FROZEN" | "CLOSED";
export type TopUpStatus = "PENDING" | "SUCCESS" | "REJECTED";

export interface WalletAccount {
  id: number;
  userId: number;
  currency: string;
  balance: number;
  status: WalletStatus;
}

export interface WalletTransaction {
  id: number;
  type: string;
  amount: number;
  balanceBefore: number;
  balanceAfter: number;
  sourceType: string;
  sourceId: number;
  createTime: string;
}

export interface WalletTopUp {
  id: number;
  walletId: number;
  amount: number;
  status: TopUpStatus;
  reason: string | null;
  createTime: string;
  resolvedTime: string | null;
}

export interface Stay {
  id: number;
  bookingId: number;
  primaryGuestId: number;
  status: number;
  actualCheckInTime: string | null;
  actualCheckOutTime: string | null;
  createTime: string;
}

export interface FolioItem {
  id: number;
  itemType: string;
  description: string | null;
  businessDate: string;
  quantity: number;
  unitPrice: number;
  amount: number;
  sourceType: string | null;
  sourceId: number | null;
}

export interface FolioPayment {
  id: number;
  folioId: number;
  amount: number;
  paymentMethod: string;
  status: string;
  referenceNo: string | null;
  paidTime: string;
}

export interface FolioExpense {
  id: number;
  folioId: number;
  itemType: string;
  amount: number;
  businessDate: string;
  description: string | null;
  reason: string | null;
  sourceExpenseId: number | null;
  status: string;
  ledgerItemId: number | null;
  registeredBy: number;
  registeredTime: string;
  resolvedBy: number | null;
  resolvedTime: string | null;
  cancelReason: string | null;
}

export interface Folio {
  id: number;
  stayId: number;
  bookingId: number;
  currency: string;
  status: number;
  totalAmount: number;
  paidAmount: number;
  refundedAmount: number;
  arTransferredAmount: number;
  balanceAmount: number;
  closedTime: string | null;
  items: FolioItem[];
  payments: FolioPayment[];
  expenses: FolioExpense[];
}

export interface FolioRefund {
  id: number;
  folioId: number;
  amount: number;
  currency: string;
  status: string;
  destination: string | null;
  reason: string;
  processReason: string | null;
  createTime: string;
  processedTime: string | null;
}

export interface Invoice {
  id: number;
  folioId: number;
  bookingId: number;
  invoiceNumber: string;
  currency: string;
  recipientName: string;
  recipientEmail: string | null;
  billingAddress: string | null;
  taxIdentifier: string | null;
  status: number;
  subtotal: number;
  totalAmount: number;
  issuedTime: string;
  voidedTime: string | null;
  voidReason: string | null;
  createTime: string;
}

export interface InvoiceLine {
  id: number;
  invoiceId: number;
  folioItemId: number;
  lineNumber: number;
  itemType: string;
  description: string;
  businessDate: string;
  quantity: number;
  unitPrice: number;
  amount: number;
  createTime: string;
}

export interface InvoiceView {
  invoice: Invoice;
  lines: InvoiceLine[];
}

export type StayPage = PageResult<Stay>;

