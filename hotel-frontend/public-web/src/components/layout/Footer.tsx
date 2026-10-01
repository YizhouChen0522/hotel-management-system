import type { PublicLocation } from "@/types/cms";
export function Footer({ location }: { location: PublicLocation | null }) { return <footer className="border-t border-zinc-200 bg-white"><div className="mx-auto max-w-6xl px-6 py-8 text-sm text-zinc-600"><p>{location?.hotelName || "Hotel"}</p>{location?.address ? <p className="mt-1">{location.address}</p> : null}</div></footer>; }
