"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { bookingApi } from "@/features/booking/booking-api";
import { bookingStatus, dateText, money } from "@/features/booking/format";
import { ClientApiError } from "@/lib/api/client-fetch";
import type { PageResult, Booking } from "@/types/booking";

export function BookingList() {
  const { session, logout } = useAuth();
  const [data, setData] = useState<PageResult<Booking> | null>(null);
  const [page, setPage] = useState(1);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!session) return;
    bookingApi.myBookings(session.token, page).then(setData).catch((e: unknown) => {
      if (e instanceof ClientApiError && e.status === 401) logout();
      setError(e instanceof Error ? e.message : "预订加载失败");
    });
  }, [session, page, logout]);
  return <section className="list-panel"><div className="list-heading"><div><p className="eyebrow">MY RESERVATIONS</p><h1>我的预订</h1></div><Link className="button-primary" href="/booking">新建预订</Link></div>
    {error && <p className="form-error">{error}</p>}
    {data && data.items.length === 0 && <p className="quiet-empty compact">还没有预订记录。</p>}
    <div className="booking-list">{data?.items.map((item) => <Link href={`/bookings/${item.id}`} className="booking-row" key={item.id}><div><small>#{item.id}</small><h2>{item.roomTypeName || `房型 ${item.roomTypeId}`}</h2><p>{dateText(item.checkInDate)} — {dateText(item.checkOutDate)} · {item.guestCount} 位住客</p></div><div><span className={`status status-${item.status}`}>{bookingStatus[item.status] || `状态 ${item.status}`}</span><strong>{money(item.totalPrice)}</strong></div></Link>)}</div>
    {data && data.total > 0 && <nav className="pagination"><button disabled={page <= 1} onClick={() => setPage(page - 1)}>上一页</button><span>第 {data.page} 页 · 共 {data.total} 条</span><button disabled={!data.hasNext} onClick={() => setPage(page + 1)}>下一页</button></nav>}
  </section>;
}
