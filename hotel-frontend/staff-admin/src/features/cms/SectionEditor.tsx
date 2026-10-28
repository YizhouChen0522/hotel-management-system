import { useState, type FormEvent } from 'react'
import type { CmsSection, SectionWriteRequest } from '../../types/cms'
import { MediaPicker } from './MediaPicker'

const types = [
  ['TEXT', '正文'],
  ['IMAGE', '单张图片'],
  ['VIDEO', '视频'],
  ['GALLERY', '图片画廊'],
  ['CTA', '行动按钮'],
  ['HERO', '头图 / 视频 Hero'],
  ['PROMOTION_LIST', '促销列表'],
] as const

function objectPayload(raw?: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(raw || '{}')
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

function text(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function mediaIds(payload: Record<string, unknown>) {
  if (Array.isArray(payload.mediaIds)) {
    return payload.mediaIds.filter((id): id is number => typeof id === 'number')
  }
  return typeof payload.mediaId === 'number' ? [payload.mediaId] : []
}

export function SectionEditor({
  section,
  nextOrder,
  onSave,
  onCancel,
}: {
  section?: CmsSection
  nextOrder: number
  onSave: (request: SectionWriteRequest) => Promise<void>
  onCancel: () => void
}) {
  const initial = objectPayload(section?.payload)
  const [sectionType, setType] = useState(section?.sectionType || 'TEXT')
  const [title, setTitle] = useState(text(initial.title))
  const [body, setBody] = useState(
    text(initial.body) || text(initial.subtitle) || text(initial.description),
  )
  const [caption, setCaption] = useState(text(initial.caption))
  const [alt, setAlt] = useState(text(initial.alt))
  const [label, setLabel] = useState(text(initial.label))
  const [href, setHref] = useState(text(initial.href))
  const [selectedMedia, setSelectedMedia] = useState<number[]>(mediaIds(initial))
  const [advanced, setAdvanced] = useState(false)
  const [raw, setRaw] = useState(section?.payload || '{}')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const needsImage = sectionType === 'IMAGE' || sectionType === 'GALLERY'
  const needsVideo = sectionType === 'VIDEO'
  const hero = sectionType === 'HERO'

  function payload() {
    if (advanced) {
      JSON.parse(raw)
      return raw
    }
    switch (sectionType) {
      case 'HERO':
        return JSON.stringify({ title, subtitle: body, ...(selectedMedia[0] ? { mediaId: selectedMedia[0] } : {}) })
      case 'TEXT':
        return JSON.stringify({ title, body })
      case 'CTA':
        return JSON.stringify({ title, description: body, label, href })
      case 'IMAGE':
        return JSON.stringify({ mediaId: selectedMedia[0], alt, caption })
      case 'VIDEO':
        return JSON.stringify({ mediaId: selectedMedia[0], title })
      case 'GALLERY':
        return JSON.stringify({ title, mediaIds: selectedMedia })
      case 'PROMOTION_LIST':
        return JSON.stringify({ title })
      default:
        return raw
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      const key = section?.sectionKey || `${sectionType.toLowerCase()}-${Date.now().toString(36)}`
      await onSave({
        sectionKey: key,
        sectionType,
        sortOrder: section?.sortOrder ?? nextOrder,
        payload: payload(),
        visibility: section?.visibility || 'PUBLIC',
        expectedVersion: section?.version ?? 0,
      })
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '保存失败')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="panel block-editor" onSubmit={submit}>
      <div className="panel-heading">
        <div>
          <h3>{section ? '编辑内容块' : '添加内容块'}</h3>
          <p>选择内容类型并填写访客实际会看到的内容。</p>
        </div>
        <button type="button" className="text-button" onClick={() => setAdvanced(!advanced)}>
          {advanced ? '返回普通编辑' : '高级 JSON'}
        </button>
      </div>

      <label>
        内容类型
        <select value={sectionType} disabled={Boolean(section)} onChange={(event) => setType(event.target.value)}>
          {types.map(([value, name]) => <option value={value} key={value}>{name}</option>)}
        </select>
      </label>

      {advanced ? (
        <label>
          结构化内容
          <textarea rows={10} required value={raw} onChange={(event) => setRaw(event.target.value)} />
        </label>
      ) : (
        <>
          {['VIDEO', 'GALLERY', 'PROMOTION_LIST'].includes(sectionType) && (
            <label>
              标题
              <input value={title} onChange={(event) => setTitle(event.target.value)} />
            </label>
          )}
          {(sectionType === 'TEXT' || sectionType === 'CTA' || hero) && (
            <>
              <label>
                标题
                <input value={title} onChange={(event) => setTitle(event.target.value)} />
              </label>
              <label>
                {sectionType === 'TEXT' ? '正文' : '说明'}
                <textarea rows={6} value={body} onChange={(event) => setBody(event.target.value)} />
              </label>
            </>
          )}
          {sectionType === 'CTA' && (
            <div className="form-grid compact-grid">
              <label>按钮文字<input value={label} onChange={(event) => setLabel(event.target.value)} /></label>
              <label>链接地址<input placeholder="/booking" value={href} onChange={(event) => setHref(event.target.value)} /></label>
            </div>
          )}
          {(needsImage || needsVideo || hero) && (
            <>
              <label>选择{needsVideo ? '视频' : hero ? '图片或视频' : '图片'}</label>
              <MediaPicker
                allowed={needsVideo ? ['VIDEO'] : hero ? ['IMAGE', 'VIDEO'] : ['IMAGE']}
                multiple={sectionType === 'GALLERY'}
                value={selectedMedia}
                onChange={setSelectedMedia}
              />
            </>
          )}
          {sectionType === 'IMAGE' && (
            <div className="form-grid compact-grid">
              <label>图片说明<input value={caption} onChange={(event) => setCaption(event.target.value)} /></label>
              <label>无障碍描述<input value={alt} onChange={(event) => setAlt(event.target.value)} /></label>
            </div>
          )}
        </>
      )}

      {error && <p className="error">{error}</p>}
      <div className="actions">
        <button disabled={busy}>{busy ? '保存中…' : '保存内容块'}</button>
        <button type="button" onClick={onCancel}>取消</button>
      </div>
    </form>
  )
}
