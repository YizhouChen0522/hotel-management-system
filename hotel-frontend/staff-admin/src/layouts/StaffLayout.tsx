import { useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../features/auth/auth-context'
import { apiFetch } from '../lib/api/client'

type TimeContext = {
  zoneId: string
  hotelDate: string
  businessDate: string
  businessDateStatus: string
  reason: string | null
}

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
  const [time, setTime] = useState<TimeContext | null>(null)
  const [timeError, setTimeError] = useState('')

  useEffect(() => {
    let active = true
    const refresh = () => apiFetch<TimeContext>('/api/admin/time-context')
      .then((value) => { if (active) { setTime(value); setTimeError('') } })
      .catch((reason: unknown) => { if (active) setTimeError(reason instanceof Error ? reason.message : '时间状态读取失败') })
    void refresh()
    const timer = window.setInterval(() => { void refresh() }, 60_000)
    return () => { active = false; window.clearInterval(timer) }
  }, [])
  const canUseCms = session?.roles.some((role) => ['MANAGER', 'OWNER', 'SUPER_ADMIN'].includes(role))

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand"><span>HOTEL</span><strong>Operations</strong></div>
        <nav className="primary-nav">
          <NavLink to="/" end>概览</NavLink>
          <NavLink to="/reservations">预订管理</NavLink>
          <NavLink to="/stays">住宿与退房</NavLink>
          <NavLink to="/housekeeping">客房清洁</NavLink>
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
        <div className={`time-context ${time?.businessDateStatus !== 'CURRENT' ? 'time-context-alert' : ''}`} role="status">
          {time ? <>
            <span>酒店日期 {time.hotelDate} · {time.zoneId}</span>
            <span>营业日期 {time.businessDate} · {time.businessDateStatus}</span>
            {time.reason && <span>{time.reason}</span>}
          </> : <span>{timeError || '正在读取酒店时间…'}</span>}
        </div>
        <main><Outlet /></main>
      </div>
    </div>
  )
}
