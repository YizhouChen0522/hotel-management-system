import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './auth-context'
export function AuthGuard() { const { session } = useAuth(); const location = useLocation(); return session ? <Outlet /> : <Navigate to="/login" replace state={{ from: location.pathname }} /> }
export function GuestGuard() { return useAuth().session ? <Navigate to="/" replace /> : <Outlet /> }
export function RoleGuard({ allowed }: { allowed: string[] }) { const { session } = useAuth(); return session?.roles.some((role) => allowed.includes(role)) ? <Outlet /> : <Navigate to="/" replace /> }
