"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { ClientApiError } from "@/lib/api/client-fetch";
import type { InvoiceView } from "@/types/financial";
import { accountApi } from "./account-api";
import { currency, dateTime } from "./format";

export function InvoiceDetail({ id }: { id: number }) {
  const { session, logout } = useAuth();
  const [view, setView] = useState<InvoiceView | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!session) return;
    accountApi.invoice(session.token, id)
      .then(setView)
      .catch((reason: unknown) => {
        if (reason instanceof ClientApiError && reason.status === 401) logout();
        setError(reason instanceof Error ? reason.message : "发票加载失败");
      });
  }, [session, id, logout]);

  if (error) return <section className="account-shell"><p className="form-error">{error}</p><Link href="/invoices">返回发票列表</Link></section>;
  if (!view) return <section className="account-shell"><p className="quiet-empty">正在读取发票…</p></section>;
  const { invoice, lines } = view;

  return (
    <section className="account-shell invoice-sheet">
      <header className="invoice-heading">
        <div><p className="eyebrow">OFFICIAL INVOICE</p><h1>{invoice.invoiceNumber}</h1><p>{invoice.status === 0 ? "ISSUED" : "VOIDED"}</p></div>
        <strong>{currency(invoice.totalAmount, invoice.currency)}</strong>
      </header>
      <dl className="profile-strip">
        <div><dt>收件人</dt><dd>{invoice.recipientName}</dd></div>
        <div><dt>开具时间</dt><dd>{dateTime(invoice.issuedTime)}</dd></div>
        <div><dt>Booking</dt><dd>#{invoice.bookingId}</dd></div>
        <div><dt>Folio</dt><dd>#{invoice.folioId}</dd></div>
      </dl>
      {invoice.status !== 0 && <div className="void-notice"><strong>该发票已作废</strong><span>{invoice.voidReason || "未提供原因"} · {dateTime(invoice.voidedTime)}</span></div>}
      <div className="data-table">
        {lines.map((line) => (
          <div className="data-row" key={line.id}>
            <span>{line.lineNumber}</span>
            <span><strong>{line.description}</strong><small>{line.itemType} · {line.businessDate}</small></span>
            <span>{line.quantity} × {currency(line.unitPrice, invoice.currency)}</span>
            <b>{currency(line.amount, invoice.currency)}</b>
          </div>
        ))}
      </div>
      <div className="invoice-total"><span>Total</span><strong>{currency(invoice.totalAmount, invoice.currency)}</strong></div>
      <div className="form-actions"><Link className="button-secondary" href="/invoices">返回发票列表</Link><Link className="button-secondary" href={`/folios/${invoice.bookingId}`}>查看关联账单</Link></div>
    </section>
  );
}

