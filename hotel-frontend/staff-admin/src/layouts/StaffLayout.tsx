import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../features/auth/auth-context'

const cmsLinks = [
  ['/cms/pages', '页面与区块'],
  ['/cms/media', '媒体库'],
  ['/cms/promotions', '促销内容'],
  ['/cms/navigation', '官网导航'],
  ['/cms/location', '酒店位置'],
  ['/cms/scenes', '3D 场景'],
] as const

export function StaffLayout() {
  const { session, logout } = useAuth()
  const canUseCms = session?.roles.some((role) => ['MANAGER', 'OWNER', 'SUPER_ADMIN'].includes(role))

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand"><span>HOTEL</span><strong>Operations</strong></div>
        <nav className="primary-nav">
          <NavLink to="/" end>概览</NavLink>
          <NavLink to="/reservations">预订管理</NavLink>
          {canUseCms && <NavLink to="/cms">官网内容 CMS</NavLink>}
        </nav>
        {canUseCms && (
          <div className="sidebar-section">
            <p>CMS 工具</p>
            <nav className="secondary-nav">
              {cmsLinks.map(([path, label]) => <NavLink key={path} to={path}>{label}</NavLink>)}
            </nav>
          </div>
        )}
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div><small>当前员工</small><strong>{session?.user.realName || session?.user.username}</strong></div>
          <span className="role-chip">{session?.roles.join(' · ')}</span>
          <button type="button" onClick={logout}>退出登录</button>
        </header>
        <main><Outlet /></main>
      </div>
    </div>
  )
}
