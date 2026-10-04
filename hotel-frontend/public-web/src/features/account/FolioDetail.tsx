"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { ClientApiError } from "@/lib/api/client-fetch";
import type { Folio, FolioRefund, InvoiceView } from "@/types/financial";
import { accountApi } from "./account-api";
import { currency, dateTime, folioStatus } from "./format";

export function FolioDetail({ bookingId }: { bookingId: number }) {
  const { session, logout } = useAuth();
  const [folio, setFolio] = useState<Folio | null>(null);
  const [refunds, setRefunds] = useState<FolioRefund[]>([]);
  const [invoices, setInvoices] = useState<InvoiceView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!session) return;
    let active = true;
    accountApi.folioByBooking(session.token, bookingId)
      .then(async (value) => {
        const [refundResult, invoiceResult] = await Promise.allSettled([
          accountApi.refunds(session.token, value.id),
          accountApi.invoicesByFolio(session.token, value.id),
        ]);
        if (!active) return;
        setFolio(value);
        if (refundResult.status === "fulfilled") setRefunds(refundResult.value);
        if (invoiceResult.status === "fulfilled") setInvoices(invoiceResult.value);
      })
      .catch((reason: unknown) => {
        if (reason instanceof ClientApiError && reason.status === 401) logout();
        if (active) setError(reason instanceof Error ? reason.message : "账单加载失败");
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [session, bookingId, logout]);

  if (loading) return <section className="account-shell"><p className="quiet-empty">正在读取账单…</p></section>;
  if (error || !folio) return <section className="account-shell"><p className="form-error">{error || "账单不存在"}</p><Link href="/folios">返回账单列表</Link></section>;

  return (
    <section className="account-shell">
      <header className="account-heading"><div><p className="eyebrow">FOLIO #{folio.id}</p><h1>住宿账单明细</h1><p>Booking #{folio.bookingId} · Stay #{folio.stayId} · {folioStatus(folio.status)}</p></div></header>
      <dl className="amount-summary">
        <div><dt>消费总额</dt><dd>{currency(folio.totalAmount, folio.currency)}</dd></div>
        <div><dt>已支付</dt><dd>{currency(folio.paidAmount, folio.currency)}</dd></div>
        <div><dt>已退款</dt><dd>{currency(folio.refundedAmount, folio.currency)}</dd></div>
        <div><dt>AR 转出</dt><dd>{currency(folio.arTransferredAmount, folio.currency)}</dd></div>
        <div className="emphasis"><dt>当前余额</dt><dd>{currency(folio.balanceAmount, folio.currency)}</dd></div>
      </dl>

      <section className="sub-panel">
        <h2>账单项目</h2>
        <div className="data-table">
          {folio.items.map((item) => (
            <div className="data-row" key={item.id}>
              <span>{item.businessDate}</span>
              <span><strong>{item.description || item.itemType}</strong><small>{item.itemType}</small></span>
              <span>{item.quantity} × {currency(item.unitPrice, folio.currency)}</span>
              <b>{currency(item.amount, folio.currency)}</b>
            </div>
          ))}
          {folio.items.length === 0 && <p className="quiet-empty">暂无账单项目。</p>}
        </div>
      </section>

      <div className="finance-columns">
        <section className="sub-panel"><h2>支付</h2>{folio.payments.map((payment) => <article className="record-row" key={payment.id}><div><strong>{payment.paymentMethod}</strong><small>{dateTime(payment.paidTime)} · {payment.status}</small></div><b>{currency(payment.amount, folio.currency)}</b></article>)}{folio.payments.length === 0 && <p className="quiet-empty">暂无支付记录。</p>}</section>
        <section className="sub-panel"><h2>退款</h2>{refunds.map((refund) => <article className="record-row" key={refund.id}><div><strong>{refund.status}</strong><small>{refund.reason} · {dateTime(refund.createTime)}</small></div><b>{currency(refund.amount, refund.currency)}</b></article>)}{refunds.length === 0 && <p className="quiet-empty">暂无退款记录。</p>}</section>
      </div>

      <section className="sub-panel"><h2>发票</h2>{invoices.map(({ invoice }) => <Link className="record-row" key={invoice.id} href={`/invoices/${invoice.id}`}><div><strong>{invoice.invoiceNumber}</strong><small>{dateTime(invoice.issuedTime)}</small></div><span>{invoice.status === 0 ? "ISSUED" : "VOIDED"}</span></Link>)}{invoices.length === 0 && <p className="quiet-empty">此账单尚未开具发票。</p>}</section>
    </section>
  );
}

