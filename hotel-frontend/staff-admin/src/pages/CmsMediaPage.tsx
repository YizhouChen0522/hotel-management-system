/* eslint-disable react-hooks/set-state-in-effect, react-hooks/exhaustive-deps */
import { useEffect, useState, type FormEvent } from 'react'
import { archiveMedia, listMedia, uploadMedia } from '../features/cms/cms-api'
import { ApiError, resolveApiUrl } from '../lib/api/client'
import type { CmsMedia, MediaType } from '../types/cms'

const formatSize = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1048576).toFixed(1)} MB`
}

export function CmsMediaPage() {
  const [items, setItems] = useState<CmsMedia[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [type, setType] = useState<MediaType>('IMAGE')
  const [pendingArchiveId, setPendingArchiveId] = useState<number | null>(null)

  const load = async () => {
    setBusy(true)
    setError('')
    try {
      const response = await listMedia(page, 20)
      setItems(response.items)
      setTotal(response.total)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '加载失败')
    } finally {
      setBusy(false)
    }
  }

  useEffect(() => {
    void load()
  }, [page])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const input = event.currentTarget.elements.namedItem('file') as HTMLInputElement
    if (!input.files?.[0]) {
      setError('请选择文件')
      return
    }

    setBusy(true)
    setError('')
    try {
      await uploadMedia(type, input.files[0])
      input.value = ''
      await load()
    } catch (uploadError) {
      setError(uploadError instanceof ApiError ? uploadError.message : '上传失败')
    } finally {
      setBusy(false)
    }
  }

  async function confirmArchive(id: number) {
    setBusy(true)
    setError('')
    try {
      await archiveMedia(id)
      setPendingArchiveId(null)
      await load()
    } catch (archiveError) {
      setError(archiveError instanceof Error ? archiveError.message : '归档失败')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section>
      <div className="page-heading">
        <div>
          <p className="kicker">PUBLIC WEBSITE</p>
          <h2>媒体库</h2>
          <p>先上传素材，再在 Promotion、Section 或 Scene 中按 Media ID 选择。</p>
        </div>
      </div>

      <form className="panel form-grid" onSubmit={submit}>
        <label>
          素材类型
          <select value={type} onChange={(event) => setType(event.target.value as MediaType)}>
            <option>IMAGE</option>
            <option>VIDEO</option>
            <option>MODEL_3D</option>
          </select>
        </label>
        <label>
          本地文件
          <input
            name="file"
            type="file"
            accept={
              type === 'IMAGE'
                ? 'image/jpeg,image/png,image/webp'
                : type === 'VIDEO'
                  ? 'video/mp4,video/webm'
                  : '.glb,.gltf'
            }
          />
        </label>
        <button disabled={busy}>{busy ? '上传中…' : '上传素材'}</button>
      </form>

      {error && <p className="error-banner">{error}</p>}
      {busy && !items.length ? (
        <p className="state-card">加载中…</p>
      ) : !items.length ? (
        <p className="empty-state">暂无媒体。</p>
      ) : (
        <div className="media-grid">
          {items.map((media) => (
            <article className="panel" key={media.id}>
              {media.assetType === 'IMAGE' ? (
                <img
                  className="media-preview"
                  src={resolveApiUrl(media.publicUrl)}
                  alt={media.originalFilename}
                />
              ) : media.assetType === 'VIDEO' ? (
                <video
                  className="media-preview"
                  src={resolveApiUrl(media.publicUrl)}
                  controls
                  preload="metadata"
                />
              ) : (
                <div className="media-placeholder">3D MODEL</div>
              )}
              <h3>{media.originalFilename}</h3>
              <p>Media ID #{media.id} · {media.assetType}</p>
              <p>{formatSize(media.sizeBytes)} · {media.status}</p>
              {media.status === 'ACTIVE' && pendingArchiveId !== media.id && (
                <button className="danger-button" onClick={() => setPendingArchiveId(media.id)}>
                  归档素材
                </button>
              )}
              {pendingArchiveId === media.id && (
                <div className="inline-confirm" role="alert">
                  <p>已发布内容引用的媒体无法归档。确认归档这项素材？</p>
                  <button disabled={busy} onClick={() => void confirmArchive(media.id)}>
                    确认归档
                  </button>
                  <button disabled={busy} onClick={() => setPendingArchiveId(null)}>
                    取消
                  </button>
                </div>
              )}
            </article>
          ))}
        </div>
      )}

      <div className="pager">
        <button disabled={page === 1} onClick={() => setPage((current) => current - 1)}>
          上一页
        </button>
        <span>第 {page} 页 · 共 {total} 条</span>
        <button disabled={page * 20 >= total} onClick={() => setPage((current) => current + 1)}>
          下一页
        </button>
      </div>
    </section>
  )
}
