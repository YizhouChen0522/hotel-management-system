import { useState, type FormEvent } from 'react'
import type { CmsSection, SectionWriteRequest } from '../../types/cms'

const types = ['HERO', 'TEXT', 'CTA', 'IMAGE', 'GALLERY', 'VIDEO', 'PROMOTION_LIST', 'ROOM_TYPE_SHOWCASE', 'MODEL_SCENE', 'LOCATION', 'CUSTOM_STRUCTURED']
function objectPayload(raw?: string): Record<string, unknown> { try { const parsed = JSON.parse(raw || '{}'); return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {} } catch { return {} } }

export function SectionEditor({ section, nextOrder, onSave, onCancel }: { section?: CmsSection; nextOrder: number; onSave: (request: SectionWriteRequest) => Promise<void>; onCancel: () => void }) {
  const initial = objectPayload(section?.payload)
  const [sectionKey, setKey] = useState(section?.sectionKey || '')
  const [sectionType, setType] = useState(section?.sectionType || 'HERO')
  const [sortOrder, setOrder] = useState(section?.sortOrder ?? nextOrder)
  const [visibility, setVisibility] = useState<'PUBLIC' | 'INTERNAL'>(section?.visibility || 'PUBLIC')
  const [title, setTitle] = useState(typeof initial.title === 'string' ? initial.title : '')
  const [mediaId, setMediaId] = useState(typeof initial.mediaId === 'number' ? String(initial.mediaId) : '')
  const [secondary, setSecondary] = useState(typeof initial.subtitle === 'string' ? initial.subtitle : typeof initial.body === 'string' ? initial.body : typeof initial.description === 'string' ? initial.description : '')
  const [raw, setRaw] = useState(section?.payload || '{}')
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  const friendly = ['HERO', 'TEXT', 'CTA'].includes(sectionType)
  async function submit(event: FormEvent) { event.preventDefault(); setError(''); setBusy(true); try { let payload = raw; if (friendly) payload = JSON.stringify(sectionType === 'HERO' ? { title, subtitle: secondary, ...(mediaId ? { mediaId: Number(mediaId) } : {}) } : sectionType === 'TEXT' ? { title, body: secondary } : { title, description: secondary }); else JSON.parse(raw); await onSave({ sectionKey, sectionType, sortOrder, payload, visibility, expectedVersion: section?.version ?? 0 }) } catch (cause) { setError(cause instanceof Error ? cause.message : '保存失败') } finally { setBusy(false) } }
  return <form className="panel form-grid" onSubmit={submit}><h3>{section ? '编辑 Section' : '新增 Section'}</h3><label>Key<input required value={sectionKey} onChange={(event) => setKey(event.target.value)} /></label><label>Type<select value={sectionType} onChange={(event) => setType(event.target.value)}>{types.map((type) => <option key={type}>{type}</option>)}</select></label><label>顺序<input type="number" required value={sortOrder} onChange={(event) => setOrder(Number(event.target.value))} /></label><label>可见性<select value={visibility} onChange={(event) => setVisibility(event.target.value as 'PUBLIC' | 'INTERNAL')}><option>PUBLIC</option><option>INTERNAL</option></select></label>{friendly ? <><label>标题<input value={title} onChange={(event) => setTitle(event.target.value)} /></label>{sectionType === 'HERO' ? <label>Hero Media ID（IMAGE/VIDEO）<input type="number" value={mediaId} onChange={(event) => setMediaId(event.target.value)} /></label> : null}<label>{sectionType === 'HERO' ? '副标题' : sectionType === 'TEXT' ? '正文' : '说明'}<textarea rows={5} value={secondary} onChange={(event) => setSecondary(event.target.value)} /></label></> : <label className="wide">结构化 JSON<textarea rows={10} required value={raw} onChange={(event) => setRaw(event.target.value)} /></label>}{error ? <p className="error wide">{error}</p> : null}<div className="actions wide"><button disabled={busy}>{busy ? '保存中…' : '保存'}</button><button type="button" onClick={onCancel}>取消</button></div></form>
}
