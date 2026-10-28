import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError } from '../lib/api/client'
import { dateTime } from '../features/reservation/format'
import {
  acceptTurnover,
  acknowledgeHousekeepingTask,
  claimHousekeepingTask,
  completeTurnover,
  completeReworkCleaning,
  decideInspection,
  getHousekeepingTask,
  getRoom,
  getTurnover,
  listBlockingWorkOrders,
  listCleaningRecords,
  listInspections,
  listTurnovers,
  releaseRoom,
} from '../features/housekeeping/housekeeping-api'
import type { HousekeepingRow } from '../types/housekeeping'
import { readSession } from '../lib/auth/session'

const PAGE_SIZE = 10

export function HousekeepingPage() {
  const [searchParams] = useSearchParams()
  const roomFilter = Number(searchParams.get('roomId')) || null
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('')
  const [rows, setRows] = useState<HousekeepingRow[]>([])
  const [total, setTotal] = useState(0)
  const [hasNext, setHasNext] = useState(false)
  const [notes, setNotes] = useState<Record<number, string>>({})
  const [failureReasons, setFailureReasons] = useState<Record<number, string>>({})
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const result = await listTurnovers(
        page,
        PAGE_SIZE,
        status === '' ? undefined : Number(status),
        roomFilter,
      )
      const detailed = await Promise.all(result.items.map(async (summary) => {
        const turnover = await getTurnover(summary.id)
        const [room, cleaningPage, inspectionPage, workOrderPage] = await Promise.all([
          getRoom(turnover.roomId),
          listCleaningRecords(turnover.stayId),
          listInspections(turnover.roomId),
          listBlockingWorkOrders(turnover.roomId),
        ])
        const cleaning = cleaningPage.items.find((record) => record.taskId === turnover.taskId) ?? null
        const inspection = inspectionPage.items.find(
          (candidate) => candidate.turnoverTaskId === turnover.id,
        ) ?? null
        const followupTask = inspection?.status === 2
          && inspection.failureReason === 0
          && inspection.followupTaskId != null
          ? await getHousekeepingTask(inspection.followupTaskId)
          : null
        return {
          turnover,
          room,
          cleaning,
          inspection,
          blockingOrders: workOrderPage.items.filter((order) => order.blocksRoomRelease),
          followupTask,
        }
      }))
      setRows(detailed)
      setTotal(result.total)
      setHasNext(result.hasNext)
    } catch (cause) {
      setError(message(cause))
    } finally {
      setLoading(false)
    }
  }, [page, roomFilter, status])

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])

  async function run(id: number, action: () => Promise<unknown>, success: string) {
    setBusyId(id)
    setError('')
    setNotice('')
    try {
      await action()
      setNotice(success)
      await load()
    } catch (cause) {
      setError(message(cause))
      await load()
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section>
      <div className="page-heading">
        <div>
          <p className="kicker">HOUSEKEEPING</p>
          <h2>客房清洁与放房</h2>
          <p>从退房周转到清洁、检查及人工放房；检查通过也不会自动改变房态。</p>
        </div>
        <span className="count-pill">{total} 条</span>
      </div>

      <div className="filter-bar">
        <label>
          周转状态
          <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(1) }}>
            <option value="">全部</option>
            <option value="0">待认领</option>
            <option value="1">清洁中</option>
            <option value="2">清洁完成</option>
          </select>
        </label>
        {roomFilter != null && <span className="filter-note">当前仅显示 Room #{roomFilter}</span>}
      </div>

      {error && <p className="error-banner">{error}</p>}
      {notice && <p className="success-banner">{notice}</p>}
      {loading && rows.length === 0 && <p className="state-card">正在读取房务任务…</p>}
      {!loading && rows.length === 0 && <p className="empty-state">当前没有符合条件的周转任务。</p>}

      <div className="housekeeping-grid">
        {rows.map((row) => (
          <TurnoverCard
            key={row.turnover.id}
            row={row}
            busy={busyId === row.turnover.id}
            note={notes[row.turnover.id] ?? ''}
            failureReason={failureReasons[row.turnover.id] ?? 'CLEANING_INCOMPLETE'}
            onNote={(value) => setNotes((current) => ({ ...current, [row.turnover.id]: value }))}
            onFailureReason={(value) => setFailureReasons((current) => ({
              ...current,
              [row.turnover.id]: value,
            }))}
            onAccept={() => run(
              row.turnover.id,
              () => acceptTurnover(row.turnover.id),
              `房间 ${row.room.roomNumber} 已认领，清洁状态进入处理中。`,
            )}
            onComplete={() => run(
              row.turnover.id,
              () => completeTurnover(row.turnover.id, notes[row.turnover.id] ?? ''),
              `房间 ${row.room.roomNumber} 清洁完成，已产生待检查记录。`,
            )}
            onPass={() => run(
              row.turnover.id,
              () => decideInspection(row.inspection!.id, true, null, notes[row.turnover.id] ?? ''),
              `房间 ${row.room.roomNumber} 已通过检查，仍需人工放房。`,
            )}
            onFail={() => run(
              row.turnover.id,
              () => decideInspection(
                row.inspection!.id,
                false,
                failureReasons[row.turnover.id] ?? 'CLEANING_INCOMPLETE',
                notes[row.turnover.id] ?? '',
              ),
              `房间 ${row.room.roomNumber} 检查未通过，后续任务已由后端创建。`,
            )}
            onRelease={() => run(
              row.turnover.id,
              () => releaseRoom(row.room.id),
              `房间 ${row.room.roomNumber} 已通过全部 gate 并人工释放为 AVAILABLE。`,
            )}
            onClaimRework={() => run(
              row.turnover.id,
              () => claimHousekeepingTask(row.inspection!.followupTaskId!),
              `房间 ${row.room.roomNumber} 的返工任务已认领，Todo 保持待确认。`,
            )}
            onAcknowledgeRework={() => run(
              row.turnover.id,
              () => acknowledgeHousekeepingTask(row.inspection!.followupTaskId!),
              `房间 ${row.room.roomNumber} 的返工任务已开始。`,
            )}
            onCompleteRework={() => run(
              row.turnover.id,
              () => completeReworkCleaning(
                row.inspection!.followupTaskId!,
                notes[row.turnover.id] ?? '',
              ),
              `房间 ${row.room.roomNumber} 返工完成，已生成新的待检查记录。`,
            )}
          />
        ))}
      </div>

      <div className="pagination-controls">
        <button disabled={page === 1 || loading} onClick={() => setPage((value) => value - 1)}>上一页</button>
        <span>第 {page} 页</span>
        <button disabled={!hasNext || loading} onClick={() => setPage((value) => value + 1)}>下一页</button>
      </div>
    </section>
  )
}

interface CardProps {
  row: HousekeepingRow
  busy: boolean
  note: string
  failureReason: string
  onNote: (value: string) => void
  onFailureReason: (value: string) => void
  onAccept: () => void
  onComplete: () => void
  onPass: () => void
  onFail: () => void
  onRelease: () => void
  onClaimRework: () => void
  onAcknowledgeRework: () => void
  onCompleteRework: () => void
}

function TurnoverCard(props: CardProps) {
  const { row } = props
  const { turnover, room, cleaning, inspection, blockingOrders } = row
  const canAttemptRelease = room.status === 3 && inspection?.status === 1
  const blocker = releaseBlocker(row)
  const currentUserId = readSession()?.user.id
  const currentFollowupAssignment = row.followupTask?.assignments.find(
    (assignment) => assignment.isCurrent === 1,
  )

  return (
    <article className="housekeeping-card">
      <div className="housekeeping-card-heading">
        <div>
          <p className="kicker">ROOM {room.roomNumber}</p>
          <h3>{room.floor} 层 · {room.roomTypeName || '客房'}</h3>
          <p>来自 <Link to={`/stays/${turnover.stayId}`}>Stay #{turnover.stayId}</Link> 的退房周转</p>
        </div>
        <span className={`room-status room-${room.status}`}>{roomStatus(room.status)}</span>
      </div>

      <div className="housekeeping-progress" aria-label="房务进度">
        <Progress label="周转" value={turnoverStatus(turnover.status)} done={turnover.status === 2} />
        <Progress label="清洁" value={cleaning ? '已完成' : turnover.status === 1 ? '进行中' : '待处理'} done={cleaning != null} />
        <Progress label="检查" value={inspectionStatus(inspection?.status)} done={inspection?.status === 1} />
        <Progress label="放房" value={room.status === 1 ? '已放房' : '未放房'} done={room.status === 1} />
      </div>

      <dl className="detail-list compact">
        <div><dt>创建时间</dt><dd>{dateTime(turnover.createTime)}</dd></div>
        <div><dt>当前负责人</dt><dd>{turnover.acceptedBy ? `员工 #${turnover.acceptedBy}` : '尚未认领'}</dd></div>
        <div><dt>清洁记录</dt><dd>{cleaning ? `${dateTime(cleaning.completedTime)} 完成` : '尚未产生'}</dd></div>
        <div><dt>检查结果</dt><dd>{inspectionStatus(inspection?.status)}</dd></div>
      </dl>

      {blockingOrders.length > 0 && (
        <div className="blocker-box">
          <strong>阻止放房的维修单</strong>
          {blockingOrders.map((order) => (
            <p key={order.id}>{order.damageType}：{order.description}（WorkOrder #{order.id}）</p>
          ))}
        </div>
      )}

      {inspection?.status === 2 && (
        <div className="blocker-box">
          <strong>检查未通过：{failureReasonLabel(inspection.failureReason)}</strong>
          <p>{inspection.notes || '后端已创建后续处理任务。'} {inspection.followupTaskId ? `Task #${inspection.followupTaskId}` : ''}</p>
        </div>
      )}

      {inspection?.status === 2 && inspection.failureReason === 0 && row.followupTask && (
        <div className="action-panel rework-panel">
          <div>
            <strong>清洁返工 Task #{row.followupTask.task.id}</strong>
            <p>{reworkStatus(row.followupTask.task.status, currentFollowupAssignment?.status)}</p>
          </div>
          {!currentFollowupAssignment && (
            <button className="primary-button" disabled={props.busy} onClick={props.onClaimRework}>认领返工任务</button>
          )}
          {currentFollowupAssignment != null
            && currentFollowupAssignment.assigneeUserId === currentUserId
            && currentFollowupAssignment.status === 0 && (
            <button className="primary-button" disabled={props.busy} onClick={props.onAcknowledgeRework}>确认开始返工</button>
          )}
          {currentFollowupAssignment != null
            && currentFollowupAssignment.assigneeUserId === currentUserId
            && currentFollowupAssignment.status === 1 && (
            <ActionConfirm
              label="完成返工并重新送检"
              message="确认返工清洁已经完成并生成新的检查？"
              disabled={props.busy}
              onConfirm={props.onCompleteRework}
            />
          )}
          {currentFollowupAssignment && currentFollowupAssignment.assigneeUserId !== currentUserId && (
            <span className="status-badge">已由员工 #{currentFollowupAssignment.assigneeUserId} 负责</span>
          )}
        </div>
      )}

      {room.status !== 1 && blocker && <p className="warning-banner">当前不能放房：{blocker}</p>}

      {turnover.status < 2 && (
        <label>
          清洁备注
          <textarea value={props.note} onChange={(event) => props.onNote(event.target.value)} maxLength={500} />
        </label>
      )}

      {turnover.status === 0 && (
        <button className="primary-button" disabled={props.busy} onClick={props.onAccept}>认领并开始清洁</button>
      )}

      {turnover.status === 1 && (
        <ActionConfirm
          label="完成清洁并送检"
          message="确认客房清洁已经完成并送交检查？"
          disabled={props.busy}
          onConfirm={props.onComplete}
        />
      )}

      {turnover.status === 2 && inspection?.status === 0 && (
        <div className="inspection-actions">
          <label>
            未通过原因
            <select value={props.failureReason} onChange={(event) => props.onFailureReason(event.target.value)}>
              <option value="CLEANING_INCOMPLETE">清洁不完整</option>
              <option value="FACILITY_DAMAGE">设施损坏</option>
              <option value="STRUCTURAL_DAMAGE">结构性损坏</option>
            </select>
          </label>
          <label>
            检查备注
            <textarea value={props.note} onChange={(event) => props.onNote(event.target.value)} maxLength={500} />
          </label>
          <div className="actions">
            <ActionConfirm
              label="检查通过"
              message="确认该客房已经通过房务检查？"
              disabled={props.busy}
              onConfirm={props.onPass}
            />
            <ActionConfirm
              label="检查不通过"
              message="确认检查不通过并创建后续处理任务？"
              tone="danger"
              disabled={props.busy || props.note.trim() === ''}
              onConfirm={props.onFail}
            />
          </div>
        </div>
      )}

      {canAttemptRelease && (
        <ActionConfirm
          label="人工确认放房"
          message="确认请求后端重新检查全部放房条件并人工放房？"
          disabled={props.busy}
          onConfirm={props.onRelease}
        />
      )}
    </article>
  )
}

interface ActionConfirmProps {
  label: string
  message: string
  onConfirm: () => void
  disabled: boolean
  tone?: 'primary' | 'danger'
}

function ActionConfirm({ label, message, onConfirm, disabled, tone = 'primary' }: ActionConfirmProps) {
  return (
    <details className={`action-confirm ${disabled ? 'disabled' : ''}`}>
      <summary className={tone === 'danger' ? 'danger-button' : 'primary-button'}>{label}</summary>
      <div className="inline-confirm" role="alert">
        <p>{message}</p>
        <button className={tone === 'danger' ? 'danger-button' : 'primary-button'} disabled={disabled} onClick={onConfirm}>
          确认执行
        </button>
      </div>
    </details>
  )
}

function Progress({ label, value, done }: { label: string; value: string; done: boolean }) {
  return <div className={done ? 'done' : ''}><span>{label}</span><strong>{value}</strong></div>
}

function reworkStatus(taskStatus: number, assignmentStatus?: number) {
  if (taskStatus === 2) return '返工已完成，等待新的检查结果。'
  if (assignmentStatus === 0) return '已认领，员工需明确确认开始。'
  if (assignmentStatus === 1) return '返工进行中，完成后将自动生成新的检查。'
  return '待认领；认领不会自动开始 Todo。'
}

function releaseBlocker(row: HousekeepingRow) {
  if (row.room.status === 1) return null
  if (row.room.status !== 3) return `房态为 ${roomStatus(row.room.status)}`
  if (row.turnover.status !== 2) return '周转清洁尚未完成'
  if (!row.inspection) return '清洁完成后尚未生成检查记录'
  if (row.inspection.status === 0) return '等待房务检查'
  if (row.inspection.status === 2) return '最近一次检查未通过，需要完成后续任务并重新检查'
  if (row.blockingOrders.length > 0) return '存在未完成且阻止放房的 WorkOrder'
  return null
}

function turnoverStatus(status: number) {
  return ({ 0: '待认领', 1: '清洁中', 2: '清洁完成', 3: '已取消', 4: '已阻塞' } as Record<number, string>)[status] || `状态 ${status}`
}

function inspectionStatus(status?: number) {
  if (status == null) return '等待清洁完成'
  return ({ 0: '待检查', 1: '已通过', 2: '未通过' } as Record<number, string>)[status] || `状态 ${status}`
}

function roomStatus(status: number) {
  return ({ 0: 'DISABLED', 1: 'AVAILABLE', 2: 'BOOKED', 3: 'MAINTENANCE', 4: 'OCCUPIED' } as Record<number, string>)[status] || `状态 ${status}`
}

function failureReasonLabel(reason: number | null) {
  if (reason == null) return '未记录原因'
  return ({ 0: '清洁不完整', 1: '设施损坏', 2: '结构性损坏' } as Record<number, string>)[reason] || `原因 ${reason}`
}

function message(cause: unknown) {
  if (cause instanceof ApiError) {
    if (cause.status === 409) return `任务状态刚刚被其他员工更新：${cause.message}`
    if (cause.status === 403) return '你没有执行该房务操作的权限。'
    if (cause.status === 404) return '房务任务或关联资源不存在。'
  }
  return cause instanceof Error ? cause.message : '请求失败'
}

