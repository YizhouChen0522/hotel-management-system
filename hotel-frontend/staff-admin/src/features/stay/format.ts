export function stayStatus(status: number) {
  return status === 1 ? '在住' : status === 2 ? '已退房' : `未知 (${status})`
}
