import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../lib/api/client'
import { dateTime, money } from '../features/reservation/format'
import {
  applyStayAdjustment,
  changeStayRoom,
  checkOutStay,
  getBooking,
  getRoom,
  getRoomChangeOptions,
  getStay,
  getStayAssignments,
  getStayFolio,
  getStayGuests,
  listTurnovers,
  listStayAdjustments,
  recordFolioPayment,
} from '../features/stay/stay-api'
import type { Reservation, Room } from '../types/reservation'
import type {
  Folio,
  RoomChangeOptions,
  Stay,
  StayAdjustment,
  StayAssignment,
  StayGuest,
  TurnoverTask,
} from '../types/stay'
import { stayStatus } from '../features/stay/format'

export function StayDetailPage() {
  const id = Number(useParams().id)
  const [stay, setStay] = useState<Stay | null>(null)
  const [booking, setBooking] = useState<Reservation | null>(null)
  const [guests, setGuests] = useState<StayGuest[]>([])
  const [assignments, setAssignments] = useState<StayAssignment[]>([])
  const [rooms, setRooms] = useState<Record<number, Room>>({})
  const [folio, setFolio] = useState<Folio | null>(null)
  const [turnovers, setTurnovers] = useState<TurnoverTask[]>([])
  const [adjustments, setAdjustments] = useState<StayAdjustment[]>([])
  const [roomChangeOptions, setRoomChangeOptions] = useState<RoomChangeOptions | null>(null)
  const [operation, setOperation] = useState<'ROOM_CHANGE' | 'EXTENSION' | 'EARLY' | 'LATE' | null>(null)
  const [selectedRoomId, setSelectedRoomId] = useState('')
  const [newEnd, setNewEnd] = useState('')
  const [adjustmentReason, setAdjustmentReason] = useState('')
  const [paymentMethod, setPaymentMethod] = useState('CREDIT_CARD')
  const [referenceNo, setReferenceNo] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const current = await getStay(id)
      const [bookingResult, guestResult, assignmentResult, folioResult, adjustmentResult] = await Promise.all([
        getBooking(current.bookingId),
        getStayGuests(id),
        getStayAssignments(id),
        getStayFolio(id),
        listStayAdjustments(id),
      ])
      const optionResult = current.status === 1 ? await getRoomChangeOptions(id) : null
      const roomPairs = await Promise.all(assignmentResult.map(async (assignment) => [
        assignment.roomId,
        await getRoom(assignment.roomId),
      ] as const))
      const turnoverPages = await Promise.all(
        [...new Set(assignmentResult.map((assignment) => assignment.roomId))].map(listTurnovers),
      )
      setStay(current)
      setBooking(bookingResult)
      setGuests(guestResult)
      setAssignments(assignmentResult)
      setRooms(Object.fromEntries(roomPairs))
      setFolio(folioResult)
      setAdjustments(adjustmentResult)
      setRoomChangeOptions(optionResult)
      setTurnovers(turnoverPages.flatMap((page) => page.items).filter((task) => task.stayId === id))
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

  const currentAssignment = useMemo(
    () => assignments.find((assignment) => assignment.endTime == null) ?? null,
    [assignments],
  )
  const currentRoom = currentAssignment ? rooms[currentAssignment.roomId] : null
  const balance = Number(folio?.balanceAmount ?? 0)
  const effectiveEnd = roomChangeOptions?.effectiveCheckOutDate
    ?? adjustments.reduce((end, item) => item.newEnd || end, booking?.checkOutDate ?? '')
  const today = new Date().toISOString().slice(0, 10)
  const canEarlyCheckout = effectiveEnd !== '' && today < effectiveEnd
  const canLateCheckout = effectiveEnd !== '' && today === effectiveEnd

  async function checkout() {
    if (!stay) return
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await checkOutStay(stay.id)
      setNotice('退房完成：Stay 已结束，房间进入 MAINTENANCE，并已创建 Turnover。')
      await load()
    } catch (cause) {
      setError(message(cause))
    } finally {
      setBusy(false)
    }
  }

  async function changeRoom() {
    if (!stay || !selectedRoomId || !adjustmentReason.trim()) return
    await runOperation(
      () => changeStayRoom(stay.id, Number(selectedRoomId), adjustmentReason),
      '换房完成：旧房进入 MAINTENANCE 并创建 Turnover；新房已建立 Assignment 并变为 OCCUPIED。',
    )
  }

  async function adjustStay(type: 'EXTENSION' | 'EARLY_CHECKOUT' | 'LATE_CHECKOUT') {
    if (!stay || !adjustmentReason.trim() || (type === 'EXTENSION' && !newEnd)) return
    await runOperation(
      () => applyStayAdjustment(stay.id, type, adjustmentReason, newEnd),
      type === 'EXTENSION'
        ? '延住已登记，新增锁定房价已进入 Folio。'
        : type === 'EARLY_CHECKOUT'
          ? '提前退房调整已登记；请复核 Folio 后办理正式退房。'
          : '晚退调整已登记；相关费用以后端 Folio 结果为准。',
    )
  }

  async function runOperation(action: () => Promise<unknown>, success: string) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await action()
      setNotice(success)
      setOperation(null)
      setSelectedRoomId('')
      setNewEnd('')
      setAdjustmentReason('')
      await load()
    } catch (cause) {
      setError(message(cause))
      await load()
    } finally {
      setBusy(false)
    }
  }

  async function settleBalance() {
    if (!folio || balance <= 0) return
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await recordFolioPayment(folio.id, balance, paymentMethod, referenceNo)
      setNotice('付款已由后端记入 Folio，请复核余额后再退房。')
      await load()
    } catch (cause) {
      setError(message(cause))
    } finally {
      setBusy(false)
    }
  }

  if (!Number.isInteger(id) || id <= 0) return <p className="error-banner">Stay ID 无效</p>
  if (loading && !stay) return <p className="state-card">正在读取住宿与账务资料…</p>
  if (!stay || !booking || !folio) return <p className="error-banner">{error || '住宿资料不存在'}</p>

  return (
    <section>
      <Link className="back-link" to="/stays">← 返回住宿列表</Link>
      <div className="page-heading">
        <div>
          <p className="kicker">STAY #{stay.id}</p>
          <h2>{guests[0] ? `${guests[0].firstName} ${guests[0].lastName}` : booking.bookerName || '实际住客'}</h2>
          <p>Booking #{booking.id} · {currentRoom ? `房间 ${currentRoom.roomNumber}` : '房间已释放'}</p>
        </div>
        <span className={`stay-badge stay-${stay.status}`}>{stayStatus(stay.status)}</span>
      </div>

      {error && <p className="error-banner">{error}</p>}
      {notice && <p className="success-banner">{notice}</p>}

      <div className="summary-grid">
        <article className="summary-card"><span>实际入住</span><strong>{dateTime(stay.actualCheckInTime)}</strong><small>计划 {booking.checkInDate}</small></article>
        <article className="summary-card"><span>当前房间</span><strong>{currentRoom?.roomNumber || '已结束'}</strong><small>{currentRoom ? roomStatus(currentRoom.status) : 'Assignment 已关闭'}</small></article>
        <article className="summary-card"><span>Folio 总额</span><strong>{money(folio.totalAmount, folio.currency)}</strong><small>Booking 合同价 {money(booking.totalPrice, folio.currency)}</small></article>
        <article className="summary-card"><span>待结余额</span><strong>{money(balance, folio.currency)}</strong><small>{balance === 0 ? '可以尝试退房' : '必须先结清'}</small></article>
      </div>

      {stay.status === 1 && (
        <article className="panel stay-operations">
          <div className="panel-heading">
            <div>
              <h3>在住操作</h3>
              <p>当前有效离店日 {effectiveEnd}。所有房态、库存和费用由后端在提交时重新校验。</p>
            </div>
          </div>
          <div className="operation-tabs">
            <button type="button" className={operation === 'ROOM_CHANGE' ? 'primary-button' : ''} onClick={() => setOperation('ROOM_CHANGE')}>换房</button>
            <button type="button" className={operation === 'EXTENSION' ? 'primary-button' : ''} onClick={() => setOperation('EXTENSION')}>延住</button>
            {canEarlyCheckout && <button type="button" className={operation === 'EARLY' ? 'primary-button' : ''} onClick={() => setOperation('EARLY')}>提前退房</button>}
            {canLateCheckout && <button type="button" className={operation === 'LATE' ? 'primary-button' : ''} onClick={() => setOperation('LATE')}>晚退登记</button>}
          </div>

          {operation === 'ROOM_CHANGE' && roomChangeOptions && (
            <div className="operation-form">
              <div className="operation-context">
                <span>当前房间 <strong>{currentRoom?.roomNumber}</strong></span>
                <span>剩余日期 <strong>{roomChangeOptions.remainingFrom} → {roomChangeOptions.effectiveCheckOutDate}</strong></span>
              </div>
              <label>
                新房间
                <select value={selectedRoomId} onChange={(event) => setSelectedRoomId(event.target.value)}>
                  <option value="">请选择后端确认可用的房间</option>
                  {roomChangeOptions.rooms.map((room) => (
                    <option key={room.id} value={room.id}>
                      {room.roomNumber} · {room.roomTypeName} · {room.floor} 层 · {roomStatus(room.status)}
                    </option>
                  ))}
                </select>
              </label>
              {roomChangeOptions.rooms.length === 0 && <p className="warning-banner">当前剩余日期没有可换入的房间。</p>}
              <ReasonField value={adjustmentReason} onChange={setAdjustmentReason} placeholder="例如：设施故障、住客合理要求" />
              <ActionConfirm
                label="确认换房"
                message="提交后旧房会关闭 Assignment、进入 MAINTENANCE 并创建 Turnover；新房将变为 OCCUPIED。"
                disabled={busy || !selectedRoomId || !adjustmentReason.trim()}
                onConfirm={() => void changeRoom()}
              />
            </div>
          )}

          {operation === 'EXTENSION' && (
            <div className="operation-form">
              <div className="operation-context"><span>当前有效离店日 <strong>{effectiveEnd}</strong></span></div>
              <label>
                新离店日
                <input type="date" min={nextDate(effectiveEnd)} value={newEnd} onChange={(event) => setNewEnd(event.target.value)} />
              </label>
              <ReasonField value={adjustmentReason} onChange={setAdjustmentReason} placeholder="记录住客延住原因" />
              <p className="workflow-note">后端会实时检查当前房间未来冲突，并按锁定价格把新增夜晚写入 Folio；此处不计算价格。</p>
              <ActionConfirm
                label="确认延住"
                message="确认提交延住？库存、锁定房价和 Folio charge 将由后端原子处理。"
                disabled={busy || !newEnd || !adjustmentReason.trim()}
                onConfirm={() => void adjustStay('EXTENSION')}
              />
            </div>
          )}

          {operation === 'EARLY' && (
            <div className="operation-form">
              <div className="operation-context">
                <span>原有效离店日 <strong>{effectiveEnd}</strong></span>
                <span>实际提前离店日 <strong>{today}</strong></span>
              </div>
              <ReasonField value={adjustmentReason} onChange={setAdjustmentReason} placeholder="记录提前离店原因" />
              <p className="workflow-note">后端将生成未住宿夜晚的 Folio reversal；完成调整后仍需复核余额并正式 Checkout。</p>
              <ActionConfirm
                label="确认提前退房调整"
                message="仅登记提前离店及账务调整，不会直接关闭 Stay 或释放房间。"
                disabled={busy || !adjustmentReason.trim()}
                onConfirm={() => void adjustStay('EARLY_CHECKOUT')}
              />
            </div>
          )}

          {operation === 'LATE' && (
            <div className="operation-form">
              <div className="operation-context"><span>有效离店日 <strong>{effectiveEnd}</strong></span></div>
              <ReasonField value={adjustmentReason} onChange={setAdjustmentReason} placeholder="记录晚退原因" />
              <p className="workflow-note">后端根据酒店时间、下一位住客冲突和锁定房价决定是否收费；不会静默修改日期。</p>
              <ActionConfirm
                label="确认晚退登记"
                message="确认登记晚退？后端将按当前规则决定费用及冲突提示。"
                disabled={busy || !adjustmentReason.trim()}
                onConfirm={() => void adjustStay('LATE_CHECKOUT')}
              />
            </div>
          )}
        </article>
      )}

      <div className="detail-columns">
        <article className="panel">
          <div className="panel-heading"><div><h3>实际住客</h3><p>入住时冻结到 Stay 的住客关系</p></div></div>
          {guests.map((guest) => (
            <div className="guest-row" key={guest.id}>
              <strong>{guest.firstName} {guest.lastName}</strong>
              <span>{guest.role}</span>
            </div>
          ))}
        </article>

        <article className="panel">
          <div className="panel-heading"><div><h3>住宿状态</h3><p>实际住宿事实，不读取 Booking 状态代替</p></div></div>
          <dl className="detail-list compact">
            <div><dt>计划日期</dt><dd>{booking.checkInDate} → {booking.checkOutDate}</dd></div>
            <div><dt>实际入住</dt><dd>{dateTime(stay.actualCheckInTime)}</dd></div>
            <div><dt>实际退房</dt><dd>{dateTime(stay.actualCheckOutTime)}</dd></div>
            <div><dt>住客人数</dt><dd>{guests.length} / 合同 {booking.guestCount}</dd></div>
          </dl>
        </article>
      </div>

      <article className="panel">
        <div className="panel-heading"><div><h3>房间分配历史</h3><p>当前与历史 StayRoomAssignment</p></div></div>
        <div className="responsive-table">
          <table className="compact-table">
            <thead><tr><th>房间</th><th>类型</th><th>开始</th><th>结束</th><th>原因</th><th>房态</th></tr></thead>
            <tbody>
              {assignments.map((assignment) => {
                const room = rooms[assignment.roomId]
                return (
                  <tr key={assignment.id}>
                    <td><strong>{room?.roomNumber || `Room #${assignment.roomId}`}</strong></td>
                    <td>{assignment.assignmentType}</td>
                    <td>{dateTime(assignment.startTime)}</td>
                    <td>{dateTime(assignment.endTime)}</td>
                    <td>{assignment.changeReason || '—'}</td>
                    <td>{room ? roomStatus(room.status) : '—'}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </article>

      <article className="panel">
        <div className="panel-heading"><div><h3>入住调整记录</h3><p>延住、提前离店与晚退均保留独立事实；Booking 合同保持不变。</p></div></div>
        {adjustments.length === 0 ? <p className="empty-state">当前没有入住期间调整。</p> : (
          <div className="responsive-table">
            <table className="compact-table">
              <thead><tr><th>类型</th><th>有效日期</th><th>原因</th><th>费用依据</th><th>操作时间</th></tr></thead>
              <tbody>
                {adjustments.map((adjustment) => (
                  <tr key={adjustment.id}>
                    <td><strong>{adjustmentType(adjustment.adjustmentType)}</strong></td>
                    <td>{adjustment.oldEnd} → {adjustment.newEnd}</td>
                    <td>{adjustment.reason}<small>{adjustment.operationalNotice}</small></td>
                    <td>{adjustment.lockedRate == null ? '以 Folio 明细为准' : `${money(adjustment.lockedRate, folio.currency)} · ${adjustment.rateSource}`}</td>
                    <td>{dateTime(adjustment.effectiveTime)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </article>

      <article className="panel">
        <div className="panel-heading">
          <div><h3>Folio #{folio.id}</h3><p>Stay 实际账务；不使用 Booking.totalPrice 代替。</p></div>
          <strong>{folioStatus(folio.status)}</strong>
        </div>
        <div className="folio-totals">
          <span>消费 <strong>{money(folio.totalAmount, folio.currency)}</strong></span>
          <span>已付 <strong>{money(folio.paidAmount, folio.currency)}</strong></span>
          <span>已退 <strong>{money(folio.refundedAmount, folio.currency)}</strong></span>
          <span>转 AR <strong>{money(folio.arTransferredAmount, folio.currency)}</strong></span>
          <span>余额 <strong>{money(balance, folio.currency)}</strong></span>
        </div>
        <div className="responsive-table">
          <table className="compact-table">
            <thead><tr><th>服务日</th><th>项目</th><th>说明</th><th>数量</th><th>金额</th><th>来源</th></tr></thead>
            <tbody>
              {folio.items.map((item) => (
                <tr key={item.id}>
                  <td>{item.businessDate}</td><td>{item.itemType}</td><td>{item.description}</td>
                  <td>{item.quantity}</td><td>{money(item.amount, folio.currency)}</td><td>{item.sourceType || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {folio.payments.length > 0 && (
          <div className="payment-list">
            <h4>付款记录</h4>
            {folio.payments.map((payment) => (
              <div className="record-row" key={payment.id}>
                <span>{payment.paymentMethod}<small>{payment.referenceNo || payment.status}</small></span>
                <strong>{money(payment.amount, folio.currency)}</strong>
                <small>{dateTime(payment.paidTime)}</small>
              </div>
            ))}
          </div>
        )}
      </article>

      {stay.status === 1 && balance > 0 && (
        <article className="action-panel checkout-blocker">
          <div><p className="kicker">CHECKOUT BLOCKED</p><h3>Folio 尚未结清</h3><p>后端会拒绝退房。请记录现实中已经成功的付款；现金付款还要求当前员工有 active Cashier Shift。</p></div>
          <label>付款方式<select value={paymentMethod} onChange={(event) => setPaymentMethod(event.target.value)}><option>CREDIT_CARD</option><option>DEBIT_CARD</option><option>ONLINE</option><option>OTHER</option><option>CASH</option></select></label>
          <label>外部参考号<input value={referenceNo} onChange={(event) => setReferenceNo(event.target.value)} maxLength={100} placeholder="收据号 / provider reference" /></label>
          <div className="actions"><button className="primary-button" disabled={busy} onClick={() => void settleBalance()}>记录已成功付款 {money(balance, folio.currency)}</button></div>
        </article>
      )}

      {stay.status === 1 && balance === 0 && (
        <article className="action-panel">
          <div><p className="kicker">READY FOR DEPARTURE</p><h3>办理退房</h3><p>后端将再次核验账务完整性。成功后房间进入 MAINTENANCE，并创建 Turnover；不会自动变为 AVAILABLE。</p></div>
          <div className="actions">
            <ActionConfirm
              label="确认退房"
              message={`确认办理房间 ${currentRoom?.roomNumber || ''} 退房？退房后房间将进入 MAINTENANCE。`}
              tone="danger"
              disabled={busy}
              onConfirm={() => void checkout()}
            />
          </div>
        </article>
      )}

      {stay.status === 2 && (
        <article className="panel turnover-panel">
          <div className="panel-heading">
            <div><h3>退房与房务衔接</h3><p>房间保持 MAINTENANCE，等待清洁、检查及人工放房。</p></div>
            {assignments[0] && <Link to={`/housekeeping?roomId=${assignments[0].roomId}`}>查看客房清洁任务 →</Link>}
          </div>
          {turnovers.length === 0 ? <p className="warning-banner">尚未读取到 Turnover，请刷新或检查房务任务。</p> : turnovers.map((task) => (
            <div className="record-row" key={task.id}>
              <span>房间 {rooms[task.roomId]?.roomNumber || `#${task.roomId}`}<small>Turnover #{task.id} · Task #{task.taskId}</small></span>
              <strong>{turnoverStatus(task.status)}</strong>
              <small>{dateTime(task.createTime)}</small>
            </div>
          ))}
        </article>
      )}
    </section>
  )
}

function folioStatus(status: number) {
  return ({ 0: 'PENDING', 1: 'COMPLETED', 2: 'REFUND_PENDING' } as Record<number, string>)[status] || `状态 ${status}`
}

function roomStatus(status: number) {
  return ({ 0: 'DISABLED', 1: 'AVAILABLE', 2: 'BOOKED', 3: 'MAINTENANCE', 4: 'OCCUPIED' } as Record<number, string>)[status] || `状态 ${status}`
}

function turnoverStatus(status: number) {
  return ({ 0: '待认领', 1: '处理中', 2: '已完成' } as Record<number, string>)[status] || `状态 ${status}`
}

function adjustmentType(type: number) {
  return ({ 1: '延住', 2: '提前退房', 3: '晚退' } as Record<number, string>)[type] || `调整 ${type}`
}

function nextDate(date: string) {
  if (!date) return undefined
  const value = new Date(`${date}T00:00:00`)
  value.setDate(value.getDate() + 1)
  return value.toISOString().slice(0, 10)
}

function ReasonField({ value, onChange, placeholder }: {
  value: string
  onChange: (value: string) => void
  placeholder: string
}) {
  return (
    <label>
      原因 / 备注
      <textarea value={value} onChange={(event) => onChange(event.target.value)} maxLength={255} placeholder={placeholder} />
    </label>
  )
}

function ActionConfirm({ label, message: confirmation, onConfirm, disabled, tone = 'primary' }: {
  label: string
  message: string
  onConfirm: () => void
  disabled: boolean
  tone?: 'primary' | 'danger'
}) {
  return (
    <details className={`action-confirm ${disabled ? 'disabled' : ''}`}>
      <summary className={tone === 'danger' ? 'danger-button' : 'primary-button'}>{label}</summary>
      <div className="inline-confirm" role="alert">
        <p>{confirmation}</p>
        <button type="button" className={tone === 'danger' ? 'danger-button' : 'primary-button'} disabled={disabled} onClick={onConfirm}>
          确认执行
        </button>
      </div>
    </details>
  )
}

function message(cause: unknown) {
  if (cause instanceof ApiError) {
    if (cause.status === 409) return `状态已被其他员工更新：${cause.message}`
    if (cause.status === 403) return '你没有执行该操作的权限。'
    if (cause.status === 404) return '住宿或关联资源不存在。'
  }
  if (cause instanceof Error && cause.message.includes('Explicit Early Checkout')) {
    return '当前日期早于计划离店日，请先完成“提前退房”调整。'
  }
  if (cause instanceof Error && cause.message.includes('Extension conflicts')) {
    return '延住日期与该房间未来预订冲突。请先换房，再提交延住。'
  }
  return cause instanceof Error ? cause.message : '请求失败'
}
