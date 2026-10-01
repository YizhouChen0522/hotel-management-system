import { apiFetch } from '../../lib/api/client'
import type { CmsPageSummary, CmsPageView, CmsSection, PageCreateRequest, PageEditRequest, PageResult, SectionWriteRequest } from '../../types/cms'

const root = '/api/admin/cms/pages'
export function listPages(options: { locale?: string; query?: string; page: number; pageSize: number }) {
  const params = new URLSearchParams({ page: String(options.page), pageSize: String(options.pageSize) })
  if (options.locale) params.set('locale', options.locale)
  if (options.query) params.set('query', options.query)
  return apiFetch<PageResult<CmsPageSummary>>(`${root}?${params}`)
}
export const createPage = (request: PageCreateRequest) => apiFetch<CmsPageView>(root, { method: 'POST', body: JSON.stringify(request) })
export const previewPage = (id: number) => apiFetch<CmsPageView>(`${root}/${id}/preview`)
export const editPage = (id: number, request: PageEditRequest) => apiFetch<CmsPageView>(`${root}/${id}`, { method: 'PUT', body: JSON.stringify(request) })
export const publishPage = (id: number) => apiFetch<CmsPageView>(`${root}/${id}/publish`, { method: 'POST' })
export const unpublishPage = (id: number) => apiFetch<void>(`${root}/${id}/unpublish`, { method: 'POST' })
export const createSection = (pageId: number, request: SectionWriteRequest) => apiFetch<CmsSection>(`${root}/${pageId}/sections`, { method: 'POST', body: JSON.stringify(request) })
export const editSection = (pageId: number, sectionId: number, request: SectionWriteRequest) => apiFetch<CmsSection>(`${root}/${pageId}/sections/${sectionId}`, { method: 'PUT', body: JSON.stringify(request) })
export const deleteSection = (pageId: number, sectionId: number) => apiFetch<void>(`${root}/${pageId}/sections/${sectionId}`, { method: 'DELETE' })
export const reorderSections = (pageId: number, sectionIds: number[]) => apiFetch<CmsPageView>(`${root}/${pageId}/sections/reorder`, { method: 'POST', body: JSON.stringify({ sectionIds }) })

export function listMedia(page: number, pageSize: number) { return apiFetch<import('../../types/cms').PageResult<import('../../types/cms').CmsMedia>>(`/api/admin/cms/media?page=${page}&pageSize=${pageSize}`) }
export function uploadMedia(type: import('../../types/cms').MediaType, file: File) { const body = new FormData(); body.append('file', file); return apiFetch<import('../../types/cms').CmsMedia>(`/api/admin/cms/media?type=${type}`, { method: 'POST', body }) }
export const archiveMedia = (id: number) => apiFetch<void>(`/api/admin/cms/media/${id}/archive`, { method: 'POST' })
export function listPromotions(page: number, pageSize: number) { return apiFetch<import('../../types/cms').PageResult<import('../../types/cms').CmsPromotion>>(`/api/admin/cms/promotions?page=${page}&pageSize=${pageSize}`) }
export const createPromotion = (request: import('../../types/cms').PromotionWriteRequest) => apiFetch<import('../../types/cms').CmsPromotion>('/api/admin/cms/promotions', { method: 'POST', body: JSON.stringify(request) })
export const updatePromotion = (id: number, request: import('../../types/cms').PromotionWriteRequest) => apiFetch<import('../../types/cms').CmsPromotion>(`/api/admin/cms/promotions/${id}`, { method: 'PUT', body: JSON.stringify(request) })
export const listNavigation=(page:number,pageSize:number)=>apiFetch<import('../../types/cms').PageResult<import('../../types/cms').CmsNavigation>>(`/api/admin/cms/navigation?page=${page}&pageSize=${pageSize}`)
export const createNavigation=(x:import('../../types/cms').NavigationWriteRequest)=>apiFetch<import('../../types/cms').CmsNavigation>('/api/admin/cms/navigation',{method:'POST',body:JSON.stringify(x)})
export const updateNavigation=(id:number,x:import('../../types/cms').NavigationWriteRequest)=>apiFetch<import('../../types/cms').CmsNavigation>(`/api/admin/cms/navigation/${id}`,{method:'PUT',body:JSON.stringify(x)})
export const getLocation=(locale:string)=>apiFetch<import('../../types/cms').CmsLocation|null>(`/api/admin/cms/location?locale=${encodeURIComponent(locale)}`)
export const saveLocation=(x:import('../../types/cms').LocationWriteRequest)=>apiFetch<import('../../types/cms').CmsLocation>('/api/admin/cms/location',{method:'PUT',body:JSON.stringify(x)})
export const listScenes=(page:number,pageSize:number)=>apiFetch<import('../../types/cms').PageResult<import('../../types/cms').CmsScene>>(`/api/admin/cms/scenes?page=${page}&pageSize=${pageSize}`)
export const getScene=(id:number)=>apiFetch<import('../../types/cms').InternalScene>(`/api/admin/cms/scenes/${id}`)
export const createScene=(x:import('../../types/cms').SceneWriteRequest)=>apiFetch<import('../../types/cms').CmsScene>('/api/admin/cms/scenes',{method:'POST',body:JSON.stringify(x)})
export const updateScene=(id:number,x:import('../../types/cms').SceneWriteRequest)=>apiFetch<import('../../types/cms').CmsScene>(`/api/admin/cms/scenes/${id}`,{method:'PUT',body:JSON.stringify(x)})

