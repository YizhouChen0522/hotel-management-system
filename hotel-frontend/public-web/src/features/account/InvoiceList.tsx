"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { ClientApiError } from "@/lib/api/client-fetch";
import type { InvoiceView } from "@/types/financial";
import { accountApi } from "./account-api";
import { currency, dateTime } from "./format";

export function InvoiceList() {
  const { session, logout } = useAuth();
  const [items, setItems] = useState<InvoiceView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!session) return;
    let active = true;
    async function load() {
      try {
        const stays = await accountApi.stays(session!.token, 1, 100);
        const folioResults = await Promise.allSettled(
          stays.items.map((stay) => accountApi.folioByBooking(session!.token, stay.bookingId)),
        );
        const folios = folioResults.flatMap((result) => result.status === "fulfilled" ? [result.value] : []);
        const invoiceResults = await Promise.allSettled(
          folios.map((folio) => accountApi.invoicesByFolio(session!.token, folio.id)),
        );
        if (active) {
          setItems(invoiceResults.flatMap((result) => result.status === "fulfilled" ? result.value : []));
        }
      } catch (reason) {
        if (reason instanceof ClientApiError && reason.status === 401) logout();
        if (active) setError(reason instanceof Error ? reason.message : "发票加载失败");
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, [session, logout]);

  return (
    <section className="account-shell">
      <header className="account-heading"><div><p className="eyebrow">INVOICES</p><h1>正式发票</h1><p>发票是开具时 Folio 的不可变快照。</p></div></header>
      {loading && <p className="quiet-empty">正在读取发票…</p>}
      {error && <p className="form-error">{error}</p>}
      {!loading && !error && items.length === 0 && <p className="quiet-empty">当前没有已开具发票。</p>}
      <div className="invoice-list">
        {items.map(({ invoice }) => (
          <Link href={`/invoices/${invoice.id}`} className="invoice-row" key={invoice.id}>
            <div><span>{invoice.status === 0 ? "ISSUED" : "VOIDED"}</span><strong>{invoice.invoiceNumber}</strong><small>{dateTime(invoice.issuedTime)} · Booking #{invoice.bookingId}</small></div>
            <b>{currency(invoice.totalAmount, invoice.currency)}</b>
          </Link>
        ))}
      </div>
    </section>
  );
}

