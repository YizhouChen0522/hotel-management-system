import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { StatusMessage } from '../components/StatusMessage'
import {
  createNavigation,
  createSection,
  deleteSection,
  editPage,
  editSection,
  previewPage,
  publishPage,
  reorderSections,
  unpublishPage,
} from '../features/cms/cms-api'
import { MediaPicker } from '../features/cms/MediaPicker'
import { PagePreview } from '../features/cms/PagePreview'
import { SectionEditor } from '../features/cms/SectionEditor'
import { ApiError } from '../lib/api/client'
import { publicPageUrl } from '../lib/config/public-site'
import type { CmsPageView, CmsSection, PageEditRequest } from '../types/cms'

export function CmsPageDetailPage() {
  const id = Number(useParams().id)
  const [page, setPage] = useState<CmsPageView | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [conflict, setConflict] = useState('')
  const [notice, setNotice] = useState('')
  const [editingSection, setEditingSection] = useState<CmsSection | null | undefined>(undefined)
  const [showPreview, setShowPreview] = useState(false)

  const load = useCallback(async () => {
    try {
      setPage(await previewPage(id))
      setError('')
    } catch (cause) {
      setError(message(cause))
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    let active = true
    previewPage(id)
      .then((data) => {
        if (!active) return
        setPage(data)
        setError('')
        setLoading(false)
      })
      .catch((cause: unknown) => {
        if (!active) return
        setError(message(cause))
        setLoading(false)
      })
    return () => {
      active = false
    }
  }, [id])

  async function action(operation: () => Promise<unknown>) {
    setError('')
    setConflict('')
    try {
      await operation()
      await load()
      return true
    } catch (cause) {
      if (cause instanceof ApiError && (cause.status === 409 || cause.message.toLowerCase().includes('concurrent'))) {
        setConflict('内容已被其他用户修改，请刷新后重试。')
      } else {
        setError(message(cause))
      }
      return false
    }
  }

  async function addToNavigation() {
    if (!page) return
    setError('')
    try {
      await createNavigation({
        label: page.title,
        locale: page.locale,
        targetType: 'INTERNAL_PAGE',
        targetValue: page.slug,
        sortOrder: 50,
        visible: true,
        openMode: 'SAME_WINDOW',
        status: 'ACTIVE',
      })
      setNotice('已加入官网导航，顾客可从顶部菜单进入。')
    } catch (cause) {
      setError(message(cause))
    }
  }

  if (!Number.isInteger(id) || id <= 0) return <StatusMessage error="Page ID 无效" />
  if (loading && !page) return <StatusMessage loading />
  if (!page) return <StatusMessage error={error || 'Page 不存在'} />

  const lockedForEditing = page.status === 'PUBLISHED'
  const publicUrl = publicPageUrl(page.slug)

  return (
    <section>
      <p><Link to="/cms/pages">← 返回官网内容</Link></p>
      <div className="page-heading">
        <div>
          <p className="kicker">{statusLabel(page.status)}</p>
          <h2>{page.title}</h2>
          <p className="public-url-preview">
            公开地址：<a href={publicUrl} target="_blank" rel="noreferrer">{publicUrl}</a>
          </p>
        </div>
        <div className="actions">
          <button onClick={() => setShowPreview((value) => !value)}>
            {showPreview ? '关闭预览' : '预览草稿'}
          </button>
          {page.status === 'PUBLISHED' && (
            <a className="button-link" href={publicUrl} target="_blank" rel="noreferrer">在官网查看</a>
          )}
          <button disabled={page.status !== 'DRAFT'} onClick={() => void action(() => publishPage(id))}>
            发布到官网
          </button>
          <button
            className="danger-button"
            disabled={page.status !== 'PUBLISHED'}
            onClick={() => void action(() => unpublishPage(id))}
          >
            取消发布
          </button>
        </div>
      </div>

      <p className={page.status === 'PUBLISHED' ? 'success-banner' : 'warning-banner'}>
        {visibilityExplanation(page.status)}
      </p>
      {error && <p className="error-banner">{error}</p>}
      {conflict && <p className="conflict">{conflict} <button onClick={() => void load()}>刷新</button></p>}
      {notice && <p className="success-banner">{notice}</p>}
      {showPreview && <PagePreview page={page} />}

      <PageMetadataForm
        key={`${page.id}-${page.version}`}
        page={page}
        onSave={(request) => action(() => editPage(id, request))}
      />

      <div className="actions content-actions">
        <button onClick={() => void addToNavigation()}>加入官网导航</button>
        <small>页面版本 v{page.version} · {page.locale}</small>
      </div>

      <div className="title-row">
        <div>
          <h2>页面内容</h2>
          <p>按从上到下的顺序展示，可使用箭头调整。</p>
        </div>
        <button disabled={lockedForEditing} onClick={() => setEditingSection(null)}>添加内容块</button>
      </div>
      {lockedForEditing && (
        <p className="notice">要修改已发布内容，请先编辑并保存页面信息，系统会复制成新草稿。</p>
      )}

      {editingSection !== undefined && (
        <SectionEditor
          key={editingSection?.id || 'new'}
          section={editingSection || undefined}
          nextOrder={page.sections.length}
          onCancel={() => setEditingSection(undefined)}
          onSave={async (request) => {
            const saved = await action(() => editingSection
              ? editSection(id, editingSection.id, request)
              : createSection(id, request))
            if (saved) setEditingSection(undefined)
            else throw new Error('保存失败，请按页面提示处理。')
          }}
        />
      )}

      {page.sections.length === 0 ? (
        <StatusMessage empty />
      ) : (
        <div className="section-list">
          {page.sections.map((section, index) => (
            <article className="panel" key={section.id}>
              <div>
                <strong>{sectionName(section.sectionType)}</strong>
                <p>{sectionSummary(section)}</p>
              </div>
              <div className="actions">
                <button
                  aria-label="上移"
                  disabled={lockedForEditing || index === 0}
                  onClick={() => void action(() => reorderSections(id, moved(page.sections, index, index - 1)))}
                >↑</button>
                <button
                  aria-label="下移"
                  disabled={lockedForEditing || index === page.sections.length - 1}
                  onClick={() => void action(() => reorderSections(id, moved(page.sections, index, index + 1)))}
                >↓</button>
                <button disabled={lockedForEditing} onClick={() => setEditingSection(section)}>编辑</button>
                <button
                  className="danger-button"
                  disabled={lockedForEditing}
                  onClick={() => {
                    if (confirm(`删除“${sectionName(section.sectionType)}”内容块？`)) {
                      void action(() => deleteSection(id, section.id))
                    }
                  }}
                >
                  删除
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}

function PageMetadataForm({ page, onSave }: { page: CmsPageView; onSave: (request: PageEditRequest) => Promise<unknown> }) {
  const [title, setTitle] = useState(page.title)
  const [seoTitle, setSeoTitle] = useState(page.seoTitle || page.title)
  const [seoDescription, setSeoDescription] = useState(page.seoDescription || '')
  const [socialMedia, setSocialMedia] = useState<number[]>(
    page.socialPreviewMediaId ? [page.socialPreviewMediaId] : [],
  )
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      await onSave({
        title,
        seoTitle: seoTitle || title,
        seoDescription,
        socialPreviewMediaId: socialMedia[0] || null,
        expectedVersion: page.status === 'DRAFT' ? page.version : page.version + 1,
      })
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="panel content-metadata" onSubmit={submit}>
      <h3>基本信息</h3>
      <label>
        顾客看到的标题
        <input required value={title} onChange={(event) => setTitle(event.target.value)} />
      </label>
      <details>
        <summary>高级设置</summary>
        <div className="form-grid">
          <label>
            搜索标题
            <input value={seoTitle} onChange={(event) => setSeoTitle(event.target.value)} placeholder={title} />
          </label>
          <label className="wide">
            搜索摘要
            <textarea
              rows={3}
              value={seoDescription}
              onChange={(event) => setSeoDescription(event.target.value)}
              placeholder="可选；留空时网站使用页面内容。"
            />
          </label>
          <div className="wide">
            <span>社交分享图片</span>
            <MediaPicker allowed={['IMAGE']} value={socialMedia} onChange={setSocialMedia} />
          </div>
        </div>
      </details>
      <button disabled={busy}>
        {busy ? '保存中…' : page.status === 'DRAFT' ? '保存草稿信息' : '创建新草稿并保存'}
      </button>
    </form>
  )
}

function moved(sections: CmsSection[], from: number, to: number) {
  const ids = sections.map((section) => section.id)
  const [item] = ids.splice(from, 1)
  ids.splice(to, 0, item)
  return ids
}

function message(cause: unknown) {
  return cause instanceof Error ? cause.message : '请求失败'
}

function statusLabel(status: CmsPageView['status']) {
  if (status === 'PUBLISHED') return '已发布'
  if (status === 'DRAFT') return '草稿'
  return '已取消发布'
}

function visibilityExplanation(status: CmsPageView['status']) {
  if (status === 'PUBLISHED') {
    return '顾客现在可以看到此版本。编辑时会生成新草稿，官网仍显示当前版本，直到再次发布。'
  }
  if (status === 'DRAFT') return '这是草稿，顾客目前看不到。完成预览后再发布。'
  return '此内容已从官网下线。保存编辑会创建新的草稿。'
}

function sectionName(type: string) {
  return ({
    HERO: '头图 / 视频',
    TEXT: '正文',
    IMAGE: '图片',
    VIDEO: '视频',
    GALLERY: '图片画廊',
    CTA: '行动按钮',
    PROMOTION_LIST: '促销列表',
  } as Record<string, string>)[type] || type
}

function sectionSummary(section: CmsSection) {
  try {
    const data = JSON.parse(section.payload) as Record<string, unknown>
    return String(data.title || data.caption || data.body || '已配置内容')
  } catch {
    return '内容格式异常'
  }
}
