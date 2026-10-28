import { Footer } from '@/components/layout/Footer'
import { Header } from '@/components/layout/Header'
import { SectionRenderer } from '@/components/sections/SectionRenderer'
import { getPublicLocation, getPublicNavigation, getPublicPromotions } from '@/features/cms/public-cms'

export const dynamic = 'force-dynamic'

export default async function PromotionsPage() {
  const [navigationResult, locationResult, promotionResult] = await Promise.allSettled([
    getPublicNavigation('zh-CN'),
    getPublicLocation('zh-CN'),
    getPublicPromotions('zh-CN'),
  ])
  const navigation = navigationResult.status === 'fulfilled' ? navigationResult.value : []
  const location = locationResult.status === 'fulfilled' ? locationResult.value : null
  const promotions = promotionResult.status === 'fulfilled' ? promotionResult.value : []

  return (
    <>
      <Header navigation={navigation} hotelName={location?.hotelName} />
      <main className="content-page">
        <header className="page-title">
          <p className="eyebrow">OFFERS</p>
          <h1>酒店促销</h1>
        </header>
        <SectionRenderer
          section={{ key: 'all-promotions', type: 'PROMOTION_LIST', sortOrder: 0, payload: '{"title":"当前礼遇"}' }}
          promotions={promotions}
        />
      </main>
      <Footer location={location} />
    </>
  )
}
