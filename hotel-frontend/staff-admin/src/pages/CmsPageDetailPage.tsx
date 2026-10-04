import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { StatusMessage } from '../components/StatusMessage'
import { ApiError } from '../lib/api/client'
import { createSection, deleteSection, editPage, editSection, previewPage, publishPage, reorderSections, unpublishPage } from '../features/cms/cms-api'
import { PagePreview } from '../features/cms/PagePreview'
import { SectionEditor } from '../features/cms/SectionEditor'
import type { CmsPageView, CmsSection } from '../types/cms'

export function CmsPageDetailPage() {
  const id = Number(useParams().id); const [page, setPage] = useState<CmsPageView | null>(null); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [conflict, setConflict] = useState(''); const [editingSection, setEditingSection] = useState<CmsSection | null | undefined>(undefined); const [showPreview, setShowPreview] = useState(false)
  const load = useCallback(async () => { try { setPage(await previewPage(id)); setError('') } catch (cause) { setError(message(cause)) } finally { setLoading(false) } }, [id])
  useEffect(() => {
    let active = true
    previewPage(id).then((data) => { if (active) { setPage(data); setError(''); setLoading(false) } }).catch((cause: unknown) => { if (active) { setError(message(cause)); setLoading(false) } })
    return () => { active = false }
  }, [id])
  async function action(operation: () => Promise<unknown>) { setError(''); setConflict(''); try { await operation(); await load(); return true } catch (cause) { if (cause instanceof ApiError && (cause.status === 409 || cause.message.toLowerCase().includes('concurrent'))) setConflict('内容已被其他用户修改，请刷新后重试。'); else setError(message(cause)); return false } }
  if (!Number.isInteger(id) || id <= 0) return <StatusMessage error="Page ID 无效" />
  if (loading && !page) return <StatusMessage loading />
  if (!page) return <StatusMessage error={error || 'Page 不存在'} />
  const lockedForEditing = page.status === 'PUBLISHED'
  return <section><p><Link to="/cms/pages">← 返回页面列表</Link></p><div className="title-row"><div><h2>{page.slug} <small>({page.locale})</small></h2><p>管理员预览版本：{page.version} · {page.status}</p></div><div className="actions"><button onClick={() => setShowPreview((value) => !value)}>预览</button><button disabled={page.status !== 'DRAFT'} onClick={() => void action(() => publishPage(id))}>发布</button><button disabled={page.status !== 'PUBLISHED'} onClick={() => void action(() => unpublishPage(id))}>取消发布</button></div></div>{error ? <p className="error">{error}</p> : null}{conflict ? <p className="conflict">{conflict} <button onClick={() => void load()}>刷新</button></p> : null}{showPreview ? <PagePreview page={page} /> : null}<PageMetadataForm key={`${page.id}-${page.version}`} page={page} onSave={(request) => action(() => editPage(id, request))} /><div className="title-row"><h2>页面区块</h2><button disabled={lockedForEditing} onClick={() => setEditingSection(null)}>新增区块</button></div>{lockedForEditing ? <p className="notice">发布版本不可直接修改。先保存页面信息，系统会复制为新的 Draft。</p> : null}{editingSection !== undefined ? <SectionEditor key={editingSection?.id || 'new'} section={editingSection || undefined} nextOrder={page.sections.length} onCancel={() => setEditingSection(undefined)} onSave={async (request) => { const saved = await action(() => editingSection ? editSection(id, editingSection.id, request) : createSection(id, request)); if (saved) setEditingSection(undefined); else throw new Error('保存失败，请按页面提示处理。') }} /> : null}{page.sections.length === 0 ? <StatusMessage empty /> : <div className="section-list">{page.sections.map((section, index) => <article className="panel" key={section.id}><div><strong>{section.sectionKey}</strong> · {section.sectionType} · v{section.version}<p>{section.visibility} · 顺序 {section.sortOrder}</p></div><div className="actions"><button disabled={lockedForEditing || index === 0} onClick={() => void action(() => reorderSections(id, moved(page.sections, index, index - 1)))}>↑</button><button disabled={lockedForEditing || index === page.sections.length - 1} onClick={() => void action(() => reorderSections(id, moved(page.sections, index, index + 1)))}>↓</button><button disabled={lockedForEditing} onClick={() => setEditingSection(section)}>编辑</button><button disabled={lockedForEditing} onClick={() => { if (confirm(`删除 Section ${section.sectionKey}？`)) void action(() => deleteSection(id, section.id)) }}>删除</button></div></article>)}</div>}</section>
}

function PageMetadataForm({ page, onSave }: { page: CmsPageView; onSave: (request: { title: string; seoTitle?: string; seoDescription?: string; socialPreviewMediaId?: number | null; expectedVersion: number }) => Promise<unknown> }) {
  const [title, setTitle] = useState(page.title); const [seoTitle, setSeoTitle] = useState(page.seoTitle || ''); const [seoDescription, setSeoDescription] = useState(page.seoDescription || ''); const [busy, setBusy] = useState(false)
  async function submit(event: FormEvent) { event.preventDefault(); setBusy(true); try { await onSave({ title, seoTitle, seoDescription, socialPreviewMediaId: page.socialPreviewMediaId, expectedVersion: page.status === 'DRAFT' ? page.version : page.version + 1 }) } finally { setBusy(false) } }
  return <form className="panel form-grid" onSubmit={submit}><h3>页面信息</h3><label>标题<input required value={title} onChange={(event) => setTitle(event.target.value)} /></label><label>SEO 标题<input value={seoTitle} onChange={(event) => setSeoTitle(event.target.value)} /></label><label className="wide">SEO 描述<textarea rows={3} value={seoDescription} onChange={(event) => setSeoDescription(event.target.value)} /></label><button className="wide" disabled={busy}>{busy ? '保存中…' : page.status === 'DRAFT' ? '保存页面信息' : '复制为新 Draft 并保存'}</button></form>
}
function moved(sections: CmsSection[], from: number, to: number) { const ids = sections.map((section) => section.id); const [item] = ids.splice(from, 1); ids.splice(to, 0, item); return ids }
function message(cause: unknown) { return cause instanceof Error ? cause.message : '请求失败' }
