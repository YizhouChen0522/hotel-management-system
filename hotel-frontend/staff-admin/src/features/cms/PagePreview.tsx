import { useEffect, useState } from 'react'
import { resolveApiUrl } from '../../lib/api/client'
import type { CmsMedia, CmsPageView, CmsSection } from '../../types/cms'
import { listMedia } from './cms-api'

type Payload = Record<string, unknown>

function payload(raw: string): Payload {
  try {
    return JSON.parse(raw) as Payload
  } catch {
    return {}
  }
}

function text(candidate: unknown) {
  return typeof candidate === 'string' ? candidate : ''
}

function number(candidate: unknown) {
  return typeof candidate === 'number' ? candidate : null
}

function numbers(candidate: unknown) {
  return Array.isArray(candidate)
    ? candidate.filter((item): item is number => typeof item === 'number')
    : []
}

function SectionPreview({ section, media }: { section: CmsSection; media: Map<number, CmsMedia> }) {
  const data = payload(section.payload)
  const title = text(data.title)
  const body = text(data.subtitle) || text(data.body) || text(data.description)
  const mediaId = number(data.mediaId)
  const selected = mediaId ? media.get(mediaId) : null

  if (section.sectionType === 'IMAGE' || section.sectionType === 'HERO') {
    return (
      <article>
        <strong>{section.sectionType === 'HERO' ? '首屏大图' : '图片'}</strong>
        {selected ? <img src={resolveApiUrl(selected.publicUrl)} alt={text(data.alt) || title} /> : null}
        <h3>{title}</h3>
        <p>{body || text(data.caption)}</p>
      </article>
    )
  }

  if (section.sectionType === 'VIDEO') {
    return (
      <article>
        <strong>视频</strong>
        {selected ? <video src={resolveApiUrl(selected.publicUrl)} controls preload="metadata" /> : null}
        <h3>{title}</h3>
      </article>
    )
  }

  if (section.sectionType === 'GALLERY') {
    return (
      <article>
        <strong>图片集</strong>
        <h3>{title}</h3>
        <div className="preview-gallery">
          {numbers(data.mediaIds).map((id) => {
            const item = media.get(id)
            return item ? <img key={id} src={resolveApiUrl(item.publicUrl)} alt="" /> : null
          })}
        </div>
      </article>
    )
  }

  return (
    <article>
      <strong>{section.sectionType}</strong>
      <h3>{title}</h3>
      <p>{body}</p>
      {section.sectionType === 'CTA' ? <button type="button">{text(data.label) || '了解更多'}</button> : null}
    </article>
  )
}

export function PagePreview({ page }: { page: CmsPageView }) {
  const [media, setMedia] = useState(new Map<number, CmsMedia>())

  useEffect(() => {
    let active = true
    listMedia(1, 100)
      .then((response) => {
        if (active) setMedia(new Map(response.items.map((item) => [item.id, item])))
      })
      .catch(() => {
        if (active) setMedia(new Map())
      })
    return () => {
      active = false
    }
  }, [])

  return (
    <section className="preview">
      <header>
        <small>员工预览 · v{page.version}（草稿预览不会公开）</small>
        <h2>{page.title}</h2>
      </header>
      {page.sections.map((section) => (
        <SectionPreview key={section.id} section={section} media={media} />
      ))}
    </section>
  )
}
