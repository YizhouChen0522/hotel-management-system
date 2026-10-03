"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { bookingApi } from "@/features/booking/booking-api";
import { bookingStatus, dateText, money } from "@/features/booking/format";
import { ClientApiError } from "@/lib/api/client-fetch";
import type { Booking, DepositBalance } from "@/types/booking";

export function BookingDetail({ id }: { id: number }) {
  const { session, logout } = useAuth();
  const [booking, setBooking] = useState<Booking | null>(null);
  const [deposit, setDeposit] = useState<DepositBalance | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!session) return;
    let active = true;
    bookingApi.booking(session.token, id).then(async (value) => {
      if (!active) return;
      setBooking(value);
      try { const balance = await bookingApi.deposit(session.token, id); if (active) setDeposit(balance); }
      catch { if (active) setDeposit(null); }
    }).catch((reason: unknown) => {
      if (!active) return;
      if (reason instanceof ClientApiError && reason.status === 401) logout();
      setError(reason instanceof Error ? reason.message : "预订加载失败");
    });
    return () => { active = false; };
  }, [session, id, logout]);
  async function cancel() {
    if (!session || !booking || !confirm("确定取消这笔预订？退款/没收金额将严格按该预订绑定的政策由后端计算。")) return;
    setBusy(true); setError("");
    try { setBooking(await bookingApi.cancel(session.token, booking.id)); setDeposit(await bookingApi.deposit(session.token, booking.id)); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "取消失败"); } finally { setBusy(false); }
  }
  if (error && !booking) return <section className="detail-panel"><p className="form-error">{error}</p><Link href="/bookings">返回我的预订</Link></section>;
  if (!booking) return <section className="detail-panel">正在读取预订…</section>;
  return <section className="detail-panel"><div className="detail-heading"><div><p className="eyebrow">RESERVATION #{booking.id}</p><h1>{booking.roomTypeName || `房型 ${booking.roomTypeId}`}</h1></div><span className={`status status-${booking.status}`}>{bookingStatus[booking.status] || `状态 ${booking.status}`}</span></div>
    <dl className="detail-grid"><div><dt>入住</dt><dd>{dateText(booking.checkInDate)}</dd></div><div><dt>离店</dt><dd>{dateText(booking.checkOutDate)}</dd></div><div><dt>住客</dt><dd>{booking.guestCount} 人</dd></div><div><dt>合同金额</dt><dd>{money(booking.totalPrice)}</dd></div><div><dt>来源</dt><dd>{booking.reservationSource}</dd></div><div><dt>预留房间</dt><dd>{booking.reservedRoomNumber || "待酒店分配"}</dd></div></dl>
    <div className="deposit-card"><h2>Reservation Deposit</h2>{deposit ? <dl className="quote-lines"><div><dt>已收订金</dt><dd>{money(deposit.received)}</dd></div><div><dt>已退款</dt><dd>{money(deposit.refunded + deposit.externalRefunded)}</dd></div><div><dt>已转入住账务</dt><dd>{money(deposit.transferred)}</dd></div><div className="quote-total"><dt>当前可用</dt><dd>{money(deposit.available)}</dd></div></dl> : <p>订金摘要暂不可用。</p>}</div>
    {error && <p className="form-error">{error}</p>}
    <div className="form-actions"><Link className="button-secondary" href="/bookings">返回列表</Link>{[0, 1].includes(booking.status) && <button className="danger-button" disabled={busy} onClick={cancel}>{busy ? "处理中…" : "取消预订"}</button>}</div>
  </section>;
}
