import Link from "next/link";
import type { PublicNavigation } from "@/types/cms";
function hrefFor(item: PublicNavigation) { if (item.targetType === "EXTERNAL_URL") return item.targetValue ?? "#"; if (item.targetType === "INTERNAL_PAGE") return item.targetValue ? `/${item.targetValue}` : "/"; if(item.targetType === "BOOKING") return "/booking"; if(item.targetType === "CUSTOMER_ACCOUNT") return "/account"; return "#"; }
export function Header({ navigation, hotelName }: { navigation: PublicNavigation[]; hotelName?: string }) {
  return <header className="border-b border-zinc-200 bg-white"><div className="mx-auto flex max-w-6xl items-center justify-between gap-8 px-6 py-5"><Link href="/" className="font-semibold">{hotelName || "Hotel"}</Link><nav aria-label="主导航"><ul className="flex flex-wrap gap-5 text-sm text-zinc-700">{navigation.map((item) => <li key={`${item.targetType}-${item.targetValue}-${item.label}`}>{item.targetType === "INTERNAL_PAGE" ? <Link href={hrefFor(item)}>{item.label}</Link> : <a href={hrefFor(item)} target={item.openMode === "NEW_TAB" ? "_blank" : undefined} rel={item.openMode === "NEW_TAB" ? "noreferrer" : undefined}>{item.label}</a>}</li>)}</ul></nav></div></header>;
}

