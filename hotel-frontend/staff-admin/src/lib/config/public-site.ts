const configuredBase = import.meta.env.VITE_PUBLIC_WEB_BASE_URL?.trim()

export const PUBLIC_WEB_BASE_URL = (configuredBase || 'http://localhost:3000').replace(/\/$/, '')

export function publicPageUrl(slug: string) {
  return slug === 'home' ? PUBLIC_WEB_BASE_URL : `${PUBLIC_WEB_BASE_URL}/${encodeURIComponent(slug)}`
}
