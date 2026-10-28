import { useCallback, useEffect, useId, useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { StatusMessage } from '../components/StatusMessage'
import { createPage, listPages } from '../features/cms/cms-api'
import { publicPageUrl } from '../lib/config/public-site'
import type { CmsPageSummary, PageResult } from '../types/cms'

const empty: PageResult<CmsPageSummary> = {
  items: [], page: 1, pageSize: 20, total: 0, hasNext: false, searchMode: false,
}

export function CmsPagesPage() {
  const [result, setResult] = useState(empty)
  const [locale, setLocale] = useState('')
  const [query, setQuery] = useState('')
  const [applied, setApplied] = useState({ locale: '', query: '' })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [creating, setCreating] = useState(false)

  const load = useCallback(async (page: number, filters = applied) => {
    try {
      setResult(await listPages({ ...filters, page, pageSize: 20 }))
      setError('')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [applied])

  useEffect(() => {
    let active = true
    listPages({ ...applied, page: 1, pageSize: 20 })
      .then((response) => {
        if (!active) return
        setResult(response)
        setError('')
        setLoading(false)
      })
      .catch((cause: unknown) => {
        if (!active) return
        setError(cause instanceof Error ? cause.message : '加载失败')
        setLoading(false)
      })
    return () => {
      active = false
    }
  }, [applied])

  return (
    <section>
      <div className="page-heading">
        <div>
          <p className="kicker">PUBLIC CONTENT</p>
          <h2>官网内容</h2>
          <p>管理酒店介绍、活动和故事。草稿不会被顾客看到。</p>
        </div>
        <button onClick={() => setCreating((value) => !value)}>
          {creating ? '关闭' : '新建内容'}
        </button>
      </div>

      {creating && (
        <CreatePageForm onCreated={() => {
          setCreating(false)
          setLoading(true)
          void load(1)
        }} />
      )}

      <form className="filters" onSubmit={(event) => {
        event.preventDefault()
        setApplied({ locale, query })
      }}>
        <label>语言<select value={locale} onChange={(event) => setLocale(event.target.value)}><option value="">全部</option><option value="zh-CN">中文</option><option value="en">English</option></select></label>
        <label>名称或地址<input placeholder="搜索标题或页面地址" value={query} onChange={(event) => setQuery(event.target.value)} /></label>
        <button>查询</button>
      </form>

      <StatusMessage loading={loading} error={error} empty={!loading && !error && result.items.length === 0} />
      {!loading && result.items.length > 0 && (
        <>
          <div className="content-list">
            {result.items.map((page) => (
              <article className="panel content-row" key={page.id}>
                <div>
                  <strong>{page.slug === 'hotel' ? '酒店详情' : page.slug === 'activities' ? '活动首页' : page.slug}</strong>
                  <p>{page.locale} · 最近更新 {new Date(page.updateTime).toLocaleString()}</p>
                  <a href={publicPageUrl(page.slug)} target="_blank" rel="noreferrer">{publicPageUrl(page.slug)}</a>
                </div>
                <Link className="button-link" to={`/cms/pages/${page.id}`}>编辑内容</Link>
              </article>
            ))}
          </div>
          <div className="pager">
            <button disabled={result.page <= 1} onClick={() => void load(result.page - 1)}>上一页</button>
            <span>第 {result.page} 页 · 共 {result.total} 条</span>
            <button disabled={!result.hasNext} onClick={() => void load(result.page + 1)}>下一页</button>
          </div>
        </>
      )}
    </section>
  )
}

type ContentKind = 'HOTEL' | 'ACTIVITIES' | 'STORY' | 'PAGE'

function slugify(title: string) {
  return title.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')
}

function CreatePageForm({ onCreated }: { onCreated: () => void }) {
  const generatedId = useId().replace(/:/g, '')
  const [kind, setKind] = useState<ContentKind>('STORY')
  const [locale, setLocale] = useState('zh-CN')
  const [title, setTitle] = useState('')
  const [manualSlug, setManualSlug] = useState('')
  const [advanced, setAdvanced] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const suggestedSlug = useMemo(() => {
    if (kind === 'HOTEL') return 'hotel'
    if (kind === 'ACTIVITIES') return 'activities'
    const words = slugify(title)
    const fallback = generatedId || 'content'
    return `${kind === 'STORY' ? 'story' : 'page'}-${words || fallback}`
  }, [generatedId, kind, title])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      await createPage({
        slug: manualSlug.trim() || suggestedSlug,
        locale,
        title,
        seoTitle: title,
      })
      onCreated()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '创建失败')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="panel create-content" onSubmit={submit}>
      <h3>新建官网内容</h3>
      <div className="form-grid">
        <label>内容类型<select value={kind} onChange={(event) => setKind(event.target.value as ContentKind)}><option value="HOTEL">酒店详情</option><option value="ACTIVITIES">活动首页</option><option value="STORY">新闻 / 故事</option><option value="PAGE">普通页面</option></select></label>
        <label>语言<select value={locale} onChange={(event) => setLocale(event.target.value)}><option value="zh-CN">中文</option><option value="en">English</option></select></label>
        <label className="wide">标题<input required value={title} onChange={(event) => setTitle(event.target.value)} /></label>
      </div>
      <p className="public-url-preview">公开地址：{publicPageUrl(manualSlug.trim() || suggestedSlug)}</p>
      <button type="button" className="text-button" onClick={() => setAdvanced(!advanced)}>{advanced ? '隐藏高级设置' : '高级设置'}</button>
      {advanced && <label>自定义页面地址<input value={manualSlug} onChange={(event) => setManualSlug(slugify(event.target.value))} placeholder={suggestedSlug} /></label>}
      <div className="actions"><button disabled={busy}>{busy ? '创建中…' : '创建草稿'}</button></div>
      {error && <p className="error">{error}</p>}
    </form>
  )
}
