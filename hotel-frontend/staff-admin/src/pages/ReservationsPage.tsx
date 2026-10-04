import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { StatusMessage } from '../components/StatusMessage'
import { listReservations } from '../features/reservation/reservation-api'
import { money, reservationStatus } from '../features/reservation/format'
import type { PageResult, Reservation } from '../types/reservation'

const empty: PageResult<Reservation> = {
  items: [],
  page: 1,
  pageSize: 20,
  total: 0,
  hasNext: false,
  searchMode: false,
}

export function ReservationsPage() {
  const [result, setResult] = useState(empty)
  const [status, setStatus] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [filters, setFilters] = useState({ status: '', startDate: '', endDate: '' })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async (page: number) => {
    setLoading(true)
    setError('')
    try {
      setResult(await listReservations({
        page,
        pageSize: 20,
        status: filters.status === '' ? undefined : Number(filters.status),
        startDate: filters.status === '' ? filters.startDate || undefined : undefined,
        endDate: filters.status === '' ? filters.endDate || undefined : undefined,
      }))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '预订列表加载失败')
    } finally {
      setLoading(false)
    }
  }, [filters])

  useEffect(() => {
    const timer = window.setTimeout(() => void load(1), 0)
    return () => window.clearTimeout(timer)
  }, [load])

  function search(event: FormEvent) {
    event.preventDefault()
    if ((startDate && !endDate) || (!startDate && endDate)) {
      setError('到店日期筛选需要同时填写开始和结束日期')
      return
    }
    setFilters({ status, startDate, endDate })
  }

  return (
    <section>
      <div className="page-heading">
        <div>
          <p className="kicker">FRONT DESK</p>
          <h2>预订管理</h2>
          <p>查看申请、确认库存并分配具体房间。价格和退款始终以后端为准。</p>
        </div>
      </div>

      <form className="filter-bar" onSubmit={search}>
        <label>
          状态
          <select value={status} onChange={(event) => setStatus(event.target.value)}>
            <option value="">全部状态</option>
            <option value="0">等待确认</option>
            <option value="1">已确认</option>
            <option value="4">已取消</option>
            <option value="5">酒店拒绝</option>
            <option value="6">未到店</option>
          </select>
        </label>
        <label>
          到店日期从
          <input type="date" value={startDate} disabled={status !== ''} onChange={(event) => setStartDate(event.target.value)} />
        </label>
        <label>
          到店日期至
          <input type="date" value={endDate} disabled={status !== ''} onChange={(event) => setEndDate(event.target.value)} />
        </label>
        <button className="primary-button">查询</button>
        <button type="button" onClick={() => { setStatus(''); setStartDate(''); setEndDate(''); setFilters({ status: '', startDate: '', endDate: '' }) }}>重置</button>
      </form>

      <StatusMessage loading={loading} error={error} empty={!loading && !error && result.items.length === 0} />
      {!loading && result.items.length > 0 && (
        <div className="table-card">
          <div className="responsive-table">
            <table>
              <thead><tr><th>预订</th><th>订房人</th><th>房型 / 日期</th><th>人数</th><th>来源</th><th>金额</th><th>状态</th><th /></tr></thead>
              <tbody>
                {result.items.map((booking) => (
                  <tr key={booking.id}>
                    <td><strong>#{booking.id}</strong><small>{booking.reservedRoomNumber ? `房间 ${booking.reservedRoomNumber}` : '尚未分房'}</small></td>
                    <td><strong>{booking.bookerName || '未登记姓名'}</strong><small>{booking.bookerEmail || `Guest #${booking.bookerGuestProfileId ?? '—'}`}</small></td>
                    <td><strong>{booking.roomTypeName || `Room Type #${booking.roomTypeId}`}</strong><small>{booking.checkInDate} → {booking.checkOutDate}</small></td>
                    <td>{booking.guestCount}</td>
                    <td>{booking.reservationSource}</td>
                    <td>{money(booking.totalPrice)}</td>
                    <td><span className={`status-badge status-${booking.status}`}>{reservationStatus(booking.status)}</span></td>
                    <td><Link className="text-link" to={`/reservations/${booking.id}`}>查看与处理</Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="pager">
            <button disabled={result.page <= 1 || loading} onClick={() => void load(result.page - 1)}>上一页</button>
            <span>第 {result.page} 页 · 共 {result.total} 条</span>
            <button disabled={!result.hasNext || loading} onClick={() => void load(result.page + 1)}>下一页</button>
          </div>
        </div>
      )}
    </section>
  )
}
