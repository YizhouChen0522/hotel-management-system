export function reservationStatus(status: number) {
  return ({ 0: '等待确认', 1: '已确认', 4: '已取消', 5: '酒店拒绝', 6: '未到店' } as Record<number, string>)[status] || `未知 (${status})`
}

export function money(value: number, currency = 'CNY') {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format(value)
}

export function dateTime(value?: string | null) {
  return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'
}
