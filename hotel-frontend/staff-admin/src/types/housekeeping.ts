import type { PageResult, Room } from './reservation'
import type { TurnoverTask } from './stay'

export interface CleaningRecord {
  id: number
  roomId: number
  stayId: number
  assignmentId: number
  taskId: number
  cleaningType: number
  completedBy: number
  startedTime: string | null
  completedTime: string
  notes: string | null
}

export interface RoomInspection {
  id: number
  roomId: number
  stayId: number
  assignmentId: number
  turnoverTaskId: number
  cleaningRecordId: number | null
  repairOrderId: number | null
  inspectorId: number | null
  followupTaskId: number | null
  status: number
  failureReason: number | null
  notes: string | null
  inspectedTime: string | null
  createTime: string
}

export interface RoomWorkOrder {
  id: number
  taskId: number
  roomId: number
  damageType: string
  description: string
  severity: number
  status: number
  blocksRoomRelease: boolean
  createTime: string
}

export interface HousekeepingTaskView {
  task: {
    id: number
    taskType: number
    status: number
    title: string
  }
  assignments: Array<{
    id: number
    assigneeUserId: number
    status: number
    isCurrent: number
  }>
}

export interface HousekeepingRow {
  turnover: TurnoverTask
  room: Room
  cleaning: CleaningRecord | null
  inspection: RoomInspection | null
  blockingOrders: RoomWorkOrder[]
  followupTask: HousekeepingTaskView | null
}

export type HousekeepingPageResult = PageResult<TurnoverTask>

