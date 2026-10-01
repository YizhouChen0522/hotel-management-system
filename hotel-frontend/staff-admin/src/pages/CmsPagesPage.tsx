import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { StatusMessage } from '../components/StatusMessage'
import { createPage, listPages } from '../features/cms/cms-api'
import type { CmsPageSummary, PageResult } from '../types/cms'

const empty: PageResult<CmsPageSummary> = { items: [], page: 1, pageSize: 20, total: 0, hasNext: false, searchMode: false }
export function CmsPagesPage() {
  const [result, setResult] = useState(empty); const [locale, setLocale] = useState(''); const [query, setQuery] = useState(''); const [applied, setApplied] = useState({ locale: '', query: '' }); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [creating, setCreating] = useState(false)
  const load = useCallback(async (page: number, filters = applied) => { try { setResult(await listPages({ ...filters, page, pageSize: 20 })); setError('') } catch (cause) { setError(cause instanceof Error ? cause.message : '加载失败') } finally { setLoading(false) } }, [applied])
  useEffect(() => {
    let active = true
    listPages({ ...applied, page: 1, pageSize: 20 }).then((data) => { if (active) { setResult(data); setError(''); setLoading(false) } }).catch((cause: unknown) => { if (active) { setError(cause instanceof Error ? cause.message : '加载失败'); setLoading(false) } })
    return () => { active = false }
  }, [applied])
  function search(event: FormEvent) { event.preventDefault(); setLoading(true); setError(''); setApplied({ locale, query }) }
  function changePage(page: number) { setLoading(true); setError(''); void load(page) }
  return <section><div className="title-row"><div><h2>CMS Pages</h2><p>列表支持后端实际提供的 locale 和 slug query 筛选。</p></div><button onClick={() => setCreating((value) => !value)}>新增 Page</button></div>{creating ? <CreatePageForm onCreated={() => { setCreating(false); setLoading(true); void load(1) }} /> : null}<form className="filters" onSubmit={search}><label>Locale<select value={locale} onChange={(event) => setLocale(event.target.value)}><option value="">全部</option><option value="zh-CN">zh-CN</option><option value="en">en</option></select></label><label>Slug<input value={query} onChange={(event) => setQuery(event.target.value)} /></label><button>查询</button></form><StatusMessage loading={loading} error={error} empty={!loading && !error && result.items.length === 0} />{!loading && result.items.length ? <><table><thead><tr><th>ID</th><th>Slug</th><th>Locale</th><th>更新时间</th><th /></tr></thead><tbody>{result.items.map((page) => <tr key={page.id}><td>{page.id}</td><td>{page.slug}</td><td>{page.locale}</td><td>{page.updateTime}</td><td><Link to={`/cms/pages/${page.id}`}>详情</Link></td></tr>)}</tbody></table><div className="pager"><button disabled={result.page <= 1} onClick={() => changePage(result.page - 1)}>上一页</button><span>第 {result.page} 页 · 共 {result.total} 条</span><button disabled={!result.hasNext} onClick={() => changePage(result.page + 1)}>下一页</button></div></> : null}</section>
}

function CreatePageForm({ onCreated }: { onCreated: () => void }) {
  const [slug, setSlug] = useState(''); const [locale, setLocale] = useState('zh-CN'); const [title, setTitle] = useState(''); const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  async function submit(event: FormEvent) { event.preventDefault(); setBusy(true); setError(''); try { await createPage({ slug, locale, title }); onCreated() } catch (cause) { setError(cause instanceof Error ? cause.message : '创建失败') } finally { setBusy(false) } }
  return <form className="panel inline-form" onSubmit={submit}><label>Slug<input required value={slug} onChange={(event) => setSlug(event.target.value)} /></label><label>Locale<select value={locale} onChange={(event) => setLocale(event.target.value)}><option value="zh-CN">zh-CN</option><option value="en">en</option></select></label><label>标题<input required value={title} onChange={(event) => setTitle(event.target.value)} /></label><button disabled={busy}>{busy ? '创建中…' : '创建'}</button>{error ? <p className="error">{error}</p> : null}</form>
}
