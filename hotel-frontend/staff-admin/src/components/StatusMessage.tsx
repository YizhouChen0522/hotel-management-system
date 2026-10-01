export function StatusMessage({ loading, error, empty }: { loading?: boolean; error?: string; empty?: boolean }) {
  if (loading) return <p className="notice">加载中…</p>
  if (error) return <p className="notice error" role="alert">{error}</p>
  if (empty) return <p className="notice">暂无数据。</p>
  return null
}
