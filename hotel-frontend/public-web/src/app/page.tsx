import { Footer } from "@/components/layout/Footer";
import { Header } from "@/components/layout/Header";
import { SectionRenderer } from "@/components/sections/SectionRenderer";
import { getHomePageData } from "@/features/cms/public-cms";

export const dynamic = "force-dynamic";

export default async function Home() {
  const { page, navigation, location, promotions, scenes, unavailable } = await getHomePageData("zh-CN");
  return <><Header navigation={navigation} hotelName={location?.hotelName} /><main className="mx-auto w-full max-w-6xl flex-1 px-6 py-12">{page ? <><header className="mb-10 border-b border-zinc-200 pb-6"><p className="text-sm text-zinc-500">{page.locale}</p><h1 className="mt-2 text-3xl font-semibold tracking-tight">{page.title}</h1></header><div className="space-y-8">{page.sections.map((section) => <SectionRenderer key={section.key} section={section} promotions={promotions} location={location} scenes={scenes} />)}</div></> : <section className="rounded border border-zinc-200 p-8"><h1 className="text-2xl font-semibold">酒店官网</h1><p className="mt-3 text-zinc-600">{unavailable ? "网站内容服务暂时不可用，请稍后再试。" : "首页内容尚未发布。"}</p></section>}</main><Footer location={location} /></>;
}


