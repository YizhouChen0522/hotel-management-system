"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { ClientApiError } from "@/lib/api/client-fetch";
import type { Folio } from "@/types/financial";
import { accountApi } from "./account-api";
import { currency, folioStatus } from "./format";

export function FolioList() {
  const { session, logout } = useAuth();
  const [folios, setFolios] = useState<Folio[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!session) return;
    let active = true;
    async function load() {
      try {
        const stays = await accountApi.stays(session!.token, 1, 100);
        const results = await Promise.allSettled(
          stays.items.map((stay) => accountApi.folioByBooking(session!.token, stay.bookingId)),
        );
        if (active) {
          setFolios(results.flatMap((result) => result.status === "fulfilled" ? [result.value] : []));
        }
      } catch (reason) {
        if (reason instanceof ClientApiError && reason.status === 401) logout();
        if (active) setError(reason instanceof Error ? reason.message : "账单加载失败");
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, [session, logout]);

  return (
    <section className="account-shell">
      <header className="account-heading"><div><p className="eyebrow">STAY FOLIOS</p><h1>住宿账单</h1></div></header>
      {loading && <p className="quiet-empty">正在读取账单…</p>}
      {error && <p className="form-error">{error}</p>}
      {!loading && !error && folios.length === 0 && <p className="quiet-empty">当前没有 Stay/Folio。预订本身不是最终住宿账单。</p>}
      <div className="folio-grid">
        {folios.map((folio) => (
          <Link key={folio.id} href={`/folios/${folio.bookingId}`} className="folio-card">
            <div><span>Folio #{folio.id}</span><small>Booking #{folio.bookingId} · {folioStatus(folio.status)}</small></div>
            <strong>{currency(folio.totalAmount, folio.currency)}</strong>
            <dl>
              <div><dt>已支付</dt><dd>{currency(folio.paidAmount, folio.currency)}</dd></div>
              <div><dt>余额</dt><dd>{currency(folio.balanceAmount, folio.currency)}</dd></div>
            </dl>
            <span className="text-link">查看账单明细 →</span>
          </Link>
        ))}
      </div>
    </section>
  );
}

