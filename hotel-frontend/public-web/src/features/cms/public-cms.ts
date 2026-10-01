import "server-only";
import { ApiRequestError, serverApiGet } from "@/lib/api/server-fetch";
import type { PublicLocation, PublicNavigation, PublicPage, PublicPromotion, PublicScene } from "@/types/cms";
const endpoint = (path: string, locale: string) => `/api/public/site${path}?locale=${encodeURIComponent(locale)}`;
export const getPublicPage = (slug: string, locale: string) => serverApiGet<PublicPage>(endpoint(`/pages/${encodeURIComponent(slug)}`, locale));
export const getPublicNavigation = (locale: string) => serverApiGet<PublicNavigation[]>(endpoint("/navigation", locale));
export const getPublicPromotions = (locale: string) => serverApiGet<PublicPromotion[]>(endpoint("/promotions", locale));
export const getPublicLocation = (locale: string) => serverApiGet<PublicLocation | null>(endpoint("/location", locale));
export const getPublicScenes = (locale: string) => serverApiGet<PublicScene[]>(endpoint("/scenes", locale));
export async function getHomePageData(locale: string) {
  const [page, navigation, location, promotions, scenes] = await Promise.allSettled([getPublicPage("home", locale), getPublicNavigation(locale), getPublicLocation(locale), getPublicPromotions(locale), getPublicScenes(locale)]);
  const pageMissing = page.status === "rejected" && page.reason instanceof ApiRequestError && page.reason.status === 404;
  return { page: page.status === "fulfilled" ? page.value : null, navigation: navigation.status === "fulfilled" ? navigation.value : [], location: location.status === "fulfilled" ? location.value : null, promotions: promotions.status === "fulfilled" ? promotions.value : [], scenes: scenes.status === "fulfilled" ? scenes.value : [], unavailable: (!pageMissing && page.status === "rejected") || navigation.status === "rejected" || location.status === "rejected" || promotions.status === "rejected" || scenes.status === "rejected" };
}


