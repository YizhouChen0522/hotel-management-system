export function reservationStatus(status: number) {
  return ({ 0: '等待确认', 1: '已确认', 4: '已取消', 5: '酒店拒绝', 6: '未到店' } as Record<number, string>)[status] || `未知 (${status})`
}

export function money(value: number, currency = 'CNY') {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format(value)
}

export function dateTime(value?: string | null) {
  if (!value) return '—'
  const parts = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value)
  if (!parts) return value
  return `${parts[1]}年${Number(parts[2])}月${Number(parts[3])}日 ${parts[4]}:${parts[5]}`
}
