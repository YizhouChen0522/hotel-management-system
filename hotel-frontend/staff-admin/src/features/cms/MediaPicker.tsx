import { useCallback, useEffect, useMemo, useState } from 'react'
import { listMedia, uploadMedia } from './cms-api'
import { resolveApiUrl } from '../../lib/api/client'
import type { CmsMedia, MediaType } from '../../types/cms'

type Props = {
  allowed: MediaType[]
  multiple?: boolean
  value: number[]
  onChange: (ids: number[]) => void
}

export function MediaPicker({ allowed, multiple = false, value, onChange }: Props) {
  const [items, setItems] = useState<CmsMedia[]>([])
  const [error, setError] = useState('')
  const [uploading, setUploading] = useState(false)

  const allowedKey = allowed.join(',')
  const allowedTypes = useMemo(() => allowedKey.split(',') as MediaType[], [allowedKey])

  const load = useCallback(async () => {
    try {
      const response = await listMedia(1, 100)
      setItems(response.items.filter((item) => item.status === 'ACTIVE' && allowedTypes.includes(item.assetType)))
      setError('')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '媒体加载失败')
    }
  }, [allowedTypes])

  useEffect(() => {
    let active = true
    listMedia(1, 100)
      .then((response) => {
        if (!active) return
        setItems(response.items.filter((item) => item.status === 'ACTIVE' && allowedTypes.includes(item.assetType)))
        setError('')
      })
      .catch((cause: unknown) => {
        if (active) setError(cause instanceof Error ? cause.message : '媒体加载失败')
      })
    return () => {
      active = false
    }
  }, [allowedTypes])

  async function upload(file: File) {
    const assetType = allowed[0]
    setUploading(true)
    setError('')
    try {
      const media = await uploadMedia(assetType, file)
      await load()
      onChange(multiple ? [...value, media.id] : [media.id])
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '上传失败')
    } finally {
      setUploading(false)
    }
  }

  function toggle(id: number) {
    if (!multiple) {
      onChange([id])
      return
    }
    onChange(value.includes(id) ? value.filter((item) => item !== id) : [...value, id])
  }

  return (
    <div className="media-picker">
      <div className="media-picker-grid">
        {items.map((item) => (
          <button
            type="button"
            className={value.includes(item.id) ? 'media-choice selected' : 'media-choice'}
            key={item.id}
            onClick={() => toggle(item.id)}
          >
            {item.assetType === 'IMAGE' ? (
              <img src={resolveApiUrl(item.publicUrl)} alt={item.originalFilename} />
            ) : (
              <span>{item.assetType}</span>
            )}
            <small>{item.originalFilename}</small>
          </button>
        ))}
      </div>
      {!items.length && <p className="empty-state">还没有可用素材，可在这里直接上传。</p>}
      <label className="compact-upload">
        {uploading ? '上传中…' : '上传新素材'}
        <input
          type="file"
          disabled={uploading}
          accept={allowed.includes('IMAGE') ? 'image/jpeg,image/png,image/webp' : 'video/mp4,video/webm'}
          onChange={(event) => {
            const file = event.target.files?.[0]
            if (file) void upload(file)
          }}
        />
      </label>
      {error && <p className="error">{error}</p>}
    </div>
  )
}
