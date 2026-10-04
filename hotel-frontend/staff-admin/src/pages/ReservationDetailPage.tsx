import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../lib/api/client'
import {
  approveReservation,
  assignRoom,
  declineReservation,
  getBookingGuests,
  getDeposit,
  getDepositPayments,
  getDepositRefunds,
  getEligibleRooms,
  getPriceSnapshot,
  getReservation,
} from '../features/reservation/reservation-api'
import { dateTime, money, reservationStatus } from '../features/reservation/format'
import type {
  BookingGuest,
  DepositBalance,
  DepositPayment,
  DepositRefund,
  PriceSnapshot,
  Reservation,
  Room,
} from '../types/reservation'

export function ReservationDetailPage() {
  const id = Number(useParams().id)
  const [booking, setBooking] = useState<Reservation | null>(null)
  const [guests, setGuests] = useState<BookingGuest[]>([])
  const [deposit, setDeposit] = useState<DepositBalance | null>(null)
  const [payments, setPayments] = useState<DepositPayment[]>([])
  const [refunds, setRefunds] = useState<DepositRefund[]>([])
  const [price, setPrice] = useState<PriceSnapshot | null>(null)
  const [rooms, setRooms] = useState<Room[]>([])
  const [roomId, setRoomId] = useState('')
  const [reason, setReason] = useState('Front desk reassignment')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const current = await getReservation(id)
      const [guestResult, depositResult, paymentResult, refundResult, priceResult, roomResult] = await Promise.allSettled([
        getBookingGuests(id),
        getDeposit(id),
        getDepositPayments(id),
        getDepositRefunds(id),
        getPriceSnapshot(id),
        getEligibleRooms(id),
      ])
      setBooking(current)
      setGuests(guestResult.status === 'fulfilled' ? guestResult.value : [])
      setDeposit(depositResult.status === 'fulfilled' ? depositResult.value : null)
      setPayments(paymentResult.status === 'fulfilled' ? paymentResult.value.items : [])
      setRefunds(refundResult.status === 'fulfilled' ? refundResult.value.items : [])
      setPrice(priceResult.status === 'fulfilled' ? priceResult.value : null)
      setRooms(roomResult.status === 'fulfilled' ? roomResult.value : [])
      setRoomId(current.reservedRoomId ? String(current.reservedRoomId) : '')
    } catch (cause) {
      setError(message(cause))
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])

  async function run(action: () => Promise<Reservation>, success: string) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await action()
      setNotice(success)
      await load()
    } catch (cause) {
      setError(message(cause))
    } finally {
      setBusy(false)
    }
  }

  if (!Number.isInteger(id) || id <= 0) return <p className="error-banner">预订 ID 无效</p>
  if (loading && !booking) return <p className="state-card">正在读取预订详情…</p>
  if (!booking) return <p className="error-banner">{error || '预订不存在'}</p>

  const selectedRoom = Number(roomId)
  const assignedOptionMissing = booking.reservedRoomId != null && !rooms.some((room) => room.id === booking.reservedRoomId)

  return (
    <section>
      <Link className="back-link" to="/reservations">← 返回预订列表</Link>
      <div className="page-heading">
        <div>
          <p className="kicker">RESERVATION #{booking.id}</p>
          <h2>{booking.bookerName || '未登记订房人'}</h2>
          <p>{booking.roomTypeName} · {booking.checkInDate} 至 {booking.checkOutDate}</p>
        </div>
        <span className={`status-badge status-${booking.status}`}>{reservationStatus(booking.status)}</span>
      </div>

      {error && <p className="error-banner">{error}</p>}
      {notice && <p className="success-banner">{notice}</p>}

      <div className="summary-grid">
        <article className="summary-card"><span>合同金额</span><strong>{money(booking.totalPrice)}</strong><small>以后端已接受报价为准</small></article>
        <article className="summary-card"><span>已收订金</span><strong>{money(deposit?.received || 0)}</strong><small>可用 {money(deposit?.available || 0)}</small></article>
        <article className="summary-card"><span>具体房间</span><strong>{booking.reservedRoomNumber || '待分配'}</strong><small>Room ID {booking.reservedRoomId || '—'}</small></article>
        <article className="summary-card"><span>来源</span><strong>{booking.reservationSource}</strong><small>{dateTime(booking.createTime)}</small></article>
      </div>

      <div className="detail-columns">
        <article className="panel">
          <div className="panel-heading"><div><h3>合同与订房人</h3><p>原始 Reservation 合同信息</p></div></div>
          <dl className="detail-list">
            <div><dt>Booking ID</dt><dd>#{booking.id}</dd></div>
            <div><dt>订房人</dt><dd>{booking.bookerName || '—'}</dd></div>
            <div><dt>联系信息</dt><dd>{booking.bookerEmail || booking.bookerPhone || '—'}</dd></div>
            <div><dt>房型</dt><dd>{booking.roomTypeName} (#{booking.roomTypeId})</dd></div>
            <div><dt>日期</dt><dd>{booking.checkInDate} → {booking.checkOutDate}</dd></div>
            <div><dt>住客数</dt><dd>{booking.guestCount}</dd></div>
            <div><dt>更新时间</dt><dd>{dateTime(booking.updateTime)}</dd></div>
          </dl>
        </article>

        <article className="panel">
          <div className="panel-heading"><div><h3>实际住客资料</h3><p>来自 Booking Guest 登记</p></div></div>
          {guests.length === 0 ? <p className="empty-state">尚未登记住客。</p> : guests.map((entry) => (
            <div className="guest-row" key={entry.relationId}>
              <div><strong>{entry.guest.firstName} {entry.guest.lastName}</strong><small>{entry.role} · {entry.guest.nationality || '国籍未填'}</small></div>
              <span>{entry.guest.documentType || '证件未填'}</span>
            </div>
          ))}
        </article>
      </div>

      <article className="panel">
        <div className="panel-heading"><div><h3>价格快照</h3><p>已锁定的 BookingPriceVersion，不重新调用当前价格。</p></div><strong>{price?.version ? `${price.version.currency} ${price.version.totalPrice}` : '暂无快照'}</strong></div>
        <div className="nightly-grid">
          {price?.nightlyRates.map((night) => <div key={night.id}><span>{night.stayDate}</span><strong>{money(night.rateAmount, price.version?.currency || 'CNY')}</strong><small>{night.rateSource}</small></div>)}
        </div>
      </article>

      <div className="detail-columns">
        <article className="panel">
          <div className="panel-heading"><div><h3>订金与付款</h3><p>Reservation Deposit Ledger</p></div></div>
          <dl className="detail-list compact">
            <div><dt>已收</dt><dd>{money(deposit?.received || 0)}</dd></div>
            <div><dt>已退款</dt><dd>{money((deposit?.refunded || 0) + (deposit?.externalRefunded || 0))}</dd></div>
            <div><dt>待退款</dt><dd>{money(deposit?.pendingRefund || 0)}</dd></div>
            <div><dt>已转 Stay Folio</dt><dd>{money(deposit?.transferred || 0)}</dd></div>
          </dl>
          {payments.map((payment) => <div className="record-row" key={payment.id}><span>{payment.paymentMethod}</span><strong>{money(payment.amount)}</strong><small>{dateTime(payment.receivedTime)}</small></div>)}
          {refunds.map((refund) => <div className="record-row" key={refund.id}><span>退款 · 状态 {refund.status}</span><strong>{money(refund.amount, refund.currency)}</strong><small>{refund.reason}</small></div>)}
        </article>

        <article className="panel">
          <div className="panel-heading"><div><h3>取消政策</h3><p>Booking 创建时绑定的版本，不读取当前政策覆盖历史。</p></div></div>
          {booking.cancellationPolicy ? (
            <>
              <p><strong>{booking.cancellationPolicy.policy.name}</strong> · v{booking.cancellationPolicy.policy.versionNo}</p>
              {booking.cancellationPolicy.bands.map((band) => <div className="policy-row" key={band.id}><span>提前 {band.startDays}{band.endDays == null ? '+' : `–${band.endDays}`} 天</span><strong>退 {band.refundPercent}%</strong></div>)}
            </>
          ) : <p className="empty-state">Legacy reservation：没有绑定取消政策。</p>}
        </article>
      </div>

      {(booking.status === 0 || booking.status === 1) && (
        <article className="action-panel">
          <div>
            <p className="kicker">ROOM CONTROL</p>
            <h3>{booking.status === 0 ? '确认预订并分配房间' : '重新分配房间'}</h3>
            <p>列表已经按房型、日期冲突和可售状态过滤。最终并发校验仍由后端事务完成。</p>
          </div>
          <label>
            可分配房间
            <select value={roomId} onChange={(event) => setRoomId(event.target.value)}>
              <option value="">请选择 Room ID / 房号</option>
              {assignedOptionMissing && <option value={booking.reservedRoomId!}>当前房间 {booking.reservedRoomNumber}</option>}
              {rooms.map((room) => <option value={room.id} key={room.id}>#{room.id} · 房号 {room.roomNumber} · {room.floor} 层</option>)}
            </select>
          </label>
          {booking.status === 1 && <label>调整原因<input value={reason} onChange={(event) => setReason(event.target.value)} maxLength={200} /></label>}
          <div className="actions">
            {booking.status === 0 ? (
              <>
                <button className="primary-button" disabled={busy || !selectedRoom} onClick={() => void run(() => approveReservation(id, selectedRoom), '预订已确认，房间已分配。')}>确认并分房</button>
                <button className="danger-button" disabled={busy} onClick={() => { if (window.confirm('确认拒绝该预订？退款与订金处理将由后端现有规则执行。')) void run(() => declineReservation(id), '预订已拒绝。') }}>拒绝申请</button>
              </>
            ) : (
              <button className="primary-button" disabled={busy || !selectedRoom || selectedRoom === booking.reservedRoomId} onClick={() => { if (window.confirm('确认把该预订调整到所选 Room ID？')) void run(() => assignRoom(id, selectedRoom, reason), '房间分配已更新。') }}>更新房间分配</button>
            )}
          </div>
          {rooms.length === 0 && <p className="warning-banner">当前日期没有可分配房间；请勿强行确认。</p>}
        </article>
      )}
    </section>
  )
}

function message(cause: unknown) {
  if (cause instanceof ApiError) {
    if (cause.status === 409) return `库存或状态刚刚发生变化：${cause.message}`
    if (cause.status === 403) return '你没有执行该操作的权限。'
    if (cause.status === 404) return '该预订或关联资源不存在。'
  }
  return cause instanceof Error ? cause.message : '请求失败'
}
