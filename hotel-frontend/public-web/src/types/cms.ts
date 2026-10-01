export interface ApiResult<T> { code: number; message: string; data: T; }
export type CmsSectionType = "HERO" | "TEXT" | "IMAGE" | "GALLERY" | "VIDEO" | "PROMOTION_LIST" | "ROOM_TYPE_SHOWCASE" | "MODEL_SCENE" | "LOCATION" | "CTA" | "CUSTOM_STRUCTURED";
export interface PublicSection { key: string; type: CmsSectionType | (string & {}); sortOrder: number; payload: string; }
export interface PublicPage { slug: string; locale: string; title: string; seoTitle: string | null; seoDescription: string | null; socialPreviewMediaId: number | null; version: number; publishedAt: string; sections: PublicSection[]; }
export interface PublicNavigation { label: string; targetType: string; targetValue: string | null; sortOrder: number; openMode: string; }
export interface PublicPromotion { id: number; title: string; subtitle: string | null; description: string | null; coverMediaId: number | null; coverMediaUrl: string | null; galleryMediaIds: string; galleryMediaUrls: string[]; startAt: string | null; endAt: string | null; priority: number; ctaType: string | null; ctaTarget: string | null; }
export interface PublicLocation { hotelName: string; address: string; latitude: number; longitude: number; phone: string | null; contact: string | null; displayMetadata: string; }
export interface PublicScene { id: number; name: string; sceneType: string; mediaAssetId: number; previewMediaId: number | null; previewMediaUrl: string | null; interactionMode: string; cameraConfig: string; }


