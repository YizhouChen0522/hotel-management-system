"use client";

import Link from "next/link";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { bookingApi } from "@/features/booking/booking-api";
import { money } from "@/features/booking/format";
import { ClientApiError, requestKey } from "@/lib/api/client-fetch";
import type { Availability, Booking, CheckoutSession, PaymentAttempt, PublicQuote, RoomType } from "@/types/booking";

type Search = { roomTypeId: string; checkInDate: string; checkOutDate: string; guestCount: number };

const tomorrow = (offset: number) => {
  const date = new Date();
  date.setDate(date.getDate() + offset);
  const part = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${part(date.getMonth() + 1)}-${part(date.getDate())}`;
};

export function BookingFlow() {
  const { session, logout } = useAuth();
  const [roomTypes, setRoomTypes] = useState<RoomType[]>([]);
  const [search, setSearch] = useState<Search>({ roomTypeId: "", checkInDate: tomorrow(1), checkOutDate: tomorrow(2), guestCount: 1 });
  const [availability, setAvailability] = useState<Availability | null>(null);
  const [quote, setQuote] = useState<PublicQuote | null>(null);
  const [checkout, setCheckout] = useState<CheckoutSession | null>(null);
  const [attempt, setAttempt] = useState<PaymentAttempt | null>(null);
  const [booking, setBooking] = useState<Booking | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [checkoutKey] = useState(() => requestKey("checkout"));
  const [attemptKey] = useState(() => requestKey("payment"));
  const [walletKey] = useState(() => requestKey("wallet"));
  const selected = useMemo(() => roomTypes.find((x) => x.id === Number(search.roomTypeId)), [roomTypes, search.roomTypeId]);

  useEffect(() => {
    bookingApi.roomTypes().then((items) => {
      setRoomTypes(items);
      const requested = new URLSearchParams(location.search).get("roomTypeId");
      const validRequested = items.some((item) => String(item.id) === requested) ? requested : null;
      setSearch((value) => ({ ...value, roomTypeId: value.roomTypeId || validRequested || String(items[0]?.id || "") }));
    }).catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "房型加载失败"));
  }, []);

  const handleError = (reason: unknown) => {
    if (reason instanceof ClientApiError && reason.status === 401) logout();
    setError(reason instanceof Error ? reason.message : "请求失败，请稍后重试");
  };

  async function searchRooms(event: FormEvent) {
    event.preventDefault();
    setBusy(true); setError(""); setQuote(null); setAvailability(null); setCheckout(null); setAttempt(null);
    try {
      const roomTypeId = Number(search.roomTypeId);
      if (!roomTypeId || search.checkInDate >= search.checkOutDate) throw new Error("请选择有效的入住和离店日期");
      if (selected && search.guestCount > selected.capacity) throw new Error(`该房型最多入住 ${selected.capacity} 人`);
      const [stock, priced] = await Promise.all([
        bookingApi.availability(roomTypeId, search.checkInDate, search.checkOutDate),
        bookingApi.quote(roomTypeId, search.checkInDate, search.checkOutDate),
      ]);
      setAvailability(stock);
      setQuote(priced);
    } catch (reason) { handleError(reason); } finally { setBusy(false); }
  }

  const payload = (key: string) => ({
    roomTypeId: Number(search.roomTypeId), checkInDate: search.checkInDate,
    checkOutDate: search.checkOutDate, guestCount: search.guestCount, requestKey: key,
  });

  async function payWithProvider() {
    if (!session || !quote || !availability?.available) return;
    setBusy(true); setError("");
    try {
      const frozen = await bookingApi.createCheckout(session.token, payload(checkoutKey));
      setCheckout(frozen);
      const created = await bookingApi.createAttempt(session.token, frozen.id, { provider: "MOCK", requestKey: attemptKey });
      setAttempt(created);
      if (created.bookingId) setBooking(await bookingApi.booking(session.token, created.bookingId));
    } catch (reason) { handleError(reason); } finally { setBusy(false); }
  }

  async function refreshPayment() {
    if (!session || !attempt) return;
    setBusy(true); setError("");
    try {
      const current = await bookingApi.attempt(session.token, attempt.id);
      setAttempt(current);
      if (current.bookingId && current.fulfillmentStatus === "COMPLETED") {
        setBooking(await bookingApi.booking(session.token, current.bookingId));
      }
    } catch (reason) { handleError(reason); } finally { setBusy(false); }
  }

  async function payWithWallet() {
    if (!session || !quote || !availability?.available) return;
    setBusy(true); setError("");
    try { setBooking(await bookingApi.createWalletBooking(session.token, payload(walletKey))); }
    catch (reason) { handleError(reason); } finally { setBusy(false); }
  }

  if (booking) return <section className="booking-panel confirmation-panel">
    <p className="eyebrow">RESERVATION RECEIVED</p><h1>预订已创建</h1>
    <p>预订号 <strong>#{booking.id}</strong>，金额 {money(booking.totalPrice)}。酒店将按当前 Reservation 状态继续处理。</p>
    <div className="form-actions"><Link className="button-primary" href={`/bookings/${booking.id}`}>查看预订详情</Link><Link className="button-secondary" href="/bookings">我的预订</Link></div>
  </section>;

  return <div className="booking-grid">
    <form className="booking-panel" onSubmit={searchRooms}>
      <p className="eyebrow">PLAN YOUR STAY</p><h1>查找住宿</h1>
      <div className="form-grid">
        <label>房型<select value={search.roomTypeId} onChange={(e) => setSearch({ ...search, roomTypeId: e.target.value })} required>{roomTypes.map((room) => <option key={room.id} value={room.id}>{room.name}</option>)}</select></label>
        <label>住客人数<input type="number" min={1} max={selected?.capacity || 20} value={search.guestCount} onChange={(e) => setSearch({ ...search, guestCount: Number(e.target.value) })}/></label>
        <label>入住日期<input type="date" min={tomorrow(0)} value={search.checkInDate} onChange={(e) => setSearch({ ...search, checkInDate: e.target.value })}/></label>
        <label>离店日期<input type="date" min={search.checkInDate} value={search.checkOutDate} onChange={(e) => setSearch({ ...search, checkOutDate: e.target.value })}/></label>
      </div>
      <button className="button-primary" disabled={busy || !search.roomTypeId}>{busy ? "查询中…" : "查询房态与价格"}</button>
      {selected && <p className="form-help">{selected.description || ""} · 最多 {selected.capacity} 人</p>}
      {error && <p className="form-error" role="alert">{error}</p>}
    </form>

    <section className="booking-panel quote-panel">
      <p className="eyebrow">SERVER QUOTE</p><h2>价格确认</h2>
      {!quote && <p className="quiet-empty compact">选择日期与房型后，由酒店服务器返回实时房态和报价。</p>}
      {quote && availability && <>
        <div className={availability.available ? "availability available" : "availability unavailable"}>{availability.available ? `尚有 ${availability.availableRoomCount} 间可订` : "当前日期已无可售房间"}</div>
        <dl className="quote-lines">{quote.nightlyRates.map((night) => <div key={night.date}><dt>{night.date}</dt><dd>{money(night.price)}</dd></div>)}<div className="quote-total"><dt>{quote.nights} 晚合计</dt><dd>{money(quote.totalPrice)}</dd></div></dl>
        <p className="form-help">最终接受金额和取消政策会在创建支付会话时由后端重新冻结。库存变化可能导致提交失败。</p>
        {availability.available && !attempt && <div className="payment-options">
          <button className="button-primary" type="button" disabled={busy} onClick={payWithProvider}>通过支付服务商支付全额订金</button>
          <button className="button-secondary" type="button" disabled={busy} onClick={payWithWallet}>使用账户钱包支付全额订金</button>
        </div>}
      </>}
      {checkout && <div className="payment-state"><strong>支付会话 #{checkout.id}</strong><span>冻结金额 {money(checkout.quotedTotal, checkout.currency)}</span><span>有效期至 {checkout.expiresAt.replace("T", " ")}</span></div>}
      {attempt && <div className="payment-state"><strong>支付状态：{attempt.status}</strong><span>履约状态：{attempt.fulfillmentStatus}</span><span>商户支付号：{attempt.merchantPaymentNo}</span><button className="button-secondary" type="button" disabled={busy} onClick={refreshPayment}>刷新后端支付结果</button><small>开发 MOCK 仅接受服务端签名 webhook；前端回调不会被当作支付成功。</small></div>}
    </section>
  </div>;
}
