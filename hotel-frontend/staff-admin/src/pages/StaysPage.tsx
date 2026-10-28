import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { StatusMessage } from '../components/StatusMessage'
import {
  getBooking,
  getRoom,
  getStayAssignments,
  listStays,
} from '../features/stay/stay-api'
import { dateTime } from '../features/reservation/format'
import { stayStatus } from '../features/stay/format'
import type { PageResult } from '../types/reservation'
import type { Stay, StayListRow } from '../types/stay'

const empty: PageResult<Stay> = {
  items: [],
  page: 1,
  pageSize: 20,
  total: 0,
  hasNext: false,
  searchMode: false,
}

export function StaysPage() {
  const [result, setResult] = useState(empty)
  const [rows, setRows] = useState<StayListRow[]>([])
  const [status, setStatus] = useState('1')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async (page: number, selectedStatus = status) => {
    setLoading(true)
    setError('')
    try {
      const pageResult = await listStays(
        page,
        20,
        selectedStatus === '' ? undefined : Number(selectedStatus),
      )
      const hydrated = await Promise.all(pageResult.items.map(async (stay) => {
        const [bookingResult, assignmentResult] = await Promise.allSettled([
          getBooking(stay.bookingId),
          getStayAssignments(stay.id),
        ])
        const assignments = assignmentResult.status === 'fulfilled' ? assignmentResult.value : []
        const assignment = assignments.find((item) => item.endTime == null) ?? assignments.at(-1) ?? null
        const roomResult = assignment ? await Promise.allSettled([getRoom(assignment.roomId)]) : []
        return {
          stay,
          booking: bookingResult.status === 'fulfilled' ? bookingResult.value : null,
          assignment,
          room: roomResult[0]?.status === 'fulfilled' ? roomResult[0].value : null,
        }
      }))
      setResult(pageResult)
      setRows(hydrated)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '住宿列表加载失败')
    } finally {
      setLoading(false)
    }
  }, [status])

  useEffect(() => {
    const timer = window.setTimeout(() => void load(1), 0)
    return () => window.clearTimeout(timer)
  }, [load])

  return (
    <section>
      <div className="page-heading">
        <div>
          <p className="kicker">IN-HOUSE OPERATIONS</p>
          <h2>住宿管理</h2>
          <p>查看在住房、实际住客、房间分配和退房状态。</p>
        </div>
      </div>

      <div className="filter-bar">
        <label>
          Stay 状态
          <select
            value={status}
            onChange={(event) => {
              const value = event.target.value
              setStatus(value)
              void load(1, value)
            }}
          >
            <option value="">全部</option>
            <option value="1">在住</option>
            <option value="2">已退房</option>
          </select>
        </label>
      </div>

      <StatusMessage loading={loading} error={error} empty={!loading && rows.length === 0} />
      {!loading && rows.length > 0 && (
        <div className="table-card">
          <div className="responsive-table">
            <table>
              <thead>
                <tr><th>住宿</th><th>住客 / 订房人</th><th>房间</th><th>计划日期</th><th>实际时间</th><th>状态</th><th /></tr>
              </thead>
              <tbody>
                {rows.map(({ stay, booking, room }) => (
                  <tr key={stay.id}>
                    <td><strong>Stay #{stay.id}</strong><small>Booking #{stay.bookingId}</small></td>
                    <td><strong>{booking?.bookerName || `Guest #${stay.primaryGuestId}`}</strong><small>{booking?.bookerEmail || '实际住客见详情'}</small></td>
                    <td><strong>{room ? `房间 ${room.roomNumber}` : '房间资料不可用'}</strong><small>{room?.roomTypeName || ''}</small></td>
                    <td>{booking ? <><strong>{booking.checkInDate}</strong><small>至 {booking.checkOutDate}</small></> : '—'}</td>
                    <td><strong>{dateTime(stay.actualCheckInTime)}</strong><small>{stay.actualCheckOutTime ? `离店 ${dateTime(stay.actualCheckOutTime)}` : '尚未退房'}</small></td>
                    <td><span className={`stay-badge stay-${stay.status}`}>{stayStatus(stay.status)}</span></td>
                    <td><Link className="text-link" to={`/stays/${stay.id}`}>查看住宿</Link></td>
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

