export interface PageResult<T> { items: T[]; page: number; pageSize: number; total: number; hasNext: boolean; searchMode: boolean; }
export interface CmsPageSummary { id: number; slug: string; locale: string; createdBy: number; createTime: string; updateTime: string; }
export interface CmsSection { id: number; pageVersionId: number; sectionKey: string; sectionType: string; sortOrder: number; payload: string; visibility: 'PUBLIC' | 'INTERNAL'; status: string; version: number; createTime: string; updateTime: string; }
export interface CmsPageView { id: number; slug: string; locale: string; status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'; title: string; seoTitle: string | null; seoDescription: string | null; socialPreviewMediaId: number | null; version: number; publishedAt: string | null; sections: CmsSection[]; }
export interface PageCreateRequest { slug: string; locale: string; title: string; seoTitle?: string; seoDescription?: string; socialPreviewMediaId?: number | null; }
export interface PageEditRequest { title: string; seoTitle?: string; seoDescription?: string; socialPreviewMediaId?: number | null; expectedVersion: number; }
export interface SectionWriteRequest { sectionKey: string; sectionType: string; sortOrder: number; payload: string; visibility: 'PUBLIC' | 'INTERNAL'; expectedVersion: number; }
export type MediaType = 'IMAGE' | 'VIDEO' | 'MODEL_3D'
export interface CmsMedia { id: number; assetType: MediaType; storageKey: string; publicUrl: string; originalFilename: string; contentType: string; sizeBytes: number; checksum: string; status: string; width: number | null; height: number | null; durationSeconds: number | null; createTime: string; }
export type PromotionStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export interface CmsPromotion { id: number; title: string; subtitle: string | null; description: string | null; locale: string; coverMediaId: number | null; galleryMediaIds: string; startAt: string | null; endAt: string | null; status: PromotionStatus; priority: number; ctaType: string | null; ctaTarget: string | null; pricingPolicyId: number | null; createTime: string; updateTime: string; }
export interface PromotionWriteRequest { title: string; subtitle?: string; description?: string; locale: string; coverMediaId: number | null; galleryMediaIds: string; startAt: string | null; endAt: string | null; status: PromotionStatus; priority: number; ctaType?: string; ctaTarget?: string; pricingPolicyId: number | null; }export type NavigationTarget='INTERNAL_PAGE'|'EXTERNAL_URL'|'BOOKING'|'CUSTOMER_ACCOUNT'
export interface CmsNavigation { id:number;label:string;locale:string;targetType:NavigationTarget;targetValue:string|null;sortOrder:number;visible:boolean;openMode:string;status:string }
export type NavigationWriteRequest=Omit<CmsNavigation,'id'>
export interface CmsLocation { id:number;locale:string;hotelName:string;address:string;latitude:number;longitude:number;phone:string|null;contact:string|null;displayMetadata:string;status:string }
export interface LocationWriteRequest { locale:string;hotelName:string;address:string;latitude:number;longitude:number;phone?:string;contact?:string;displayMetadata:string;status:string }
export interface CmsScene { id:number;name:string;locale:string;sceneType:string;mediaAssetId:number;previewMediaId:number|null;visibility:'PUBLIC'|'INTERNAL';interactionMode:'PUBLIC_ORBIT_ONLY'|'INTERNAL_INTERACTIVE';cameraConfig:string;status:string }
export type SceneWriteRequest=Omit<CmsScene,'id'>
export interface SceneBinding { id:number;sceneId:number;nodeKey:string;floorReference:string|null;roomId:number|null }
export interface InternalScene { scene:CmsScene;bindings:SceneBinding[] }
