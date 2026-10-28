import Image from 'next/image'
import Link from 'next/link'
import { AuthGate } from '@/components/auth/AuthGate'
import type { PublicLocation, PublicPromotion, PublicScene, PublicSection } from '@/types/cms'

type Payload = Record<string, unknown>

function parse(raw: string): Payload | null {
  try {
    const value: unknown = JSON.parse(raw)
    return value && typeof value === 'object' && !Array.isArray(value) ? value as Payload : null
  } catch {
    return null
  }
}

const stringValue = (value: unknown) => typeof value === 'string' ? value : null
const stringArray = (value: unknown) => Array.isArray(value)
  ? value.filter((item): item is string => typeof item === 'string')
  : []

function safeTarget(value: string | null) {
  return value && (value.startsWith('/') || /^https?:\/\//.test(value)) ? value : null
}

function Picture({ src, alt, className = 'editorial-image' }: { src: string; alt: string; className?: string }) {
  return <Image src={src} alt={alt} width={1800} height={1100} unoptimized className={className} />
}

function Empty({ type }: { type: string }) {
  return <section className="section-empty">{type} 内容正在准备中</section>
}

type Props = {
  section: PublicSection
  promotions?: PublicPromotion[]
  location?: PublicLocation | null
  scenes?: PublicScene[]
}

export function SectionRenderer({ section, promotions = [], location = null, scenes = [] }: Props) {
  const payload = parse(section.payload)
  if (!payload) return <Empty type={section.type} />

  switch (section.type) {
    case 'HERO': {
      const mediaUrl = stringValue(payload.mediaUrl)
      const video = payload.mediaType === 'VIDEO'
      return (
        <section className="hero-section">
          {mediaUrl && (video
            ? <video className="hero-media" src={mediaUrl} autoPlay muted loop playsInline />
            : <Picture src={mediaUrl} alt="" className="hero-media" />)}
          <div className="hero-shade" />
          <div className="hero-copy">
            <p className="eyebrow">A SENSE OF PLACE</p>
            <h1>{stringValue(payload.title) || '静居于此，感受旅途本身'}</h1>
            <p>{stringValue(payload.subtitle) || ''}</p>
            <AuthGate label="探索住宿" target="/booking" />
          </div>
        </section>
      )
    }
    case 'TEXT':
      return (
        <section className="story-section">
          <p className="eyebrow">OUR STORY</p>
          <h2>{stringValue(payload.title) || ''}</h2>
          <p>{stringValue(payload.body) || ''}</p>
        </section>
      )
    case 'CTA': {
      const target = safeTarget(stringValue(payload.href)) || '/booking'
      const label = stringValue(payload.label) || '了解更多'
      return (
        <section className="cta-section">
          <div>
            <p className="eyebrow">DISCOVER MORE</p>
            <h2>{stringValue(payload.title) || ''}</h2>
            <p>{stringValue(payload.description) || ''}</p>
          </div>
          {target === '/booking'
            ? <AuthGate label={label} target={target} />
            : <Link className="button-primary" href={target}>{label}</Link>}
        </section>
      )
    }
    case 'IMAGE': {
      const url = stringValue(payload.mediaUrl)
      return url ? (
        <section className="image-section">
          <Picture src={url} alt={stringValue(payload.alt) || ''} />
          {stringValue(payload.caption) && <p>{stringValue(payload.caption)}</p>}
        </section>
      ) : <Empty type={section.type} />
    }
    case 'GALLERY': {
      const urls = stringArray(payload.mediaUrls)
      return urls.length ? (
        <section className="gallery-section">
          <div className="section-heading">
            <p className="eyebrow">GALLERY</p>
            <h2>{stringValue(payload.title) || '空间与光影'}</h2>
          </div>
          <div className="gallery-grid">
            {urls.map((url, index) => (
              <Picture key={url} src={url} alt={`${stringValue(payload.alt) || 'Hotel'} ${index + 1}`} />
            ))}
          </div>
        </section>
      ) : <Empty type={section.type} />
    }
    case 'VIDEO': {
      const url = stringValue(payload.mediaUrl)
      return url ? (
        <section className="video-section">
          <video src={url} controls playsInline preload="metadata" />
          <div>
            <p className="eyebrow">FILM</p>
            <h2>{stringValue(payload.title) || '在这里，时间有另一种流速'}</h2>
          </div>
        </section>
      ) : <Empty type={section.type} />
    }
    case 'PROMOTION_LIST':
      return (
        <section className="promotion-section">
          <div className="section-heading">
            <p className="eyebrow">OFFERS</p>
            <h2>{stringValue(payload.title) || '为旅程增添一段特别体验'}</h2>
          </div>
          {promotions.length ? (
            <div className="card-grid">
              {promotions.map((promotion) => (
                <article className="content-card" key={promotion.id}>
                  {promotion.coverMediaUrl && <Picture src={promotion.coverMediaUrl} alt={promotion.title} />}
                  <div>
                    <p className="eyebrow">CURATED OFFER</p>
                    <h3>{promotion.title}</h3>
                    <p>{promotion.subtitle || promotion.description}</p>
                    {safeTarget(promotion.ctaTarget) && (
                      <a href={safeTarget(promotion.ctaTarget)!}>{promotion.ctaType || '了解更多'} →</a>
                    )}
                  </div>
                </article>
              ))}
            </div>
          ) : <p className="quiet-empty">暂无正在进行的活动。</p>}
        </section>
      )
    case 'ROOM_TYPE_SHOWCASE':
      return (
        <section className="story-section">
          <p className="eyebrow">ROOMS & SUITES</p>
          <h2>{stringValue(payload.title) || '安静、舒适，与在地风景相连'}</h2>
          <p>{stringValue(payload.description) || '公开浏览真实房型，登录后查看实时房态与价格。'}</p>
          <Link className="button-primary" href="/rooms">浏览房型</Link>
        </section>
      )
    case 'LOCATION':
      return location ? (
        <section className="location-section">
          <div>
            <p className="eyebrow">FIND US</p>
            <h2>{location.hotelName}</h2>
            <p>{location.address}</p>
            {location.phone && <p>{location.phone}</p>}
            <a
              href={`https://www.openstreetmap.org/?mlat=${location.latitude}&mlon=${location.longitude}`}
              target="_blank"
              rel="noreferrer"
            >
              查看位置 →
            </a>
          </div>
          <div className="coordinate-art">
            <span>{location.latitude}</span><i /><span>{location.longitude}</span>
          </div>
        </section>
      ) : <Empty type={section.type} />
    case 'MODEL_SCENE': {
      const selected = typeof payload.sceneId === 'number'
        ? scenes.filter((scene) => scene.id === payload.sceneId)
        : scenes
      return selected.length ? (
        <section className="scene-section">
          <div className="section-heading">
            <p className="eyebrow">VIRTUAL PREVIEW</p>
            <h2>{stringValue(payload.title) || '先一步走近酒店'}</h2>
          </div>
          {selected.map((scene) => (
            <article key={scene.id}>
              {scene.previewMediaUrl && <Picture src={scene.previewMediaUrl} alt={scene.name} />}
              <div>
                <h3>{scene.name}</h3>
                <p>{scene.sceneType} · PUBLIC ORBIT PREVIEW</p>
                <small>3D 交互将在后续阶段提供；此处不包含房间映射或内部节点。</small>
              </div>
            </article>
          ))}
        </section>
      ) : <Empty type={section.type} />
    }
    case 'CUSTOM_STRUCTURED':
      return (
        <section className="story-section">
          <p className="eyebrow">JOURNAL</p>
          <h2>{stringValue(payload.title) || ''}</h2>
          <p>{stringValue(payload.summary) || stringValue(payload.body) || ''}</p>
          {safeTarget(stringValue(payload.href)) && (
            <a href={safeTarget(stringValue(payload.href))!}>阅读全文 →</a>
          )}
        </section>
      )
    default:
      return <Empty type={section.type} />
  }
}
