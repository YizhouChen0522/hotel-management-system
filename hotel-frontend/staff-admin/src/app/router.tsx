import { createBrowserRouter } from 'react-router-dom'
import { AuthGuard, GuestGuard, RoleGuard } from '../features/auth/AuthGuard'
import { StaffLayout } from '../layouts/StaffLayout'
import { CmsPage } from '../pages/CmsPage'
import { DashboardPage } from '../pages/DashboardPage'
import { LoginPage } from '../pages/LoginPage'
import { CmsPagesPage } from '../pages/CmsPagesPage'
import { CmsPageDetailPage } from '../pages/CmsPageDetailPage'
import { CmsMediaPage } from '../pages/CmsMediaPage'
import { CmsPromotionsPage } from '../pages/CmsPromotionsPage'
import { CmsNavigationPage } from '../pages/CmsNavigationPage'
import { CmsLocationPage } from '../pages/CmsLocationPage'
import { CmsScenesPage } from '../pages/CmsScenesPage'
import { ReservationsPage } from '../pages/ReservationsPage'
import { ReservationDetailPage } from '../pages/ReservationDetailPage'

export const router = createBrowserRouter([
  {
    element: <GuestGuard />,
    children: [{ path: '/login', element: <LoginPage /> }],
  },
  {
    element: <AuthGuard />,
    children: [{
      element: <StaffLayout />,
      children: [
        { path: '/', element: <DashboardPage /> },
        {
          element: <RoleGuard allowed={['STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN']} />,
          children: [
            { path: '/reservations', element: <ReservationsPage /> },
            { path: '/reservations/:id', element: <ReservationDetailPage /> },
          ],
        },
        {
          element: <RoleGuard allowed={['MANAGER', 'OWNER', 'SUPER_ADMIN']} />,
          children: [
            { path: '/cms', element: <CmsPage /> },
            { path: '/cms/pages', element: <CmsPagesPage /> },
            { path: '/cms/pages/:id', element: <CmsPageDetailPage /> },
            { path: '/cms/media', element: <CmsMediaPage /> },
            { path: '/cms/promotions', element: <CmsPromotionsPage /> },
            { path: '/cms/navigation', element: <CmsNavigationPage /> },
            { path: '/cms/location', element: <CmsLocationPage /> },
            { path: '/cms/scenes', element: <CmsScenesPage /> },
          ],
        },
      ],
    }],
  },
])


