import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../features/auth/auth-context'
export function StaffLayout() {
  const { session, logout } = useAuth()
  return <div className="shell"><aside><h1>Hotel PMS</h1><nav><NavLink to="/" end>Dashboard</NavLink><NavLink to="/cms">CMS</NavLink></nav></aside><div className="workspace"><header><span>{session?.user.realName || session?.user.username}</span><button type="button" onClick={logout}>退出</button></header><main><Outlet /></main></div></div>
}
